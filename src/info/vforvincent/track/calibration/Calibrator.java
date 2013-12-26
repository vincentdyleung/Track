package info.vforvincent.track.calibration;

import info.vforvincent.track.MainActivity;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class Calibrator implements SensorEventListener{
	private CalibratorListener mListener;
	private static final int SAMPLE_SIZE = 1000;
	private double[] mSamples = new double[SAMPLE_SIZE];
	private int mSampleIndex;
	
	public Calibrator(CalibratorListener listener) {
		mListener = listener;
	}
	
	public void start() {
		mSampleIndex = 0;
		mListener.getSensorManager().registerListener(this, mListener.getSensor(), SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		Log.d(MainActivity.TAG, "Value: " + Double.toString(event.values[1]));
		Log.d(MainActivity.TAG, "Index: " + Integer.toString(mSampleIndex));
		if (mSampleIndex < 1000) {
			mSamples[mSampleIndex] = event.values[1];
			mSampleIndex++;
		} else {
			mListener.getSensorManager().unregisterListener(this, mListener.getSensor());
			double[] results = new double[2];
			Variance variance = new Variance();
			Mean mean = new Mean();
			results[0] = mean.evaluate(mSamples);
			results[1] = variance.evaluate(mSamples);
			mListener.onFinish(results);
		}
	}

}
