package sghku.tianchi.IntelligentAviation.comparator;

import java.util.Comparator;

import sghku.tianchi.IntelligentAviation.clique.Clique;

public class CliqueComparator implements Comparator<Clique> {

	@Override
	public int compare(Clique o1, Clique o2) {
		// TODO Auto-generated method stub
		if(o1.value > o2.value){
			return -1;
		}else if(o1.value < o2.value){
			return 1;
		}else{
			return 0;
		}	
	}

}
