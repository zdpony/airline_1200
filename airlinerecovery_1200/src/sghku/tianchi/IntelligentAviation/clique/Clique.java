package sghku.tianchi.IntelligentAviation.clique;

import java.util.ArrayList;
import java.util.List;

import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;

public class Clique {
	public int id;
	public List<Aircraft> aircraftList = new ArrayList<Aircraft>();
	public List<Flight> realFlightList = new ArrayList<>();
	public List<ConnectingFlightpair> realConnectingFlightPairList = new ArrayList<>();
	
	public List<Aircraft> candidateAircraftList = new ArrayList<>();
	
	public double value;
	
	public void init(){
		realFlightList.clear();
		realConnectingFlightPairList.clear();
	}
}
