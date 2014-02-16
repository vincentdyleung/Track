package info.vforvincent.track.calibration;

import info.vforvincent.track.MainActivity;

import java.util.LinkedList;

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
	private LinkedList<Double> mAccelerationSamples;
	private LinkedList<Double> mPitchSamples;
	
	public Calibrator(CalibratorListener listener) {
		mAccelerationSamples = new LinkedList<Double>();
		mPitchSamples = new LinkedList<Double>();
		mListener = listener;
	}
	
	public void start() {
		registerListener();
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		int sampleCount;
		switch(event.sensor.getType()) {
			case Sensor.TYPE_LINEAR_ACCELERATION:
				sampleCount = mAccelerationSamples.size();
				if (sampleCount < SAMPLE_SIZE) {
					mAccelerationSamples.add((double) event.values[1]);
				}
				break;
			case Sensor.TYPE_ROTATION_VECTOR:
				sampleCount = mPitchSamples.size();
				if (sampleCount < SAMPLE_SIZE) {
					float[] values = new float[3];
					float[] rotationMatrix = new float[9];
					SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
					SensorManager.getOrientation(rotationMatrix, values);
					mPitchSamples.add(((double) values[1]));
					Log.d(MainActivity.TAG, "Value: " + Double.toString(values[1]));
					Log.d(MainActivity.TAG, "Index: " + Integer.toString(sampleCount));
				}
				break;
			default:
		}
		if (mAccelerationSamples.size() >= SAMPLE_SIZE && mPitchSamples.size() >= SAMPLE_SIZE) {
			unregisterListener();
			double[] results = new double[3];
			Variance variance = new Variance();
			Mean mean = new Mean();
			double[] accelerationSamples = toDoubleArray(mAccelerationSamples);
			double[] pitchSamples = toDoubleArray(mPitchSamples);
			results[0] = mean.evaluate(accelerationSamples);
			results[1] = variance.evaluate(accelerationSamples);
			results[2] = mean.evaluate(pitchSamples);
			mListener.onFinish(results);
		}
	}

	private void registerListener() {
		for (Sensor sensor : mListener.getSensors()) {
			mListener.getSensorManager().registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
		}
	}
	
	private void unregisterListener() {
		for (Sensor sensor : mListener.getSensors()) {
			mListener.getSensorManager().unregisterListener(this, sensor);
		}
	}
	
	private double[] toDoubleArray(LinkedList<Double> list) {
		double[] array = new double[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i);
		}
		return array;
	}
}
