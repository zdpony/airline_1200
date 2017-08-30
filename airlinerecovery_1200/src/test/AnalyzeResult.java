package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.common.DateUtils;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class AnalyzeResult {
	public static List<Flight> resultFlightList = new ArrayList<>();
	
	public static void readResult(){
		Scanner sn = null;
		try {
			sn = new Scanner(new File("result/sghku_20170709.csv"));
		
			while(sn.hasNextLine()){
				String nextLine = sn.nextLine().trim();
				if(nextLine.equals("")){
					break;
				}
				resultFlightList.add(readFlight(nextLine));
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static Flight readFlight(String line){
		Flight f = new Flight();
		
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");
		
		f.id = sn.nextInt();
		sn.next();
		sn.next();
		
		f.takeoffTime = DateUtils.getNowDate(sn.next());

		return f;
	}
	
	public static void checkDelay(Scenario scenario){
		readResult();
		
		double earlyLimit = 100000;
		double delayLimit = -100000;
		
		int delayNum = 0;
		
		for(Flight f:resultFlightList){
			if(f.id-1 <= scenario.flightList.size()-1){
				Flight of = scenario.flightList.get(f.id-1);

				double gap = DateUtils.getTimeGap(f.takeoffTime, of.takeoffTime);
				if(gap < earlyLimit){
					earlyLimit = gap;
				}
				if(gap > delayLimit){
					delayLimit = gap;
				}
				
				if(gap <= 300){
					delayNum++;
				}else{
					System.out.println("affected flight:"+of.id+"  "+of.flightNo+" "+of.leg+" "+of.takeoffTime+"  "+of.landingTime+"  "+gap);
				}
			}
		}
		
		System.out.println(earlyLimit+"　"+delayLimit);
		System.out.println(delayNum+" "+resultFlightList.size());
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);

		checkDelay(scenario);
	}

}
