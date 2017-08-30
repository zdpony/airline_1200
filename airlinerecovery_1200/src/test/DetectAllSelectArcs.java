package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.LineValueComparator;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.LineValue;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class DetectAllSelectArcs {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Parameter.isReadFixedRoutes = false;
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		
		String fileName = "linearsolution_30_421761.807_15.8.csv";
		
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

		System.out.println("--------------------------------------");
		
		int totalN = 0;
		
		int maxDelay = 0;
		int maxAdvance = 0;
		
		try {
			File file = new File("alldelayoptions");
			if(file.exists()) {
				file.delete();
			}
			MyFile.creatTxtFile("alldelayoptions");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		StringBuilder sb = new StringBuilder();
		
		//sb.append("f,aircraft,origin,destination,takeoff,landing,delays\n");
		
		for(Flight f:scenario.flightList){
			System.out.println(f.possibleDelaySet);
			if(f.possibleDelaySet.size() > 0){
				sb.append(f.id+",");
				
				for(int delay:f.possibleDelaySet){
					//System.out.print(delay+", ");
					sb.append(delay+"_");	
				}
				sb.append("\n");
				//System.out.println("]");
				
				
				totalN++;		
			}
			
		}
		try {
			MyFile.writeTxtFile(sb.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void readLine(Scenario scenario, String line){
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);
		
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
				
				f.aircraft = a;
				
				f.possibleDelaySet.add(f.actualTakeoffT - f.initialTakeoffT);
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
				
			} else if (flightArray[0].equals("d")) {

				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
			}
		}

	}

}
