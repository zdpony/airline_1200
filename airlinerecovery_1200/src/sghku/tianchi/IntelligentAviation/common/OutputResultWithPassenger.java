package sghku.tianchi.IntelligentAviation.common;

import java.io.File;
import java.io.IOException;

import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightArcItinerary;
import sghku.tianchi.IntelligentAviation.entity.FlightSectionItinerary;
import sghku.tianchi.IntelligentAviation.entity.Itinerary;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class OutputResultWithPassenger {
	
	
	public void writeResult(Scenario scenario, String outputName){
		for(ConnectingFlightpair cp:scenario.connectingFlightList) {
			cp.firstFlight.isIncludedInConnecting = true;
			cp.secondFlight.isIncludedInConnecting = true;
		}
		
		StringBuilder sb = new StringBuilder();

		try {
			File file = new File(outputName);
			if(file.exists()){
				file.delete();
			}
			
			MyFile.creatTxtFile(outputName);

			int deadheadIndex = 9001;
			
			for(Flight f:scenario.flightList){
				if(f.isDeadhead && f.isCancelled) {
					continue;
				}
				
				int dateTk = f.actualTakeoffT/1440;
				int remainderTk = f.actualTakeoffT%1440;
				int hourTk = remainderTk/60;
				int minuteTk = remainderTk%60;
				String writtenTimeTk = "2017/05/0"+dateTk+" ";
				if(hourTk<10){
					writtenTimeTk += "0"+hourTk+":"; 
				}else{
					writtenTimeTk += ""+hourTk+":"; 
				}
				if(minuteTk<10){
					writtenTimeTk += "0"+minuteTk; 
				}else{
					writtenTimeTk += ""+minuteTk; 
				}
				
				int dateLd = f.actualLandingT/1440;
				int remainderLd = f.actualLandingT%1440;
				int hourLd = remainderLd/60;
				int minuteLd = remainderLd%60;
				String writtenTimeLd = "2017/05/0"+dateLd+" ";
				if(hourLd<10){
					writtenTimeLd += "0"+hourLd+":"; 
				}else{
					writtenTimeLd += ""+hourLd+":"; 
				}
				if(minuteLd<10){
					writtenTimeLd += "0"+minuteLd; 
				}else{
					writtenTimeLd += ""+minuteLd; 
				}
				
				//System.out.println(f.id+","+f.isCancelled+", "+f.isStraightened+","+f.isDeadhead);
				sb.append((f.isDeadhead?deadheadIndex++:f.id)+","+f.leg.originAirport.id+","+f.leg.destinationAirport.id+",");		
				sb.append(writtenTimeTk+","+writtenTimeLd+","+f.aircraft.id+",");
						
				if(f.isCancelled){
					sb.append("1,");
				}else{
					sb.append("0,");
				}
				if(f.isStraightened){
					sb.append("1,");
				}else{
					sb.append("0,");
				}
				if(f.isDeadhead){
					sb.append("1,");
				}else{
					sb.append("0,");
				}
				
				//passengers
				
				boolean isSignChange = false;
				StringBuilder signChangeSb = new StringBuilder();
				
				if(f.itinerary != null){
					for(FlightSectionItinerary fsi:f.itinerary.flightSectionItineraryList){
						if(fsi.volume > 1e-6){
							if(fsi.flightSection.flight.id != f.id){
								int num = (int)Math.round(fsi.volume);
								if(num > 0){
									signChangeSb.append(fsi.flightSection.flight.id +":"+(int)Math.round(fsi.volume)+"&");
									isSignChange = true;
								}		
							}
						}
					}
					
					for(FlightArcItinerary fai:f.itinerary.flightArcItineraryList){
						if(fai.volume > 1e-6){
							if(fai.flightArc.flight.id != f.id){
								int num = (int)Math.round(fai.volume);
								if(num > 0){
									
									signChangeSb.append(fai.flightArc.flight.id +":"+(int)Math.round(fai.volume)+"&");
									isSignChange = true;
								}		
							}
						}
					}
					
					if(isSignChange){

						//System.out.println("previous:"+signChangeSb.toString());
						signChangeSb.deleteCharAt(signChangeSb.length()-1);  //delete the last &
						//System.out.println("after:"+signChangeSb.toString());

					}
				}
				
				
				if(isSignChange){
					
					sb.append("1,");			
					sb.append(signChangeSb.toString());
					sb.append("\n");
				}else{
					sb.append("0,"+"\n");
				}
			}
			
	
			sb.deleteCharAt(sb.length()-1);

			MyFile.writeTxtFile(sb.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
