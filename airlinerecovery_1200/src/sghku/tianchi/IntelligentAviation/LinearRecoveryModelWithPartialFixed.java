package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.OutputResult;
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
import sghku.tianchi.IntelligentAviation.model.PushForwardCplexModel;

public class LinearRecoveryModelWithPartialFixed {

	public static int gap = 30;

	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = false;
		Parameter.isReadFixedRoutes = true;
		
		//前面两个循环求解线性松弛模型
		//runOneIteration(70,true);   //(iteration结束后固定住的aircraft route，是否解LP)
		//runOneIteration(50, true);
		//runOneIteration(10, true);
		runOneIteration(12, false);
		//最后一个循环直接解整数规划模型
		//runOneIteration(32, false);
		
		//将航班时刻尽可能往前推进
		//pushforward();
	}
		
	public static void runOneIteration(int fixNumber, boolean isFractional){
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		
		//设定每一个flight都被affect到
		int changedNum = 0;
		for(Flight f:scenario.flightList){
			if(f.isIncludedInTimeWindow){
				if(!f.isAffected){
					changedNum++;
				}
				f.isAffected = true;
			}
		}
		System.out.println("changedNum:"+changedNum);
		
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
		
		System.out.println("candidate:"+candidateAircraftList.size()+" "+candidateFlightList.size()+" "+candidateConnectingFlightList.size());
		
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
		//创建网络模型
		buildNetwork(scenario, candidateAircraftList, candidateFlightList, candidateConnectingFlightList, gap);
		
		//System.exit(1);
		//求解CPLEX模型
		CplexModel model = new CplexModel();
		Solution solution = model.run(candidateAircraftList, candidateFlightList, candidateConnectingFlightList, scenario.airportList,scenario, new ArrayList(), new ArrayList(), new ArrayList(), isFractional, true, true);		
	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, List<ConnectingFlightpair> candidateConnectingFlightList, int gap) {
	
		// 计算当前问题需要考虑的航班
		for (Aircraft a : candidateAircraftList) {
			
			for(Flight f1:candidateFlightList) {
				if (!a.checkFlyViolation(f1)) {
					a.singleFlightList.add(f1);
				}
			}
					
			for(ConnectingFlightpair cf:candidateConnectingFlightList) {				
				if (!a.checkFlyViolation(cf)) {					
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
					targetA.straightenedFlightList.add(straightenedFlight);
					
				}
			}
		}
		
		
		// 每一个航班生成arc

		// 为每一个飞机的网络模型生成arc
		NetworkConstructor networkConstructor = new NetworkConstructor();
		for (Aircraft aircraft : candidateAircraftList) {
	
			for (Flight f : aircraft.singleFlightList) {
				List<FlightArc> faList = networkConstructor.generateArcForFlight(aircraft, f, gap, scenario);			
			}

			for (Flight f : aircraft.straightenedFlightList) {
				List<FlightArc> faList = networkConstructor.generateArcForFlight(aircraft, f, gap, scenario);
			}
			for (Flight f : aircraft.deadheadFlightList) {
				List<FlightArc> faList = networkConstructor.generateArcForFlight(aircraft, f, gap, scenario);
			}

			for (ConnectingFlightpair cf : aircraft.connectingFlightList) {
				List<ConnectingArc> caList = networkConstructor.generateArcForConnectingFlightPair(aircraft, cf, gap,
						false, scenario);
			}			
		}
		
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);
	}
	
	/*//将航班时刻尽量往前推
	public static void pushforward() {
		
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);

		//RecoverySolver solver = new RecoverySolver(scenario);

		// 读取初始解
		ScheduleReader scheduleReader = new ScheduleReader();
		scheduleReader.readV2(scenario);

		for (Flight f : scenario.flightList) {
			f.isCancelled = true;
		}
		for (Aircraft a : scenario.aircraftList) {
			for (Flight f : a.flightList) {
				if (f.isStraightened) {
					f.connectingFlightpair.firstFlight.isCancelled = false;
					f.connectingFlightpair.secondFlight.isCancelled = false;
				} else if (f.isDeadhead) {

				} else {
					f.isCancelled = false;
				}
			}
		}

		PushForwardCplexModel pushforward = new PushForwardCplexModel();
		pushforward.run(scenario.aircraftList);

		// 根据最终信息更新航班的信息
		solver.updateFlightList();

		String outputName = "result/sghku_20170715_" + Parameter.fileIndex + ".csv";
		Parameter.fileIndex++;

		OutputResult opr = new OutputResult();
		opr.writeResult(scenario, outputName);

		File fff = new File("current.csv");
		if (fff.exists()) {
			fff.delete();
		}
		outputName = "current.csv";
		opr.writeResult(scenario, outputName);

		long endTime = System.currentTimeMillis();
		System.out.println(
				"computation time: " + 0 + " obj:" + scenario.calculateObj());

	}*/
}
