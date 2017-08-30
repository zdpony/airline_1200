package sghku.tianchi.IntelligentAviation;


import java.util.HashSet;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.algorithm.NetworkBuilder;
import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.OutputResult;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightSection;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.model.PushForwardCplexModel;

public class FlightRecovery {

	public static void main(String[] args) {

		//Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME, Parameter.FLYTIME_FILENAME);
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
			
		Clique clique = new Clique();
		
		for(int i=0;i<scenario.aircraftList.size();i++) {
		//for(int i=0;i<10;i++) {
			clique.aircraftList.add(scenario.aircraftList.get(i));
		}
		//clique.aircraftList.addAll(scenario.aircraftList);
		
		NetworkBuilder nb = new NetworkBuilder(scenario, 60);
		nb.init();
		nb.buildNetwork(clique, false, true, false, true);
				
	}

}

