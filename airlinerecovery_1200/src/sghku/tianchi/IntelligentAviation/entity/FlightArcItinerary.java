package sghku.tianchi.IntelligentAviation.entity;

public class FlightArcItinerary {
	public int id;
	public FlightArc flightArc; //签转出去的flightArc
	public Itinerary itinerary; //来自于itinerary
	public double unitCost;  //decide by evaluating arc and itinerary
	public double volume;  //从ite分给flightArc的乘客数，to be decided by solving model
}
