package sghku.tianchi.IntelligentAviation.entity;

public class FlightTransferItinerary {
	public int id;
	public FlightArc flightArc; 
	public Itinerary itinerary;
	public double unitCost;  //decide by evaluating arc and itinerary
	public double volume;  //to be decided by solving model
}
