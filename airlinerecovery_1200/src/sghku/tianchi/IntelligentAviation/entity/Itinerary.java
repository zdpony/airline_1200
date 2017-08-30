package sghku.tianchi.IntelligentAviation.entity;

import java.util.ArrayList;
import java.util.List;

//乘客行程
public class Itinerary {
	public int id;
	public Flight flight;
	public int volume;  //all normal passengers on flight
	
	public List<Flight> candidateFlightList = new ArrayList<>();
	
	public List<FlightSectionItinerary> flightSectionItineraryList = new ArrayList<>();

	public List<FlightArc> flightArcList = new ArrayList<>();   //ite自己的flight对应的flightarc
	public List<ConnectingArc> firstConnectionArcList = new ArrayList<>(); //ite自己的flight对应的connectingarc
	public List<ConnectingArc> secondConnectingArcList = new ArrayList<>();
	
	public List<FlightArcItinerary> flightArcItineraryList = new ArrayList<>();  //从ite签转到其他flightArc的信息
}
