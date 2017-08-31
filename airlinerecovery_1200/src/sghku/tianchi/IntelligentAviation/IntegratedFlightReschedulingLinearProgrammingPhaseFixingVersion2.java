package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;

import javax.swing.plaf.synth.SynthSpinnerUI;

import checker.ArcChecker;
import sghku.tianchi.IntelligentAviation.algorithm.FlightDelayLimitGenerator;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructorBasedOnDelayAndEarlyLimit;
import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.OutputResult;
import sghku.tianchi.IntelligentAviation.common.OutputResultWithPassenger;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ClosureInfo;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightSection;
import sghku.tianchi.IntelligentAviation.entity.FlightSectionItinerary;
import sghku.tianchi.IntelligentAviation.entity.GroundArc;
import sghku.tianchi.IntelligentAviation.entity.Itinerary;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.Solution;
import sghku.tianchi.IntelligentAviation.model.CplexModel;
import sghku.tianchi.IntelligentAviation.model.CplexModelForPureAircraft;
import sghku.tianchi.IntelligentAviation.model.IntegratedCplexModel;
import sghku.tianchi.IntelligentAviation.model.PushForwardCplexModel;

public class IntegratedFlightReschedulingLinearProgrammingPhaseFixingVersion2 {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = true;
		Parameter.isReadFixedRoutes = true;
		Parameter.gap = 5;
		
		Parameter.linearsolutionfilename = "linearsolutionwithpassenger_0831_stage9.csv";
		runOneIteration(false, 70);
		/*Parameter.linearsolutionfilename = "linearsolution_0829_stage2.csv";
		runOneIteration(true, 40);*/
	}
		
	public static void runOneIteration(boolean isFractional, int fixNumber){
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
				
		FlightDelayLimitGenerator flightDelayLimitGenerator = new FlightDelayLimitGenerator();
		flightDelayLimitGenerator.setFlightDelayLimit(scenario);
		
		List<Flight> candidateFlightList = new ArrayList<>();
		List<ConnectingFlightpair> candidateConnectingFlightList = new ArrayList<>();
		
		List<Aircraft> candidateAircraftList= new ArrayList<>();
		List<Aircraft> fixedAircraftList = new ArrayList<>();
		
		for(Flight f:scenario.flightList) {
			if(!f.isFixed) {
				if(f.isIncludedInConnecting) {
					//如果联程航班另一截没被fix，则加入candidate，否则只能cancel（因为联程必须同一个飞机）
					if(!f.brotherFlight.isFixed) {  
						candidateFlightList.add(f);					
					}
				}else {
					candidateFlightList.add(f);					
				}
			}
		}
		
		for(ConnectingFlightpair cf:scenario.connectingFlightList) {
			if(!cf.firstFlight.isFixed && !cf.secondFlight.isFixed) {
				candidateConnectingFlightList.add(cf);
			}				
		}
		
		//将所有candidate flight设置为not cancel
		for(Flight f:candidateFlightList){
			f.isCancelled = false;
		}
		
		for(Aircraft a:scenario.aircraftList) {
			if(!a.isFixed) {
				candidateAircraftList.add(a);
			}else{
				fixedAircraftList.add(a);
			}
		}
		
		for(Aircraft a:candidateAircraftList){
			for(Flight f:candidateFlightList){
				if(!a.tabuLegs.contains(f.leg)){
					a.singleFlightList.add(f);
				}
			}
			for(ConnectingFlightpair cf:candidateConnectingFlightList){
				if(!a.tabuLegs.contains(cf.firstFlight.leg) && !a.tabuLegs.contains(cf.secondFlight.leg)){
					a.connectingFlightList.add(cf);
				}
			}
		}
		
		// 生成联程拉直航班
		for (int i = 0; i < candidateAircraftList.size(); i++) {
			Aircraft targetA = candidateAircraftList.get(i);

			for (ConnectingFlightpair cp : targetA.connectingFlightList) {
				Flight straightenedFlight = targetA.generateStraightenedFlight(cp);
				if (straightenedFlight != null) {
					//设置联程拉直
					targetA.straightenedFlightList.add(straightenedFlight);
					flightDelayLimitGenerator.setFlightDelayLimitForStraightenedFlight(straightenedFlight, scenario);
				}
			}
		}
		
		//加入fix aircraft航班
		for(int i=0;i<fixedAircraftList.size();i++){
			Aircraft a = fixedAircraftList.get(i);
			
			for(Flight f:a.fixedFlightList){
				a.singleFlightList.add(f);
			}
		}
		
		for(Aircraft a:fixedAircraftList){
			for(int i=0;i<a.fixedFlightList.size()-1;i++){
				Flight f1 = a.fixedFlightList.get(i);
				Flight f2 = a.fixedFlightList.get(i+1);

				if(f1.isIncludedInConnecting && f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id){

					ConnectingFlightpair cf = scenario.connectingFlightMap.get(f1.id+"_"+f2.id);
					f1.isConnectionFeasible = true;
					f2.isConnectionFeasible = true;
					
				}
			}
		}
		
		//更新base information
		for(Aircraft a:scenario.aircraftList) {
			a.flightList.get(a.flightList.size()-1).leg.destinationAirport.finalAircraftNumber[a.type-1]++;
		}
			
		//基于目前固定的飞机路径来进一步求解线性松弛模型
		solver(scenario, scenario.aircraftList, scenario.flightList, candidateConnectingFlightList, isFractional);		
		
		//根据线性松弛模型来确定新的需要固定的飞机路径
		AircraftPathReader scheduleReader = new AircraftPathReader();
		scheduleReader.fixAircraftRoute(scenario, fixNumber);	
		
		if(!isFractional){
			OutputResultWithPassenger outputResultWithPassenger = new OutputResultWithPassenger();
			outputResultWithPassenger.writeResult(scenario, "firstresult828.csv");
		}
	}
	
	//求解线性松弛模型或者整数规划模型
	public static void solver(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, List<ConnectingFlightpair> candidateConnectingFlightList, boolean isFractional) {
		buildNetwork(scenario, candidateAircraftList, candidateFlightList, 5);
		
		List<FlightSection> flightSectionList = new ArrayList<>();
		List<FlightSectionItinerary> flightSectionItineraryList = new ArrayList<>();
		
		for(Flight f:scenario.flightList) {
			flightSectionList.addAll(f.flightSectionList);
		}
		for(Itinerary ite:scenario.itineraryList) {
			flightSectionItineraryList.addAll(ite.flightSectionItineraryList);
		}
		

		//求解CPLEX模型
		//CplexModelForPureAircraft model = new CplexModelForPureAircraft();
		//Solution solution = model.run(candidateAircraftList, candidateFlightList, new ArrayList(), scenario.airportList,scenario, isFractional, true, false);		

		IntegratedCplexModel model = new IntegratedCplexModel();
		model.run(candidateAircraftList, candidateFlightList, candidateConnectingFlightList, scenario.airportList, scenario, flightSectionList, scenario.itineraryList, flightSectionItineraryList, isFractional, true);

	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, int gap) {
		
		// 每一个航班生成arc

		// 为每一个飞机的网络模型生成arc
		NetworkConstructorBasedOnDelayAndEarlyLimit networkConstructorBasedOnDelayAndEarlyLimit = new NetworkConstructorBasedOnDelayAndEarlyLimit();
		//ArcChecker.init();
		//System.out.println("total cost："+ArcChecker.totalCost+"  "+ArcChecker.totalCancelCost);
	
		for (Aircraft aircraft : candidateAircraftList) {	
			List<FlightArc> totalFlightArcList = new ArrayList<>();
			List<ConnectingArc> totalConnectingArcList = new ArrayList<>();
		
			for (Flight f : aircraft.singleFlightList) {
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForFlight(aircraft, f, scenario);
				totalFlightArcList.addAll(faList);
			}
			
			for (Flight f : aircraft.straightenedFlightList) {
				//System.out.println("straightend flight:"+f.connectingFlightpair.firstFlight.leg.originAirport+"->"+f.connectingFlightpair.secondFlight.leg.destinationAirport+"  "+f.leg.originAirport+"->"+f.leg.destinationAirport);
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForFlight(aircraft, f, scenario);
				totalFlightArcList.addAll(faList);
			}
			
			for(ConnectingFlightpair cf:aircraft.connectingFlightList){
				
				List<ConnectingArc> caList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForConnectingFlightPair(aircraft, cf, scenario);
				totalConnectingArcList.addAll(caList);
			}
			/*
			Map<String,Double> wholeFlightArcSet = ArcChecker.aircraftFlightArcMap.get(aircraft.id);
			Map<String,Double> wholeStraightenedArcSet = ArcChecker.aircraftStraightenedArcMap.get(aircraft.id);
			Map<String,Double> wholeConnectingArcSet = ArcChecker.aircraftConnectingArcMap.get(aircraft.id);
			
			Set<String> fSet = new HashSet<>();
			Set<String> sSet = new HashSet<>();
			Set<String> cSet = new HashSet<>();
			
			for(int j=totalFlightArcList.size()-1;j>=0;j--){
				FlightArc arc = totalFlightArcList.get(j);
				if(arc.flight.isStraightened){
					String label = arc.flight.connectingFlightpair.firstFlight.id+"_"+arc.flight.connectingFlightpair.secondFlight.id+"_"+arc.takeoffTime;
					if(!wholeStraightenedArcSet.keySet().contains(label)){
						totalFlightArcList.remove(j);
					}else{
						sSet.add(label);
						double flow = wholeStraightenedArcSet.get(label);
						arc.fractionalFlow = flow;
					}
				}else{
					String label = arc.flight.id+"_"+arc.takeoffTime;
					if(!wholeFlightArcSet.keySet().contains(label)){
						totalFlightArcList.remove(j);
					}else{
						fSet.add(label);
						double flow = wholeFlightArcSet.get(label);
						arc.fractionalFlow = flow;
					}
				}
			}
			
			for(int j=totalConnectingArcList.size()-1;j>=0;j--){
				ConnectingArc arc = totalConnectingArcList.get(j);
				
				String label = arc.firstArc.flight.id+"_"+arc.secondArc.flight.id+"_"+arc.firstArc.takeoffTime+"_"+arc.secondArc.takeoffTime;
		
				if(!wholeConnectingArcSet.keySet().contains(label)){
					totalConnectingArcList.remove(j);
				}else{
					//System.out.println("add label:"+label);
					cSet.add(label);
					double flow = wholeConnectingArcSet.get(label);
					arc.fractionalFlow = flow;
				}
			}
			
			for(String key:wholeConnectingArcSet.keySet()){
				if(!cSet.contains(key)){
					System.out.println("we find error "+key+"  "+aircraft.id);
				}
			}
			for(String key:wholeFlightArcSet.keySet()){
				if(!fSet.contains(key)){
					System.out.println("we find error 2 "+key);
				}
			}
			for(String key:wholeStraightenedArcSet.keySet()){
				if(!sSet.contains(key)){
					System.out.println("we find error 3 "+key);
				}
			}
			
			int n1 = totalFlightArcList.size();
			int n2 = totalConnectingArcList.size();*/
			networkConstructorBasedOnDelayAndEarlyLimit.eliminateArcs(aircraft, scenario.airportList, totalFlightArcList, totalConnectingArcList, scenario);
			/*int n3 = aircraft.flightArcList.size();
			int n4 = aircraft.connectingArcList.size();
			
			if(n1 != n3){
				System.out.println("error 1");
			}
			if(n2 != n4){
				System.out.println("error 2");
			}*/
		}
		
		
		NetworkConstructor networkConstructor = new NetworkConstructor();
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);
	}
	
}
