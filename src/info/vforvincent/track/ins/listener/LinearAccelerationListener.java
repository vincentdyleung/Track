package info.vforvincent.track.ins.listener;

import info.vforvincent.track.Track;
import info.vforvincent.track.ins.KalmanFilter;

import java.util.LinkedList;

import org.apache.commons.math3.stat.descriptive.moment.Variance;

import Jama.Matrix;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class LinearAccelerationListener implements SensorEventListener {

	private static final double UPPER_THRESHOLD = 1d;
	private static final double LOWER_THRESHOLD = 0.05d;
	private static final double FACTOR = 2;
	private SharedPreferences mParameters;
	private long mLastUpdateTime = 0;
	private double mLastFirstPass = 0;
	private double mLastSecondPass = 0;
	private static double DAMP = 0.95;
	private static int WINDOW_SIZE = 125;
	private LinkedList<Double> mHistory;
	private int mDelaySlots = WINDOW_SIZE;
	private KalmanFilter mKalmanFilter;
	private boolean mStarted = false;
	private double lastAcceleration;
	
	public LinearAccelerationListener(KalmanFilter kalmanFilter, SharedPreferences parameters) {
		mHistory = new LinkedList<Double>();
		mKalmanFilter = kalmanFilter;
		mParameters = parameters;
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		float factor = mParameters.getFloat(Track.PITCH_FACTOR, 1);
		double adjustedValue = (event.values[1] - mParameters.getFloat(Track.ACCELERATION_OFFSET, 0)) * factor;
		lastAcceleration = adjustedValue;
		double value = 0;
		// first update received
		if (mLastUpdateTime == 0) {
			mLastFirstPass = adjustedValue;
			mLastSecondPass = adjustedValue;
			value = adjustedValue;
		} else {
			double firstPass = DAMP * mLastFirstPass + (1 - DAMP) * adjustedValue;
			double secondPass = DAMP * mLastSecondPass + (1 - DAMP) * firstPass;
			mLastFirstPass = firstPass;
			mLastSecondPass = secondPass;
			value = secondPass;
		}
		double interval = ((double) event.timestamp - mLastUpdateTime) / 1000000000;
		mLastUpdateTime = event.timestamp;
		mHistory.add(value);
		// wait until the window is full
		if (mHistory.size() < WINDOW_SIZE) {
			return;
		}
		Variance movingVariance = new Variance();
		double[] doubleValues = new double[WINDOW_SIZE];
		for (int i = 0; i < WINDOW_SIZE; i++) {
			doubleValues[i] = mHistory.get(i);
		}
		double varianceResult = movingVariance.evaluate(doubleValues);
		double filterInput = mHistory.poll();
		if (varianceResult < UPPER_THRESHOLD && varianceResult > LOWER_THRESHOLD) {
			if (!mStarted) {
				mStarted = true;
			}
			mDelaySlots = 0;
		} else {
			if (mDelaySlots < WINDOW_SIZE) {
				mDelaySlots++;
			} else {
				filterInput = 0;
			}
		}
		Matrix measurement = new Matrix(new double[][]{{ filterInput*=FACTOR }});
		mKalmanFilter.update(measurement, interval);
	}
	
	public double getLastAcceleration() {
		return lastAcceleration;
	}
	
	public KalmanFilter getKalmanFilter() {
		return mKalmanFilter;
	}

}
