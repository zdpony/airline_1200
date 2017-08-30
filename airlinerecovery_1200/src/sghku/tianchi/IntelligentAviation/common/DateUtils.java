package sghku.tianchi.IntelligentAviation.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {
	public static Date getNowDate(String dateString) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm");
		Date currentTime_2 = null;
		try {
			currentTime_2 = formatter.parse(dateString);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return currentTime_2;
	}
	
	public static double getTimeGap(Date date1, Date date2){
		return (date1.getTime()-date2.getTime())*1.0/1000.0/60.0;
	}
}
