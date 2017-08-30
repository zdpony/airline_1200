package sghku.tianchi.IntelligentAviation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator2;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class PushForwardCplexModel {
	public IloCplex cplex;
	
	public void run(List<Aircraft> aircraftList, Scenario scenario){
		for(Aircraft aircraft:aircraftList){
			Collections.sort(aircraft.flightList, new FlightComparator2());
			for(Flight f:aircraft.flightList){
				f.flightarcList.clear();
			}
			NetworkConstructor initialization = new NetworkConstructor();
			for(Flight f:aircraft.flightList) {
				initialization.generateArcForFlight(aircraft, f , 5, scenario);					
			}
		
			solveEach(aircraft);
		}
	}
	
	public void solveEach(Aircraft aircraft){
		List<FlightArc> arcList = new ArrayList<>();
		for(Flight f:aircraft.flightList){
			arcList.addAll(f.flightarcList);
		}
		
		int index = 0;
		for(FlightArc arc:arcList){
			arc.id = index;
			index++;
		}
		
		try {
			cplex = new IloCplex();
			cplex.setOut(null);
		
			IloNumVar[] x = new IloNumVar[arcList.size()];
			IloLinearNumExpr obj = cplex.linearNumExpr();
			for(int i=0;i<arcList.size();i++){
				x[i] = cplex.boolVar();
				obj.addTerm(arcList.get(i).cost, x[i]);
			}
			cplex.addMinimize(obj);
			
			//每一个flight只能有一个arc选择
			for(Flight f:aircraft.flightList){
				IloLinearNumExpr cont = cplex.linearNumExpr();
				
				for(FlightArc arc:f.flightarcList){
					cont.addTerm(1, x[arc.id]);
				}
				
				cplex.addEq(cont, 1);
			}
			
			//连接约束
			for(int i=0;i<aircraft.flightList.size()-1;i++){
				Flight f1 = aircraft.flightList.get(i);
				Flight f2 = aircraft.flightList.get(i+1);
				
				int connT = Parameter.MIN_BUFFER_TIME;
				if(f1.isIncludedInConnecting && f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id){
					int initialConnT = f2.initialTakeoffT-f1.initialLandingT;
					if(initialConnT < connT){
						connT = initialConnT;
					}
				}
				
				IloLinearNumExpr cont = cplex.linearNumExpr();
				for(FlightArc arc:f1.flightarcList){
					//cont.addTerm(arc.readyTime, x[arc.id]);
					cont.addTerm(arc.landingTime+connT, x[arc.id]);
				}
				for(FlightArc arc:f2.flightarcList){
					cont.addTerm(-arc.takeoffTime, x[arc.id]);
				}
				
				cplex.addLe(cont, 0);
			}
			
			if(cplex.solve()){
				for(Flight f:aircraft.flightList){
					for(FlightArc arc:f.flightarcList){
						if(cplex.getValue(x[arc.id]) > 1e-6){
							f.actualTakeoffT = arc.takeoffTime;
							f.actualLandingT = arc.landingTime;
						}
					}
				}
			}
			
			cplex.end();
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
}
