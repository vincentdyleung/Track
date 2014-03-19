package info.vforvincent.track;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;

public interface Track {
	public void start();
	public Track setTimeInterval(long timeInterval);
	public Track setAccelerometer(Sensor accelerometer);
	public Track setSensorManager(SensorManager sensorManager);
	public Track setContext(Context context);
	public Track setSiteName(String siteName);
	public Track setDataFilePath(String dataFilePath);
	public Track setParameters(SharedPreferences parameters);
	public void stop();
	
	static interface OnTimeIntervalListener{
		void onTimeIntervalReached();
	}
}
