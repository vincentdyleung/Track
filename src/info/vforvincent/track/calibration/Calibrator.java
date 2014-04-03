package info.vforvincent.track.calibration;

import java.util.LinkedList;
import java.util.concurrent.Callable;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

public class Calibrator implements Callable<double[]> {
	private static final int SAMPLE_SIZE = 1000;
	private LinkedList<Double> accelerationSamples;
	private LinkedList<Double> pitchSamples;
	private LinkedList<Sensor> sensors;
	private Context context;
	private SensorManager sensorManager;
	private double[] results;
	private final Object lock;
	
	public Calibrator(int[] sensorTypes, Context context) {
		accelerationSamples = new LinkedList<Double>();
		pitchSamples = new LinkedList<Double>();
		this.sensors = new LinkedList<Sensor>();
		this.context = context;
		sensorManager = (SensorManager) this.context.getSystemService(Context.SENSOR_SERVICE);
		for (int i = 0; i < sensorTypes.length; i++) {
			sensors.add(sensorManager.getDefaultSensor(sensorTypes[i]));
		}
		results = new double[4];
		lock = new Object();
	}
	
	private double[] toDoubleArray(LinkedList<Double> list) {
		double[] array = new double[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i);
		}
		return array;
	}

	private void calculateResults() {
		Variance variance = new Variance();
		Mean mean = new Mean();
		double[] accelerationSamplesArray = toDoubleArray(accelerationSamples);
		double[] pitchSamplesArray = toDoubleArray(pitchSamples);
		results[0] = mean.evaluate(accelerationSamplesArray);
		results[1] = variance.evaluate(accelerationSamplesArray);
		results[2] = mean.evaluate(pitchSamplesArray);
		results[3] = Math.cos(pitchSamples.getLast() - results[2]);
	}
	
	@Override
	public double[] call() throws Exception {
		// TODO Auto-generated method stub
		HandlerThread handlerThread = new HandlerThread("calibration");
		handlerThread.start();
		Handler calibrationHandler = new Handler(handlerThread.getLooper());
		CalibrationListener listener = new CalibrationListener();
		for (Sensor sensor : sensors) {
			sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST, calibrationHandler);
		}
		
		synchronized(lock) {
			lock.wait();
			handlerThread.quit();
		}
		return results;
	}
	
	private class CalibrationListener implements SensorEventListener {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			return;
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			switch(event.sensor.getType()) {
				case Sensor.TYPE_LINEAR_ACCELERATION:
					if (accelerationSamples.size() < SAMPLE_SIZE) {
						accelerationSamples.add((double) event.values[1]);
					}
					break;
				case Sensor.TYPE_ROTATION_VECTOR:
					if (pitchSamples.size() < SAMPLE_SIZE) {
						float[] values = new float[3];
						float[] rotationMatrix = new float[9];
						SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
						SensorManager.getOrientation(rotationMatrix, values);
						pitchSamples.add(((double) values[1]));
					}
					break;
				default:
					return;
			}
			if (accelerationSamples.size() >= SAMPLE_SIZE && pitchSamples.size() >= SAMPLE_SIZE) {
				for (Sensor sensor : sensors) {
					sensorManager.unregisterListener(this, sensor);
				}
				calculateResults();
				synchronized(lock) {
					lock.notify();
				}
			}
		}
	}
}
