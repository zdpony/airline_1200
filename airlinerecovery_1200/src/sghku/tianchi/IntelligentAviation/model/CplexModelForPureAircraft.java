package sghku.tianchi.IntelligentAviation.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ilog.concert.IloException;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Param.MIP.Strategy;
import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator2;
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
import sghku.tianchi.IntelligentAviation.entity.Node;
import sghku.tianchi.IntelligentAviation.entity.ParkingInfo;
import sghku.tianchi.IntelligentAviation.entity.Path;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.Solution;

public class CplexModelForPureAircraft {
	public IloCplex cplex;

	//the network flow model for initial problem solving

	public Solution run(List<Aircraft> aircraftList, List<Flight> flightList, List<ConnectingFlightpair> cfList, List<Airport> airportList, Scenario sce, boolean isFractional, boolean isAllowToCancelSingleFlightInAnConnectingFlight, boolean isCancelAllowed){
		Solution solution = new Solution();
		solution.involvedAircraftList.addAll(aircraftList);
		
		try {
				
			cplex = new IloCplex();
			
			if(isFractional){
				
			}else{
				//cplex.setOut(null);				
			}
			
			/*cplex.setParam(IloCplex.Param.RootAlgorithm,
                    IloCplex.Algorithm.Network);*/

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
			
			IloLinearNumExpr obj = cplex.linearNumExpr();

			for(int i=0;i<flightArcList.size();i++){
				if(isFractional){
					x[i] = cplex.numVar(0, 1);
				}else{
					x[i] = cplex.boolVar();
				}
				
				obj.addTerm(flightArcList.get(i).cost, x[i]);
				flightArcList.get(i).id = i;
			}
			for(int i=0;i<connectingArcList.size();i++){
				if(isFractional){
					beta[i] = cplex.numVar(0, 1);
				}else{
					beta[i] = cplex.boolVar();					
				}
				obj.addTerm(connectingArcList.get(i).cost, beta[i]);
				connectingArcList.get(i).id = i;
			}
			for(int i=0;i<groundArcList.size();i++){
				//y[i] = cplex.boolVar();
				y[i] = cplex.numVar(0, 1);
				groundArcList.get(i).id = i;
			}
			for(int i=0;i<flightList.size();i++){
				Flight f = flightList.get(i);
				//z[i] = cplex.boolVar();
				f.idInCplexModel = i;
				z[i] = cplex.numVar(0, 1);

				if(Parameter.isPassengerCostConsidered){
					obj.addTerm(f.importance*Parameter.COST_CANCEL+f.totalTransferCancellationCost+f.totalConnectingCancellationCost, z[i]);					
				}else{
					obj.addTerm(f.importance*Parameter.COST_CANCEL, z[i]);
				}
			}
					
			cplex.addMinimize(obj);

			//1. flow balance constraints
			for(Node n:nodeList){
				IloLinearNumExpr flowBalanceConstraint = cplex.linearNumExpr();

				for(FlightArc arc:n.flowinFlightArcList){
					flowBalanceConstraint.addTerm(1, x[arc.id]);
				}
				for(FlightArc arc:n.flowoutFlightArcList){
					flowBalanceConstraint.addTerm(-1, x[arc.id]);
				}

				for(ConnectingArc arc:n.flowinConnectingArcList){
					flowBalanceConstraint.addTerm(1, beta[arc.id]);
				}
				for(ConnectingArc arc:n.flowoutConnectingArcList){
					flowBalanceConstraint.addTerm(-1, beta[arc.id]);
				}

				for(GroundArc arc:n.flowinGroundArcList){
					flowBalanceConstraint.addTerm(1, y[arc.id]);
				}
				for(GroundArc arc:n.flowoutGroundArcList){
					flowBalanceConstraint.addTerm(-1, y[arc.id]);
				}

				cplex.addEq(flowBalanceConstraint, 0);
			}

			//2. turn-around arc flow
			for(Aircraft aircraft:aircraftList){
				IloLinearNumExpr turnaroundConstraint = cplex.linearNumExpr();

				turnaroundConstraint.addTerm(1, y[aircraft.turnaroundArc.id]);

				cplex.addEq(turnaroundConstraint, 1);
			}

			//3. for each flight, at least one arc can be selected, the last flight can not be cancelled
			for(int i=0;i<flightList.size();i++){
				Flight f = flightList.get(i);

				IloLinearNumExpr flightSelectionConstraint = cplex.linearNumExpr();

				for(FlightArc arc:f.flightarcList){
					flightSelectionConstraint.addTerm(1, x[arc.id]);
				}

				for(ConnectingArc arc:f.connectingarcList){
					flightSelectionConstraint.addTerm(1, beta[arc.id]);
				}

				/*if(!f.isLatest){
					flightSelectionConstraint.addTerm(1, z[i]);
				}*/
				//对于在调整窗口内的航班才可以取消
				if(isCancelAllowed){
					if(f.isIncludedInTimeWindow){
						flightSelectionConstraint.addTerm(1, z[i]);	
					}
				}
				
				cplex.addEq(flightSelectionConstraint, 1);
			}

			//4. base balance constraints
			for(int i=0;i<airportList.size();i++){
				Airport airport = airportList.get(i);

				for(int j=0;j<Parameter.TOTAL_AIRCRAFTTYPE_NUM;j++) {
					IloLinearNumExpr baseConstraint = cplex.linearNumExpr();
					for(GroundArc arc:airport.sinkArcList[j]){
		
						baseConstraint.addTerm(1, y[arc.id]);
					}

					cplex.addEq(baseConstraint, airport.finalAircraftNumber[j]);
				}			
			}
			
			// 5. 对于每一个联程航班，两趟航班必须由同一个飞机执行
			if(isAllowToCancelSingleFlightInAnConnectingFlight) {
				for (ConnectingFlightpair cf : cfList) {
					
					IloLinearNumExpr cont = cplex.linearNumExpr();
					
					for (FlightArc arc : cf.firstFlight.flightarcList) {
						cont.addTerm(1, x[arc.id]);
					}
					cont.addTerm(-1, z[cf.secondFlight.idInCplexModel]);

					cplex.addLe(cont, 0);

					cont = cplex.linearNumExpr();

					for (FlightArc arc : cf.secondFlight.flightarcList) {
						cont.addTerm(1, x[arc.id]);		
					}
					cont.addTerm(-1, z[cf.firstFlight.idInCplexModel]);

					cplex.addLe(cont, 0);
				}
			}
			
			//8. 机场起降约束
			for(String key:sce.keyList) {
				IloLinearNumExpr airportConstraint = cplex.linearNumExpr();
				List<FlightArc> faList = sce.airportTimeFlightArcMap.get(key);
				List<ConnectingArc> caList = sce.airportTimeConnectingArcMap.get(key);
				
				for(FlightArc arc:faList) {
					airportConstraint.addTerm(1, x[arc.id]);
				}
				for(ConnectingArc arc:caList) {
					airportConstraint.addTerm(1, beta[arc.id]);
				}
				
				cplex.addLe(airportConstraint, sce.affectAirportLdnTkfCapacityMap.get(key));
			}
			
			//9. 停机约束
			for(Integer airport:sce.affectedAirportSet) {
				IloLinearNumExpr parkingConstraint = cplex.linearNumExpr();
				List<GroundArc> gaList = sce.affectedAirportCoverParkLimitGroundArcMap.get(airport);
				for(GroundArc ga:gaList) {
					parkingConstraint.addTerm(1, y[ga.id]);					
				}
				
				cplex.addLe(parkingConstraint, sce.affectedAirportParkingLimitMap.get(airport));
			}
			
			if(cplex.solve()){
		
				if(isFractional){
					System.out.println("fractional value:"+cplex.getObjValue()+"  ");
				
					Parameter.fractionalObjective = cplex.getObjValue();
					
					try {
						File file = new File("linearsolution.csv");
						if(file.exists()){
							file.delete();
						}
						
						MyFile.creatTxtFile("linearsolution.csv");
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
					for(FlightArc fa:flightArcList){
						
						if(cplex.getValue(x[fa.id])>1e-6){
							fa.fractionalFlow = cplex.getValue(x[fa.id]);
						}
					}
					for(ConnectingArc arc:connectingArcList){
						if(cplex.getValue(beta[arc.id]) > 1e-6){
							arc.fractionalFlow = cplex.getValue(beta[arc.id]);
						}
					}
					for(GroundArc ga:groundArcList){
						if(cplex.getValue(y[ga.id]) > 1e-6){
							ga.fractionalFlow = cplex.getValue(y[ga.id]);
						}
					}
					
					StringBuilder sb = new StringBuilder();
					for(Aircraft a:aircraftList){
						System.out.println("aircraft:"+a.id);
						boolean isContinue = true;
						
						while(isContinue){
							Node currentNode = a.sourceNode;
							
							double flowOut = 0;
							for(FlightArc arc:currentNode.flowoutFlightArcList){
								flowOut += arc.fractionalFlow;
							}
							for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
								flowOut += arc.fractionalFlow;
							}
							for(GroundArc arc:currentNode.flowoutGroundArcList){
								flowOut += arc.fractionalFlow;
							}
							
							System.out.println("flow out:"+flowOut);
							if(flowOut > 1e-6){
								
								Path p = new Path();
								p.aircraft = a;
								
								while(!currentNode.isSink){
									
									double maximumFlow = -1e-6;
									FlightArc maxFlightArc = null;
									ConnectingArc maxConnectingArc = null;
									GroundArc maxGroundArc = null;
									for(FlightArc arc:currentNode.flowoutFlightArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = arc;
											maxConnectingArc = null;
											maxGroundArc = null;
										}
									}
									
									for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = arc;
											maxGroundArc = null;
										}
									}
									
									for(GroundArc arc:currentNode.flowoutGroundArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = null;
											maxGroundArc = arc;
										}
									}
									
									if(maxFlightArc != null){
										currentNode = maxFlightArc.toNode; 
										p.flightArcList.add(maxFlightArc);
									}
									if(maxConnectingArc != null){
										currentNode = maxConnectingArc.toNode;
										p.connectingArcList.add(maxConnectingArc);
									}
									if(maxGroundArc != null){
										currentNode = maxGroundArc.toNode;
										p.groundArcList.add(maxGroundArc);
									}
									
								}	
								

								p.value = 1.0;
								for(FlightArc arc:p.flightArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(ConnectingArc arc:p.connectingArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(GroundArc arc:p.groundArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								
								for(FlightArc arc:p.flightArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(ConnectingArc arc:p.connectingArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(GroundArc arc:p.groundArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								
								//System.out.println("p value:"+p.value);
								
								sb.append(p.toString()+"\n");
							}else{
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
					
				}else{
					//if we solve it integrally 
					System.out.println("solve: obj = "+cplex.getObjValue());
					
					solution.objValue = cplex.getObjValue();
					Parameter.objective += cplex.getObjValue();
					
					
					for(FlightArc fa:flightArcList){
						
						if(cplex.getValue(x[fa.id])>1e-6){
							solution.selectedFlightArcList.add(fa);
						}
					}
					for(ConnectingArc arc:connectingArcList){
						if(cplex.getValue(beta[arc.id]) > 1e-6){
							
							solution.selectedConnectingArcList.add(arc);			
						}
					}

					int cancelN1 = 0;
					int cancelN2 = 0;
					for(int i=0;i<flightList.size();i++){
						Flight f = flightList.get(i);
						if(f.isCancelled){
							cancelN1++;
						}
					}
					
					
					for(int i=0;i<flightList.size();i++){
						Flight f = flightList.get(i);
						if(cplex.getValue(z[i]) > 1e-6){
							solution.cancelledFlightList.add(f);
							System.out.println("cancelled flight:"+f.id+"  "+flightList.size());
							
							cancelN2++;
						}
					}
					
					System.out.println("saved flights:"+cancelN1+" "+cancelN2);
					
					try {
						File file = new File("linearsolution.csv");
						if(file.exists()){
							file.delete();
						}
						
						MyFile.creatTxtFile("linearsolution.csv");
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
					for(FlightArc fa:flightArcList){
						
						if(cplex.getValue(x[fa.id])>1e-6){
							fa.fractionalFlow = cplex.getValue(x[fa.id]);
						}
					}
					for(ConnectingArc arc:connectingArcList){
						if(cplex.getValue(beta[arc.id]) > 1e-6){
							arc.fractionalFlow = cplex.getValue(beta[arc.id]);
						}
					}
					for(GroundArc ga:groundArcList){
						if(cplex.getValue(y[ga.id]) > 1e-6){
							ga.fractionalFlow = cplex.getValue(y[ga.id]);
						}
					}
					
					StringBuilder sb = new StringBuilder();
					for(Aircraft a:aircraftList){
						System.out.println("aircraft:"+a.id);
						boolean isContinue = true;
						
						while(isContinue){
							Node currentNode = a.sourceNode;
							
							double flowOut = 0;
							for(FlightArc arc:currentNode.flowoutFlightArcList){
								flowOut += arc.fractionalFlow;
							}
							for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
								flowOut += arc.fractionalFlow;
							}
							for(GroundArc arc:currentNode.flowoutGroundArcList){
								flowOut += arc.fractionalFlow;
							}
							
							System.out.println("flow out:"+flowOut);
							if(flowOut > 1e-6){
								
								Path p = new Path();
								p.aircraft = a;
								
								while(!currentNode.isSink){
									
									double maximumFlow = -1e-6;
									FlightArc maxFlightArc = null;
									ConnectingArc maxConnectingArc = null;
									GroundArc maxGroundArc = null;
									for(FlightArc arc:currentNode.flowoutFlightArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = arc;
											maxConnectingArc = null;
											maxGroundArc = null;
										}
									}
									
									for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = arc;
											maxGroundArc = null;
										}
									}
									
									for(GroundArc arc:currentNode.flowoutGroundArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = null;
											maxGroundArc = arc;
										}
									}
									
									if(maxFlightArc != null){
										currentNode = maxFlightArc.toNode; 
										p.flightArcList.add(maxFlightArc);
									}
									if(maxConnectingArc != null){
										currentNode = maxConnectingArc.toNode;
										p.connectingArcList.add(maxConnectingArc);
									}
									if(maxGroundArc != null){
										currentNode = maxGroundArc.toNode;
										p.groundArcList.add(maxGroundArc);
									}
									
								}	
								

								p.value = 1.0;
								for(FlightArc arc:p.flightArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(ConnectingArc arc:p.connectingArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(GroundArc arc:p.groundArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								
								for(FlightArc arc:p.flightArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(ConnectingArc arc:p.connectingArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(GroundArc arc:p.groundArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								
								System.out.println("p value:"+p.value);
								
								sb.append(p.toString()+"\n");
							}else{
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
			
			}else{
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
