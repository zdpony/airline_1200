package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.comparator.FlightComparator;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator2;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class ScheduleReader {
	
	public void readV2(Scenario scenario) {
		try {
			for(Aircraft a:scenario.aircraftList) {
				a.flightList.clear();
			}
			
			Scanner sn = new Scanner(new File("fixschedule"));
			
			while(sn.hasNextLine()) {
				String nextLine = sn.nextLine().trim();
				if(nextLine.equals("")) {
					break;
				}
				
				Scanner innerSn = new Scanner(nextLine);
				innerSn.useDelimiter(",");
				
				int aircraftId = innerSn.nextInt();
				Aircraft a = scenario.aircraftList.get(aircraftId-1);
				innerSn.next();
				
				while(innerSn.hasNext()) {
					String flightStr = innerSn.next();
					String[] flightArray = flightStr.split("_");
					
					if(flightArray[0].equals("n")) {
						Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);
						f.actualTakeoffT = Integer.parseInt(flightArray[2]);
						f.actualLandingT = Integer.parseInt(flightArray[3]);
						
						f.aircraft = a;
						a.flightList.add(f);
						
					}else if(flightArray[0].equals("s")) {
						Flight f1 = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);			
						Flight f2 = scenario.flightList.get(Integer.parseInt(flightArray[2])-1);			
						
						ConnectingFlightpair cp = f1.connectingFlightpair;
						
						int flyTime = cp.straightenLeg.flytimeArray[a.type-1];
						
						if(flyTime <= 0){  // if cannot retrieve fly time
							flyTime = cp.firstFlight.initialLandingT-cp.firstFlight.initialTakeoffT+ cp.secondFlight.initialLandingT-cp.secondFlight.initialTakeoffT;
						}
						
						Flight straightenedFlight = new Flight();
						straightenedFlight.isStraightened = true;
						straightenedFlight.connectingFlightpair = cp;
						straightenedFlight.leg = cp.straightenLeg;
						
						straightenedFlight.flyTime = flyTime;
								
						straightenedFlight.initialTakeoffT = cp.firstFlight.initialTakeoffT;
						straightenedFlight.initialLandingT = straightenedFlight.initialTakeoffT + flyTime;
						
						straightenedFlight.isAllowtoBringForward = cp.firstFlight.isAllowtoBringForward;
						straightenedFlight.isAffected = cp.firstFlight.isAffected;
						straightenedFlight.isDomestic = true;
						straightenedFlight.earliestPossibleTime = cp.firstFlight.earliestPossibleTime;
						straightenedFlight.latestPossibleTime = cp.firstFlight.latestPossibleTime;
					
						straightenedFlight.actualTakeoffT = Integer.parseInt(flightArray[3]);;
						straightenedFlight.actualLandingT = Integer.parseInt(flightArray[4]);;
						
						straightenedFlight.aircraft = a;
						a.flightList.add(straightenedFlight);
					}else if(flightArray[0].equals("d")) {
						
						a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2])-1);
					}
				}
				
			}
			
			for(Flight f:scenario.flightList) {
				f.isCancelled = true;
			}
			for(Aircraft a:scenario.aircraftList) {
				for(Flight f:a.flightList) {
					if(f.isStraightened) {
						f.connectingFlightpair.firstFlight.isCancelled = false;
						f.connectingFlightpair.secondFlight.isCancelled = false;
					}else {
						f.isCancelled = false;
					}
				}
				
				Collections.sort(a.flightList, new FlightComparator2());
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
