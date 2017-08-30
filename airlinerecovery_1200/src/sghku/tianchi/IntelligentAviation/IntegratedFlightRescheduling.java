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

import sghku.tianchi.IntelligentAviation.algorithm.FlightDelayLimitGenerator;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructorBasedOnDelayAndEarlyLimit;
import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.OutputResult;
import sghku.tianchi.IntelligentAviation.common.OutputResultWithPassenger;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
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

public class IntegratedFlightRescheduling {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = true;
		Parameter.isReadFixedRoutes = true;
		
		runOneIteration(false);
		
	}
		
	public static void runOneIteration(boolean isFractional){
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		
		
		FlightDelayLimitGenerator flightDelayLimitGenerator = new FlightDelayLimitGenerator();
		flightDelayLimitGenerator.setFlightDelayLimit(scenario);
		
		/*for(Flight f:scenario.flightList){
			System.out.print(f.id+"  ");
			for(int[] timeLimit:f.timeLimitList){
				System.out.print("["+timeLimit[0]+","+timeLimit[1]+"] ");
			}
			System.out.println();
		}
		
		try {

			Scanner sn = new Scanner(new File("delayfiles/linearsolution_30_421761.807_15.8.csv"));
		
			sn.nextLine();
			while(sn.hasNextLine()){
				String nextLine = sn.nextLine();
				if(nextLine.trim().equals("")){
					break;
				}
				
				Scanner innerSn = new Scanner(nextLine);
				innerSn.useDelimiter(",");
				int fId = innerSn.nextInt();
				
				Flight f = scenario.flightList.get(fId-1);
				
				innerSn.next();
				innerSn.next();
				innerSn.next();
				innerSn.next();
				innerSn.next();
				
				String[] delayArray = innerSn.next().split("_");
			
				for(String delay:delayArray){
					int d = Integer.parseInt(delay);
					int t = f.initialTakeoffT + d;
					boolean isInclude = false;
					for(int[] timeLimit:f.timeLimitList){
						int startT = timeLimit[0];
						int endT = timeLimit[1];
						
						if(t >= startT && t <= endT){
							isInclude = true;
							break;
						}
					}
					
					if(!isInclude){
						System.out.println("error "+f.id+" delay:"+d+"  actual time:"+t);
					}
				}
				
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.exit(1);*/
		
		System.out.println("---------------this way ---------");
		
		List<Flight> candidateFlightList = new ArrayList<>();
		List<ConnectingFlightpair> candidateConnectingFlightList = new ArrayList<>();
		
		int nnn = 0;
		for(Aircraft a:scenario.aircraftList){
			
			nnn += a.fixedFlightList.size();
			
			for(Flight f1:a.fixedFlightList){				
				if (!a.checkFlyViolation(f1)) {
					a.singleFlightList.add(f1);
				}else{
					System.out.println("航班飞机匹配错误");
				}
				
				candidateFlightList.add(f1);
			}
			
			for(int i=0;i<a.fixedFlightList.size()-1;i++){
				Flight f1 = a.fixedFlightList.get(i);
				Flight f2 = a.fixedFlightList.get(i+1);
				
				f1.isShortConnection = false;
				f2.isShortConnection = false;
				
				Integer connT = scenario.shortConnectionMap.get(f1.id+"_"+f2.id);
				if(connT != null){
					f1.isShortConnection = true;
					f1.shortConnectionTime = connT;
				}

				if((f1.actualLandingT+(f1.isShortConnection?f1.shortConnectionTime:50)) > f2.actualTakeoffT){
					System.out.println("connection error  "+f1.actualLandingT+"  "+f2.actualTakeoffT+" "+f1.isIncludedInTimeWindow+" "+f2.isIncludedInTimeWindow+" "+f1.isShortConnection+" "+f1.shortConnectionTime+" "+f1.id+" "+f2.id+" "+f1.leg.destinationAirport.id);
				}
				
				if(f1.isIncludedInConnecting && f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id){
					ConnectingFlightpair cf = scenario.connectingFlightMap.get(f1.id+"_"+f2.id);
					
					if(cf != null){
						a.connectingFlightList.add(cf);
						candidateConnectingFlightList.add(cf);
					}else{
						System.out.println("null connecting flight");
					}
				}
			}
		}
		
		//更新base information
		for(Aircraft a:scenario.aircraftList) {
			a.flightList.get(a.flightList.size()-1).leg.destinationAirport.finalAircraftNumber[a.type-1]++;
		}
		
		//基于目前固定的飞机路径来进一步求解线性松弛模型
		solver(scenario, scenario.aircraftList, candidateFlightList, candidateConnectingFlightList, isFractional);		
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
		model.run(candidateAircraftList, candidateFlightList, candidateConnectingFlightList, scenario.airportList, scenario, flightSectionList, scenario.itineraryList, flightSectionItineraryList, isFractional, false);

		OutputResultWithPassenger outputResultWithPassenger = new OutputResultWithPassenger();
		outputResultWithPassenger.writeResult(scenario, "firstresult825.csv");
	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, int gap) {
		
		// 每一个航班生成arc

		// 为每一个飞机的网络模型生成arc
		NetworkConstructorBasedOnDelayAndEarlyLimit networkConstructorBasedOnDelayAndEarlyLimit = new NetworkConstructorBasedOnDelayAndEarlyLimit();

		int nnn = 0;
		for (Aircraft aircraft : candidateAircraftList) {	
			List<FlightArc> totalFlightArcList = new ArrayList<>();
			List<ConnectingArc> totalConnectingArcList = new ArrayList<>();
			
			System.out.println("aircraft:"+aircraft.id+"  "+aircraft.singleFlightList.size()+" "+aircraft.connectingFlightList.size());
			
			for (Flight f : aircraft.singleFlightList) {
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForFlight(aircraft, f, scenario);
				totalFlightArcList.addAll(faList);
				System.out.print(faList.size()+",");
			}
			System.out.println();
	
			for(ConnectingFlightpair cf:aircraft.connectingFlightList){
				List<ConnectingArc> caList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForConnectingFlightPair(aircraft, cf, scenario);
				totalConnectingArcList.addAll(caList);
				System.out.print(caList.size()+":");
			}
			System.out.println();
			
			//networkConstructorBasedOnDelayAndEarlyLimit.eliminateArcs(aircraft, scenario.airportList, totalFlightArcList, totalConnectingArcList, scenario);
		
			System.out.println(totalFlightArcList.size()+" "+totalConnectingArcList.size()+"  "+aircraft.flightArcList.size()+"  "+aircraft.connectingArcList.size());
			nnn += aircraft.flightArcList.size();
			nnn += aircraft.connectingArcList.size();
		}
		System.out.println("nnn:"+nnn);
		System.exit(1);
		NetworkConstructor networkConstructor = new NetworkConstructor();
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);
	}
	
}
