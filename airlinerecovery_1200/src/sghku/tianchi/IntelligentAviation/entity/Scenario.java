package sghku.tianchi.IntelligentAviation.entity;

import java.awt.FlowLayout;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.AircraftPathReader;
import sghku.tianchi.IntelligentAviation.common.ExcelOperator;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator;

public class Scenario {

	public List<Failure> faultList;
	public List<Airport> airportList;
	public List<Leg> legList;
	public List<Aircraft> aircraftList = new ArrayList<Aircraft>();
	public List<Flight> flightList = new ArrayList<Flight>();
	public List<PassengerTransfer> passengerTransferList;

	// 所有停机约束
	public List<ParkingInfo> parkingInfoList = new ArrayList<>();

	// 整体时间窗的最早时间和最晚时间
	public long earliestTimeLong = 0;
	public Date earliestDate = null;
	public long latestTimeLong = 0;
	public Date latestDate = null;

	// 所有联程航班
	public Map<String, ConnectingFlightpair> connectingFlightMap = new HashMap<>();
	public List<ConnectingFlightpair> connectingFlightList = null;

	// 转机乘客
	public List<TransferPassenger> transferPassengerList = new ArrayList<>();

	// 乘客信息
	public List<Itinerary> itineraryList = new ArrayList<>();

	public Set<Integer> affectedAirportSet = new HashSet();

	public List<String> keyList = new ArrayList<>();
	public Map<String, List<FlightArc>> airportTimeFlightArcMap = new HashMap<>();
	public Map<String, List<ConnectingArc>> airportTimeConnectingArcMap = new HashMap<>();
	public Map<String, Integer> affectAirportLdnTkfCapacityMap = new HashMap<>();  //landing & takeoff capacity of affected airport 

	public Map<Integer, List<GroundArc>> affectedAirportCoverParkLimitGroundArcMap = new HashMap<>();
	public Map<Integer, Integer> affectedAirportParkingLimitMap = new HashMap<>();

	// short connection
	public Map<String, Integer> shortConnectionMap = new HashMap<>();

	// 限制25和67
	public List<GroundArc> airport25ClosureGroundArcList = new ArrayList<>();
	public List<GroundArc> airport67ClosureGroundArcList = new ArrayList<>();

	public List<FlightArc> airport25ParkingFlightArcList = new ArrayList<>();
	public List<FlightArc> airport67ParkingFlightArcList = new ArrayList<>();

	public List<ConnectingArc> airport25ClosureConnectingArcList = new ArrayList<>();
	public List<ConnectingArc> airport67ClosureConnectingArcList = new ArrayList<>();

	public int airport25ParkingLimit = 11;
	public int airport67ParkingLimit = 7;
	
	//all straightened flight
	public List<Flight> straightenedFlightList = new ArrayList<>();
	public Map<String,Flight> straightenedFlightMap = new HashMap<>();
	
	public Scenario() {

	}

	public Scenario(String filename) {

		// 读取机场
		airportList = getAirportList(filename);
		// 读取航段
		legList = getLegList();
		// 读取所有飞机
		aircraftList = getAircraftList(filename, legList);

		// 读取所有航班
		flightList = ExcelOperator.getFlightList(filename, aircraftList, legList);
		// 读取联程航班
		connectingFlightList = ExcelOperator.getConnectingFlightList(flightList, legList);

		// 更新时间窗的最早和最晚时间
		for (Flight flight : flightList) {
			if (earliestDate == null) {
				earliestDate = flight.takeoffTime;
				earliestTimeLong = flight.takeoffTime.getTime();
			} else {
				if (flight.takeoffTime.getTime() < earliestTimeLong) {
					earliestDate = flight.takeoffTime;
					earliestTimeLong = flight.takeoffTime.getTime();
				}
			}
			if (latestDate == null) {
				latestDate = flight.landingTime;
				latestTimeLong = flight.landingTime.getTime();
			} else {
				if (flight.landingTime.getTime() > latestTimeLong) {
					latestDate = flight.landingTime;
					latestTimeLong = flight.landingTime.getTime();
				}
			}
		}

		// 将飞机飞行的航班排序，计算初始机场
		for (Aircraft a : aircraftList) {
			Collections.sort(a.flightList, new FlightComparator());
			if (a.flightList.size() > 0) {
				a.flightList.get(0).aircraft.initialLocation = a.flightList.get(0).leg.originAirport;
			}

			for (Flight f : a.flightList) {
				f.initialAircraft = a;
			}
		}

		// 读取故障信息
		faultList = ExcelOperator.getFaultList(filename, airportList, flightList, aircraftList, earliestTimeLong,
				earliestDate, latestTimeLong, latestDate);

		ExcelOperator.getClosureInfo(filename, airportList, earliestDate, latestDate);

		// 读取每一个航段的飞行信息
		ExcelOperator.readflytimeMap(filename, legList);

		// 对每一个航段检查是否可以提前，是否受到影响
		for (Flight f : flightList) {
			f.checkBringForward();
			f.checkAffected();

			// 初始化航班的飞行时间
			f.flyTime = f.initialLandingT - f.initialTakeoffT;
		}
		// 检查该联程航班是否可以被拉直以及是否受到影响
		for (ConnectingFlightpair cf : connectingFlightList) {
			cf.checkStraighten();

			cf.checkAffected();
			// 将该联程拉直航班放到map中
			connectingFlightMap.put(cf.firstFlight.id + "_" + cf.secondFlight.id, cf);

			// 设置每一个flight的inIncludedInConnecting
			cf.firstFlight.isIncludedInConnecting = true;
			cf.secondFlight.isIncludedInConnecting = true;

			cf.firstFlight.connectingFlightpair = cf;
			cf.secondFlight.connectingFlightpair = cf;

			cf.firstFlight.brotherFlight = cf.secondFlight;
			cf.secondFlight.brotherFlight = cf.firstFlight;
			
		}
		
		//generate straightend flight list
		generateStraightenedFlightList(connectingFlightList);

		// 判断某一个机场是否为国内机场
		for (Flight f : flightList) {
			if (f.isDomestic) {
				f.leg.originAirport.isDomestic = true;
				f.leg.destinationAirport.isDomestic = true;
			}
		}

		// 初始化航班和飞机的信息
		for (Flight f : flightList) {
			f.actualLandingT = f.initialLandingT;
			f.actualTakeoffT = f.initialTakeoffT;
		}
		
		//是否需要读取飞机固定的路径
		if(Parameter.isReadFixedRoutes){
			readFixedRoutes();
		}

		// 初始化机场流量限制
		for (long i = Parameter.airportBeforeTyphoonTimeWindowStart; i <= Parameter.airportBeforeTyphoonTimeWindowEnd; i += 5) {
			keyList.add("49_" + i);
			keyList.add("50_" + i);
			keyList.add("61_" + i);

			airportTimeFlightArcMap.put("49_" + i, new ArrayList<>());
			airportTimeFlightArcMap.put("50_" + i, new ArrayList<>());
			airportTimeFlightArcMap.put("61_" + i, new ArrayList<>());

			airportTimeConnectingArcMap.put("49_" + i, new ArrayList<>());
			airportTimeConnectingArcMap.put("50_" + i, new ArrayList<>());
			airportTimeConnectingArcMap.put("61_" + i, new ArrayList<>());

			affectAirportLdnTkfCapacityMap.put("49_" + i, 2);
			affectAirportLdnTkfCapacityMap.put("50_" + i, 2);
			affectAirportLdnTkfCapacityMap.put("61_" + i, 2);
		}

		for (long i = Parameter.airportAfterTyphoonTimeWindowStart; i <= Parameter.airportAfterTyphoonTimeWindowEnd; i += 5) {
			keyList.add("49_" + i);
			keyList.add("50_" + i);
			keyList.add("61_" + i);

			airportTimeFlightArcMap.put("49_" + i, new ArrayList<>());
			airportTimeFlightArcMap.put("50_" + i, new ArrayList<>());
			airportTimeFlightArcMap.put("61_" + i, new ArrayList<>());

			airportTimeConnectingArcMap.put("49_" + i, new ArrayList<>());
			airportTimeConnectingArcMap.put("50_" + i, new ArrayList<>());
			airportTimeConnectingArcMap.put("61_" + i, new ArrayList<>());

			affectAirportLdnTkfCapacityMap.put("49_" + i, 2);
			affectAirportLdnTkfCapacityMap.put("50_" + i, 2);
			affectAirportLdnTkfCapacityMap.put("61_" + i, 2);
		}
		System.out.println("------------------------------------------");

		// read transfer passenger
		readTransferPassengerInformation();

		// 判断航班是否处在调整时间窗
		for (Flight f : flightList) {
			if (f.initialTakeoffT < Parameter.timeWindowStartTime || f.initialTakeoffT > Parameter.timeWindowEndTime) {
				f.isIncludedInTimeWindow = false;
				f.fixedTakeoffTime = f.initialTakeoffT;
				f.fixedLandingTime = f.initialLandingT;
			} else {
				f.isIncludedInTimeWindow = true;
			}
		}

		// 判断前半段处于调整时间窗外的联程航班
		for (ConnectingFlightpair cf : connectingFlightList) {
			if (!cf.firstFlight.isIncludedInTimeWindow && cf.secondFlight.isIncludedInTimeWindow) {

				System.out.println("we find this exception : flight" + cf.secondFlight.id);
				for (int i = 0; i <= 432; i++) {
					FlightArc fa = new FlightArc();
					fa.flight = cf.secondFlight;
					fa.aircraft = cf.initialAircraft;
					fa.delay = i * 5;
					fa.takeoffTime = cf.secondFlight.initialTakeoffT + fa.delay;
					fa.landingTime = cf.secondFlight.initialLandingT + fa.delay;

					if (!fa.checkViolation()) {

						cf.secondFlight.fixedTakeoffTime = fa.takeoffTime;
						cf.secondFlight.fixedLandingTime = fa.landingTime;

						String key = cf.secondFlight.leg.originAirport.id + "_" + fa.takeoffTime;
						Integer num = affectAirportLdnTkfCapacityMap.get(key);
						if (num != null) {
							affectAirportLdnTkfCapacityMap.put(key, num - 1);
						}
						key = cf.secondFlight.leg.destinationAirport.id + "_" + fa.landingTime;
						num = affectAirportLdnTkfCapacityMap.get(key);
						if (num != null) {
							affectAirportLdnTkfCapacityMap.put(key, num - 1);
						}

						break;
					}
				}

				cf.secondFlight.isIncludedInTimeWindow = false;
			}
		}
		
		

		// 生成单程乘客行程
		if(Parameter.onlySignChangeDisruptedPassenger){
			//只生成disrupted itinerary
			for (Flight f : flightList) {
				if (f.isIncludedInTimeWindow) {
					int capacity = f.aircraft.passengerCapacity;
					if(f.isCancelled || f.isStraightened){
						capacity = 0;
					}
					int totalVolume = 0;  //本来要乘坐的乘客
					
					if(f.isStraightened){
						totalVolume = f.normalPassengerNumber;
					}else{
						if(f.isIncludedInConnecting){
							if(f.brotherFlight.isCancelled){
								totalVolume = f.passengerNumber;
							}else{
								totalVolume = f.connectedPassengerNumber + f.passengerNumber;
							}
						}else{
							totalVolume = f.passengerNumber;
						}						
					}	
					
			
					
					int cancelNum = Math.max(0, totalVolume-capacity);
					cancelNum = Math.min(cancelNum, f.normalPassengerNumber);
					
					if(cancelNum > 0){
						Itinerary ite = new Itinerary();
						ite.flight = f;
						ite.volume = cancelNum;

						f.itinerary = ite;

						itineraryList.add(ite);
					}					
				}
			}
		}else{
			for (Flight f : flightList) {
				if (f.isIncludedInTimeWindow) {
					Itinerary ite = new Itinerary();
					ite.flight = f;
					ite.volume = f.normalPassengerNumber;

					f.itinerary = ite;

					itineraryList.add(ite);
				}
			}
		}	
		
		

		// 为每一个行程检测可以替代的航班
		for (Itinerary ite : itineraryList) {
			for (Flight f : flightList) {

				if (f.isIncludedInTimeWindow && f.leg.equals(ite.flight.leg) && f.id != ite.flight.id) {
					// 判断该航班是否可以承载该行程
					
					int earliestT = f.initialTakeoffT - (f.isAllowtoBringForward ? Parameter.MAX_LEAD_TIME : 0);
					int latestT = f.initialTakeoffT + (f.isDomestic?Parameter.MAX_DELAY_DOMESTIC_TIME:Parameter.MAX_DELAY_INTERNATIONAL_TIME);
					
					if(latestT >= ite.flight.initialTakeoffT && earliestT <= ite.flight.initialTakeoffT + 48*60){
						ite.candidateFlightList.add(f);
					}
				}
			}	
			
			for (Flight f : straightenedFlightList) {

				if (f.leg.equals(ite.flight.leg) && f.id != ite.flight.id) {
					// 判断该航班是否可以承载该行程
					
					int earliestT = f.initialTakeoffT - (f.isAllowtoBringForward ? Parameter.MAX_LEAD_TIME : 0);
					int latestT = f.initialTakeoffT + (f.isDomestic?Parameter.MAX_DELAY_DOMESTIC_TIME:Parameter.MAX_DELAY_INTERNATIONAL_TIME);
					
					if(latestT >= ite.flight.initialTakeoffT && earliestT <= ite.flight.initialTakeoffT + 48*60){
						ite.candidateFlightList.add(f);
					}
				}
			}
		}

		// 生成flight section itinerary
		readFlightSectionItinerary();

		// 计算每一个航班(如果取消)的联程乘客cancel cost，记住一个联程乘客只有一次cost（第二截不加）
		for (ConnectingFlightpair cf : connectingFlightList) {
			cf.firstFlight.totalConnectingCancellationCost += cf.firstFlight.connectedPassengerNumber
					* Parameter.passengerCancelCost;			
		}
		
		// 把flight上中转首段乘客的cancel cost 也加到connecting cost里，方便加入model的z coefficient
		// 因为中转首段cancel也影响后段，所以*2
		for(Flight f:flightList){
			f.totalTransferCancellationCost += f.firstTransferPassengerNumber * Parameter.passengerCancelCost;
			f.totalTransferCancellationCost += f.secondTransferPassengerNumber * Parameter.passengerCancelCost;
		}
		
		checkTyphoonAffectedFlights();

		// 计算初始存在的short connection
		for (Aircraft a : aircraftList) {
			for (int i = 0; i < a.flightList.size() - 1; i++) {
				Flight f1 = a.flightList.get(i);
				Flight f2 = a.flightList.get(i + 1);
				
				int connT = f2.initialTakeoffT - f1.initialLandingT;

				if (connT < Parameter.MIN_BUFFER_TIME) {
					f1.isShortConnection = true;
					f1.shortConnectionTime = connT;

					shortConnectionMap.put(f1.id + "_" + f2.id, connT);
				}
			}
		}
	}

	// 读取机场信息
	private List<Airport> getAirportList(String filename) {

		List<Airport> aList = new ArrayList<Airport>();
		for (int i = 0; i < Parameter.TOTAL_AIRPORT_NUM; i++) {
			Airport airport = new Airport();
			airport.id = i + 1;
			aList.add(airport);

		}

		return aList;
	}

	// 读取航段信息
	private List<Leg> getLegList() {

		List<Leg> lList = new ArrayList<Leg>();

		for (int i = 0; i < Parameter.TOTAL_AIRPORT_NUM; i++) {

			for (int j = 0; j < Parameter.TOTAL_AIRPORT_NUM; j++) {

				Leg leg = new Leg();
				leg.id = (i * Parameter.TOTAL_AIRPORT_NUM) + j; // ��ID�͸ú�����List�е�λ��һ��
				leg.originAirport = airportList.get(i);
				leg.destinationAirport = airportList.get(j);

				lList.add(leg);
			}

		}

		return lList;

	}

	// 读取飞机信息
	private List<Aircraft> getAircraftList(String filename, List<Leg> legList) {

		List<Aircraft> aList = new ArrayList<Aircraft>();

		for (int i = 0; i < Parameter.TOTAL_AIRCRAFT_NUM; i++) {

			Aircraft aircraft = new Aircraft();
			aircraft.id = i + 1;

			aList.add(aircraft);
		}

		if (this.faultList != null) {

			for (int i = 0; i < this.faultList.size(); i++) {

				Failure fault = this.faultList.get(i); // ��ȡ��ǰ�Ĺ�����Ϣ

				if (fault.aircraft.id != -1 && fault.aircraft.id != 0) {
					aList.get(fault.aircraft.id).faultList.add(fault); // ��ӹ�����Ϣ����Ӧ�ɻ�
				}
			}

		}

		ExcelOperator.getTabuLegs(filename, aList, legList);

		return aList;

	}

	// 读取停机约束
	public void readParkingInfor(int gap) {
		for (Airport airport : airportList) {
			for (Failure failure : airport.failureList) {
				if (failure.type.equals(FailureType.parking)) {
					int totalNum = (failure.eTime - failure.sTime) / gap;
					for (int i = 0; i < totalNum; i++) {
						int startTime = failure.eTime + i * gap;
						int endTime = failure.eTime + (i + 1) * gap;

						ParkingInfo pi = new ParkingInfo();
						pi.startTime = startTime;
						pi.endTime = endTime;
						pi.airport = airport;
						pi.parkingLimit = failure.parkingLimit;

						airport.parkingInfoList.add(pi);

						parkingInfoList.add(pi);
					}
				}
			}
		}
	}


	// 读取行程航班信息
	public void readFlightSectionItinerary() {
		int n = 0;
		List<Integer> delayOptionList = new ArrayList<>();
		// 六个不同的延误值
		delayOptionList.add(0);
		delayOptionList.add(6 * 60);
		delayOptionList.add(12 * 60);
		delayOptionList.add(24 * 60);
		delayOptionList.add(36 * 60);
		delayOptionList.add(48 * 60);

		for (Itinerary ite : itineraryList) {
			for (Flight f : ite.candidateFlightList) {
				int earlistT = f.initialTakeoffT;
				int latestT = f.initialTakeoffT;

				if (f.isAllowtoBringForward) {
					earlistT = earlistT - Parameter.MAX_LEAD_TIME;
				}
				if (f.isDomestic) {
					latestT = latestT + Parameter.MAX_DELAY_DOMESTIC_TIME;
				} else {
					latestT = latestT + Parameter.MAX_DELAY_INTERNATIONAL_TIME;
				}

				for (int d : delayOptionList) {
					int tkfTime = ite.flight.initialTakeoffT + d;

					if (tkfTime >= earlistT && tkfTime <= latestT) {

						f.discreteTimePointSet.add(tkfTime);
					}
				}
			}
		}
		
		List<Flight> allFlightList = new ArrayList<>();
		allFlightList.addAll(flightList);
		allFlightList.addAll(straightenedFlightList);

		for (Flight f : allFlightList) {
			if (f.isIncludedInTimeWindow) {
				int earlistT = f.initialTakeoffT;
				int latestT = f.initialTakeoffT;

				if (f.isAllowtoBringForward) {
					earlistT = earlistT - Parameter.MAX_LEAD_TIME;
				}
				if (f.isDomestic) {
					latestT = latestT + Parameter.MAX_DELAY_DOMESTIC_TIME;
				} else {
					latestT = latestT + Parameter.MAX_DELAY_INTERNATIONAL_TIME;
				}

				f.discreteTimePointSet.add(earlistT);
				f.discreteTimePointSet.add(latestT);
			}
		}

		for (Flight f : allFlightList) {
			if (f.isIncludedInTimeWindow) {
				f.discreteTimePointList.addAll(f.discreteTimePointSet);
				Collections.sort(f.discreteTimePointList);

				for (int i = 0; i < f.discreteTimePointList.size() - 1; i++) {
					int t1 = f.discreteTimePointList.get(i);
					int t2 = f.discreteTimePointList.get(i + 1);

					FlightSection flightSection = new FlightSection();
					flightSection.flight = f;
					flightSection.startTime = t1;
					flightSection.endTime = t2;

					f.flightSectionList.add(flightSection);
				}
			}
		}

		// 为每一itinerary生成flight section itinerary
		for (Itinerary ite : itineraryList) {
			// 生成替代航班相关的flight section itinerary
			for (Flight f : ite.candidateFlightList) {
				for (FlightSection flightSection : f.flightSectionList) {
					int t1 = flightSection.startTime;
					int t2 = flightSection.endTime;

					FlightSectionItinerary fsi = new FlightSectionItinerary();
					fsi.itinerary = ite;
					fsi.flightSection = flightSection;

					int delay1 = t1 - ite.flight.initialTakeoffT;
					int delay2 = t2 - ite.flight.initialTakeoffT;

					if (delay1 >= 0 && delay2 >= 0) {

						/*if (delay2 <= 6 * 60) {
							fsi.unitCost = 0.1;
						} else if (delay2 <= 12 * 60 && delay1 >= 6 * 60) {
							fsi.unitCost = 0.375;
						} else if (delay2 <= 24 * 60 && delay1 >= 12 * 60) {
							fsi.unitCost = 0.75;
						} else if (delay2 <= 36 * 60 && delay1 >= 24 * 60) {
							fsi.unitCost = 1.67;
						}else if (delay2 <= 48 * 60 && delay1 >= 36 * 60) {
							fsi.unitCost = 2.625;
						} else {
							if(delay1 < 48*60){
								System.out.println("error delay:" + delay1 + " " + delay2);								
							}
						}*/
						//在第一阶段用一种比较粗略的方式
						if (delay2 <= 24 * 60) {
							fsi.unitCost = 0.5;
						} 
						else if (delay2 <= 48 * 60 && delay1 >= 24 * 60) {
							fsi.unitCost = 2;
						}
						else {
							if(delay1 < 48*60){
								System.out.println("error delay:" + delay1 + " " + delay2);								
							}
						}
					}

					if (fsi.unitCost > 1e-6) {
						ite.flightSectionItineraryList.add(fsi);
						flightSection.flightSectionItineraryList.add(fsi);
					}
				}
			}

			// 生成每一个行程初始航班对应的flight section itinerary
			/*for (FlightSection flightSection : ite.flight.flightSectionList) {
				int t1 = flightSection.startTime;
				int t2 = flightSection.endTime;

				FlightSectionItinerary fsi = new FlightSectionItinerary();
				fsi.itinerary = ite;
				fsi.flightSection = flightSection;
				fsi.unitCost = 0;

				ite.flightSectionItineraryList.add(fsi);
				flightSection.flightSectionItineraryList.add(fsi);
			}*/
		}
	}

	// 读取转机乘客信息
	public void readTransferPassengerInformation() {
		Set<Integer> fliedFlightSet = new HashSet<>();
		if(Parameter.isReadFixedRoutes){
			for(Aircraft a:aircraftList){
				for(Flight f:a.fixedFlightList){
					if(!f.isStraightened){
						fliedFlightSet.add(f.id);
					}
				}
			}
		}
		
		try {
			Scanner sn = new Scanner(new File("transferpassenger"));

			sn.nextLine();

			while (sn.hasNextLine()) {
				String nextLine = sn.nextLine().trim();
				if (nextLine.equals("")) {
					break;
				}

				Scanner innerSn = new Scanner(nextLine);

				int inFlightID = innerSn.nextInt();
				int outFlightID = innerSn.nextInt();
				int minTurnaroundTime = innerSn.nextInt();
				int volume = innerSn.nextInt();

				Flight inFlight = flightList.get(inFlightID - 1);
				Flight outFlight = flightList.get(outFlightID - 1);

				TransferPassenger transferPassenger = new TransferPassenger();
				transferPassenger.inFlight = inFlight;
				transferPassenger.outFlight = outFlight;
				transferPassenger.minTurnaroundTime = minTurnaroundTime;
				transferPassenger.volume = volume;
				
				inFlight.firstPassengerTransferList.add(transferPassenger);
				outFlight.secondPassengerTransferList.add(transferPassenger);
				
				transferPassengerList.add(transferPassenger);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// 计算每一个航班的转机乘客和普通乘客
		int totalTransfer = 0;
		for (Flight f : flightList) {
			for (TransferPassenger tp : f.firstPassengerTransferList) {
				f.transferPassengerNumber += tp.volume;
				f.firstTransferPassengerNumber += tp.volume;
			}
			for (TransferPassenger tp : f.secondPassengerTransferList) {
				f.transferPassengerNumber += tp.volume;
				f.secondTransferPassengerNumber += tp.volume;
			}

			f.normalPassengerNumber = f.passengerNumber - f.transferPassengerNumber;
			totalTransfer += f.transferPassengerNumber;
		}

		System.out.println("totalTransfer:"+totalTransfer);
		
		if(Parameter.stageIndex == 1){
			for(Flight f:flightList){
				f.occupiedSeatsByTransferPassenger = f.transferPassengerNumber;
			}
		}else if(Parameter.stageIndex == 2){
			for(TransferPassenger tp:transferPassengerList){
				if(fliedFlightSet.contains(tp.inFlight.id)){
					if(fliedFlightSet.contains(tp.outFlight.id)){
						tp.inFlight.occupiedSeatsByTransferPassenger += tp.volume;
						tp.outFlight.occupiedSeatsByTransferPassenger += tp.volume;
					}else{
						tp.inFlight.occupiedSeatsByTransferPassenger += tp.volume;
					}
				}
			}
		}
		
/*		
		for(TransferPassenger tp:transferPassengerList){
			tp.inFlight.occupiedSeatsByTransferPassenger += tp.volume;
			tp.outFlight.occupiedSeatsByTransferPassenger += tp.volume;
		}*/
	}

	// 检测某一个航班是否处于台风影响范围从事限制起降个数
	public void checkTyphoonAffectedFlights() {

		affectedAirportSet.add(49);
		affectedAirportSet.add(50);
		affectedAirportSet.add(61);

		// 读取台风影响下的停机数
		for (Airport airport : airportList) {
			if (affectedAirportSet.contains(airport.id)) {
				for (Failure scene : airport.failureList) {
					if (scene.type.equals(FailureType.parking)) {
						affectedAirportParkingLimitMap.put(airport.id, scene.parkingLimit);
					}
				}
			}
		}

		// 初始化affectedGroundArcMap
		for (int airportId : affectedAirportSet) {
			List<GroundArc> gaList = new ArrayList<>();
			affectedAirportCoverParkLimitGroundArcMap.put(airportId, gaList);
		}
	}
	
	//generate all straightened flight leg
	public void generateStraightenedFlightList(List<ConnectingFlightpair> cpList) {
		for(ConnectingFlightpair cp:cpList) {
			if(cp.isAllowStraighten) {
				//for straightend flight, the fly time can not be decided at this phase, therefore we temporarily set this value as -1
				int flyTime = -1;
				
				//生成联程拉直航班
				
				Flight straightenedFlight = new Flight();
				straightenedFlight.isIncludedInTimeWindow = true;
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
	
				
				straightenedFlightList.add(straightenedFlight);
			}
		}
		
		for(Flight f:straightenedFlightList) {
			straightenedFlightMap.put(f.connectingFlightpair.firstFlight.id+"_"+f.connectingFlightpair.secondFlight.id, f);
		}
	}

	// 读取飞机固定的路径
	public void readFixedRoutes() {
		// 1.初始化，所有的航班取消
		for (Flight f : this.flightList) {
			f.isCancelled = true;
			f.aircraft = f.initialAircraft;
			f.actualTakeoffT = f.initialTakeoffT;
			f.actualLandingT = f.initialLandingT;
		}

		AircraftPathReader scheduleReader = new AircraftPathReader();

		// 读取已经固定的飞机路径
		Scanner sn = null;
		try {
			sn = new Scanner(new File("fixschedule"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while (sn.hasNextLine()) {
			String nextLine = sn.nextLine().trim();

			if (nextLine.equals("")) {
				break;
			}
			scheduleReader.read(nextLine, this);
		}
		
	}
	
}