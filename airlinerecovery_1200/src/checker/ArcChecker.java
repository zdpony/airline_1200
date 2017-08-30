package checker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.GroundArc;
import sghku.tianchi.IntelligentAviation.entity.LineValue;
import sghku.tianchi.IntelligentAviation.entity.Node;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class ArcChecker {

	/*public static Map<Integer, Set<String>> aircraftConnectingArcMap = new HashMap<>();
	public static Map<Integer, Set<String>> aircraftFlightArcMap = new HashMap<>();
	public static Map<Integer, Set<String>> aircraftStraightenedArcMap = new HashMap<>();*/
	
	public static Map<Integer, Map<String,Double>> aircraftConnectingArcMap = new HashMap<>();
	public static Map<Integer, Map<String,Double>> aircraftFlightArcMap = new HashMap<>();
	public static Map<Integer, Map<String,Double>> aircraftStraightenedArcMap = new HashMap<>();

	public static double totalCost = 0;
	public static double totalCancelCost = 0;
	
	public static Map<Integer, List<String>> pathList = new HashMap<>();
	
	//检查optimal solution是否在我们的arc中出现
	public static void init() {
		// TODO Auto-generated method stub
		Parameter.isReadFixedRoutes = false;
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		
		for(Aircraft a:scenario.aircraftList) {
			/*Set<String> connectingArcSet = new HashSet<>();
			Set<String> flightArcSet = new HashSet<>();
			Set<String> straightenedArcSet = new HashSet<>();*/
			
			Map<String,Double> connectingArcSet = new HashMap();
			Map<String,Double> flightArcSet = new HashMap();
			Map<String,Double> straightenedArcSet = new HashMap();
			
			aircraftConnectingArcMap.put(a.id, connectingArcSet);
			aircraftFlightArcMap.put(a.id, flightArcSet);
			aircraftStraightenedArcMap.put(a.id, straightenedArcSet);
			
		}
		
		String fileName = "linearsolution_30_0828_80_421225.68.csv";
		
		Scanner sn = null;
		try {
			sn = new Scanner(new File(fileName));			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		while (sn.hasNextLine()) {
			String nextLine = sn.nextLine().trim();

			if (nextLine.equals("")) {
				break;
			}

			Scanner innerSn = new Scanner(nextLine);
			innerSn.useDelimiter(",");

			String nnn = innerSn.next();

			LineValue lv = new LineValue();
			lv.line = nextLine;
			lv.value = innerSn.nextDouble();

			readLine(scenario, lv.line);			
		}

		for(Aircraft a:scenario.aircraftList) {
			if(Math.abs(a.flow-1) > 1e-5) {
				System.out.println("aircraft flow error");
			}
		}
	
		for(Flight f:scenario.flightList){
			totalCancelCost += (1-f.flow) * f.importance * Parameter.COST_CANCEL;
			totalCost  += (1-f.flow) * f.importance * Parameter.COST_CANCEL;
		}
	}
	
	/*public static void checkFlightArcs(Aircraft a, List<FlightArc> flightArcList) {
		Set<String> wholeFlightArcSet = new HashSet<>();
		
		for(FlightArc arc:flightArcList) {
			if(!arc.flight.isStraightened) {
				wholeFlightArcSet.add(arc.flight.id+"_"+arc.takeoffTime);				
			}
		}
		
		Set<String> currentFlightArcSet = aircraftFlightArcMap.get(a.id);
		
		if(!wholeFlightArcSet.containsAll(currentFlightArcSet)) {
			System.out.println("we find exception : "+a.id+"  currentFlightArcSet:"+currentFlightArcSet.size()+"  "+wholeFlightArcSet.size());
		
			for(String sss:currentFlightArcSet){
				if(!wholeFlightArcSet.contains(sss)){
					System.out.println("exception : "+sss);
				}else{
					System.out.println("contains");
				}
			}
			System.exit(1);
		}
	}
	
	public static void checkStraightenedArcs(Aircraft a, List<FlightArc> flightArcList) {
		Set<String> wholeStraightenedArcSet = new HashSet<>();
		
		for(FlightArc arc:flightArcList) {
			if(arc.flight.isStraightened) {
				wholeStraightenedArcSet.add(arc.flight.connectingFlightpair.firstFlight.id+"_"+arc.flight.connectingFlightpair.secondFlight.id+"_"+arc.takeoffTime);				
			}
		}
		
		Set<String> currentStraightenedArcSet = aircraftStraightenedArcMap.get(a.id);
		
		if(!wholeStraightenedArcSet.containsAll(currentStraightenedArcSet)) {
			System.out.println("we find exception : "+a.id);
		
			for(String sss:currentStraightenedArcSet){
				if(!wholeStraightenedArcSet.contains(sss)){
					System.out.println("exception : "+sss+"  "+wholeStraightenedArcSet.size());
				}
			}
		}
	}*/
	
	/*public static void checkConnectingArcs(Aircraft a, List<ConnectingArc> connectingArcList) {
		Set<String> wholeConnectingArcSet = new HashSet<>();
		
		for(ConnectingArc arc:connectingArcList) {
			wholeConnectingArcSet.add(arc.connectingFlightPair.firstFlight.id+"_"+arc.connectingFlightPair.secondFlight.id+"_"+arc.firstArc.takeoffTime+"_"+arc.secondArc.takeoffTime);
		}
		
		Set<String> currentConnectingArcSet = aircraftConnectingArcMap.get(a.id);
		if(!wholeConnectingArcSet.containsAll(currentConnectingArcSet)) {
			System.out.println("we find exception : "+a.id);
			
			for(String sss:currentConnectingArcSet){
				if(!wholeConnectingArcSet.contains(sss)){
					System.out.println("exception:"+sss);
				}
			}
		}
	}*/
	
	public static void readLine(Scenario scenario, String line){
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);
		
		/*Set<String> connectingArcSet = aircraftConnectingArcMap.get(a.id);
		Set<String> flightArcSet = aircraftFlightArcMap.get(a.id);
		Set<String> straightenedArcSet = aircraftStraightenedArcMap.get(a.id);*/
		
		Map<String,Double> connectingArcSet = aircraftConnectingArcMap.get(a.id);
		Map<String,Double> flightArcSet = aircraftFlightArcMap.get(a.id);
		Map<String,Double> straightenedArcSet = aircraftStraightenedArcMap.get(a.id);
		
		double flow = sn.nextDouble();

		a.fixedDestination = a.initialLocation;
		a.flow += flow;
		List<Flight> flightList = new ArrayList<>();
		
		List<Flight> selectedFlightList = new ArrayList<>();
		
		while (sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");

			if (flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);

				f.actualTakeoffT = Integer.parseInt(flightArray[2]);
				f.actualLandingT = Integer.parseInt(flightArray[3]);
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f.leg.destinationAirport;
				
				f.aircraft = a;
				
				f.possibleDelaySet.add(f.actualTakeoffT - f.initialTakeoffT);
				selectedFlightList.add(f);
			} else if (flightArray[0].equals("s")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);
				f.isFixed = true;

				Flight f2 = scenario.flightList.get(Integer.parseInt(flightArray[2]) - 1);
				f2.isFixed = true;

				a.fixedDestination = scenario.flightList
						.get(Integer.parseInt(flightArray[2]) - 1).leg.destinationAirport;
			
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f2.leg.destinationAirport;
				f.actualTakeoffT = Integer.parseInt(flightArray[3]);
				f.actualLandingT = Integer.parseInt(flightArray[4]);
				
				f.aircraft = a;
				
				f.possibleDelaySet.add(f.actualTakeoffT - f.initialTakeoffT);
				selectedFlightList.add(f);
			} else if (flightArray[0].equals("d")) {

				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
			}
		}

		//处理联程拉直
		for(int i=0;i<selectedFlightList.size();i++) {
			Flight f1 = selectedFlightList.get(i);
			
			if(!f1.actualDestination.equals(f1.leg.destinationAirport)) {
				f1.connectingFlightpair.firstFlight.isStraightened = true;
				f1.connectingFlightpair.secondFlight.isStraightened = true;
				
				
				//straightenedArcSet.add(f1.connectingFlightpair.firstFlight.id+"_"+f1.connectingFlightpair.secondFlight.id+"_"+f1.actualTakeoffT);
				
				f1.connectingFlightpair.firstFlight.flow += flow;
				f1.connectingFlightpair.secondFlight.flow += flow;
			}else {
				
				
				f1.flow += flow;
			}
		}
		
		//处理联程航班
		for(int i=0;i<selectedFlightList.size()-1;i++) {
			Flight f1 = selectedFlightList.get(i);
			Flight f2 = selectedFlightList.get(i+1);
			
			if(f1.isIncludedInConnecting && f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id) {
				
				//connectingArcSet.add(f1.id+"_"+f2.id+"_"+f1.actualTakeoffT+"_"+f2.actualTakeoffT);
			}
		}
		
		while(selectedFlightList.size() > 0){
			Flight currentF = selectedFlightList.get(0);
			selectedFlightList.remove(0);
			
			if(!currentF.isIncludedInConnecting){
				FlightArc arc = new FlightArc();
				arc.aircraft = a;
				arc.flight = currentF;
				arc.takeoffTime = currentF.actualTakeoffT;
				arc.landingTime = currentF.actualLandingT;
				int delay = currentF.actualTakeoffT - currentF.initialTakeoffT;
				if(delay < 0){
					arc.earliness = -delay;
				}else{
					arc.delay = delay;
				}
				arc.calculateCost();
				totalCost += arc.cost * flow;
				
				String key = currentF.id+"_"+currentF.actualTakeoffT;
				if(flightArcSet.get(key) != null){
					flightArcSet.put(key, flightArcSet.get(key)+flow);
				}else{
					flightArcSet.put(key, flow);
				}
			}else{
				if(currentF.isStraightened){
					FlightArc arc = new FlightArc();
					arc.aircraft = a;
					arc.flight = currentF;
					arc.takeoffTime = currentF.actualTakeoffT;
					arc.landingTime = currentF.actualLandingT;
					
					int delay = currentF.actualTakeoffT - currentF.initialTakeoffT;
					if(delay < 0){
						arc.earliness = -delay;
					}else{
						arc.delay = delay;
					}
					
					arc.calculateCost();
					totalCost += arc.cost * flow;
					
					String key = currentF.connectingFlightpair.firstFlight.id+"_"+currentF.connectingFlightpair.secondFlight.id+"_"+currentF.connectingFlightpair.firstFlight.actualTakeoffT;
					if(straightenedArcSet.get(key) != null){
						straightenedArcSet.put(key, straightenedArcSet.get(key)+flow);
					}else{
						straightenedArcSet.put(key, flow);
					}
				}else{
					if(selectedFlightList.size() > 0){
						Flight nextF = selectedFlightList.get(0);
						if(nextF.isIncludedInConnecting && currentF.brotherFlight.id == nextF.id){
							FlightArc firstArc = new FlightArc();
							firstArc.flight = nextF.connectingFlightpair.firstFlight;
							firstArc.aircraft = a;
							firstArc.takeoffTime = nextF.connectingFlightpair.firstFlight.actualTakeoffT;
							firstArc.landingTime = nextF.connectingFlightpair.firstFlight.actualLandingT;
							
							int delay = nextF.connectingFlightpair.firstFlight.actualTakeoffT - nextF.connectingFlightpair.firstFlight.initialTakeoffT;
							if(delay < 0){
								firstArc.earliness = -delay;
							}else{
								firstArc.delay = delay;
							}
							
							FlightArc secondArc = new FlightArc();
							secondArc.flight = nextF.connectingFlightpair.secondFlight;
							secondArc.aircraft = a;
							secondArc.takeoffTime = nextF.connectingFlightpair.secondFlight.actualTakeoffT;
							secondArc.landingTime = nextF.connectingFlightpair.secondFlight.actualLandingT;
							
							delay = nextF.connectingFlightpair.secondFlight.actualTakeoffT - nextF.connectingFlightpair.secondFlight.initialTakeoffT;
							if(delay < 0){
								secondArc.earliness = -delay;
							}else{
								secondArc.delay = delay;
							}
							
							ConnectingArc ca = new ConnectingArc();
							ca.firstArc = firstArc;
							ca.secondArc = secondArc;
							ca.aircraft = a;
							ca.calculateCost();
							totalCost += ca.cost * flow;
							
							selectedFlightList.remove(0);
							
							String key = firstArc.flight.id+"_"+secondArc.flight.id+"_"+firstArc.takeoffTime+"_"+secondArc.takeoffTime;
							if(connectingArcSet.get(key) != null){
								connectingArcSet.put(key, connectingArcSet.get(key)+flow);
							}else{
								connectingArcSet.put(key, flow);
							}
						}else{
							FlightArc arc = new FlightArc();
							arc.aircraft = a;
							arc.flight = currentF;
							arc.takeoffTime = currentF.actualTakeoffT;
							arc.landingTime = currentF.actualLandingT;
							
							int delay = currentF.actualTakeoffT - currentF.initialTakeoffT;
							if(delay < 0){
								arc.earliness = -delay;
							}else{
								arc.delay = delay;
							}
							
							arc.calculateCost();
							totalCost += arc.cost * flow;
							
							String key = currentF.id+"_"+currentF.actualTakeoffT;
							if(flightArcSet.get(key) != null){
								flightArcSet.put(key, flightArcSet.get(key)+flow);
							}else{
								flightArcSet.put(key, flow);
							}
						}
					}else{
						FlightArc arc = new FlightArc();
						arc.aircraft = a;
						arc.flight = currentF;
						arc.takeoffTime = currentF.actualTakeoffT;
						arc.landingTime = currentF.actualLandingT;
						
						int delay = currentF.actualTakeoffT - currentF.initialTakeoffT;
						if(delay < 0){
							arc.earliness = -delay;
						}else{
							arc.delay = delay;
						}
						
						arc.calculateCost();
						totalCost += arc.cost * flow;
						
						String key = currentF.id+"_"+currentF.actualTakeoffT;
						if(flightArcSet.get(key) != null){
							flightArcSet.put(key, flightArcSet.get(key)+flow);
						}else{
							flightArcSet.put(key, flow);
						}
					}
				}
			}
		}
	}

	public static void updateArcCost(Scenario scenario, String line){
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);
		
		double flow = sn.nextDouble();

		List<Flight> selectedFlightList = new ArrayList<>();
		
		while (sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");

			if (flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);

				f.actualTakeoffT = Integer.parseInt(flightArray[2]);
				f.actualLandingT = Integer.parseInt(flightArray[3]);
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f.leg.destinationAirport;
				
				f.aircraft = a;
				
				selectedFlightList.add(f);
			} else if (flightArray[0].equals("s")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);
				f.isFixed = true;

				Flight f2 = scenario.flightList.get(Integer.parseInt(flightArray[2]) - 1);
				f2.isFixed = true;

				a.fixedDestination = scenario.flightList
						.get(Integer.parseInt(flightArray[2]) - 1).leg.destinationAirport;
			
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f2.leg.destinationAirport;
				f.actualTakeoffT = Integer.parseInt(flightArray[3]);
				f.actualLandingT = Integer.parseInt(flightArray[4]);
				
				f.aircraft = a;
				
				selectedFlightList.add(f);
			} else if (flightArray[0].equals("d")) {

				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
			}
		}

		Node currentNode = a.sourceNode;
		Flight currentFlight = selectedFlightList.get(0);
		selectedFlightList.remove(0);
		
		while(!currentNode.isSource){			
			boolean isFound = false;
			for(FlightArc arc:currentNode.flowoutFlightArcList){
				if(arc.flight.isStraightened){
					if(arc.flight.connectingFlightpair.firstFlight.id == currentFlight.id && currentFlight.actualTakeoffT == arc.takeoffTime){
						currentNode = arc.toNode;
						currentFlight = selectedFlightList.get(0);
						selectedFlightList.remove(0);
						
						arc.flow += flow;
						
						isFound = true;		
						break;
					}
				}else{
					if(arc.flight.id == currentFlight.id && arc.takeoffTime == currentFlight.actualTakeoffT){
						
						currentNode = arc.toNode;
						currentFlight = selectedFlightList.get(0);
						selectedFlightList.remove(0);
						
						arc.flow += flow;
						
						isFound = true;
						break;
					}
				}
			}
			if(!isFound){
				for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
					if(arc.firstArc.flight.id == currentFlight.id && arc.firstArc.takeoffTime == currentFlight.actualTakeoffT){
						if(selectedFlightList.size() > 0){
							Flight nextFlight = selectedFlightList.get(0);
							if(arc.secondArc.flight.id == nextFlight.id && arc.secondArc.takeoffTime == nextFlight.actualTakeoffT){
								
								currentNode = arc.toNode;
								selectedFlightList.remove(0);
								currentFlight = selectedFlightList.get(0);
								selectedFlightList.remove(0);
								arc.flow += flow;
								
								isFound = true;
							}
						}
					}
				}
			}
			if(!isFound){
				for(GroundArc arc:currentNode.flowoutGroundArcList){
					if(arc.toNode.isSink){
						
					}
				}
			}
		}
		
	}
}
