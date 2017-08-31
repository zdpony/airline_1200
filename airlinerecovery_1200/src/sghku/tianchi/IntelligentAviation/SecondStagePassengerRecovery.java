package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
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
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator2;
import sghku.tianchi.IntelligentAviation.entity.*;
import sghku.tianchi.IntelligentAviation.model.*;

public class SecondStagePassengerRecovery {
	
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = true;
		Parameter.isReadFixedRoutes = true;
		Parameter.gap = 5;
		Parameter.stageIndex = 2;

		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);

		runSecondStage(false,scenario);
		

		runThirdStage(false,scenario);

		OutputResultWithPassenger outputResultWithPassenger = new OutputResultWithPassenger();
		outputResultWithPassenger.writeResult(scenario, "rachel_includeThirdStage_0830.csv");	

		System.exit(1);
			
	}

	public static void runSecondStage(boolean isFractional, Scenario scenario){


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

			//Scanner sn = new Scanner(new File("delayfiles/linearsolution_30_421761.807_15.8.csv"));
			Scanner sn = new Scanner(new File("delayfiles/linearsolution_30_489295.42_967_largecancelcost.csv"));

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

		List<Flight> candidateFlightList = new ArrayList<>();
		List<ConnectingFlightpair> candidateConnectingFlightList = new ArrayList<>();

		List<Aircraft> candidateAircraftList = new ArrayList<>();
		for(int j=0;j<scenario.aircraftList.size();j++){
			Aircraft a = scenario.aircraftList.get(j);
			candidateAircraftList.add(a);
		}

		for(Aircraft a:candidateAircraftList){
			for(Flight f1:a.fixedFlightList){	

				if (!a.checkFlyViolation(f1)) {
					a.singleFlightList.add(f1);
					if(f1.isStraightened){
						f1.connectingFlightpair.firstFlight.isSelectedInSecondPhase = true;
						f1.connectingFlightpair.secondFlight.isSelectedInSecondPhase = true;
					}else{
						f1.isSelectedInSecondPhase = true;
					}
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
					f1.isConnectionFeasible = true;
					f2.isConnectionFeasible = true;

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
		//solver(scenario, scenario.aircraftList, candidateFlightList, candidateConnectingFlightList, isFractional);		
		solver(scenario, candidateAircraftList, scenario.flightList, new ArrayList(), isFractional);		

	}

	//求解线性松弛模型或者整数规划模型
	public static void solver(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, List<ConnectingFlightpair> candidateConnectingFlightList, boolean isFractional) {
		//生成flight arc 和  node
		buildNetwork(scenario, candidateAircraftList, 5); 
		//生成FlightArcItinerary用来转签disrupted passengers
		constructFlightArcItinerary(scenario);
		//
		List<FlightArcItinerary> flightArcItineraryList = new ArrayList<>();

		for(Itinerary ite:scenario.itineraryList) {
			flightArcItineraryList.addAll(ite.flightArcItineraryList);
		}

		System.out.println("candidate:"+candidateFlightList.size()+" "+candidateConnectingFlightList.size());

		SecondStageCplexModel model = new SecondStageCplexModel();
		model.run(candidateAircraftList, candidateFlightList, candidateConnectingFlightList, scenario,flightArcItineraryList,
				isFractional, false);


	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, int gap) {
		// 为每一个飞机的网络模型生成arc
		NetworkConstructorBasedOnDelayAndEarlyLimit networkConstructorBasedOnDelayAndEarlyLimit = new NetworkConstructorBasedOnDelayAndEarlyLimit();

		for (Aircraft aircraft : candidateAircraftList) {	
			List<FlightArc> totalFlightArcList = new ArrayList<>();

			for (Flight f : aircraft.singleFlightList) {
				f.isFixed = false;  //设置flight为非固定属性，否则只会给该flight生产一个arc
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForFlight(aircraft, f, scenario);
				totalFlightArcList.addAll(faList);
			}

			networkConstructorBasedOnDelayAndEarlyLimit.eliminateArcs(aircraft, scenario.airportList, totalFlightArcList, new ArrayList<>(), scenario, 1);
		}

		NetworkConstructor networkConstructor = new NetworkConstructor();
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);


		/*NetworkConstructor networkConstructor = new NetworkConstructor();

		for (Aircraft aircraft : candidateAircraftList) {	

			for (Flight f : aircraft.singleFlightList) {
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructor.generateArcForFlight(aircraft, f, 5, scenario);
			}

			for(ConnectingFlightpair cf:aircraft.connectingFlightList){
				List<ConnectingArc> caList = networkConstructor.generateArcForConnectingFlightPair(aircraft, cf, 5, false, scenario);
			}
		}


		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);*/
	}

	//计算itinerary，和FlightArcItinerary
	public static void constructFlightArcItinerary(Scenario sce) {
		/*// clear scenario 里面储存的上阶段用的itinerary
		sce.itineraryList.clear();

		// 检测每一个flight的（可被签转的）disrupted passenger, itinerary只包含可被签转的disrupted passenger
		for (Flight f : sce.flightList) {
			//只考虑time_window内
			if (f.isIncludedInTimeWindow) {
				int capacity = f.aircraft.passengerCapacity;  //分配给flight的aircraft的容量
				if(f.isCancelled || f.isStraightened){       //如果cancel或者straighten，normal passenger不能再坐这个航班了，所以capacity设为0
					capacity = 0;
				}
				int totalVolume = 0;    

				if(f.isIncludedInConnecting && !f.brotherFlight.isCancelled) {  //联程航班另一截没被cancel，要给connecting passenger留座
					totalVolume = f.connectedPassengerNumber + f.passengerNumber;
				}else {
					totalVolume = f.passengerNumber;
				}

				int cancelNum = Math.min(Math.max(0, totalVolume-capacity), f.normalPassengerNumber);  //只签转普通乘客

				if(cancelNum > 0){
					Itinerary ite = new Itinerary();
					ite.flight = f;
					ite.volume = cancelNum;

					f.itinerary = ite;

					sce.itineraryList.add(ite);
				}					
			}
		}

		// 为每一个行程检测可以替代的航班
		for (Itinerary ite :sce.itineraryList) {
			for (Flight f : sce.flightList) {    //不考虑connectingflight，因其不能承载转签乘客
				//在time_window内，起始、到达机场相同，不是原来的航班，没被cancel
				if (f.isIncludedInTimeWindow && f.leg.equals(ite.flight.leg) && f.id != ite.flight.id && !f.isCancelled) {
					// 判断时间上是够可以承载该行程
					int earliestT = f.initialTakeoffT - (f.isAllowtoBringForward ? Parameter.MAX_LEAD_TIME : 0);
					int latestT = f.initialTakeoffT + (f.isDomestic?Parameter.MAX_DELAY_DOMESTIC_TIME:Parameter.MAX_DELAY_INTERNATIONAL_TIME);

					if(latestT >= ite.flight.initialTakeoffT && earliestT <= ite.flight.initialTakeoffT + 48*60){
						ite.candidateFlightList.add(f);
					}
				}
			}		
		}
		 */
		//生成FlightArcItinerary
		for (Itinerary ite : sce.itineraryList) {
			// 生成替代航班相关的FlightArcItinerary
			for (Flight f : ite.candidateFlightList) {
				for (FlightArc fa : f.flightarcList) {
					int tkfTime = fa.takeoffTime;

					FlightArcItinerary fai = new FlightArcItinerary();
					fai.itinerary = ite;
					fai.flightArc = fa;
					fai.unitCost = -1;
					int delay = tkfTime - ite.flight.initialTakeoffT;  //in minute

					if (delay >= 0) {
						if(Parameter.passengerCostWay == 1){
							if (delay < 6 * 60) {
								fai.unitCost = delay/(60.0*30.0);   //if delay 5 minutes, cost = 0.0027
							} else if (delay >= 6 * 60 && delay < 24 * 60) {
								fai.unitCost = delay/(60.0*24.0); 
							} else if (delay >= 24 * 60 && delay < 36 * 60) {
								fai.unitCost = delay/(60.0*18.0); 
							} else if (delay >= 36 * 60 && delay <= 48 * 60) {
								fai.unitCost = delay/(60.0*16.0);
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}else{
							if (delay < 6 * 60) {
								fai.unitCost = 0;
							} else if (delay < 24 * 60 && delay >= 6 * 60) {
								fai.unitCost = 0.5;
							} else if (delay <= 48 * 60 && delay >= 24 * 60) {
								fai.unitCost = 1;
							} else {
								if(delay <= 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}
						
					}

					if (fai.unitCost > 0-1e-5) {
						ite.flightArcItineraryList.add(fai);
						fa.flightArcItineraryList.add(fai);
					}
				}


				for (ConnectingArc ca : f.connectingarcList) {

					//connecting arc's first arc
					int tkfTime = ca.firstArc.takeoffTime;

					FlightArcItinerary fai = new FlightArcItinerary();
					fai.itinerary = ite;
					fai.flightArc = ca.firstArc;
					fai.unitCost = -1;

					int delay = tkfTime - ite.flight.initialTakeoffT;  //in minute

					if (delay >= 0) {
						if(Parameter.passengerCostWay == 1){
							if (delay < 6 * 60) {
								fai.unitCost = delay/(60.0*30.0);   //if delay 5 minutes, cost = 0.0027
							} else if (delay >= 6 * 60 && delay < 24 * 60) {
								fai.unitCost = delay/(60.0*24.0); 
							} else if (delay >= 24 * 60 && delay < 36 * 60) {
								fai.unitCost = delay/(60.0*18.0); 
							} else if (delay >= 36 * 60 && delay <= 48 * 60) {
								fai.unitCost = delay/(60.0*16.0);
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}else{
							if (delay < 6 * 60) {
								fai.unitCost = 0;
							} else if (delay < 24 * 60 && delay >= 6 * 60) {
								fai.unitCost = 0.5;
							} else if (delay <= 48 * 60 && delay >= 24 * 60) {
								fai.unitCost = 1;
							} else {
								if(delay <= 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}
					}

					if (fai.unitCost > 0-1e-5) {
						ite.flightArcItineraryList.add(fai);
						ca.firstArc.flightArcItineraryList.add(fai);
					}

					//connecting arc's second arc
					tkfTime = ca.secondArc.takeoffTime;

					fai = new FlightArcItinerary();
					fai.itinerary = ite;
					fai.flightArc = ca.secondArc;
					fai.unitCost = -1;

					delay = tkfTime - ite.flight.initialTakeoffT;  //in minute

					if (delay >= 0) {
						if(Parameter.passengerCostWay == 1){
							if (delay < 6 * 60) {
								fai.unitCost = delay/(60.0*30.0);   //if delay 5 minutes, cost = 0.0027
							} else if (delay >= 6 * 60 && delay < 24 * 60) {
								fai.unitCost = delay/(60.0*24.0); 
							} else if (delay >= 24 * 60 && delay < 36 * 60) {
								fai.unitCost = delay/(60.0*18.0); 
							} else if (delay >= 36 * 60 && delay <= 48 * 60) {
								fai.unitCost = delay/(60.0*16.0);
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}else{
							if (delay < 6 * 60) {
								fai.unitCost = 0;
							} else if (delay < 24 * 60 && delay >= 6 * 60) {
								fai.unitCost = 0.5;
							} else if (delay <= 48 * 60 && delay >= 24 * 60) {
								fai.unitCost = 1;
							} else {
								if(delay <= 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}	
					}

					if (fai.unitCost > 0-1e-5) {
						ite.flightArcItineraryList.add(fai);
						ca.secondArc.flightArcItineraryList.add(fai);
					}
				}
			}
		}
	}

	public static void runThirdStage(boolean isFractional,Scenario sce){
		//计算每个flight剩余的座位数
		for(Flight f:sce.flightList) {
			if(f.isIncludedInTimeWindow) {
				if(f.isCancelled) {
					f.remainingSeatNum = 0;
					//if(f.id==246 || f.id==246)System.out.println("f_"+f.id+" cancell");

				}else {
					int totalPassenger = 0;
					if(f.isIncludedInConnecting && f.brotherFlight.isCancelled) {
						//如果是联程，且另一截cancel了，位子也可以空出来，不加入total passenger
					}else {
						totalPassenger += f.connectedPassengerNumber;  //优先承载联程旅客（不能签转出去）
					}
					if(f.id==1749 || f.id==1749)System.out.println("f_"+f.id+" firstTransferNum "+f.firstTransferPassengerNumber+" secondTransferNum "+f.secondTransferPassengerNumber);

					totalPassenger += f.firstTransferPassengerNumber; //优先承载第一截转乘旅客（不能签转出去）
					//totalPassenger += f.secondTransferPassengerNumber; //优先承载第一截转乘旅客（能签转出去）
					//如果飞机容量容不下以上两类“重要”乘客，则得出剩余座位数为0
					if(f.aircraft.passengerCapacity<=totalPassenger) {
						System.out.println("error! connecting and firstTransfer passengers on the flight already exceeds its capacity: flight id "+f.id);
						System.exit(1);
					}

					totalPassenger += f.normalPassengerNumber; //普通乘客总数

					//减去cancel的普通乘客数
					totalPassenger -= f.normalPassengerCancelNum;

					//减去签转出去的普通旅客
					if(f.itinerary!=null) {  //如果在上阶段有itinerary赋给f
						double totalPss = f.itinerary.volume;
						//System.out.println("itinerary exists! passenger in itinerary"+totalPss+" cancel "+f.normalPassengerCancelNum);

						for(FlightArcItinerary fai:f.itinerary.flightArcItineraryList) {
							if(fai.volume>0) {//有普通乘客签转出去，于是标记不能再接受其他航班签转乘客进来(注意！自己航班上的第二截转乘乘客是可以的，所以remainingSeatNum不能设为0)
								f.canAcceptSignChangePssgr = false;  
								totalPassenger -= (int)Math.round(fai.volume);
								//System.out.println(" some sign out normal passengers ");
							}
						}

					}


					//加上签转进来的普通旅客，有签转进来的乘客，则不能考虑上面的第二截transfer乘客
					for(FlightArcItinerary fai:f.flightArcItineraryList) {
						
						if(fai.volume>0) {
							f.canSignOutTransfer = false;
							totalPassenger += (int)Math.round(fai.volume);
							if(f.id==1749)System.out.println(" sign in normal passengers :"+fai.volume+ "total passenger after add "+totalPassenger);
						}
					}
					if(f.aircraft.passengerCapacity - totalPassenger<0) {
						System.out.println("error! total passenger on the flight exceeds its capacity: flight id "+f.id+" aircraft capacity "+f.aircraft.passengerCapacity+" total passenger "+totalPassenger);	
						System.out.println("total second transfer "+f.secondTransferPassengerNumber);
						System.exit(1);
					}else {
						f.remainingSeatNum = f.aircraft.passengerCapacity - totalPassenger;
					}
					if(f.id==1749)System.out.println("f_id:"+f.id+"-> remaining seat:"+f.remainingSeatNum);

				}
			}

		}

		//根据second transfer passenger是否被disrupted来进一步确定每个flight remaining seatNum 
		for(TransferPassenger tp:sce.transferPassengerList) {
			if(tp.outFlight.isIncludedInTimeWindow) {
				if(tp.inFlight.isCancelled) {
					if(!tp.outFlight.isCancelled) {  //如果第一截cancel，第二截flight没被cancel，

					}		
				}
				else if(tp.outFlight.isCancelled) {   //如果第二截flight cancel了，对remaining seat没影响

				}

				else if(tp.outFlight.actualTakeoffT-tp.inFlight.actualLandingT<tp.minTurnaroundTime){  //如果miss-connection

				}

				else {   //如果第二截没受影响，则仍要把位子占住，remainingSeatNum 减少
					if(tp.outFlight.remainingSeatNum>=tp.volume) {
						tp.outFlight.remainingSeatNum -= tp.volume;  
					}else {
						System.out.println("error! insufficient seat to serve these second transfers! f_id:"+
								tp.outFlight.id+" seatNum:"+tp.outFlight.remainingSeatNum+" secondTransferNum:"+tp.volume);
					}

				}
			}

		}


		//flight按actual takeoff time 从小往大排序
		List<Flight> sortedFlightList = new ArrayList<>();
		sortedFlightList.addAll(sce.flightList);
		Collections.sort(sortedFlightList, new FlightComparator2());  


		//计算每个flight上等待签转的转乘乘客（第二截），因为如果第一截flight cancel，则不能转签，只能cancel
		for(TransferPassenger tp:sce.transferPassengerList) {
			if(tp.outFlight.isIncludedInTimeWindow) {
				if(!tp.outFlight.canSignOutTransfer) {  //如果已经接受过普通乘客签转，则不能签转第二截转乘乘客出去了
					continue;
				}
				if(tp.inFlight.isCancelled) {
					if(!tp.outFlight.isCancelled) {  //如果第一截cancel，第二截flight没被cancel，这些乘客不能recover

					}		
				}
				//如果第一截flight没被cancel，则所有第一截转乘乘客都已经坐上飞机了(因为他们有优先上飞机权！)，判断第二截flight
				else if(tp.outFlight.isCancelled) {   //如果第二截flight cancel了
					//tp.outFlight.disruptedSecondTransferPssgrNum += tp.volume;		//如果cancel，把对应的volume加到flight信息里	
					for(Flight signToFlight:sortedFlightList) {
						//如果可以承接签转旅客，没被cancel，leg一致
						if(signToFlight.isIncludedInTimeWindow && signToFlight.canAcceptSignChangePssgr && tp.outFlight.leg.equals(signToFlight.leg) 
								&& tp.outFlight.id != signToFlight.id && !signToFlight.isCancelled) {
							//签转航班要留足turnaround_time
							if(signToFlight.actualTakeoffT >= tp.inFlight.actualLandingT + tp.minTurnaroundTime 
									&& signToFlight.actualTakeoffT  <= tp.outFlight.initialTakeoffT + 48*60
									&& signToFlight.actualTakeoffT >= tp.outFlight.initialTakeoffT
									&& signToFlight.remainingSeatNum >0) {
								//有空位接受转乘乘客，则outFLight被标记成不能接受其他签转
								tp.outFlight.canAcceptSignChangePssgr = false;
								int vol = tp.volume - signToFlight.remainingSeatNum;

								if(vol>0) {
									if(!tp.outFlight.transferSignChangeMap.keySet().contains(signToFlight.id)) {
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,signToFlight.remainingSeatNum);
									}else {
										int signToNum = tp.outFlight.transferSignChangeMap.get(signToFlight.id) + signToFlight.remainingSeatNum;
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,signToNum);
									}

									signToFlight.remainingSeatNum = 0;
									tp.volume = vol;
								}else {
									if(!tp.outFlight.transferSignChangeMap.keySet().contains(signToFlight.id)) {
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,tp.volume);
									}else {
										int signToNum = tp.outFlight.transferSignChangeMap.get(signToFlight.id) + tp.volume;
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,signToNum);
									}
									tp.volume = 0;  //完全被recover
									signToFlight.remainingSeatNum = -vol;
									break;
								}
							}
						}
					}
				}
				//如果两截都没cancel，判断是否miss-connection
				else if(tp.outFlight.actualTakeoffT-tp.inFlight.actualLandingT<tp.minTurnaroundTime){  //如果miss-connection
					//tp.outFlight.disruptedSecondTransferPssgrNum += tp.volume;  //把对应的volume加到flight信息里
					tp.outFlight.remainingSeatNum += tp.volume;  //第二截的remainingSeatNum会增加
					for(Flight signToFlight:sortedFlightList) {
						//如果可以承接签转旅客，没被cancel，leg一致
						if(signToFlight.isIncludedInTimeWindow && signToFlight.canAcceptSignChangePssgr && tp.outFlight.leg.equals(signToFlight.leg) 
								&& tp.outFlight.id != signToFlight.id && !signToFlight.isCancelled) {
							//签转航班要留足turnaround_time
							if(signToFlight.actualTakeoffT >= tp.inFlight.actualLandingT + tp.minTurnaroundTime 
									&& signToFlight.actualTakeoffT  <= tp.outFlight.initialTakeoffT + 48*60
									&& signToFlight.actualTakeoffT >= tp.outFlight.initialTakeoffT
									&& signToFlight.remainingSeatNum >0) {
								//有空位接受转乘乘客，则outFLight被标记成不能接受其他签转
								tp.outFlight.canAcceptSignChangePssgr = false;
								int vol = tp.volume - signToFlight.remainingSeatNum;

								if(vol>0) {
									if(!tp.outFlight.transferSignChangeMap.keySet().contains(signToFlight.id)) {
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,signToFlight.remainingSeatNum);
									}else {
										int signToNum = tp.outFlight.transferSignChangeMap.get(signToFlight.id) + signToFlight.remainingSeatNum;
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,signToNum);
									}

									signToFlight.remainingSeatNum = 0;
									tp.volume = vol;
								}else {
									if(!tp.outFlight.transferSignChangeMap.keySet().contains(signToFlight.id)) {
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,tp.volume);
									}else {
										int signToNum = tp.outFlight.transferSignChangeMap.get(signToFlight.id) + tp.volume;
										tp.outFlight.transferSignChangeMap.put(signToFlight.id,signToNum);
									}
									tp.volume = 0;  //完全被recover
									signToFlight.remainingSeatNum = -vol;
									break;
								}
							}
						}
					}

				}
			}	
		}

		/*for(Flight f:sortedFlightList) {
			if(!f.transferSignChangeMap.isEmpty()) {
				System.out.print(f.id+":");
				for(Integer signTo:f.transferSignChangeMap.keySet()) {
					System.out.print(signTo+" num "+f.transferSignChangeMap.get(signTo)+",");
				}
				System.out.println();
			}
		}*/


		for(Flight f:sortedFlightList) {
			if(!f.transferSignChangeMap.isEmpty()) {
				for(Integer signToFlightID:f.transferSignChangeMap.keySet()) {
					Flight signToFlight = sce.flightList.get(signToFlightID-1);
					boolean findExistingFai = false;
					for(FlightArcItinerary fai:signToFlight.flightArcItineraryList) {
						if(fai.itinerary.flight.id==f.id) {  //已经有从f到signToFlight的转签记录
							fai.volume += f.transferSignChangeMap.get(signToFlightID);
							findExistingFai = true;
							break;
						}
					}
					if(!findExistingFai) {
						Itinerary ite = new Itinerary();
						ite.flight = f;
						FlightArc fa = new FlightArc();
						fa.flight = signToFlight;

						FlightArcItinerary fai = new FlightArcItinerary();			
						fai.itinerary = ite;
						fai.flightArc = fa;
						fai.volume = f.transferSignChangeMap.get(signToFlightID);
						signToFlight.flightArcItineraryList.add(fai);
						f.itinerary.flightArcItineraryList.add(fai);  
					}
				}
			}
		}

	}

}
