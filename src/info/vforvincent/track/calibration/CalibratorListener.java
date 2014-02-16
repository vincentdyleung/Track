package info.vforvincent.track.calibration;

import java.util.LinkedList;

import android.hardware.Sensor;
import android.hardware.SensorManager;

public interface CalibratorListener {
	public void onFinish(double[] results);
	public SensorManager getSensorManager();
	public LinkedList<Sensor> getSensors();
}
