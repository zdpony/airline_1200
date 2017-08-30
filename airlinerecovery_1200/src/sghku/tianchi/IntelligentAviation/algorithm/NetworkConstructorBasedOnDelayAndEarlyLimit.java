package sghku.tianchi.IntelligentAviation.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.NodeComparator;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.Failure;
import sghku.tianchi.IntelligentAviation.entity.FailureType;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightSection;
import sghku.tianchi.IntelligentAviation.entity.GroundArc;
import sghku.tianchi.IntelligentAviation.entity.Leg;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Node;
import sghku.tianchi.IntelligentAviation.entity.ParkingInfo;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.TransferPassenger;

/**
 * @author ych Construct a feasible route (a sequence of flights) for all
 *         aircraft
 *
 */
public class NetworkConstructorBasedOnDelayAndEarlyLimit {
	public int presetGap = 5;

	
	// 第一种生成方法，根据原始schedule大范围生成arc
	public List<FlightArc> generateArcForFlight(Aircraft aircraft, Flight f, Scenario scenario) {
		List<FlightArc> generatedFlightArcList = new ArrayList<>();

		
		FlightArc arc = null;

		if (!f.isIncludedInTimeWindow) {
			// 因为在调整窗口外，不存在单独的联程航班
			// if(!f.isIncludedInConnecting){
			// 如果该航班在调整时间窗口外
			if (f.initialAircraft.id == aircraft.id) {
				arc = new FlightArc();
				arc.flight = f;
				arc.aircraft = aircraft;
				arc.delay = f.fixedTakeoffTime - f.initialTakeoffT;

				arc.takeoffTime = f.fixedTakeoffTime;
				arc.landingTime = f.fixedLandingTime;

				arc.readyTime = arc.landingTime
						+ (f.isShortConnection ? f.shortConnectionTime : Parameter.MIN_BUFFER_TIME);

				generatedFlightArcList.add(arc);								
			}
		} else {
			if(f.isFixed){
				arc = new FlightArc();
				arc.flight = f;
				arc.aircraft = aircraft;
				if(f.actualTakeoffT >= f.initialTakeoffT){
					arc.delay = f.actualTakeoffT - f.initialTakeoffT;
				}else{
					arc.delay = f.initialTakeoffT - f.actualTakeoffT;
				}
				
				arc.takeoffTime = f.actualTakeoffT;
				arc.landingTime = f.actualLandingT;

				arc.readyTime = arc.landingTime
						+ (f.isShortConnection ? f.shortConnectionTime : Parameter.MIN_BUFFER_TIME);

				if (scenario.affectedAirportSet.contains(f.leg.originAirport.id)) {
					int tkfTime = arc.takeoffTime;
					if ((tkfTime >= Parameter.airportBeforeTyphoonTimeWindowStart
							&& tkfTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
							|| (tkfTime >= Parameter.airportAfterTyphoonTimeWindowStart
									&& tkfTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
						arc.isWithinAffectedRegionOrigin = true;
					}
				} else if (scenario.affectedAirportSet.contains(f.leg.destinationAirport.id)) {
					int ldnTime = arc.landingTime;
					if ((ldnTime >= Parameter.airportBeforeTyphoonTimeWindowStart
							&& ldnTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
							|| (ldnTime >= Parameter.airportAfterTyphoonTimeWindowStart
									&& ldnTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
						arc.isWithinAffectedRegionDestination = true;
					}
				}
				
				generatedFlightArcList.add(arc);
			}else{
				// 2.1 check whether f can be brought forward and generate earliness
				// arcs
				// 读取该航班的飞行时间
				int flyTime = f.flyTime;

				if (f.isDeadhead) {
					flyTime = f.leg.flytimeArray[aircraft.type - 1];
				} else if (f.isStraightened) {
					flyTime = f.leg.flytimeArray[aircraft.type - 1];
					if (flyTime <= 0) {
						flyTime = f.connectingFlightpair.firstFlight.initialLandingT
								- f.connectingFlightpair.firstFlight.initialTakeoffT
								+ f.connectingFlightpair.secondFlight.initialLandingT
								- f.connectingFlightpair.secondFlight.initialTakeoffT;
					}
				}

				for (int[] timeLimit : f.timeLimitList) {
					// 读取时间段的开始和结束时间
					int startTime = timeLimit[0];
					int endTime = timeLimit[1];
					
					for (int t = startTime; t <= endTime; t += presetGap) {
						int i = (t - f.initialTakeoffT) / presetGap;

						boolean isOriginInAffectedLdnTkfLimitPeriod = false;
						boolean isDestinationInAffectedLdnTkfLimitPeriod = false;

						if (scenario.affectedAirportSet.contains(f.leg.originAirport.id)) {
							int tkfTime = f.initialTakeoffT + i * presetGap;
							if ((tkfTime >= Parameter.airportBeforeTyphoonTimeWindowStart
									&& tkfTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
									|| (tkfTime >= Parameter.airportAfterTyphoonTimeWindowStart
											&& tkfTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isOriginInAffectedLdnTkfLimitPeriod = true;
							}
						} else if (scenario.affectedAirportSet.contains(f.leg.destinationAirport.id)) {
							int ldnTime = f.initialLandingT + i * presetGap;
							if ((ldnTime >= Parameter.airportBeforeTyphoonTimeWindowStart
									&& ldnTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
									|| (ldnTime >= Parameter.airportAfterTyphoonTimeWindowStart
											&& ldnTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isDestinationInAffectedLdnTkfLimitPeriod = true;
							}
						}

						if ((i * presetGap) % Parameter.gap != 0 && t != endTime && t != startTime) { // 小gap，只有当其受影响时才继续生成相应arc,
																				// 同时当t在最后的时候也需要选择
							if (!isOriginInAffectedLdnTkfLimitPeriod && !isDestinationInAffectedLdnTkfLimitPeriod) {
								continue;
							}
						}

						arc = new FlightArc();
						arc.flight = f;
						arc.aircraft = aircraft;
						if (i < 0) {
							arc.earliness = -i * presetGap;
						} else {
							arc.delay = i * presetGap;
						}

						arc.takeoffTime = f.initialTakeoffT + i * presetGap;
						arc.landingTime = arc.takeoffTime + flyTime;

						// arc.readyTime = arc.landingTime +
						// Parameter.MIN_BUFFER_TIME;
						arc.readyTime = arc.landingTime
								+ (f.isShortConnection ? f.shortConnectionTime : Parameter.MIN_BUFFER_TIME);

						arc.isWithinAffectedRegionOrigin = isOriginInAffectedLdnTkfLimitPeriod;
						arc.isWithinAffectedRegionDestination = isDestinationInAffectedLdnTkfLimitPeriod;
						
						if (!arc.checkViolation()) {

							generatedFlightArcList.add(arc);
							
						}
					}
				}
			}
			
		}

		return generatedFlightArcList;
	}

	// 为联程航班生成arc
	public List<ConnectingArc> generateArcForConnectingFlightPair(Aircraft aircraft, ConnectingFlightpair cf,
			 Scenario scenario) {

		List<ConnectingArc> generatedConnectingArcList = new ArrayList<>();

		int connectionTime = Math.min(cf.secondFlight.initialTakeoffT - cf.firstFlight.initialLandingT,
				Parameter.MIN_BUFFER_TIME);

		// 如果该联程航班在调整窗口之外
		if (!cf.firstFlight.isIncludedInTimeWindow) {
			if (cf.firstFlight.initialAircraft.id == aircraft.id) {

				// 构建第一个flight arc

				FlightArc firstArc = new FlightArc();
				firstArc.flight = cf.firstFlight;
				firstArc.aircraft = aircraft;
				firstArc.delay = cf.firstFlight.fixedTakeoffTime - cf.firstFlight.initialTakeoffT;

				firstArc.takeoffTime = cf.firstFlight.initialTakeoffT + firstArc.delay;
				firstArc.landingTime = firstArc.takeoffTime + cf.firstFlight.flyTime;
				firstArc.readyTime = firstArc.landingTime + connectionTime;

				// 构建第二个flight arc
				FlightArc secondArc = new FlightArc();
				secondArc.flight = cf.secondFlight;
				secondArc.aircraft = aircraft;
				secondArc.delay = cf.secondFlight.fixedTakeoffTime - cf.secondFlight.initialTakeoffT;
				secondArc.takeoffTime = cf.secondFlight.initialTakeoffT + secondArc.delay;
				secondArc.landingTime = secondArc.takeoffTime + cf.secondFlight.flyTime;
				secondArc.readyTime = secondArc.landingTime + (cf.secondFlight.isShortConnection
						? cf.secondFlight.shortConnectionTime : Parameter.MIN_BUFFER_TIME);

				ConnectingArc ca = new ConnectingArc();
				ca.firstArc = firstArc;
				ca.secondArc = secondArc;
				ca.aircraft = aircraft;
				ca.connectingFlightPair = cf;

				generatedConnectingArcList.add(ca);
				
				
			}
		} else {
			// otherwise, create a set of connecting arcs for this connecting
			// flight
			List<FlightArc> firstFlightArcList = new ArrayList<>();
	
			if (!aircraft.tabuLegs.contains(cf.firstFlight.leg) && !aircraft.tabuLegs.contains(cf.secondFlight.leg)) {
				// only if this leg is not in the tabu list of the corresponding
				// aircraft

				for (int[] timeLimit : cf.firstFlight.timeLimitList) {
					int startTime = timeLimit[0];
					int endTime = timeLimit[1];

					for (int t = startTime; t <= endTime; t += presetGap) {
						int i = (t - cf.firstFlight.initialTakeoffT) / presetGap;

						FlightArc arc = null;

						boolean isOriginInAffectedLdnTkfPeriod = false;
						boolean isDestinationInAffectedLdnTkfPeriod = false;

						if (scenario.affectedAirportSet.contains(cf.firstFlight.leg.originAirport.id)) {
							int tkfTime = cf.firstFlight.initialTakeoffT + i * presetGap;
							if ((tkfTime >= Parameter.airportBeforeTyphoonTimeWindowStart
									&& tkfTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
									|| (tkfTime >= Parameter.airportAfterTyphoonTimeWindowStart
											&& tkfTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isOriginInAffectedLdnTkfPeriod = true;
							}
						} else if (scenario.affectedAirportSet.contains(cf.firstFlight.leg.destinationAirport.id)) {
							int ldnTime = cf.firstFlight.initialLandingT + i * presetGap;
							if ((ldnTime >= Parameter.airportBeforeTyphoonTimeWindowStart
									&& ldnTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
									|| (ldnTime >= Parameter.airportAfterTyphoonTimeWindowStart
											&& ldnTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isDestinationInAffectedLdnTkfPeriod = true;
							}
						}

						if ((i * presetGap) % Parameter.gap != 0 && t != endTime && t != startTime) {
							if (!isOriginInAffectedLdnTkfPeriod && !isDestinationInAffectedLdnTkfPeriod) {
								continue;
							}
						}

						arc = new FlightArc();
						arc.flight = cf.firstFlight;
						arc.aircraft = aircraft;
						if (i < 0) {
							arc.earliness = -i * presetGap;
						} else {
							arc.delay = i * presetGap;
						}

						arc.takeoffTime = cf.firstFlight.initialTakeoffT + i * presetGap;
						arc.landingTime = arc.takeoffTime + cf.firstFlight.flyTime;
						arc.readyTime = arc.landingTime + connectionTime;

						arc.isWithinAffectedRegionOrigin = isOriginInAffectedLdnTkfPeriod;
						arc.isWithinAffectedRegionDestination = isDestinationInAffectedLdnTkfPeriod;
						
						if (!arc.checkViolation()) {
							firstFlightArcList.add(arc);
						}
					}
				}
			}
			
			for (FlightArc firstArc : firstFlightArcList) {

				for (int[] timeLimit : cf.secondFlight.timeLimitList) {
					int startTime = timeLimit[0];
					int endTime = timeLimit[1];

					
					
					for (int t = startTime; t <= endTime; t += presetGap) {
						
						int i = (t - cf.secondFlight.initialTakeoffT) / presetGap;

						boolean isWithinAffectedRegionOrigin2 = false;
						boolean isWithinAffectedRegionDestination2 = false;

						if (scenario.affectedAirportSet.contains(cf.secondFlight.leg.originAirport.id)) {
							int tkfTime = cf.secondFlight.initialTakeoffT + i * presetGap;
							if ((tkfTime >= Parameter.airportBeforeTyphoonTimeWindowStart
									&& tkfTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
									|| (tkfTime >= Parameter.airportAfterTyphoonTimeWindowStart
											&& tkfTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isWithinAffectedRegionOrigin2 = true;
							}
						} else if (scenario.affectedAirportSet.contains(cf.secondFlight.leg.destinationAirport.id)) {
							int ldnTime = cf.secondFlight.initialLandingT + i * presetGap;
							if ((ldnTime >= Parameter.airportBeforeTyphoonTimeWindowStart
									&& ldnTime <= Parameter.airportBeforeTyphoonTimeWindowEnd)
									|| (ldnTime >= Parameter.airportAfterTyphoonTimeWindowStart
											&& ldnTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isWithinAffectedRegionDestination2 = true;
							}
						}

						if ((i * presetGap) % Parameter.gap != 0 && t != endTime && t != startTime) {
							if (!isWithinAffectedRegionOrigin2 && !isWithinAffectedRegionDestination2) {
								continue;
							}
						}
						
						if (cf.secondFlight.initialTakeoffT + presetGap * i >= firstArc.readyTime) {
							FlightArc secondArc = new FlightArc();
							secondArc.flight = cf.secondFlight;
							secondArc.aircraft = aircraft;
							if(i < 0){
								secondArc.earliness = -i * presetGap;
							}else{
								secondArc.delay = i * presetGap;
							}
							
							secondArc.takeoffTime = cf.secondFlight.initialTakeoffT + i * presetGap;
							secondArc.landingTime = secondArc.takeoffTime + cf.secondFlight.flyTime;
							secondArc.readyTime = secondArc.landingTime + (cf.secondFlight.isShortConnection
									? cf.secondFlight.shortConnectionTime : Parameter.MIN_BUFFER_TIME);

							secondArc.isWithinAffectedRegionOrigin = isWithinAffectedRegionOrigin2;
							secondArc.isWithinAffectedRegionDestination = isWithinAffectedRegionDestination2;

							if (!secondArc.checkViolation()) {
								ConnectingArc ca = new ConnectingArc();
								ca.firstArc = firstArc;
								ca.secondArc = secondArc;
								ca.connectingFlightPair = cf;
								ca.aircraft = aircraft;

								generatedConnectingArcList.add(ca);
					
								if (i >= 0) {
									if (!isWithinAffectedRegionOrigin2 && !isWithinAffectedRegionDestination2) {
										break;
									}
								}
								
							}
						}
					}
				}

				
			}

		}

		return generatedConnectingArcList;
	}

	// 生成点和地面arc
	public void eliminateArcs(Aircraft aircraft, List<Airport> airportList, List<FlightArc> totalFlightArcList, List<ConnectingArc> totalConnectingArcList, Scenario scenario) {
		// 2. generate nodes for each arc
		List<Node> totalNodeList = new ArrayList<>();
		
		// 1. clear node map for each airport
		for (int i = 0; i < airportList.size(); i++) {
			aircraft.nodeMapArray[i] = new HashMap<>();
			aircraft.nodeListArray[i] = new ArrayList<>();
		}

		for (FlightArc flightArc : totalFlightArcList) {

			Airport departureAirport = flightArc.flight.leg.originAirport;
			Airport arrivalAirport = flightArc.flight.leg.destinationAirport;

			Node node = aircraft.nodeMapArray[departureAirport.id - 1].get(flightArc.takeoffTime);

			if (node == null) {
				node = new Node();
				node.airport = departureAirport;
				node.time = flightArc.takeoffTime;
				aircraft.nodeMapArray[departureAirport.id - 1].put(flightArc.takeoffTime, node);
			}

			node.flowoutFlightArcList.add(flightArc);
			flightArc.fromNode = node;

			node = aircraft.nodeMapArray[arrivalAirport.id - 1].get(flightArc.readyTime);
			if (node == null) {
				node = new Node();
				node.airport = arrivalAirport;
				node.time = flightArc.readyTime;
				aircraft.nodeMapArray[arrivalAirport.id - 1].put(flightArc.readyTime, node);
			}

			node.flowinFlightArcList.add(flightArc);
			flightArc.toNode = node;
		}

		for (ConnectingArc flightArc : totalConnectingArcList) {

			Airport departureAirport = flightArc.firstArc.flight.leg.originAirport;
			Airport arrivalAirport = flightArc.secondArc.flight.leg.destinationAirport;

			int takeoffTime = flightArc.firstArc.takeoffTime;
			int readyTime = flightArc.secondArc.readyTime;

			Node node = aircraft.nodeMapArray[departureAirport.id - 1].get(takeoffTime);

			if (node == null) {
				node = new Node();
				node.airport = departureAirport;
				node.time = takeoffTime;
				aircraft.nodeMapArray[departureAirport.id - 1].put(takeoffTime, node);
			}

			node.flowoutConnectingArcList.add(flightArc);
			flightArc.fromNode = node;

			node = aircraft.nodeMapArray[arrivalAirport.id - 1].get(readyTime);
			if (node == null) {
				node = new Node();
				node.airport = arrivalAirport;
				node.time = readyTime;
				aircraft.nodeMapArray[arrivalAirport.id - 1].put(readyTime, node);

			}

			node.flowinConnectingArcList.add(flightArc);
			flightArc.toNode = node;
		}

		// 生成source和sink点
		Node sourceNode = new Node();
		sourceNode.isSource = true;
		
		Node sinkNode = new Node();
		sinkNode.isSink = true;
		
		// 3. sort nodes of each airport
		for (int i = 0; i < airportList.size(); i++) {
			for (Integer key : aircraft.nodeMapArray[i].keySet()) {
				aircraft.nodeListArray[i].add(aircraft.nodeMapArray[i].get(key));
			}

			Collections.sort(aircraft.nodeListArray[i], new NodeComparator());
			
			totalNodeList.addAll(aircraft.nodeListArray[i]);

			for (int j = 0; j < aircraft.nodeListArray[i].size() - 1; j++) {
				Node n1 = aircraft.nodeListArray[i].get(j);
				Node n2 = aircraft.nodeListArray[i].get(j + 1);

				GroundArc groundArc = new GroundArc();
				groundArc.fromNode = n1;
				groundArc.toNode = n2;
				groundArc.aircraft = aircraft;

				n1.flowoutGroundArcList.add(groundArc);
				n2.flowinGroundArcList.add(groundArc);
			}
		}

		//对全部node进行排序
		Collections.sort(totalNodeList, new NodeComparator());
		totalNodeList.add(0,sourceNode);
		totalNodeList.add(sinkNode);
		
		// 4. construct source and sink arcs
		// 连接source和每个飞机初始机场的第一个点
		if (aircraft.nodeListArray[aircraft.initialLocation.id - 1].size() > 0) {
			Node firstNode = aircraft.nodeListArray[aircraft.initialLocation.id - 1].get(0);

			GroundArc arc = new GroundArc();
			arc.fromNode = sourceNode;
			arc.toNode = firstNode;
			arc.isSource = true;
			arc.aircraft = aircraft;
			sourceNode.flowoutGroundArcList.add(arc);
			firstNode.flowinGroundArcList.add(arc);		
		}

		for (Airport airport : airportList) {
			if (aircraft.nodeListArray[airport.id - 1].size() > 0) {
				Node lastNode = aircraft.nodeListArray[airport.id - 1]
						.get(aircraft.nodeListArray[airport.id - 1].size() - 1);

				GroundArc arc = new GroundArc();
				arc.fromNode = lastNode;
				arc.toNode = sinkNode;
				arc.isSink = true;
				arc.aircraft = aircraft;
				lastNode.flowoutGroundArcList.add(arc);
				sinkNode.flowinGroundArcList.add(arc);				
			}
		}

		
		//检测每一个arc是否可以被访问到
		for(FlightArc arc:totalFlightArcList){
			arc.isVisited = false;
		}
		for(ConnectingArc arc:totalConnectingArcList){
			arc.isVisited = false;
		}
		for(Node n:totalNodeList){
			n.isVisited = false;
		}
		
		sourceNode.isVisited = true;
		
		for(Node n:totalNodeList){
			if(n.isVisited){
				for(GroundArc arc:n.flowoutGroundArcList){
					arc.toNode.isVisited = true;
				}
				for(FlightArc arc:n.flowoutFlightArcList){
					arc.toNode.isVisited = true;
					arc.isVisited = true;
				}
				for(ConnectingArc arc:n.flowoutConnectingArcList){
					arc.toNode.isVisited = true;
					arc.isVisited = true;
				}
			}
		}
		
		//update所有选中的flight arc和connecting arc
		for(FlightArc arc:totalFlightArcList){
			if(arc.isVisited){
				arc.fromNode = null;
				arc.toNode = null;
				
				Flight f = arc.flight;
				if(!f.isIncludedInTimeWindow){
					
					f.flightarcList.add(arc);
					aircraft.flightArcList.add(arc);
					arc.calculateCost();

					//更新25和67机场的停机约束
					if (f.leg.destinationAirport.id == 25 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& arc.readyTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport25ParkingFlightArcList.add(arc);
					}
					if (f.leg.destinationAirport.id == 67 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& arc.readyTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport67ParkingFlightArcList.add(arc);
					}
					
				}else{
					
					// 如果是调剂航班，不需要做任何处理
					if (f.isDeadhead) {

					} else if (f.isStraightened) {
						// 如果是联程拉直，将该arc加到对应的两段航班中
						f.connectingFlightpair.firstFlight.flightarcList.add(arc);
						f.connectingFlightpair.secondFlight.flightarcList.add(arc);

						// 联程拉直航班则没有对应的flight section
						// 联程拉直乘客容量
						arc.passengerCapacity = aircraft.passengerCapacity;
						// 要减去对应的联程乘客
						arc.passengerCapacity = arc.passengerCapacity - f.connectedPassengerNumber;

						// 其他乘客全部被取消，所以不需要考虑
						arc.passengerCapacity = Math.max(0, arc.passengerCapacity);
						
					} else {
						f.flightarcList.add(arc);

						// 将该arc加入到对应的flight section中
						boolean isFound = false;
						for (FlightSection currentFlightSection : f.flightSectionList) {
							if (arc.takeoffTime >= currentFlightSection.startTime
									&& arc.takeoffTime < currentFlightSection.endTime) {
								isFound = true;
								currentFlightSection.flightArcList.add(arc);

								break;
							}
						}

						if (!isFound) { // check 是否每个arc都放进了一个section
							if (f.flightSectionList
									.get(f.flightSectionList.size() - 1).endTime != arc.takeoffTime) {
								System.out.println("no flight section found 3!"
										+ f.flightSectionList.get(f.flightSectionList.size() - 1).endTime + " "
										+ arc.takeoffTime);
								System.out.println(f.initialTakeoffT+" "+f.isAllowtoBringForward+" "+f.id+"  "+arc.aircraft.id+"   "+f.isIncludedInTimeWindow);
								for(FlightSection fs:f.flightSectionList){
									System.out.print("["+fs.startTime+","+fs.endTime+"]  ");
								}
								System.out.println();
								System.exit(1);
							}
							f.flightSectionList.get(f.flightSectionList.size() - 1).flightArcList.add(arc);
						}

						// 乘客容量
						arc.passengerCapacity = aircraft.passengerCapacity;

						// 减去转乘乘客（首先假设所有的转乘乘客都可以成功转乘）
						arc.passengerCapacity = arc.passengerCapacity - f.occupiedSeatsByTransferPassenger;
			
						if(f.isConnectionFeasible){
							// 如果该航班是联程航班，则代表联程航班已经被取消，所以不需要在考虑对应的联程乘客
							arc.passengerCapacity = arc.passengerCapacity - f.connectedPassengerNumber;
						}

						// 减去普通乘客
						arc.fulfilledNormalPassenger = Math.min(arc.passengerCapacity, f.normalPassengerNumber);
						arc.passengerCapacity = arc.passengerCapacity - arc.fulfilledNormalPassenger;

						arc.flight.itinerary.flightArcList.add(arc);
					}

					arc.calculateCost();
					aircraft.flightArcList.add(arc);
					
					// 加入对应的起降时间点

					if (arc.isWithinAffectedRegionOrigin) {
						List<FlightArc> faList = scenario.airportTimeFlightArcMap
								.get(f.leg.originAirport.id + "_" + arc.takeoffTime);
						faList.add(arc);

					} else if (arc.isWithinAffectedRegionDestination) {
						List<FlightArc> faList = scenario.airportTimeFlightArcMap
								.get(f.leg.destinationAirport.id + "_" + arc.landingTime);
						faList.add(arc);

					}

					// 加入停机约束
					if (f.leg.destinationAirport.id == 25
							&& arc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& arc.readyTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport25ParkingFlightArcList.add(arc);
					}
					if (f.leg.destinationAirport.id == 67
							&& arc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& arc.readyTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport67ParkingFlightArcList.add(arc);
					}
				}
				
			}
		}
		
		for(ConnectingArc ca:totalConnectingArcList){
			if(ca.isVisited){
				ca.fromNode = null;
				ca.toNode = null;
				
				ConnectingFlightpair cf = ca.connectingFlightPair;

				if(!cf.firstFlight.isIncludedInTimeWindow){
					aircraft.connectingArcList.add(ca);

					cf.firstFlight.connectingarcList.add(ca);
					cf.secondFlight.connectingarcList.add(ca);
					ca.connectingFlightPair = cf;

					ca.calculateCost();
					
					// 加入25和67停机约束
					if (cf.firstFlight.leg.destinationAirport.id == 25
							&& ca.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& ca.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport25ClosureConnectingArcList.add(ca);
					}
					if (cf.firstFlight.leg.destinationAirport.id == 67
							&& ca.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& ca.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport25ClosureConnectingArcList.add(ca);
					}
				}else{
								
					aircraft.connectingArcList.add(ca);

					cf.firstFlight.connectingarcList.add(ca);
					cf.secondFlight.connectingarcList.add(ca);
					ca.connectingFlightPair = cf;

					ca.calculateCost();
					
					if (ca.firstArc.isWithinAffectedRegionOrigin) {
						List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap
								.get(ca.firstArc.flight.leg.originAirport.id + "_" + ca.firstArc.takeoffTime);
						caList.add(ca);
					}
					if (ca.firstArc.isWithinAffectedRegionDestination) {
						List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap
								.get(ca.firstArc.flight.leg.destinationAirport.id + "_" + ca.firstArc.landingTime);
						caList.add(ca);
					}

					if (ca.secondArc.isWithinAffectedRegionOrigin) {
						List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap
								.get(ca.secondArc.flight.leg.originAirport.id + "_" + ca.secondArc.takeoffTime);

						caList.add(ca);
					}
					if (ca.secondArc.isWithinAffectedRegionDestination) {
						List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap
								.get(ca.secondArc.flight.leg.destinationAirport.id + "_" + ca.secondArc.landingTime);
						caList.add(ca);
					}

					// 加入25和67停机约束
					if (cf.firstFlight.leg.destinationAirport.id == 25
							&& ca.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& ca.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport25ClosureConnectingArcList.add(ca);
					}
					if (cf.firstFlight.leg.destinationAirport.id == 67
							&& ca.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart
							&& ca.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd) {
						scenario.airport25ClosureConnectingArcList.add(ca);
					}

					// 设置第一个arc
					// arc.firstArc.isIncludedInConnecting = true;
					// arc.firstArc.connectingArcList.add(arc);

					// 乘客容量
					ca.firstArc.passengerCapacity = aircraft.passengerCapacity;
					// 减去联程乘客
					ca.firstArc.passengerCapacity = ca.firstArc.passengerCapacity
							- cf.firstFlight.connectedPassengerNumber;
					// 减去转乘乘客
					ca.firstArc.passengerCapacity = ca.firstArc.passengerCapacity
							- cf.firstFlight.occupiedSeatsByTransferPassenger;

					// 减去普通乘客
					ca.firstArc.fulfilledNormalPassenger = Math.min(ca.firstArc.passengerCapacity, cf.firstFlight.normalPassengerNumber);
					ca.firstArc.passengerCapacity = ca.firstArc.passengerCapacity
							- ca.firstArc.fulfilledNormalPassenger;
					
					ca.firstArc.flight.itinerary.firstConnectionArcList.add(ca);
					
					boolean isFound = false;
					for (FlightSection currentFlightSection : cf.firstFlight.flightSectionList) {
						if (ca.firstArc.takeoffTime >= currentFlightSection.startTime
								&& ca.firstArc.takeoffTime < currentFlightSection.endTime) {
							// currentFlightSection.flightArcList.add(arc.firstArc);
							currentFlightSection.connectingFirstArcList.add(ca);
							isFound = true;

							break;
						}
					}

					if (!isFound) {
						if (ca.firstArc.takeoffTime != cf.firstFlight.flightSectionList
								.get(cf.firstFlight.flightSectionList.size() - 1).endTime) {
							System.out.println("no flight section found! " + ca.firstArc.takeoffTime + " "
									+ cf.firstFlight.flightSectionList
											.get(cf.firstFlight.flightSectionList.size() - 1).endTime);
							System.exit(1);
						}

						cf.firstFlight.flightSectionList
								.get(cf.firstFlight.flightSectionList.size() - 1).connectingFirstArcList.add(ca);

					}

					// 设置第二个arc
					// arc.secondArc.isIncludedInConnecting = true;
					// arc.secondArc.connectingArcList.add(arc);
					// 乘客容量
					ca.secondArc.passengerCapacity = aircraft.passengerCapacity;
					// 减去联程乘客
					ca.secondArc.passengerCapacity = ca.secondArc.passengerCapacity
							- cf.secondFlight.connectedPassengerNumber;
					// 减去转乘乘客
					ca.secondArc.passengerCapacity = ca.secondArc.passengerCapacity
							- cf.secondFlight.occupiedSeatsByTransferPassenger;
					
					// 减去普通乘客
					ca.secondArc.fulfilledNormalPassenger = Math.min(ca.secondArc.passengerCapacity, cf.secondFlight.normalPassengerNumber);
					
					ca.secondArc.passengerCapacity = ca.secondArc.passengerCapacity- ca.secondArc.fulfilledNormalPassenger;
					
					ca.secondArc.flight.itinerary.secondConnectingArcList.add(ca);
					
					isFound = false;
					for (FlightSection currentFlightSection : cf.secondFlight.flightSectionList) {
						if (ca.secondArc.takeoffTime >= currentFlightSection.startTime
								&& ca.secondArc.takeoffTime < currentFlightSection.endTime) {
							currentFlightSection.connectingSecondArcList.add(ca);
							isFound = true;

							break;
						}
					}

					if (!isFound) {
						if (ca.secondArc.takeoffTime != cf.secondFlight.flightSectionList
								.get(cf.secondFlight.flightSectionList.size() - 1).endTime) {
							System.out.println("no flight section found 2! " + ca.secondArc.takeoffTime + " "
									+ cf.secondFlight.flightSectionList
											.get(cf.secondFlight.flightSectionList.size() - 1).endTime);
							System.exit(1);
						}
						cf.secondFlight.flightSectionList
								.get(cf.secondFlight.flightSectionList.size() - 1).connectingSecondArcList.add(ca);

					}
				}
			}
		}
	}
	

}
