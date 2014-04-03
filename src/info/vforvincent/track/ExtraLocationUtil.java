package info.vforvincent.track;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import yl.demo.rock.lbs.process.LocationUtil;
import android.content.Context;
import android.os.Environment;

public class ExtraLocationUtil extends LocationUtil {

	public ExtraLocationUtil(Context arg0, String arg1, String arg2) {
		super(arg0, arg1, arg2);
	}
	
	public static double getBuildingScale(String areaId) {
		String areaInfoPath = Environment.getExternalStorageDirectory()
				+ "/wherami/hkust/" + areaId + "/areaInfo.txt";
		HashMap<String, String> areaInfoMap = new HashMap<String, String>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(areaInfoPath));
			String line;
			while ((line = reader.readLine()) != null && line.length() > 0) {
				String[] kvp = new String[2];
				kvp = line.split("=");
				areaInfoMap.put(kvp[0], kvp[1]);
			}
			reader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return Double.valueOf(areaInfoMap.get("building_scale_value"));
	}
}
