package sghku.tianchi.IntelligentAviation.algorithm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ClosureInfo;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class FlightDelayLimitGenerator {
	private Set<Integer> AirportID_49_50_61 = new HashSet(Arrays.asList(49, 50, 61)); //contains id
	private Map<Integer, Set<Integer>> formerAirportMap = new HashMap<>();
	private List<Integer> AirportID_5_6_22_49_76 = Arrays.asList(5, 6, 22, 49, 76); //contains id
	private Set<Integer> indirectTyphoonAffectedAirportSet = new HashSet<>();
	private Set<Integer> AirportID_25_67 = new HashSet(Arrays.asList(25, 67)); //contains id
		
	//设定flight delay限制
	public void setFlightDelayLimit(Scenario scenario){

		//在7号08:00-22:00，被49_50_61所关联的到达机场，视为间接受影响机场
		for(Flight f:scenario.flightList) {
			if(f.initialTakeoffT > 1440*7+8*60 && f.initialTakeoffT <= 1440*7+22*60) {
				if(AirportID_49_50_61.contains(f.leg.originAirport.id)){
					indirectTyphoonAffectedAirportSet.add(f.leg.destinationAirport.id);
				}
			}
			//把7号00:00-16:00之间起飞的航班的起飞和到达机场放入formerAirportMap,方便后面检索
			if(f.initialTakeoffT > 1440*7 && f.initialTakeoffT <= 1440*7 + 16*60) {
				Set formerAirportSet = formerAirportMap.get(f.leg.destinationAirport.id);
				if(formerAirportSet==null){
					formerAirportSet = new HashSet();
					formerAirportSet.add(f.leg.originAirport.id);
					formerAirportMap.put(f.leg.destinationAirport.id, formerAirportSet);
				}else{
					formerAirportSet.add(f.leg.originAirport.id);
				}
				/*if(f.leg.destinationAirport.id==7){
					System.out.println(formerAirportSet);
				}*/
			}
		}
		if(indirectTyphoonAffectedAirportSet.contains(77))System.out.println("contains 77");
		//System.out.println("typhoonAffectedAirportSet:"+typhoonAffectedAirportSet.size()+" "+airportList.size());

		for(Flight f:scenario.flightList) {
			//首先处理台风场景的影响 7号00:00-24:00
			if(f.initialTakeoffT > 1440*7 && f.initialTakeoffT <= 1440*8){
				//1. 起飞机场属于49_50_61
				if(AirportID_49_50_61.contains(f.leg.originAirport.id)){
					//initial tkfTime 在17:00以后
					if(f.initialTakeoffT > 1440*7+17*60){
						int periodStart = f.initialTakeoffT;
						int periodEnd = f.initialTakeoffT + 6*60;  //可以最多delay 6 小时
						int landingEnd = f.initialLandingT + 6*60;  //用来检测是否进入机场关闭阶段
						if(AirportID_5_6_22_49_76.contains(f.leg.destinationAirport.id)){
							boolean vio = false;
							int airportCloseTime = 0;
							int airportOpenTime = 0;
							for(ClosureInfo ci:f.leg.destinationAirport.closedSlots){
								if(landingEnd>ci.startTime && landingEnd<ci.endTime){
									vio = true;
									airportCloseTime = ci.startTime;
									airportOpenTime = ci.endTime;
									break;
								}
							}
							if(vio){
								periodEnd = airportCloseTime;
								int periodStart2 = airportOpenTime - (f.initialLandingT - f.initialTakeoffT);
								int periodEnd2 = periodStart2;
								f.timeLimitList.add(new int[] {periodStart2,periodEnd2});
							}
							
						}
						//第一个int[]加入timeLimitList
						f.timeLimitList.add(new int[] {periodStart,periodEnd});
					}else{  //initial tkfTime 在17:00之前，不可能delay到机场关闭时间，所以不用考虑机场关闭
						int periodStart = 1440*7+17*60;   //最早时间是台风结束的时间
						int periodEnd = periodStart + 7*60;  //可以最多delay 7 小时
						f.timeLimitList.add(new int[] {periodStart,periodEnd});
					}
				}
				//2. 降落机场属于49_50_61
				else if(AirportID_49_50_61.contains(f.leg.destinationAirport.id)){
					// initial降落时间17:00之后
					if(f.initialLandingT > 1440*7+17*60){
						int periodStart = f.initialTakeoffT;
						int periodEnd = f.initialTakeoffT + 6*60;  //可以最多是delay 6 小时
						int landingEnd = f.initialLandingT + 6*60;  //用来检测是否进入机场关闭阶段
						//此处要判断最晚降落时间是否进入到达机场的机场关闭阶段
						if(AirportID_5_6_22_49_76.contains(f.leg.destinationAirport.id)){
							boolean vio = false;
							int airportCloseTime = 0;
							int airportOpenTime = 0;
							for(ClosureInfo ci:f.leg.destinationAirport.closedSlots){
								if(landingEnd>ci.startTime && landingEnd<ci.endTime){
									vio = true;
									airportCloseTime = ci.startTime;
									airportOpenTime = ci.endTime;
									break;
								}
							}
							if(vio){
								periodEnd = airportCloseTime;
								int periodStart2 = airportOpenTime - (f.initialLandingT - f.initialTakeoffT);
								int periodEnd2 = periodStart2 + 60;   //之后可以改成不加60，而只是设一个时间点，这是由于gap不是5分钟而设置的粗略时间段
								f.timeLimitList.add(new int[] {periodStart2,periodEnd2});

							}
						}
						//第一个int[]加入timeLimitList
						f.timeLimitList.add(new int[] {periodStart,periodEnd});
					}else{  //initial ldnTime 在17:00之前，不可能delay到机场关闭时间，所以不用考虑机场关闭
						int periodStart = 1440*7+17*60 - (f.initialLandingT - f.initialTakeoffT); //最早起飞时间是台风结束的时间减去飞行时间
						int periodEnd = periodStart + 7*60;  //可以最多delay 7 小时
						f.timeLimitList.add(new int[] {periodStart,periodEnd});
					}
				}
				//3. 如果起飞机场是间接受影响机场
				else if(indirectTyphoonAffectedAirportSet.contains(f.leg.originAirport.id)){
					/*如果前序航班都是从49_50_61出发的，且在17:00前的，就或者马上能飞，或者等到17:00之后才能飞
					 * 因为在17:00之前，所以不用考虑进入机场关闭阶段
					 */
					if(AirportID_49_50_61.containsAll(formerAirportMap.get(f.leg.originAirport.id))
							&&f.initialTakeoffT<1440*7+17*60){
						System.out.println("debug tool "+f.id);
						f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT});  //马上能飞
						f.timeLimitList.add(new int[] {1440*7+17*60,1440*7+17*60+6*60});  //17:00之后delay6小时
						
					}else{ //如果是17:00之后的，或者origin_airport 不是49_50_61，则initial_tkfTime Delay最多6小时
						int periodStart = f.initialTakeoffT;
						int periodEnd = f.initialTakeoffT + 6*60;  //可以最多是delay 6 小时
						int landingEnd = f.initialLandingT + 6*60;  //用来检测是否进入机场关闭阶段
						//此处要判断最晚降落时间是否进入到达机场的机场关闭阶段
						if(AirportID_5_6_22_49_76.contains(f.leg.destinationAirport.id)){
							boolean vio = false;
							int airportCloseTime = 0;
							int airportOpenTime = 0;
							for(ClosureInfo ci:f.leg.destinationAirport.closedSlots){
								if(landingEnd>ci.startTime && landingEnd<ci.endTime){
									vio = true;
									airportCloseTime = ci.startTime;
									airportOpenTime = ci.endTime;
									break;
								}
							}
							if(vio){
								periodEnd = airportCloseTime;
								int periodStart2 = airportOpenTime - (f.initialLandingT - f.initialTakeoffT);
								int periodEnd2 = periodStart2;
								f.timeLimitList.add(new int[] {periodStart2,periodEnd2});

							}
							
						}
						//第一个int[]加入timeLimitList
						f.timeLimitList.add(new int[] {periodStart,periodEnd});
					}
				}
				//剩下的如果在7号12点之后，则允许delay6小时
				else if(f.initialTakeoffT > 1440*7 +12*60 && f.initialTakeoffT <= 1440*8) {
					f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT+360});
				}
				
				else{  //剩下的统一只能delay1小时
					f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT+60});
				}
			}
			// 如果出发时间在7号之前的“国际航班”，降落时间在6号20:00之后，降落机场是49_50_61,也允许delay到[17:00,20:00]--而且不会进入机场关闭时间
			else if(!f.isDomestic && f.initialTakeoffT<= 1440*7 && f.initialLandingT > 1440*6 + 20*60 && AirportID_49_50_61.contains(f.leg.destinationAirport.id)){
				int periodStart = 1440*7 + 17*60 - (f.initialLandingT - f.initialTakeoffT);
				int periodEnd = periodStart + 3*60;  //可以最多是delay 6 小时
				f.timeLimitList.add(new int[] {periodStart,periodEnd});//可以最多是delay 6 小时
			}
			
			//然后，处理6号16:00-24:00，25和67的停机约束限制和 6号航班受台风影响提前的情况
			else if(f.initialTakeoffT > 1440*6 + 16*60 && f.initialTakeoffT <= 1440*7){
				f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT+60});
				//如果到达机场是25或者67,则到达时间在7号04:05
				if(AirportID_25_67.contains(f.leg.destinationAirport.id)){	
					int periodStart = 1440*7+4*60+5 - (f.initialLandingT - f.initialTakeoffT);
					int periodEnd = periodStart + 60;  //可以最多delay 6 小时
					f.timeLimitList.add(new int[] {periodStart,periodEnd});		
				}
				//如果航班起飞时间处于16:00-22:00，可以提前
				if(f.isDomestic&&AirportID_49_50_61.contains(f.leg.originAirport.id) &&f.initialTakeoffT<=1440*6 + 22*60){
					int periodStart = Math.max(f.initialTakeoffT - 6*60, 1440*6 + 14*60 +55);
					int periodEnd = 1440*6 + 16*60;  //可以最多delay 6 小时
					f.timeLimitList.add(new int[] {periodStart,periodEnd});
				}
				
			}
			
			
			
			//剩下情况 8 号delay2小时
			else if(f.initialTakeoffT > 1440*8){
				f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT+120});
			}
			
			//其余情况delay 1小时
			else {
				f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT+60});
			}
			
			
		}
	}
	//设置联程拉直航班的flight delay limit
	public void setFlightDelayLimitForStraightenedFlight(Flight f, Scenario scenario){	
		//如果在7号12点以后，则允许delay6小时
		if(f.initialTakeoffT > 1440*7 +12*60 && f.initialTakeoffT <= 1440*8){
			f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT+360});
		}
		
		//否则都是delay 1小时
		else {
			f.timeLimitList.add(new int[] {f.initialTakeoffT,f.initialTakeoffT+60});
		}
	}
	
	
	
	
}