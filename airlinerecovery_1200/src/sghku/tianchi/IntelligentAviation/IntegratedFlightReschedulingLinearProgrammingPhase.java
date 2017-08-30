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

public class IntegratedFlightReschedulingLinearProgrammingPhase {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = false;
		Parameter.isReadFixedRoutes = true;
		
		runOneIteration(true, 70);
		
	}
		
	public static void runOneIteration(boolean isFractional, int fixNumber){
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
			Scanner sn = new Scanner(new File("flightdelay.csv"));
		
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
		
		List<Flight> candidateFlightList = new ArrayList<>();
		List<ConnectingFlightpair> candidateConnectingFlightList = new ArrayList<>();
		
		List<Aircraft> candidateAircraftList= new ArrayList<>();
		
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
		
		//更新base information
		for(Aircraft a:scenario.aircraftList) {
			a.flightList.get(a.flightList.size()-1).leg.destinationAirport.finalAircraftNumber[a.type-1]++;
		}
		
		for(Aircraft a:scenario.aircraftList) {
			if(a.isFixed) {				
				a.fixedDestination.finalAircraftNumber[a.type-1]--;
			}
		}
		//更新起降约束
		for(Flight f:scenario.flightList) {
			if(f.isFixed) {			
				if(scenario.affectedAirportSet.contains(f.actualOrigin)) {
					for(long i=Parameter.airportBeforeTyphoonTimeWindowStart;i<=Parameter.airportBeforeTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualTakeoffT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualOrigin.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualOrigin.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}
					
					for(long i=Parameter.airportAfterTyphoonTimeWindowStart;i<=Parameter.airportAfterTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualTakeoffT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualOrigin.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualOrigin.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}					
				}else if(scenario.affectedAirportSet.contains(f.actualDestination)) {
					for(long i=Parameter.airportBeforeTyphoonTimeWindowStart;i<=Parameter.airportBeforeTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualLandingT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualDestination.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualDestination.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}
					
					for(long i=Parameter.airportAfterTyphoonTimeWindowStart;i<=Parameter.airportAfterTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualLandingT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualDestination.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualDestination.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}	
				}			
			}
		}
		
		//更新停机约束
		for(Aircraft a:scenario.aircraftList) {
			if(a.isFixed) {
				if(a.fixedFlightList.size() > 0) {
					for(int i=0;i<a.fixedFlightList.size()-1;i++) {
						Flight f1 = a.fixedFlightList.get(i);
						Flight f2 = a.fixedFlightList.get(i+1);
					
						if(!f1.actualDestination.equals(f2.actualOrigin)) {
							System.out.println("error aircraft routes");
						}
						if(f1.actualLandingT >= f2.actualTakeoffT) {
							System.out.println("error connection time");
						}
						
						if(scenario.affectedAirportSet.contains(f1.actualDestination.id)) {
							if(f1.actualLandingT <= Parameter.airport49_50_61ParkingLimitStart && f2.actualTakeoffT >= Parameter.airport49_50_61ParkingLimitEnd) {
								int limit = scenario.affectedAirportParkingLimitMap.get(f1.actualDestination.id);
								scenario.affectedAirportParkingLimitMap.put(f1.actualDestination.id, limit-1);
								
								if(limit-1 < 0) {
									System.out.println("negative airport limit");
								}
							}
						}
						
						//判断25和67机场停机约束
						if(f1.actualDestination.id == 25){
							if(f1.actualLandingT <= Parameter.airport25_67ParkingLimitStart && f2.actualTakeoffT >= Parameter.airport25_67ParkingLimitEnd) {
								scenario.airport25ParkingLimit--;
								
								if(scenario.airport25ParkingLimit< 0) {
									System.out.println("negative airport limit");
								}
							}
						}
						if(f1.actualDestination.id == 67){
							if(f1.actualLandingT <= Parameter.airport25_67ParkingLimitStart && f2.actualTakeoffT >= Parameter.airport25_67ParkingLimitEnd) {
								scenario.airport67ParkingLimit--;
								
								if(scenario.airport67ParkingLimit< 0) {
									System.out.println("negative airport limit");
								}
							}
						}
					}
				}		
			}
		}
		
		for(Airport a:scenario.airportList){
			for(int type=0;type<Parameter.TOTAL_AIRCRAFTTYPE_NUM;type++) {
				if(a.finalAircraftNumber[type] < 0){
					System.out.println("error "+a.finalAircraftNumber+" "+a.id);
				}
			}		
		}
		
		//基于目前固定的飞机路径来进一步求解线性松弛模型
		solver(scenario, candidateAircraftList, candidateFlightList, candidateConnectingFlightList, isFractional);		
		
		//根据线性松弛模型来确定新的需要固定的飞机路径
		AircraftPathReader scheduleReader = new AircraftPathReader();
		scheduleReader.fixAircraftRoute(scenario, fixNumber);		
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
		ArcChecker.init();
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
