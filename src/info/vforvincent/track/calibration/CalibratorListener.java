package info.vforvincent.track.calibration;

import android.hardware.Sensor;
import android.hardware.SensorManager;

public interface CalibratorListener {
	public void onFinish(double[] results);
	public SensorManager getSensorManager();
	public Sensor getSensor();
}
