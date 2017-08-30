package sghku.tianchi.IntelligentAviation.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.NodeComparator;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.Failure;
import sghku.tianchi.IntelligentAviation.entity.FailureType;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightSection;
import sghku.tianchi.IntelligentAviation.entity.GroundArc;
import sghku.tianchi.IntelligentAviation.entity.Leg;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Node;
import sghku.tianchi.IntelligentAviation.entity.ParkingInfo;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.TransferPassenger;

/**
 * @author ych
 * Construct a feasible route (a sequence of flights) for all aircraft
 *
 */
public class NetworkConstructor {
	
	//第一种生成方法，根据原始schedule大范围生成arc
	public List<FlightArc> generateArcForFlight(Aircraft aircraft, Flight f, int givenGap, Scenario scenario){
		List<FlightArc> generatedFlightArcList = new ArrayList<>();
		int presetGap = 5;
		
		FlightArc arc = null;
		
		if(!f.isIncludedInTimeWindow) {
			//因为在调整窗口外，不存在单独的联程航班
			//if(!f.isIncludedInConnecting){
				//如果该航班在调整时间窗口外
				if(f.initialAircraft.id == aircraft.id) {			
					arc = new FlightArc();
					arc.flight = f;
					arc.aircraft = aircraft;
					arc.delay = f.fixedTakeoffTime - f.initialTakeoffT;
					
					/*arc.takeoffTime = f.initialTakeoffT+arc.delay;
					arc.landingTime = arc.takeoffTime+flyTime;*/
					
					arc.takeoffTime = f.fixedTakeoffTime;
					arc.landingTime = f.fixedLandingTime;
					
					arc.readyTime = arc.landingTime + (f.isShortConnection?f.shortConnectionTime:Parameter.MIN_BUFFER_TIME);
					
					f.flightarcList.add(arc);
					aircraft.flightArcList.add(arc);				
					
					arc.calculateCost();
					
					generatedFlightArcList.add(arc);
				
					if(f.leg.destinationAirport.id == 25 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.readyTime >= Parameter.airport25_67ParkingLimitEnd){
						scenario.airport25ParkingFlightArcList.add(arc);
					}
					if(f.leg.destinationAirport.id == 67 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.readyTime >= Parameter.airport25_67ParkingLimitEnd){
						scenario.airport67ParkingFlightArcList.add(arc);
					}
				}
			//}						
		}else {
			//2.1 check whether f can be brought forward and generate earliness arcs

			int nnn = 0;
			
			int startIndex = 0;
			int endIndex = 0;
			
			if(f.isDeadhead) {
				//如果是调机航班，则只能小范围延误
				endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;
			}else {
				if(f.isAffected){  //在scenario里根据input预判断，缩小了solution space
					if(f.isDomestic){
						endIndex = Parameter.MAX_DELAY_DOMESTIC_TIME/presetGap;
					}else{
						endIndex = Parameter.MAX_DELAY_INTERNATIONAL_TIME/presetGap;
					}
				}else{
					endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;
				}
			}
					
			if(f.isAllowtoBringForward){
				startIndex = Parameter.MAX_LEAD_TIME/presetGap;
			}
			
			int flyTime = f.flyTime;
			
			if(f.isDeadhead) {
				flyTime = f.leg.flytimeArray[aircraft.type-1];
			}else if(f.isStraightened) {
				flyTime = f.leg.flytimeArray[aircraft.type-1];
				if(flyTime <= 0) {
					flyTime = f.connectingFlightpair.firstFlight.initialLandingT-f.connectingFlightpair.firstFlight.initialTakeoffT + f.connectingFlightpair.secondFlight.initialLandingT-f.connectingFlightpair.secondFlight.initialTakeoffT;
				}
			}
			
			for(int i=-startIndex;i<=endIndex;i++){
				
				boolean isOriginInAffectedLdnTkfLimitPeriod = false;
				boolean isDestinationInAffectedLdnTkfLimitPeriod = false;
				
				if(scenario.affectedAirportSet.contains(f.leg.originAirport.id)) {
					int tkfTime = f.initialTakeoffT + i*presetGap;
					if((tkfTime >= Parameter.airportBeforeTyphoonTimeWindowStart && tkfTime <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (tkfTime >= Parameter.airportAfterTyphoonTimeWindowStart && tkfTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
						isOriginInAffectedLdnTkfLimitPeriod = true;
					}
				}else if(scenario.affectedAirportSet.contains(f.leg.destinationAirport.id)) {
					int ldnTime = f.initialLandingT + i*presetGap;
					if((ldnTime >= Parameter.airportBeforeTyphoonTimeWindowStart && ldnTime <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (ldnTime >= Parameter.airportAfterTyphoonTimeWindowStart && ldnTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
						isDestinationInAffectedLdnTkfLimitPeriod = true;
					}
				}
				
			
				if((i*presetGap)%givenGap != 0){   //小gap，只有当其受影响时才继续生成相应arc
					if(!isOriginInAffectedLdnTkfLimitPeriod && !isDestinationInAffectedLdnTkfLimitPeriod){
						continue;
					}
				}
								
				arc = new FlightArc();
				arc.flight = f;
				arc.aircraft = aircraft;
				if(i < 0) {
					arc.earliness = -i*presetGap;
				}else {
					arc.delay = i*presetGap;
				}
				
				arc.takeoffTime = f.initialTakeoffT+i*presetGap;
				arc.landingTime = arc.takeoffTime+flyTime;
				
				//arc.readyTime = arc.landingTime + Parameter.MIN_BUFFER_TIME;
				arc.readyTime = arc.landingTime + (f.isShortConnection?f.shortConnectionTime:Parameter.MIN_BUFFER_TIME);			
				
				if(!arc.checkViolation()){
					
					
					//如果是调剂航班，不需要做任何处理
					if(f.isDeadhead) {
						
					}else if(f.isStraightened) {
						//如果是联程拉直，将该arc加到对应的两段航班中
						f.connectingFlightpair.firstFlight.flightarcList.add(arc);
						f.connectingFlightpair.secondFlight.flightarcList.add(arc);
						
						//联程拉直航班则没有对应的flight section						
						//联程拉直乘客容量
						arc.passengerCapacity = aircraft.passengerCapacity;
						//要减去对应的联程乘客
						arc.passengerCapacity = arc.passengerCapacity - f.connectedPassengerNumber;
						
						//其他乘客全部被取消，所以不需要考虑
						arc.passengerCapacity = Math.max(0, arc.passengerCapacity);
					}else {
						f.flightarcList.add(arc);
						
						//将该arc加入到对应的flight section中
						boolean isFound = false;
						for(FlightSection currentFlightSection:f.flightSectionList) {
							if(arc.takeoffTime >= currentFlightSection.startTime && arc.takeoffTime < currentFlightSection.endTime) {
								isFound = true;
								currentFlightSection.flightArcList.add(arc);
								if(currentFlightSection.startTime == 10930 && currentFlightSection.endTime == 11090){
									
									nnn++;
								}
								break;
							}
						}
						
						if(!isFound) {  //check 是否每个arc都放进了一个section
							if(f.flightSectionList.get(f.flightSectionList.size()-1).endTime != arc.takeoffTime) {
								System.out.println("no flight section found 3!"+f.flightSectionList.get(f.flightSectionList.size()-1).endTime+" "+arc.takeoffTime);
								System.exit(1);
							}
							f.flightSectionList.get(f.flightSectionList.size()-1).flightArcList.add(arc);
						}
						
						//乘客容量
						arc.passengerCapacity = aircraft.passengerCapacity;	
						
						
						//减去转乘乘客
						arc.passengerCapacity = arc.passengerCapacity - f.occupiedSeatsByTransferPassenger;
						
						//如果该航班是联程航班，则代表联程航班已经被取消，所以不需要在考虑对应的联程乘客
						
						//剩下的则为有效座位
						arc.passengerCapacity = Math.max(0, arc.passengerCapacity);
						
						if(Parameter.onlySignChangeDisruptedPassenger){
							//减去普通乘客
							arc.passengerCapacity = arc.passengerCapacity - f.normalPassengerNumber;
						}else{
							// 减去普通乘客
							arc.fulfilledNormalPassenger = Math.min(arc.passengerCapacity, f.normalPassengerNumber);
							arc.passengerCapacity = arc.passengerCapacity - arc.fulfilledNormalPassenger;

							arc.flight.itinerary.flightArcList.add(arc);
							
						}	
						
						//剩下的则为有效座位
						arc.passengerCapacity = Math.max(0, arc.passengerCapacity);				
					}
					
					arc.calculateCost();
					aircraft.flightArcList.add(arc);
					generatedFlightArcList.add(arc);
					
					//加入对应的起降时间点
					
					
					
					if(isOriginInAffectedLdnTkfLimitPeriod) {
						int tkfTime = f.initialTakeoffT + i*presetGap;
						
						List<FlightArc> faList = scenario.airportTimeFlightArcMap.get(f.leg.originAirport.id+"_"+tkfTime);
						faList.add(arc);

					}else if(isDestinationInAffectedLdnTkfLimitPeriod){
						int ldnTime = f.initialLandingT + i*presetGap;
						List<FlightArc> faList = scenario.airportTimeFlightArcMap.get(f.leg.destinationAirport.id+"_"+ldnTime);
						faList.add(arc);
						
					}
					
					//加入停机约束
					if(f.leg.destinationAirport.id == 25 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.readyTime >= Parameter.airport25_67ParkingLimitEnd){
						scenario.airport25ParkingFlightArcList.add(arc);
					}
					if(f.leg.destinationAirport.id == 67 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.readyTime >= Parameter.airport25_67ParkingLimitEnd){
						scenario.airport67ParkingFlightArcList.add(arc);
					}
					
				}
			}
			if(f.id == 1333){
				System.out.println("nnn："+nnn);				
			}
		}
		
		
		
		return generatedFlightArcList;
	}

	//根据fix的schedule来
	public List<FlightArc> generateArcForFlightBasedOnFixedSchedule(Aircraft aircraft, Flight f, Scenario scenario){
		List<FlightArc> generatedFlightArcList = new ArrayList<>();
		int presetGap = 5;
		
		FlightArc arc = null;
		
		arc = new FlightArc();
		arc.flight = f;
		arc.aircraft = aircraft;
		if(f.actualTakeoffT - f.initialTakeoffT < 0){
			arc.earliness = f.initialTakeoffT - f.actualTakeoffT;
		}else{
			arc.delay = f.actualTakeoffT - f.initialTakeoffT;			
		}
		
		/*arc.takeoffTime = f.initialTakeoffT+arc.delay;
		arc.landingTime = arc.takeoffTime+flyTime;*/
		
		arc.takeoffTime = f.actualTakeoffT;
		arc.landingTime = f.actualLandingT;
		
		arc.readyTime = arc.landingTime + (f.isShortConnection?f.shortConnectionTime:Parameter.MIN_BUFFER_TIME);
		
		f.flightarcList.add(arc);
		aircraft.flightArcList.add(arc);				
		
		arc.calculateCost();
		
		generatedFlightArcList.add(arc);
		
		//加入对应的起降时间点
		boolean isOriginInAffectedLdnTkfLimitPeriod = false;
		boolean isDestinationInAffectedLdnTkfLimitPeriod = false;
		
		if(scenario.affectedAirportSet.contains(f.leg.originAirport.id)) {
			int tkfTime = arc.takeoffTime;
			if((tkfTime >= Parameter.airportBeforeTyphoonTimeWindowStart && tkfTime <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (tkfTime >= Parameter.airportAfterTyphoonTimeWindowStart && tkfTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
				isOriginInAffectedLdnTkfLimitPeriod = true;
			}
		}else if(scenario.affectedAirportSet.contains(f.leg.destinationAirport.id)) {
			int ldnTime = arc.landingTime;
			if((ldnTime >= Parameter.airportBeforeTyphoonTimeWindowStart && ldnTime <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (ldnTime >= Parameter.airportAfterTyphoonTimeWindowStart && ldnTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
				isDestinationInAffectedLdnTkfLimitPeriod = true;
			}
		}
		
		if(isOriginInAffectedLdnTkfLimitPeriod) {
			int tkfTime = arc.takeoffTime;
			
			List<FlightArc> faList = scenario.airportTimeFlightArcMap.get(f.leg.originAirport.id+"_"+tkfTime);
			faList.add(arc);
		}else if(isDestinationInAffectedLdnTkfLimitPeriod){
			int ldnTime = arc.landingTime;
			List<FlightArc> faList = scenario.airportTimeFlightArcMap.get(f.leg.destinationAirport.id+"_"+ldnTime);
			faList.add(arc);
		}
		
		//加入停机约束
		if(f.leg.destinationAirport.id == 25 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.readyTime >= Parameter.airport25_67ParkingLimitEnd){
			scenario.airport25ParkingFlightArcList.add(arc);
		}
		if(f.leg.destinationAirport.id == 67 && arc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.readyTime >= Parameter.airport25_67ParkingLimitEnd){
			scenario.airport67ParkingFlightArcList.add(arc);
		}
		
		return generatedFlightArcList;
	}
	
	public List<ConnectingArc> generateArcForConnectingFlightPair(Aircraft aircraft, ConnectingFlightpair cf, int givenGap, boolean isGenerateArcForEachFlight, Scenario scenario){
		
		List<ConnectingArc> generatedConnectingArcList = new ArrayList<>();
		
		int presetGap = 5;
		
		int connectionTime = Math.min(cf.secondFlight.initialTakeoffT-cf.firstFlight.initialLandingT, Parameter.MIN_BUFFER_TIME);
		List<FlightArc> firstFlightArcList = new ArrayList<>();
		List<ConnectingArc> connectingArcList = new ArrayList<>();
		
		//如果该联程航班在调整窗口之外
		if(!cf.firstFlight.isIncludedInTimeWindow) {
			if(cf.firstFlight.initialAircraft.id == aircraft.id) {
				
				//构建第一个flight arc
				
				FlightArc firstArc = new FlightArc();
				firstArc.flight = cf.firstFlight;
				firstArc.aircraft = aircraft;
				firstArc.delay = cf.firstFlight.fixedTakeoffTime - cf.firstFlight.initialTakeoffT;
			
				firstArc.takeoffTime = cf.firstFlight.initialTakeoffT+firstArc.delay;
				firstArc.landingTime = firstArc.takeoffTime+cf.firstFlight.flyTime;
				firstArc.readyTime = firstArc.landingTime + connectionTime;
			
				//构建第二个flight arc
				FlightArc secondArc = new FlightArc();
				secondArc.flight = cf.secondFlight;
				secondArc.aircraft = aircraft;
				secondArc.delay = cf.secondFlight.fixedTakeoffTime - cf.secondFlight.initialTakeoffT;
				secondArc.takeoffTime = cf.secondFlight.initialTakeoffT+secondArc.delay;
				secondArc.landingTime = secondArc.takeoffTime+cf.secondFlight.flyTime;
				secondArc.readyTime = secondArc.landingTime + (cf.secondFlight.isShortConnection?cf.secondFlight.shortConnectionTime:Parameter.MIN_BUFFER_TIME);

				ConnectingArc ca = new ConnectingArc();
				ca.firstArc = firstArc;
				ca.secondArc = secondArc;
				ca.aircraft = aircraft;
				
				aircraft.connectingArcList.add(ca);
				
				cf.firstFlight.connectingarcList.add(ca);
				cf.secondFlight.connectingarcList.add(ca);
				ca.connectingFlightPair = cf;
				
				ca.calculateCost();
				generatedConnectingArcList.add(ca);
				
				//加入25和67停机约束
				if(cf.firstFlight.leg.destinationAirport.id == 25 && ca.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart && ca.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd){
					scenario.airport25ClosureConnectingArcList.add(ca);
				}
				if(cf.firstFlight.leg.destinationAirport.id == 67 && ca.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart && ca.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd){
					scenario.airport25ClosureConnectingArcList.add(ca);
				}
			}
		}else {
			//otherwise, create a set of connecting arcs for this connecting flight
			
			if(!aircraft.tabuLegs.contains(cf.firstFlight.leg) && !aircraft.tabuLegs.contains(cf.secondFlight.leg)){
				//only if this leg is not in the tabu list of the corresponding aircraft
				FlightArc arc = null;
		
				//2.1 check whether f can be brought forward and generate earliness arcs
				int startIndex = 0;

				if(cf.firstFlight.isAllowtoBringForward){
					startIndex = Parameter.MAX_LEAD_TIME/presetGap;				
				}

				//2.3 generate delay arcs
				int endIndex = 0;
				if(cf.isAffected){
					if(cf.firstFlight.isDomestic){
						endIndex = Parameter.MAX_DELAY_DOMESTIC_TIME/presetGap;								
					}else{
						endIndex = Parameter.MAX_DELAY_INTERNATIONAL_TIME/presetGap;		
					}
				}else{
					endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;		
				}
				
				for(int i=-startIndex;i<=endIndex;i++){
	
					boolean isOriginInAffectedLdnTkfPeriod = false;
					boolean isDestinationInAffectedLdnTkfPeriod = false;
					
					if(scenario.affectedAirportSet.contains(cf.firstFlight.leg.originAirport.id)) {
						int tkfTime = cf.firstFlight.initialTakeoffT + i*presetGap;
						if((tkfTime >= Parameter.airportBeforeTyphoonTimeWindowStart && tkfTime <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (tkfTime >= Parameter.airportAfterTyphoonTimeWindowStart && tkfTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
							isOriginInAffectedLdnTkfPeriod = true;
						}
					}else if(scenario.affectedAirportSet.contains(cf.firstFlight.leg.destinationAirport.id)) {
						int ldnTime = cf.firstFlight.initialLandingT + i*presetGap;
						if((ldnTime >= Parameter.airportBeforeTyphoonTimeWindowStart && ldnTime <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (ldnTime >= Parameter.airportAfterTyphoonTimeWindowStart && ldnTime <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
							isDestinationInAffectedLdnTkfPeriod = true;
						}
					}
					
					if((i*presetGap)%givenGap != 0){
						if(!isOriginInAffectedLdnTkfPeriod && !isDestinationInAffectedLdnTkfPeriod){
							continue;
						}
					}
									
					arc = new FlightArc();
					arc.flight = cf.firstFlight;
					arc.aircraft = aircraft;
					if(i < 0) {
						arc.earliness = -i*presetGap;	
					}else {
						arc.delay = i*presetGap;
					}
				
					arc.takeoffTime = cf.firstFlight.initialTakeoffT+i*presetGap;
					arc.landingTime = arc.takeoffTime+cf.firstFlight.flyTime;
					arc.readyTime = arc.landingTime + connectionTime;
											
					if(!arc.checkViolation()){
						firstFlightArcList.add(arc);
						
						arc.isWithinAffectedRegionOrigin = isOriginInAffectedLdnTkfPeriod;
						arc.isWithinAffectedRegionDestination = isDestinationInAffectedLdnTkfPeriod;
					}
				}
			}
			
			for(FlightArc firstArc:firstFlightArcList){
				
				
				if(cf.secondFlight.isAllowtoBringForward){
					int startIndex = Parameter.MAX_LEAD_TIME/presetGap;

					for(int i=startIndex;i>0;i--){
												
						boolean isWithinAffectedRegionOrigin2 = false;
						boolean isWithinAffectedRegionDestination2 = false;
							
						if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.originAirport.id)) {
							int t = cf.secondFlight.initialTakeoffT + i*presetGap;
							if((t >= Parameter.airportBeforeTyphoonTimeWindowStart && t <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (t >= Parameter.airportAfterTyphoonTimeWindowStart && t <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isWithinAffectedRegionOrigin2 = true;
							}
						}else if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.destinationAirport.id)) {
							int t = cf.secondFlight.initialLandingT + i*presetGap;
							if((t >= Parameter.airportBeforeTyphoonTimeWindowStart && t <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (t >= Parameter.airportAfterTyphoonTimeWindowStart && t <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
								isWithinAffectedRegionDestination2 = true;
							}
						}
						
						if((i*presetGap)%givenGap != 0){
							if(!isWithinAffectedRegionOrigin2 && !isWithinAffectedRegionDestination2){
								continue;
							}
						}
						
						if(cf.secondFlight.initialTakeoffT-presetGap*i >= firstArc.readyTime){
							FlightArc secondArc = new FlightArc();
							secondArc.flight = cf.secondFlight;
							secondArc.aircraft = aircraft;
							secondArc.earliness = i*presetGap;
							secondArc.takeoffTime = cf.secondFlight.initialTakeoffT-secondArc.earliness;
							secondArc.landingTime = secondArc.takeoffTime+cf.secondFlight.flyTime;
							secondArc.readyTime = secondArc.landingTime + (cf.secondFlight.isShortConnection?cf.secondFlight.shortConnectionTime:Parameter.MIN_BUFFER_TIME);

							secondArc.isWithinAffectedRegionOrigin =  isWithinAffectedRegionOrigin2;
							secondArc.isWithinAffectedRegionDestination = isWithinAffectedRegionDestination2;
							
							if(!secondArc.checkViolation()){
								ConnectingArc ca = new ConnectingArc();
								ca.firstArc = firstArc;
								ca.secondArc = secondArc;
								ca.aircraft = aircraft;
								
								aircraft.connectingArcList.add(ca);
								
								cf.firstFlight.connectingarcList.add(ca);
								cf.secondFlight.connectingarcList.add(ca);
								ca.connectingFlightPair = cf;
								
								connectingArcList.add(ca);
								ca.calculateCost();
								generatedConnectingArcList.add(ca);
							}
						}
						
					}
				}
								
				int endIndex = 0;
				if(cf.isAffected){
					if(cf.secondFlight.isDomestic){
						endIndex = Parameter.MAX_DELAY_DOMESTIC_TIME/presetGap;								
					}else{
						endIndex = Parameter.MAX_DELAY_INTERNATIONAL_TIME/presetGap;		
					}
				}else{
					endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;
				}
				
				for(int i=0;i<=endIndex;i++){
					
					
					boolean isWithinAffectedRegionOrigin2 = false;
					boolean isWithinAffectedRegionDestination2 = false;
					
				
					if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.originAirport.id)) {
						int t = cf.secondFlight.initialTakeoffT + i*presetGap;
						
						if((t >= Parameter.airportBeforeTyphoonTimeWindowStart && t <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (t >= Parameter.airportAfterTyphoonTimeWindowStart && t <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
							isWithinAffectedRegionOrigin2 = true;
						}
					}else if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.destinationAirport.id)) {
						int t = cf.secondFlight.initialLandingT + i*presetGap;
				
						if((t >= Parameter.airportBeforeTyphoonTimeWindowStart && t <= Parameter.airportBeforeTyphoonTimeWindowEnd) || (t >= Parameter.airportAfterTyphoonTimeWindowStart && t <= Parameter.airportAfterTyphoonTimeWindowEnd)) {
							isWithinAffectedRegionDestination2 = true;
						}
					}
					
					if((i*presetGap)%givenGap != 0){
						if(!isWithinAffectedRegionOrigin2 && !isWithinAffectedRegionDestination2){
							continue;
						}
					}
					
					if(cf.secondFlight.initialTakeoffT+presetGap*i >= firstArc.readyTime){
						
						FlightArc secondArc = new FlightArc();
						secondArc.flight = cf.secondFlight;
						secondArc.aircraft = aircraft;
						secondArc.delay = i*presetGap;
						secondArc.takeoffTime = cf.secondFlight.initialTakeoffT+secondArc.delay;
						secondArc.landingTime = secondArc.takeoffTime+cf.secondFlight.flyTime;
						secondArc.readyTime = secondArc.landingTime + + (cf.secondFlight.isShortConnection?cf.secondFlight.shortConnectionTime:Parameter.MIN_BUFFER_TIME);

						secondArc.isWithinAffectedRegionOrigin =  isWithinAffectedRegionOrigin2;
						secondArc.isWithinAffectedRegionDestination = isWithinAffectedRegionDestination2;
						
						if(!secondArc.checkViolation()){
							ConnectingArc ca = new ConnectingArc();
							ca.firstArc = firstArc;
							ca.secondArc = secondArc;
							
							aircraft.connectingArcList.add(ca);
							
							cf.firstFlight.connectingarcList.add(ca);
							cf.secondFlight.connectingarcList.add(ca);
							ca.connectingFlightPair = cf;
							
							ca.aircraft = aircraft;
							
							connectingArcList.add(ca);
							ca.calculateCost();
							generatedConnectingArcList.add(ca);
							
							
							if(!isWithinAffectedRegionOrigin2 && !isWithinAffectedRegionDestination2 ) {
								break;								
							}
						}
					}			
				}
				
			}
		
			int nnn = 0;
			
			for(ConnectingArc arc:connectingArcList) {
				//加入到对应的机场
				if(arc.firstArc.isWithinAffectedRegionOrigin) {
					List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap.get(arc.firstArc.flight.leg.originAirport.id+"_"+arc.firstArc.takeoffTime);
					caList.add(arc);
				}
				if(arc.firstArc.isWithinAffectedRegionDestination) {
					List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap.get(arc.firstArc.flight.leg.destinationAirport.id+"_"+arc.firstArc.landingTime);
					caList.add(arc);
				}
				
				if(arc.secondArc.isWithinAffectedRegionOrigin) {
					List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap.get(arc.secondArc.flight.leg.originAirport.id+"_"+arc.secondArc.takeoffTime);
					
					caList.add(arc);
				}
				if(arc.secondArc.isWithinAffectedRegionDestination) {
					List<ConnectingArc> caList = scenario.airportTimeConnectingArcMap.get(arc.secondArc.flight.leg.destinationAirport.id+"_"+arc.secondArc.landingTime);
					caList.add(arc);
				}
				
				//加入25和67停机约束
				if(cf.firstFlight.leg.destinationAirport.id == 25 && arc.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd){
					scenario.airport25ClosureConnectingArcList.add(arc);
				}
				if(cf.firstFlight.leg.destinationAirport.id == 67 && arc.firstArc.landingTime <= Parameter.airport25_67ParkingLimitStart && arc.secondArc.takeoffTime >= Parameter.airport25_67ParkingLimitEnd){
					scenario.airport25ClosureConnectingArcList.add(arc);
				}
				
				
				//设置第一个arc
				//arc.firstArc.isIncludedInConnecting = true;
				//arc.firstArc.connectingArcList.add(arc);
				
				//乘客容量
				arc.firstArc.passengerCapacity = aircraft.passengerCapacity;			
				//减去联程乘客
				arc.firstArc.passengerCapacity = arc.firstArc.passengerCapacity - cf.firstFlight.connectedPassengerNumber;
				//减去转乘乘客
				arc.firstArc.passengerCapacity = arc.firstArc.passengerCapacity - cf.firstFlight.occupiedSeatsByTransferPassenger;
				
				if(Parameter.onlySignChangeDisruptedPassenger){
					//减去普通乘客
					arc.firstArc.passengerCapacity = arc.firstArc.passengerCapacity - cf.firstFlight.normalPassengerNumber;	
				}else{
					// 减去普通乘客
					arc.firstArc.fulfilledNormalPassenger = Math.min(arc.firstArc.passengerCapacity, cf.firstFlight.normalPassengerNumber);
					arc.firstArc.passengerCapacity = arc.firstArc.passengerCapacity
							- arc.firstArc.fulfilledNormalPassenger;

					arc.firstArc.flight.itinerary.firstConnectionArcList.add(arc);
				}

				/*if(arc.firstArc.flight.id == 1333){
					System.out.println("flight 1333:"+arc.firstArc.passengerCapacity+" ");
				}*/
				
				
				//剩下的则为有效座位
				arc.firstArc.passengerCapacity = Math.max(0, arc.firstArc.passengerCapacity);
				
				boolean isFound = false;
				for(FlightSection currentFlightSection:cf.firstFlight.flightSectionList) {
					if(arc.firstArc.takeoffTime >= currentFlightSection.startTime && arc.firstArc.takeoffTime < currentFlightSection.endTime) {
						//currentFlightSection.flightArcList.add(arc.firstArc);
						currentFlightSection.connectingFirstArcList.add(arc);
						isFound = true;
						
						/*if(currentFlightSection.flight.id == 1333 && currentFlightSection.startTime == 10930 && currentFlightSection.endTime == 11090){
							System.out.println("we find this flight section 1: "+arc.firstArc.takeoffTime+" "+arc.secondArc.takeoffTime);
							nnn++;
						}*/
						
						break;
					}
				}
				
				if(!isFound) {				
					if(arc.firstArc.takeoffTime != cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1).endTime) {
						System.out.println("no flight section found! "+arc.firstArc.takeoffTime+" "+cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1).endTime);
						System.exit(1);
					}
					
					cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1).connectingFirstArcList.add(arc);	
				
					/*FlightSection currentFlightSection = cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1);
					if(currentFlightSection.flight.id == 1333 && currentFlightSection.startTime == 10930 && currentFlightSection.endTime == 11090){
						System.out.println("we find this flight section 2: "+arc.firstArc.takeoffTime+" "+arc.secondArc.takeoffTime);
					}*/
				}
				
				//设置第二个arc
				//arc.secondArc.isIncludedInConnecting = true;
				//arc.secondArc.connectingArcList.add(arc);
				//乘客容量
				arc.secondArc.passengerCapacity = aircraft.passengerCapacity;		
				//减去联程乘客
				arc.secondArc.passengerCapacity = arc.secondArc.passengerCapacity - cf.secondFlight.connectedPassengerNumber;
				//减去转乘乘客
				arc.secondArc.passengerCapacity = arc.secondArc.passengerCapacity - cf.secondFlight.occupiedSeatsByTransferPassenger;
				if(Parameter.onlySignChangeDisruptedPassenger){
					//减去普通乘客
					arc.secondArc.passengerCapacity = arc.secondArc.passengerCapacity - cf.secondFlight.normalPassengerNumber;
				}else{
					// 减去普通乘客
					arc.secondArc.fulfilledNormalPassenger = Math.min(arc.secondArc.passengerCapacity, cf.secondFlight.normalPassengerNumber);
					arc.secondArc.passengerCapacity = arc.secondArc.passengerCapacity
							- arc.secondArc.fulfilledNormalPassenger;

					arc.secondArc.flight.itinerary.secondConnectingArcList.add(arc);
				}
				
				//剩下的则为有效座位
				arc.secondArc.passengerCapacity = Math.max(0, arc.secondArc.passengerCapacity);
				
				isFound = false;
				for(FlightSection currentFlightSection:cf.secondFlight.flightSectionList) {
					if(arc.secondArc.takeoffTime >= currentFlightSection.startTime && arc.secondArc.takeoffTime < currentFlightSection.endTime) {
						currentFlightSection.connectingSecondArcList.add(arc);
						isFound = true;
						
						/*if(currentFlightSection.flight.id == 1333 && currentFlightSection.startTime == 10930 && currentFlightSection.endTime == 11090){
							System.out.println("we find this flight section 3: "+arc.firstArc.takeoffTime+" "+arc.secondArc.takeoffTime);
						}*/
						break;
					}
				}
				
				if(!isFound) {				
					if(arc.secondArc.takeoffTime != cf.secondFlight.flightSectionList.get(cf.secondFlight.flightSectionList.size()-1).endTime) {
						System.out.println("no flight section found 2! "+arc.secondArc.takeoffTime+" "+cf.secondFlight.flightSectionList.get(cf.secondFlight.flightSectionList.size()-1).endTime);
						System.exit(1);
					}
					cf.secondFlight.flightSectionList.get(cf.secondFlight.flightSectionList.size()-1).connectingSecondArcList.add(arc);	
				
					/*FlightSection currentFlightSection = cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1);
					if(currentFlightSection.flight.id == 1333 && currentFlightSection.startTime == 10930 && currentFlightSection.endTime == 11090){
						System.out.println("we find this flight section 4: "+arc.firstArc.takeoffTime+" "+arc.secondArc.takeoffTime);
					}*/
				}
				
			}
			
			
			/*//3. 为每一个flight生成arc，可以单独取消联程航班中的一段
			if(isGenerateArcForEachFlight) {
				if(!aircraft.tabuLegs.contains(cf.firstFlight.leg)){
					generateArcForFlight(aircraft, cf.firstFlight, givenGap, scenario);
				}
				
				if(!aircraft.tabuLegs.contains(cf.secondFlight.leg)){
					
					generateArcForFlight(aircraft, cf.secondFlight, givenGap, scenario);
				}
			}*/
		
			if(cf.firstFlight.id == 1333){
				System.out.println("nnn："+nnn);
			}
		}
		
		
		
		
		return generatedConnectingArcList;
	}
	
	//生成点和地面arc
	public void generateNodes(List<Aircraft> aircraftList, List<Airport> airportList, Scenario scenario){
		//2. generate nodes for each arc		
		for(Aircraft aircraft:aircraftList){
			//1. clear node map for each airport
			for(int i=0;i<airportList.size();i++){
				Airport a = airportList.get(i);
				aircraft.nodeMapArray[i] = new HashMap<>();
				aircraft.nodeListArray[i] = new ArrayList<>();
			}
		
			for(FlightArc flightArc:aircraft.flightArcList){
				
				Airport departureAirport = flightArc.flight.leg.originAirport;
				Airport arrivalAirport = flightArc.flight.leg.destinationAirport;
				
				Node node = aircraft.nodeMapArray[departureAirport.id-1].get(flightArc.takeoffTime);
						
				if(node == null){
					node = new Node();
					node.airport = departureAirport;
					node.time = flightArc.takeoffTime;
					aircraft.nodeMapArray[departureAirport.id-1].put(flightArc.takeoffTime, node);
				}
				
				node.flowoutFlightArcList.add(flightArc);
				flightArc.fromNode = node;
				
				node = aircraft.nodeMapArray[arrivalAirport.id-1].get(flightArc.readyTime);
				if(node == null){
					node = new Node();
					node.airport = arrivalAirport;
					node.time = flightArc.readyTime;
					aircraft.nodeMapArray[arrivalAirport.id-1].put(flightArc.readyTime, node);
				
				}
				
				node.flowinFlightArcList.add(flightArc);
				flightArc.toNode = node;
			}
			
			for(ConnectingArc flightArc:aircraft.connectingArcList){
				
				Airport departureAirport = flightArc.firstArc.flight.leg.originAirport;
				Airport arrivalAirport = flightArc.secondArc.flight.leg.destinationAirport;
				
				int takeoffTime = flightArc.firstArc.takeoffTime;
				int readyTime = flightArc.secondArc.readyTime;
				
				Node node = aircraft.nodeMapArray[departureAirport.id-1].get(takeoffTime);
						
				if(node == null){
					node = new Node();
					node.airport = departureAirport;
					node.time = takeoffTime;
					aircraft.nodeMapArray[departureAirport.id-1].put(takeoffTime, node);
				}
				
				node.flowoutConnectingArcList.add(flightArc);
				flightArc.fromNode = node;
				
				node = aircraft.nodeMapArray[arrivalAirport.id-1].get(readyTime);
				if(node == null){
					node = new Node();
					node.airport = arrivalAirport;
					node.time = readyTime;
					aircraft.nodeMapArray[arrivalAirport.id-1].put(readyTime, node);
				
				}
				
				node.flowinConnectingArcList.add(flightArc);
				flightArc.toNode = node;
			}
			
			//生成source和sink点
			Node sourceNode = new Node();
			sourceNode.isSource = true;
			aircraft.sourceNode = sourceNode;
			
			Node sinkNode = new Node();
			sinkNode.isSink = true;
			aircraft.sinkNode = sinkNode;
					
			//3. sort nodes of each airport			
			for(int i=0;i<airportList.size();i++){
				for(Integer key:aircraft.nodeMapArray[i].keySet()){
					aircraft.nodeListArray[i].add(aircraft.nodeMapArray[i].get(key));
				}
				
				Airport airport = airportList.get(i);
				boolean isFound = false;
				
				Collections.sort(aircraft.nodeListArray[i], new NodeComparator());
						
				for(int j=0;j<aircraft.nodeListArray[i].size()-1;j++){
					Node n1 = aircraft.nodeListArray[i].get(j);
					Node n2 = aircraft.nodeListArray[i].get(j+1);
					
					GroundArc groundArc = new GroundArc();
					groundArc.fromNode = n1;
					groundArc.toNode = n2;
					groundArc.aircraft = aircraft;
					
					n1.flowoutGroundArcList.add(groundArc);
					n2.flowinGroundArcList.add(groundArc);
					
					aircraft.groundArcList.add(groundArc);
					
					if(!isFound) {
						if(scenario.affectedAirportSet.contains(airport.id)) {					
							if(n1.time <= Parameter.airport49_50_61ParkingLimitStart && n2.time >= Parameter.airport49_50_61ParkingLimitEnd) {
								List<GroundArc> gaList = scenario.affectedAirportCoverParkLimitGroundArcMap.get(airport.id);
								gaList.add(groundArc);

								isFound = true;
							}
						}						
					}
					
					if(n1.airport.id == 25 && n1.time <= Parameter.airport25_67ParkingLimitStart && n2.time >=Parameter.airport25_67ParkingLimitEnd){
						scenario.airport25ClosureGroundArcList.add(groundArc);
					}
					if(n1.airport.id == 67 && n1.time <= Parameter.airport25_67ParkingLimitStart && n2.time >=Parameter.airport25_67ParkingLimitEnd){
						scenario.airport67ClosureGroundArcList.add(groundArc);
					}
					
					/*if(!groundArc.checkViolation()){
						n1.flowoutGroundArcList.add(groundArc);
						n2.flowinGroundArcList.add(groundArc);
						
						aircraft.groundArcList.add(groundArc);
					}*/
				}
							
			}
			
			//4. construct source and sink arcs
			//连接source和每个飞机初始机场的第一个点
			if(aircraft.nodeListArray[aircraft.initialLocation.id-1].size() > 0) {
				Node firstNode = aircraft.nodeListArray[aircraft.initialLocation.id-1].get(0);
				
				GroundArc arc = new GroundArc();
				arc.fromNode = sourceNode;
				arc.toNode = firstNode;
				arc.isSource = true;
				arc.aircraft = aircraft;
				sourceNode.flowoutGroundArcList.add(arc);
				firstNode.flowinGroundArcList.add(arc);
				aircraft.groundArcList.add(arc);
			}
			
			//对停机限制的机场飞机可以刚开停靠
			/*if(Parameter.restrictedAirportSet.contains(aircraft.initialLocation)){
				if(aircraft.nodeListArray[aircraft.initialLocation.id-1].size() > 0){
					for(int j=1;j<aircraft.nodeListArray[aircraft.initialLocation.id-1].size();j++){
						Node n = aircraft.nodeListArray[aircraft.initialLocation.id-1].get(j);
						
						GroundArc arc = new GroundArc();
						arc.fromNode = sourceNode;
						arc.toNode = n;
						arc.isSource = true;
						sourceNode.flowoutGroundArcList.add(arc);
						n.flowinGroundArcList.add(arc);
						aircraft.groundArcList.add(arc);
					}
				}				
			}*/
			//对停机限制的机场飞机可以刚开始停靠
			if(scenario.affectedAirportSet.contains(aircraft.initialLocation.id)){
				if(aircraft.nodeListArray[aircraft.initialLocation.id-1].size() > 0){
					for(int j=1;j<aircraft.nodeListArray[aircraft.initialLocation.id-1].size();j++){
						Node n = aircraft.nodeListArray[aircraft.initialLocation.id-1].get(j);
						
						if(n.time >= Parameter.airportAfterTyphoonTimeWindowStart) {
							GroundArc arc = new GroundArc();
							arc.fromNode = sourceNode;
							arc.toNode = n;
							arc.isSource = true;
							arc.aircraft = aircraft;
							sourceNode.flowoutGroundArcList.add(arc);
							n.flowinGroundArcList.add(arc);
							aircraft.groundArcList.add(arc);
							
							break;
						}
					}
				}				
			}
			
			

			/*for(Airport airport:airportList){
				if(aircraft.nodeListArray[airport.id-1].size() > 0){
					if(Parameter.restrictedAirportSet.contains(airport)){
						for(Node lastNode:aircraft.nodeListArray[airport.id-1]){
							GroundArc arc = new GroundArc();
							arc.fromNode = lastNode;
							arc.toNode = sinkNode;
							arc.isSink = true;
							lastNode.flowoutGroundArcList.add(arc);
							sinkNode.flowinGroundArcList.add(arc);
							aircraft.groundArcList.add(arc);
							
							lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
						}
					}else{
						Node lastNode = aircraft.nodeListArray[airport.id-1].get(aircraft.nodeListArray[airport.id-1].size()-1);
						
						GroundArc arc = new GroundArc();
						arc.fromNode = lastNode;
						arc.toNode = sinkNode;
						arc.isSink = true;
						lastNode.flowoutGroundArcList.add(arc);
						sinkNode.flowinGroundArcList.add(arc);
						aircraft.groundArcList.add(arc);
						
						lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
					}					
				}
			}*/
			
			
			for(Airport airport:airportList){
				if(aircraft.nodeListArray[airport.id-1].size() > 0){
					if(scenario.affectedAirportSet.contains(airport.id)){
						for(int j=aircraft.nodeListArray[airport.id-1].size()-2;j>=0;j--) {
							Node lastNode = aircraft.nodeListArray[airport.id-1].get(j);
							
							if(lastNode.time <= Parameter.airportBeforeTyphoonTimeWindowEnd) {
								GroundArc arc = new GroundArc();
								arc.fromNode = lastNode;
								arc.toNode = sinkNode;
								arc.isSink = true;
								arc.aircraft = aircraft;
								lastNode.flowoutGroundArcList.add(arc);
								sinkNode.flowinGroundArcList.add(arc);
								aircraft.groundArcList.add(arc);
								
								lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
								
								break;
							}
						}
					}
					
					Node lastNode = aircraft.nodeListArray[airport.id-1].get(aircraft.nodeListArray[airport.id-1].size()-1);
					
					GroundArc arc = new GroundArc();
					arc.fromNode = lastNode;
					arc.toNode = sinkNode;
					arc.isSink = true;
					arc.aircraft = aircraft;
					lastNode.flowoutGroundArcList.add(arc);
					sinkNode.flowinGroundArcList.add(arc);
					aircraft.groundArcList.add(arc);
					
					lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
				}
			}
			
			/*//4.2 生成直接从source node连接到sink node的arc
			GroundArc arc = new GroundArc();
			arc.fromNode = sourceNode;
			arc.toNode = sinkNode;
			arc.isSink = true;
			arc.aircraft = aircraft;
			sourceNode.flowoutGroundArcList.add(arc);
			sinkNode.flowinGroundArcList.add(arc);
			aircraft.groundArcList.add(arc);			
			aircraft.initialLocation.sinkArcList[aircraft.type-1].add(arc);*/
			
			//5. construct turn-around arc
			GroundArc arc = new GroundArc();
			arc.fromNode = sinkNode;
			arc.toNode = sourceNode;
			arc.aircraft = aircraft;
			sinkNode.flowoutGroundArcList.add(arc);
			sourceNode.flowinGroundArcList.add(arc);
			aircraft.groundArcList.add(arc);
			aircraft.turnaroundArc = arc;
		}
		
	}
	
}
