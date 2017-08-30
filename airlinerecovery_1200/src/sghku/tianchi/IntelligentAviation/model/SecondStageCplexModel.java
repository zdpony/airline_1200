package sghku.tianchi.IntelligentAviation.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import ilog.concert.IloException;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Param.MIP.Strategy;
import sghku.tianchi.IntelligentAviation.common.ExcelOperator;
import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator2;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightArcItinerary;
import sghku.tianchi.IntelligentAviation.entity.FlightSection;
import sghku.tianchi.IntelligentAviation.entity.FlightSectionItinerary;
import sghku.tianchi.IntelligentAviation.entity.GroundArc;
import sghku.tianchi.IntelligentAviation.entity.Itinerary;
import sghku.tianchi.IntelligentAviation.entity.Node;
import sghku.tianchi.IntelligentAviation.entity.ParkingInfo;
import sghku.tianchi.IntelligentAviation.entity.PassengerTransfer;
import sghku.tianchi.IntelligentAviation.entity.Path;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.Solution;
import sghku.tianchi.IntelligentAviation.entity.TransferPassenger;

public class SecondStageCplexModel {
	public IloCplex cplex;

	/*
	 * public double totalSignChangeDelayCost = 0; public double
	 * totalOriginalPassengerDelayCost = 0; public double
	 * totalPassengerCancelCost = 0;
	 */

	// the network flow model for initial problem solving

	public Solution run(List<Aircraft> aircraftList, List<Flight> flightList, List<ConnectingFlightpair> cfList,
			Scenario sce, List<FlightArcItinerary> flightArcItineraryList, boolean isFractional,
			boolean isCancelAllowed) {
		Solution solution = new Solution();
		solution.involvedAircraftList.addAll(aircraftList);

		try {

			cplex = new IloCplex();

			List<FlightArc> flightArcList = new ArrayList<>();
			List<ConnectingArc> connectingArcList = new ArrayList<>();
			List<GroundArc> groundArcList = new ArrayList<>();
			List<Node> nodeList = new ArrayList<>();

			for(Aircraft aircraft:aircraftList){
				flightArcList.addAll(aircraft.flightArcList);
				connectingArcList.addAll(aircraft.connectingArcList);
				groundArcList.addAll(aircraft.groundArcList);

				for(int i=0;i<Parameter.TOTAL_AIRPORT_NUM;i++){
					nodeList.addAll(aircraft.nodeListArray[i]);
				}

				nodeList.add(aircraft.sourceNode);
				nodeList.add(aircraft.sinkNode);
			}

			System.out.println("start solving:"+flightArcList.size()+" "+connectingArcList.size()+" "+groundArcList.size()+" "+nodeList.size()+" "+ flightList.size());

			IloNumVar[] x = new IloNumVar[flightArcList.size()];
			IloNumVar[] beta = new IloNumVar[connectingArcList.size()];
			IloNumVar[] y = new IloNumVar[groundArcList.size()];
			IloNumVar[] z = new IloNumVar[flightList.size()];

			IloNumVar[] passX = new IloNumVar[flightArcItineraryList.size()]; // number
			// of
			// passenger
			// in
			// itinerary
			// choosing
			// flightArc
			IloNumVar[] passCancel = new IloNumVar[sce.itineraryList.size()];

			IloLinearNumExpr obj = cplex.linearNumExpr();

			for (int i = 0; i < flightArcList.size(); i++) {
				if (isFractional) {
					x[i] = cplex.numVar(0, 1);
				} else {
					x[i] = cplex.boolVar();
				}

				obj.addTerm(flightArcList.get(i).cost, x[i]);
				flightArcList.get(i).id = i;
			}
			for (int i = 0; i < connectingArcList.size(); i++) {
				if (isFractional) {
					beta[i] = cplex.numVar(0, 1);
				} else {
					beta[i] = cplex.boolVar();
				}
				obj.addTerm(connectingArcList.get(i).cost, beta[i]);
				connectingArcList.get(i).id = i;
			}
			for (int i = 0; i < groundArcList.size(); i++) {
				// y[i] = cplex.boolVar();
				y[i] = cplex.numVar(0, 1);
				groundArcList.get(i).id = i;
			}
			for (int i = 0; i < flightList.size(); i++) {
				Flight f = flightList.get(i);
				// z[i] = cplex.boolVar();
				f.idInCplexModel = i;
				z[i] = cplex.numVar(0, 1);

				obj.addTerm(f.importance * Parameter.COST_CANCEL + f.totalConnectingCancellationCost
						+ f.totalTransferCancellationCost, z[i]);

			}

			for (int i = 0; i < flightArcItineraryList.size(); i++) {
				FlightArcItinerary fai = flightArcItineraryList.get(i);
				fai.id = i;
				passX[i] = cplex.numVar(0, fai.itinerary.volume); // 可以最多转成volume的乘客量

				obj.addTerm(fai.unitCost, passX[i]);
			}
			for (int i = 0; i < sce.itineraryList.size(); i++) {
				Itinerary ite = sce.itineraryList.get(i);
				ite.id = i;
				passCancel[i] = cplex.numVar(0, ite.volume);
				obj.addTerm(Parameter.passengerCancelCost, passCancel[i]);
			}

			/*for(FlightArc arc:flightArcList){
				if(arc.flight.id == 108){
					System.out.println("arc 108:"+arc.passengerCapacity+" "+arc.fulfilledNormalPassenger+" "+arc.aircraft.passengerCapacity+" "+arc.flight.connectedPassengerNumber+" "+arc.flight.transferPassengerNumber+" "+arc.flight.normalPassengerNumber);
				}
			}
			System.exit(1);*/
			cplex.addMinimize(obj);

			// 1. flow balance constraints
			for (Node n : nodeList) {
				IloLinearNumExpr flowBalanceConstraint = cplex.linearNumExpr();

				for (FlightArc arc : n.flowinFlightArcList) {
					flowBalanceConstraint.addTerm(1, x[arc.id]);
				}
				for (FlightArc arc : n.flowoutFlightArcList) {
					flowBalanceConstraint.addTerm(-1, x[arc.id]);
				}

				for (ConnectingArc arc : n.flowinConnectingArcList) {
					flowBalanceConstraint.addTerm(1, beta[arc.id]);
				}
				for (ConnectingArc arc : n.flowoutConnectingArcList) {
					flowBalanceConstraint.addTerm(-1, beta[arc.id]);
				}

				for (GroundArc arc : n.flowinGroundArcList) {
					flowBalanceConstraint.addTerm(1, y[arc.id]);
				}
				for (GroundArc arc : n.flowoutGroundArcList) {
					flowBalanceConstraint.addTerm(-1, y[arc.id]);
				}

				cplex.addEq(flowBalanceConstraint, 0);
			}

			// 2. turn-around arc flow
			for (Aircraft aircraft : aircraftList) {
				IloLinearNumExpr turnaroundConstraint = cplex.linearNumExpr();

				turnaroundConstraint.addTerm(1, y[aircraft.turnaroundArc.id]);

				cplex.addEq(turnaroundConstraint, 1);
			}

			// 3. for each flight, at least one arc can be selected, the last
			// flight can not be cancelled
			for (int i = 0; i < flightList.size(); i++) {
				Flight f = flightList.get(i);

				IloLinearNumExpr flightSelectionConstraint = cplex.linearNumExpr();

				for (FlightArc arc : f.flightarcList) {
					flightSelectionConstraint.addTerm(1, x[arc.id]);
				}

				for (ConnectingArc arc : f.connectingarcList) {
					flightSelectionConstraint.addTerm(1, beta[arc.id]);
				}

				/*if (isCancelAllowed) { // 因为已经fix route，算schedule
					if (f.isIncludedInTimeWindow) {
						flightSelectionConstraint.addTerm(1, z[i]);
					}
					System.out.println("flight cancell allowed");
				}*/

				if (f.isIncludedInTimeWindow) {					
					flightSelectionConstraint.addTerm(1, z[i]);
				}
				cplex.addEq(flightSelectionConstraint, 1);
			}

			// 4. base balance constraints
			for (int i = 0; i < sce.airportList.size(); i++) {
				Airport airport = sce.airportList.get(i);

				for (int j = 0; j < Parameter.TOTAL_AIRCRAFTTYPE_NUM; j++) {
					IloLinearNumExpr baseConstraint = cplex.linearNumExpr();
					for (GroundArc arc : airport.sinkArcList[j]) {

						baseConstraint.addTerm(1, y[arc.id]);
					}

					cplex.addEq(baseConstraint, airport.finalAircraftNumber[j]);
				}
			}

			// 5. 对于每一个联程航班，两趟航班必须由同一个飞机执行
			for (ConnectingFlightpair cf : cfList) {
				IloLinearNumExpr cont = cplex.linearNumExpr();

				for (FlightArc arc : cf.firstFlight.flightarcList) {
					cont.addTerm(1, x[arc.id]);
				}
				cont.addTerm(-1, z[cf.secondFlight.idInCplexModel]); // 防止两个flight被不同飞机飞

				cplex.addLe(cont, 0);

				cont = cplex.linearNumExpr();

				for (FlightArc arc : cf.secondFlight.flightarcList) {
					cont.addTerm(1, x[arc.id]);
				}
				cont.addTerm(-1, z[cf.firstFlight.idInCplexModel]);

				cplex.addLe(cont, 0);
			}

			// 6. 乘客相关约束
			for (Itinerary ite : sce.itineraryList) {
				IloLinearNumExpr iteNumConstraint = cplex.linearNumExpr();
				// 转签到其他flight的乘客数量
				for (FlightArcItinerary fai : ite.flightArcItineraryList) {
					iteNumConstraint.addTerm(1, passX[fai.id]);
				}
				iteNumConstraint.addTerm(1, passCancel[ite.id]);

				// ite原来的flight对应的flightarc、connectingarc自己承载的乘客数量
				for (FlightArc arc : ite.flightArcList) {
					iteNumConstraint.addTerm(arc.fulfilledNormalPassenger, x[arc.id]);
				}
				for (ConnectingArc arc : ite.firstConnectionArcList) {
					iteNumConstraint.addTerm(arc.firstArc.fulfilledNormalPassenger, beta[arc.id]);
				}
				for (ConnectingArc arc : ite.secondConnectingArcList) {
					iteNumConstraint.addTerm(arc.secondArc.fulfilledNormalPassenger, beta[arc.id]);
				}
				// 加起来等于总数量
				cplex.addEq(iteNumConstraint, ite.volume);
			}

			// 7. flight arc的座位限制
			for (FlightArc fa : flightArcList) {
				IloLinearNumExpr seatConstraint = cplex.linearNumExpr();
				for (FlightArcItinerary fai : fa.flightArcItineraryList) {
					seatConstraint.addTerm(1, passX[fai.id]);
				}

				seatConstraint.addTerm(-fa.passengerCapacity, x[fa.id]);

				cplex.addLe(seatConstraint, 0);
			}

			for (ConnectingArc ca : connectingArcList) {
				IloLinearNumExpr seatConstraint1 = cplex.linearNumExpr();
				for (FlightArcItinerary fai : ca.firstArc.flightArcItineraryList) {
					seatConstraint1.addTerm(1, passX[fai.id]);
				}
				seatConstraint1.addTerm(-ca.firstArc.passengerCapacity, beta[ca.id]);

				cplex.addLe(seatConstraint1, 0);

				IloLinearNumExpr seatConstraint2 = cplex.linearNumExpr();
				for (FlightArcItinerary fai : ca.secondArc.flightArcItineraryList) {
					seatConstraint2.addTerm(1, passX[fai.id]);
				}
				seatConstraint2.addTerm(-ca.secondArc.passengerCapacity, beta[ca.id]);

				cplex.addLe(seatConstraint2, 0);
			}

			// 8. 机场起降约束
			for (String key : sce.keyList) {
				IloLinearNumExpr airportConstraint = cplex.linearNumExpr();
				List<FlightArc> faList = sce.airportTimeFlightArcMap.get(key);
				List<ConnectingArc> caList = sce.airportTimeConnectingArcMap.get(key);

				for (FlightArc arc : faList) {
					airportConstraint.addTerm(1, x[arc.id]);
				}
				for (ConnectingArc arc : caList) {
					airportConstraint.addTerm(1, beta[arc.id]);
				}

				cplex.addLe(airportConstraint, sce.affectAirportLdnTkfCapacityMap.get(key));
			}

			// 9. 停机约束
			for (Integer airport : sce.affectedAirportSet) {
				IloLinearNumExpr parkingConstraint = cplex.linearNumExpr();
				List<GroundArc> gaList = sce.affectedAirportCoverParkLimitGroundArcMap.get(airport);
				for (GroundArc ga : gaList) {
					parkingConstraint.addTerm(1, y[ga.id]);
				}

				cplex.addLe(parkingConstraint, sce.affectedAirportParkingLimitMap.get(airport));
			}

			// 10. 25 和 67 停机约束
			IloLinearNumExpr parkingConstraint25 = cplex.linearNumExpr();
			for (GroundArc ga : sce.airport25ClosureGroundArcList) {
				parkingConstraint25.addTerm(1, y[ga.id]);
			}
			for (FlightArc arc : sce.airport25ParkingFlightArcList) {
				parkingConstraint25.addTerm(1, x[arc.id]);
			}
			for (ConnectingArc arc : sce.airport25ClosureConnectingArcList) {
				parkingConstraint25.addTerm(1, beta[arc.id]);
			}
			cplex.addLe(parkingConstraint25, sce.airport25ParkingLimit);

			IloLinearNumExpr parkingConstraint67 = cplex.linearNumExpr();
			for (GroundArc ga : sce.airport67ClosureGroundArcList) {
				parkingConstraint67.addTerm(1, y[ga.id]);
			}
			for (FlightArc arc : sce.airport67ParkingFlightArcList) {
				parkingConstraint67.addTerm(1, x[arc.id]);
			}
			for (ConnectingArc arc : sce.airport67ClosureConnectingArcList) {
				parkingConstraint67.addTerm(1, beta[arc.id]);
			}
			cplex.addLe(parkingConstraint67, sce.airport67ParkingLimit);

			int totalCancelledNum = 0;
			for(int i=0;i<flightList.size();i++){
				Flight f = flightList.get(i);
				IloLinearNumExpr cont = cplex.linearNumExpr();
				cont.addTerm(1, z[i]);
				if(f.isSelectedInSecondPhase){
					cplex.addEq(cont, 0);
				}else{
					cplex.addEq(cont, 1);
					totalCancelledNum++;
				}
			}
			System.out.println("totalCancelledNum:"+totalCancelledNum);

			/*try {
				Scanner sn = null;

				sn = new Scanner(new File("testflight"));
				while(sn.hasNextInt()){
					int id = sn.nextInt();
					IloLinearNumExpr cont = cplex.linearNumExpr();
					cont.addTerm(1, x[id]);
					cplex.addEq(cont, 1);
				}

				sn = new Scanner(new File("testpassenger"));

				while(sn.hasNextLine()){
					String nextLine = sn.nextLine().trim();

					if(nextLine.equals("")){
						break;
					}
					String[] arr = nextLine.split("");
					int id = Integer.parseInt(arr[0]);
					double num = Double.parseDouble(arr[1]);

					IloLinearNumExpr cont = cplex.linearNumExpr();
					cont.addTerm(1, passX[id]);
					cplex.addEq(cont, num);
				}

			} catch (FileNotFoundException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}*/

			if (cplex.solve()) {

				if (isFractional) {
					System.out.println("fractional value:" + cplex.getObjValue() + "  ");

					Parameter.fractionalObjective = cplex.getObjValue();

					try {
						File file = new File("linearsolution.csv");
						if (file.exists()) {
							file.delete();
						}

						MyFile.creatTxtFile("linearsolution.csv");
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

					for (FlightArc fa : flightArcList) {

						if (cplex.getValue(x[fa.id]) > 1e-5) {

							fa.fractionalFlow = cplex.getValue(x[fa.id]);
						}
					}
					for (ConnectingArc arc : connectingArcList) {
						if (cplex.getValue(beta[arc.id]) > 1e-5) {
							arc.fractionalFlow = cplex.getValue(beta[arc.id]);
						}
					}
					for (GroundArc ga : groundArcList) {
						if (cplex.getValue(y[ga.id]) > 1e-5) {
							ga.fractionalFlow = cplex.getValue(y[ga.id]);
						}
					}

					StringBuilder sb = new StringBuilder();
					for (Aircraft a : aircraftList) {
						// System.out.println("aircraft:"+a.id);
						boolean isContinue = true;

						while (isContinue) {
							Node currentNode = a.sourceNode;

							double flowOut = 0;
							for (FlightArc arc : currentNode.flowoutFlightArcList) {
								flowOut += arc.fractionalFlow;
							}
							for (ConnectingArc arc : currentNode.flowoutConnectingArcList) {
								flowOut += arc.fractionalFlow;
							}
							for (GroundArc arc : currentNode.flowoutGroundArcList) {
								flowOut += arc.fractionalFlow;
							}

							// System.out.println("flow out:"+flowOut);
							if (flowOut > 1e-5) {

								Path p = new Path();
								p.aircraft = a;

								while (!currentNode.isSink) {

									double maximumFlow = -1e-5;

									FlightArc maxFlightArc = null;
									ConnectingArc maxConnectingArc = null;
									GroundArc maxGroundArc = null;
									for (FlightArc arc : currentNode.flowoutFlightArcList) {
										if (arc.fractionalFlow > maximumFlow) {
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = arc;
											maxConnectingArc = null;
											maxGroundArc = null;
										}
									}

									for (ConnectingArc arc : currentNode.flowoutConnectingArcList) {
										if (arc.fractionalFlow > maximumFlow) {
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = arc;
											maxGroundArc = null;
										}
									}

									for (GroundArc arc : currentNode.flowoutGroundArcList) {
										if (arc.fractionalFlow > maximumFlow) {
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = null;
											maxGroundArc = arc;
										}
									}

									if (maxFlightArc != null) {
										currentNode = maxFlightArc.toNode;
										p.flightArcList.add(maxFlightArc);
									}
									if (maxConnectingArc != null) {
										currentNode = maxConnectingArc.toNode;
										p.connectingArcList.add(maxConnectingArc);
									}
									if (maxGroundArc != null) {
										currentNode = maxGroundArc.toNode;
										p.groundArcList.add(maxGroundArc);
									}

								}

								p.value = 1.0;
								for (FlightArc arc : p.flightArcList) {
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for (ConnectingArc arc : p.connectingArcList) {
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for (GroundArc arc : p.groundArcList) {
									p.value = Math.min(p.value, arc.fractionalFlow);
								}

								for (FlightArc arc : p.flightArcList) {
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for (ConnectingArc arc : p.connectingArcList) {
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for (GroundArc arc : p.groundArcList) {
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}

								sb.append(p.toString() + "\n");
							} else {
								isContinue = false;
							}

						}
					}

					try {
						MyFile.writeTxtFile(sb.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				} else {
					// if we solve it integrally
					System.out.println("solve: obj = " + cplex.getObjValue());

					solution.objValue = cplex.getObjValue();
					Parameter.objective += cplex.getObjValue();

					double totalCancelCost1 = 0;
					double totalCancelCost2 = 0;
					double totalCancelCost3 = 0;
					double totalCancelCost4 = 0;

					double cancelCost3 = 0;
					double cancelCost4 = 0;

					double delayCost = 0;

					for (FlightArc fa : flightArcList) {

						if (cplex.getValue(x[fa.id]) > 1e-5) {

							solution.selectedFlightArcList.add(fa);

							// 更新flight arc的时间
							fa.flight.actualTakeoffT = fa.takeoffTime;
							fa.flight.actualLandingT = fa.landingTime;

							fa.flight.aircraft = fa.aircraft;

							fa.fractionalFlow = cplex.getValue(x[fa.id]);

							if(fa.flight.isStraightened){
								double totalSignChange = 0;
								if (fa.flight.itinerary != null) {
									for (FlightArcItinerary fai : fa.flight.itinerary.flightArcItineraryList) {										
										totalSignChange += cplex.getValue(passX[fai.id]);
									}
								}
								cancelCost3 += (fa.flight.passengerNumber - totalSignChange) * 4.0;
							}else{
								cancelCost4 += fa.cancelRelatedCost;
							}

							totalCancelCost1 += fa.cancelRelatedCost;
							delayCost += fa.delayRelatedCost;

							// System.out.println("fa:"+fa.fractionalFlow+"
							// "+fa.cost+" "+fa.delay+" "+fa.aircraft.id+"
							// "+fa.flight.initialAircraft.id+"
							// "+fa.aircraft.type+"
							// "+fa.flight.initialAircraftType+"
							// "+fa.flight.id+"
							// "+fa.flight.isIncludedInConnecting);

							/*
							 * totalOriginalPassengerDelayCost += fa.delayCost;
							 * totalPassengerCancelCost +=
							 * fa.connPssgrCclDueToSubseqCclCost;
							 * totalPassengerCancelCost +=
							 * fa.connPssgrCclDueToStraightenCost;
							 */

						}
					}
					/*try {
						MyFile.creatTxtFile("testflight");
						MyFile.writeTxtFile(testSb.toString());
					} catch (IOException e3) {
						// TODO Auto-generated catch block
						e3.printStackTrace();
					}*/

					for (ConnectingArc arc : connectingArcList) {

						if (cplex.getValue(beta[arc.id]) > 1e-5) {

							solution.selectedConnectingArcList.add(arc);
							// 更新flight arc的时间

							arc.connectingFlightPair.firstFlight.actualTakeoffT = arc.firstArc.takeoffTime;
							arc.connectingFlightPair.firstFlight.actualLandingT = arc.firstArc.landingTime;

							arc.connectingFlightPair.secondFlight.actualTakeoffT = arc.secondArc.takeoffTime;
							arc.connectingFlightPair.secondFlight.actualLandingT = arc.secondArc.landingTime;

							arc.connectingFlightPair.firstFlight.aircraft = arc.aircraft;
							arc.connectingFlightPair.secondFlight.aircraft = arc.aircraft;

							arc.fractionalFlow = cplex.getValue(beta[arc.id]);

							cancelCost4 += arc.cancelRelatedCost;

							delayCost += arc.delayRelatedCost;
							totalCancelCost1 += arc.cancelRelatedCost;
							/*
							 * totalPassengerCancelCost +=
							 * arc.pssgrCclCostDueToInsufficientSeat;
							 * totalOriginalPassengerDelayCost += arc.delayCost;
							 */
						}
					}
					/*try {
						MyFile.creatTxtFile("testconnect");
						MyFile.writeTxtFile(testSb.toString());
					} catch (IOException e3) {
						// TODO Auto-generated catch block
						e3.printStackTrace();
					}*/

					for (GroundArc ga : groundArcList) {
						if (cplex.getValue(y[ga.id]) > 1e-5) {
							ga.fractionalFlow = cplex.getValue(y[ga.id]);
						}
					}

					double totalSignChangeDelayCost = 0;
					for (int i = 0; i < flightArcItineraryList.size(); i++) {
						FlightArcItinerary fai = flightArcItineraryList.get(i);
						if (cplex.getValue(passX[i]) > 1e-5) {
							// 更新具体转签行程信息
							fai.volume = cplex.getValue(passX[i]);
							fai.flightArc.flight.flightArcItineraryList.add(fai);
							totalSignChangeDelayCost += fai.volume * fai.unitCost;

						}

					}


					/*try {
						MyFile.creatTxtFile("testpassenger");
						MyFile.writeTxtFile(testSb.toString());
					} catch (IOException e3) {
						// TODO Auto-generated catch block
						e3.printStackTrace();
					}*/

					System.out.println("totalSignChangeDelayCost:" + totalSignChangeDelayCost);
					double cancelCost5 = 0;
					for (int i = 0; i < sce.itineraryList.size(); i++) {
						Itinerary ite = sce.itineraryList.get(i);
						if (cplex.getValue(passCancel[i]) > 1e-6) {
							cancelCost5 += cplex.getValue(passCancel[i])*4.0;
							totalCancelCost2 += cplex.getValue(passCancel[i])*4.0;
							
							//把每个itinerary被cancel掉的存进它对应的flight的normalPassengerCancelNum 属性
							sce.itineraryList.get(i).flight.normalPassengerCancelNum = cplex.getValue(passCancel[i]); 
						 
						}
					}

					for (FlightArc fa : flightArcList) {
						if(fa.flight.id == 108){
							double totalSignChange = 0;
							for (FlightArcItinerary fai : fa.flightArcItineraryList) {
								totalSignChange += cplex.getValue(passX[fai.id]);
							}
						}						
					}

					double cancelCost1 = 0;
					double cancelCost2 = 0;
					double signChange2 = 0;
					double transferCancelCost = 0;
					double cancelCost6 = 0;
					double cancelCost7 = 0;

					for (int i = 0; i < flightList.size(); i++) {
						Flight f = flightList.get(i);

						if (cplex.getValue(z[i]) > 1e-5) {

							solution.cancelledFlightList.add(f);

							// totalPassengerCancelCost +=
							// f.connectedPassengerNumber*Parameter.passengerCancelCost+Parameter.passengerCancelCost*f.firstTransferPassengerNumber*2;

							f.isCancelled = true;

							double totalSignChange = 0;
							if (f.itinerary != null) {
								for (FlightArcItinerary fai : f.itinerary.flightArcItineraryList) {
									totalSignChange += fai.volume;
								}
							}
							if(f.isIncludedInConnecting && f.id == f.connectingFlightpair.firstFlight.id){
								cancelCost1 += (f.passengerNumber + f.connectedPassengerNumber - totalSignChange) * 4.0;
								cancelCost6 += f.totalConnectingCancellationCost;
							}else{
								cancelCost2 += (f.passengerNumber - totalSignChange) * 4.0;			
							}
							for(TransferPassenger tp:f.firstPassengerTransferList){
								if(!tp.outFlight.isCancelled){
									transferCancelCost += tp.volume;
								}
							}

							cancelCost7 += f.totalTransferCancellationCost;
							totalCancelCost3 += f.totalConnectingCancellationCost;
							totalCancelCost4 += f.totalTransferCancellationCost;
						}
					}

					System.out.println("cancelCost1:"+cancelCost1+"     cancelCost2:"+cancelCost2+"   cancelCost3:"+cancelCost3+"  transferCancelCost:"+transferCancelCost+"  cancelCost4："+cancelCost4+"  cancelCost5:"+cancelCost5+"  cancelCost6："+cancelCost6+"  cancelCost7:"+cancelCost7);
					System.out.println("delay:"+delayCost);
					System.out.println("cancel:"+totalCancelCost1+" "+totalCancelCost2+" "+totalCancelCost3+" "+totalCancelCost4);
					try {
						File file = new File("linearsolution.csv");
						if (file.exists()) {
							file.delete();
						}

						MyFile.creatTxtFile("linearsolution.csv");
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

					StringBuilder sb = new StringBuilder();
					for (Aircraft a : aircraftList) {
						// System.out.println("aircraft:"+a.id);
						boolean isContinue = true;

						while (isContinue) {
							Node currentNode = a.sourceNode;

							double flowOut = 0;
							for (FlightArc arc : currentNode.flowoutFlightArcList) {
								flowOut += arc.fractionalFlow;
							}
							for (ConnectingArc arc : currentNode.flowoutConnectingArcList) {
								flowOut += arc.fractionalFlow;
							}
							for (GroundArc arc : currentNode.flowoutGroundArcList) {
								flowOut += arc.fractionalFlow;
							}

							if (flowOut > 1e-5) {

								Path p = new Path();
								p.aircraft = a;

								while (!currentNode.isSink) {

									double maximumFlow = -1e-5;

									FlightArc maxFlightArc = null;
									ConnectingArc maxConnectingArc = null;
									GroundArc maxGroundArc = null;
									for (FlightArc arc : currentNode.flowoutFlightArcList) {
										if (arc.fractionalFlow > maximumFlow) {
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = arc;
											maxConnectingArc = null;
											maxGroundArc = null;
										}
									}

									for (ConnectingArc arc : currentNode.flowoutConnectingArcList) {
										if (arc.fractionalFlow > maximumFlow) {
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = arc;
											maxGroundArc = null;
										}
									}

									for (GroundArc arc : currentNode.flowoutGroundArcList) {
										if (arc.fractionalFlow > maximumFlow) {
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = null;
											maxGroundArc = arc;
										}
									}

									if (maxFlightArc != null) {
										currentNode = maxFlightArc.toNode;
										p.flightArcList.add(maxFlightArc);
									}
									if (maxConnectingArc != null) {
										currentNode = maxConnectingArc.toNode;
										p.connectingArcList.add(maxConnectingArc);
									}
									if (maxGroundArc != null) {
										currentNode = maxGroundArc.toNode;
										p.groundArcList.add(maxGroundArc);
									}

								}

								p.value = 1.0;
								for (FlightArc arc : p.flightArcList) {
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for (ConnectingArc arc : p.connectingArcList) {
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for (GroundArc arc : p.groundArcList) {
									p.value = Math.min(p.value, arc.fractionalFlow);
								}

								for (FlightArc arc : p.flightArcList) {
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for (ConnectingArc arc : p.connectingArcList) {
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for (GroundArc arc : p.groundArcList) {
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}

								// System.out.println("p value:"+p.value);

								sb.append(p.toString() + "\n");
							} else {
								isContinue = false;
							}

						}
					}

					try {
						MyFile.writeTxtFile(sb.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

			} else {
				System.out.println("Infeasible!!!");
			}

			cplex.end();
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return solution;
	}

}
