package sghku.tianchi.IntelligentAviation.entity;

import java.util.ArrayList;
import java.util.List;

//乘客行程
public class TransferItinerary {
	public int id;
	public Flight flight;
	public int volume;
	
	public List<Flight> candidateFlightList = new ArrayList<>();
	
	public List<FlightTransferItinerary> flightTransferItineraryList = new ArrayList<>();
}
