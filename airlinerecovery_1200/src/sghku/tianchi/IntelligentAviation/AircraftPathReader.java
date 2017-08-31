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

import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.LineValueComparator;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.LineValue;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class AircraftPathReader {
	public Set<Integer> idSet = new HashSet<>();

	// 读取已经固定的飞机路径
	public void read(String line, Scenario scenario) {
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);
		if (a.isFixed) {
			System.out.println("we find aircraft error " + a.id);
		}
		a.isFixed = true;

		double flow = sn.nextDouble();

		a.fixedDestination = a.initialLocation;

		while (sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");

			if (flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);
				
				f.isFixed = true;
				f.aircraft = a;
				f.isCancelled = false;
				f.actualTakeoffT = Integer.parseInt(flightArray[2]);
				f.actualLandingT = Integer.parseInt(flightArray[3]);
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f.leg.destinationAirport;

				a.fixedDestination = f.leg.destinationAirport;

				a.fixedFlightList.add(f);

				if (idSet.contains(f.id)) {
					System.out.println("error 1 " + f.id);
				} else {
					idSet.add(f.id);
				}
			} else if (flightArray[0].equals("s")) {
				Flight f1 = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);
				f1.isFixed = true;

				if (idSet.contains(f1.id)) {
					System.out.println("error 2");
				} else {
					idSet.add(f1.id);
				}

				Flight f2 = scenario.flightList.get(Integer.parseInt(flightArray[2]) - 1);
				f2.isFixed = true;

				if (idSet.contains(f2.id)) {
					System.out.println("error 3");
				} else {
					idSet.add(f2.id);
				}
				
				Flight f = scenario.straightenedFlightMap.get(f1.id+"_"+f2.id);
				
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f2.leg.destinationAirport;
				f.actualTakeoffT = Integer.parseInt(flightArray[3]);
				f.actualLandingT = Integer.parseInt(flightArray[4]);
				f.aircraft = a;
				f1.isCancelled = false;
				f2.isCancelled = false;
				f.isCancelled = false;
				
				f.flyTime = f.leg.flytimeArray[a.type-1];				
				f.initialLandingT = f.initialTakeoffT + f.flyTime;

				a.fixedFlightList.add(f);

				a.fixedDestination = scenario.flightList
						.get(Integer.parseInt(flightArray[2]) - 1).leg.destinationAirport;
			} else if (flightArray[0].equals("d")) {

				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
			}
		}
	}

	// 检查某一个路径是否约束冲突
	public boolean check(String line, Scenario scenario) {
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);

		double flow = sn.nextDouble();

		a.fixedDestination = a.initialLocation;

		boolean isFeasible = true;
		if (a.isFixed) {
			isFeasible = false;
			System.out.println("this way 1");
		}

		List<Flight> flightList = new ArrayList<>();

		if (isFeasible) {
			while (sn.hasNext()) {
				String flightStr = sn.next();
				String[] flightArray = flightStr.split("_");

				if (flightArray[0].equals("n")) {
					Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);
					f.actualOrigin = f.leg.originAirport;
					f.actualDestination = f.leg.destinationAirport;
					f.actualTakeoffT = Integer.parseInt(flightArray[2]);
					f.actualLandingT = Integer.parseInt(flightArray[3]);

					if (f.isFixed) {
						isFeasible = false;
						System.out.println("this way 2");
					}
					a.fixedDestination = f.leg.destinationAirport;
					flightList.add(f);
				} else if (flightArray[0].equals("s")) {
					Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);

					if (f.isFixed) {
						isFeasible = false;
						System.out.println("this way 3");
					}

					Flight f2 = scenario.flightList.get(Integer.parseInt(flightArray[2]) - 1);
					if (f2.isFixed) {
						isFeasible = false;
						System.out.println("this way 4");
					}
					a.fixedDestination = scenario.flightList
							.get(Integer.parseInt(flightArray[2]) - 1).leg.destinationAirport;

					f.actualOrigin = f.leg.originAirport;
					f.actualDestination = f2.leg.destinationAirport;
					f.actualTakeoffT = Integer.parseInt(flightArray[3]);
					f.actualLandingT = Integer.parseInt(flightArray[4]);
					flightList.add(f);
				} else if (flightArray[0].equals("d")) {

					a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
				}
			}
		}

		if (a.fixedDestination.finalAircraftNumber[a.type - 1] <= 0) {
			isFeasible = false;
			System.out.println("this way 5");
		}

		// 检查停机约束
		if (isFeasible) {
			Map<Integer, Integer> affectedGroundArcLimitMapClone = new HashMap<>();
			for (int key : scenario.affectedAirportParkingLimitMap.keySet()) {
				affectedGroundArcLimitMapClone.put(key, scenario.affectedAirportParkingLimitMap.get(key));
			}

			for (int i = 0; i < flightList.size() - 1; i++) {
				Flight f1 = flightList.get(i);
				Flight f2 = flightList.get(i + 1);

				boolean isFound = false;
				if (scenario.affectedAirportSet.contains(f1.actualDestination.id)) {
					for (int t = f1.actualLandingT + 1; t <= f2.actualTakeoffT - 1; t++) {
						if (t >= Parameter.airportBeforeTyphoonTimeWindowEnd && t <= Parameter.airportAfterTyphoonTimeWindowStart) {
							isFound = true;
							break;
						}
					}
				}

				if (isFound) {
					int value = affectedGroundArcLimitMapClone.get(f1.actualDestination.id);
					affectedGroundArcLimitMapClone.put(f1.actualDestination.id, value - 1);
				}
			}

			for (int key : scenario.affectedAirportParkingLimitMap.keySet()) {
				if (affectedGroundArcLimitMapClone.get(key) < 0) {
					isFeasible = false;
					System.out.println("this way 6");
				}
			}
		}

		// 检查起降约束
		if (isFeasible) {
			Map<String, Integer> airportCapacityMapClone = new HashMap<>();
			for (String key : scenario.affectAirportLdnTkfCapacityMap.keySet()) {
				airportCapacityMapClone.put(key, scenario.affectAirportLdnTkfCapacityMap.get(key));
			}

			for (int i = 0; i < flightList.size(); i++) {
				Flight f1 = flightList.get(i);

				Integer value = airportCapacityMapClone.get(f1.actualOrigin.id + "_" + f1.actualTakeoffT);
				if (value != null) {
					airportCapacityMapClone.put(f1.actualOrigin.id + "_" + f1.actualTakeoffT, value - 1);
					
					if(value - 1 < 0){
						System.out.println("info takeoff:"+f1.id+" "+f1.actualTakeoffT);
					}
				}

				value = airportCapacityMapClone.get(f1.actualDestination.id + "_" + f1.actualLandingT);
				if (value != null) {
					airportCapacityMapClone.put(f1.actualDestination.id + "_" + f1.actualLandingT, value - 1);
					
					if(value - 1 < 0){
						System.out.println("info landing:"+f1.id+" "+f1.actualLandingT);
					}
				}
			}
			
			for(String key:airportCapacityMapClone.keySet()){
				int value = airportCapacityMapClone.get(key);
				if(value < 0){
					isFeasible = false;
					System.out.println("this way 7 ");
					
				}
			}
		}

		return isFeasible;
	}

	// 固定飞机路径
	public void fixAircraftRoute(Scenario scenario, int number) {
		Scanner sn = null;
		try {
			//sn = new Scanner(new File("linearsolution.csv"));
			sn = new Scanner(new File(Parameter.linearsolutionfilename));
			//sn = new Scanner(new File("fixschedule"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<LineValue> lvList = new ArrayList<>();

		while (sn.hasNextLine()) {
			String nextLine = sn.nextLine().trim();

			if (nextLine.equals("")) {
				break;
			}
			System.out.println(nextLine);
			Scanner innerSn = new Scanner(nextLine);
			innerSn.useDelimiter(",");

			String nnn = innerSn.next();

			LineValue lv = new LineValue();
			lv.line = nextLine;
			lv.value = innerSn.nextDouble();

			if (lv.value > 0.5 - 1e-6) {
				lvList.add(lv);
			}

			/*
			 * if(lv.value > 1e-6) { lvList.add(lv); }
			 */
		}

		Collections.sort(lvList, new LineValueComparator());

		/*int nnnn = 0;

		for (int i = 0; i < lvList.size(); i++) {
			System.out.println("index:"+i);
			LineValue lv = lvList.get(i);

			if (check(lv.line, scenario)) {
				update(lv.line, scenario);
				// System.out.println(lv.line);
				
				nnnn++;

				if (nnnn >= number) {
					break;
				}
			} else {
				System.out.println("violation " + i);
			}
		}
		System.exit(1);*/
		
		try {
			MyFile.creatTxtFile("fixschedule");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("-------------------------------------");
		int nnn = 0;

		for (int i = 0; i < lvList.size(); i++) {
			System.out.println("index:"+i);
			LineValue lv = lvList.get(i);

			if (check(lv.line, scenario)) {
				update(lv.line, scenario);
				// System.out.println(lv.line);
				try {
					MyFile.writeTxtFile(lv.line);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				nnn++;

				if (nnn >= number) {
					break;
				}
			} else {
				System.out.println("violation " + i);
			}
		}
		
		// System.out.println("nnn:"+nnn);
	}

	// 固定某一个飞机路径，更新对应的信息
	public void update(String line, Scenario scenario) {
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);
		a.isFixed = true;

		double flow = sn.nextDouble();

		a.fixedDestination = a.initialLocation;
		List<Flight> flightList = new ArrayList<>();
		
		while (sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");

			if (flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);

				f.actualTakeoffT = Integer.parseInt(flightArray[2]);
				f.actualLandingT = Integer.parseInt(flightArray[3]);
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f.leg.destinationAirport;
				
				f.isFixed = true;
				a.fixedDestination = f.leg.destinationAirport;
				flightList.add(f);
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
				flightList.add(f);
			} else if (flightArray[0].equals("d")) {

				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
			}
		}

		a.fixedDestination.finalAircraftNumber[a.type - 1]--;

		// 更新停机约束
		for (int i = 0; i < flightList.size() - 1; i++) {
			Flight f1 = flightList.get(i);
			Flight f2 = flightList.get(i + 1);

			boolean isFound = false;
			if (scenario.affectedAirportSet.contains(f1.actualDestination.id)) {
				for (int t = f1.actualLandingT + 1; t <= f2.actualTakeoffT - 1; t++) {
					if (t >= Parameter.airportBeforeTyphoonTimeWindowEnd && t <= Parameter.airportAfterTyphoonTimeWindowStart) {
						isFound = true;
						break;
					}
				}
			}

			if (isFound) {
				int value = scenario.affectedAirportParkingLimitMap.get(f1.actualDestination.id);
				scenario.affectedAirportParkingLimitMap.put(f1.actualDestination.id, value - 1);
			}
		}

		// 更新起降约束
		for (int i = 0; i < flightList.size(); i++) {
			Flight f1 = flightList.get(i);

			Integer value = scenario.affectAirportLdnTkfCapacityMap.get(f1.actualOrigin.id + "_" + f1.actualTakeoffT);
			if (value != null) {
				if("50_11155".equals(f1.actualOrigin.id + "_" + f1.actualTakeoffT)){
					System.out.println("one takeoff "+f1.id+" "+f1.actualTakeoffT);
				}
				scenario.affectAirportLdnTkfCapacityMap.put(f1.actualOrigin.id + "_" + f1.actualTakeoffT, value - 1);
			}

			value = scenario.affectAirportLdnTkfCapacityMap.get(f1.actualDestination.id + "_" + f1.actualLandingT);
			if (value != null) {
				if("50_11155".equals(f1.actualDestination.id + "_" + f1.actualLandingT)){
					System.out.println("one landing "+f1.id+" "+f1.actualLandingT);
				}
				scenario.affectAirportLdnTkfCapacityMap.put(f1.actualDestination.id + "_" + f1.actualLandingT, value - 1);
			}
		}
	}
}
