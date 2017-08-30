package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			Scanner sn1 = new Scanner(new File("file1"));
			Scanner sn2 = new Scanner(new File("file2"));

			Map<Integer, Integer> map1 = new HashMap<>();
			Map<Integer, Integer> map2 = new HashMap<>();

			while(sn1.hasNextLine()){
				String nextLine = sn1.nextLine().trim();
				if(nextLine.equals("")){
					break;
				}
				Scanner innerSn = new Scanner(nextLine);
				map1.put(innerSn.nextInt(), innerSn.nextInt());
			}
			
			while(sn2.hasNextLine()){
				String nextLine = sn2.nextLine().trim();
				if(nextLine.equals("")){
					break;
				}
				
				Scanner innerSn = new Scanner(nextLine);
				map2.put(innerSn.nextInt(), innerSn.nextInt());
			}
			
			for(int key:map1.keySet()){
				int value1 = map1.get(key);
				int value2 = map2.get(key);
				
				if(value1 != value2){
					System.out.println("f:"+key+" "+value1+" "+value2);
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
