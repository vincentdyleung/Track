package info.vforvincent.track;

import info.vforvincent.track.calibration.Calibrator;
import info.vforvincent.track.calibration.CalibratorListener;
import info.vforvincent.track.ins.KalmanFilter;
import info.vforvincent.track.ui.DistanceDialogFragment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.moment.Variance;

import Jama.Matrix;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Joiner;

public class MainActivity extends FragmentActivity 
					implements SensorEventListener, OnClickListener, CalibratorListener,
					SharedPreferences.OnSharedPreferenceChangeListener {
	public static final String TAG = "Track";
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private Sensor mRotation;
	private TextView mText;
	private TextView mDistanceText;
	private TextView mParameterText;
	private Button mStartButton;
	private Button mStopButton;
	private Button mCaptureButton;
	private Button mCaptureStopButton;
	private Button mPitchButton;
	private KalmanFilter mKalmanFilter;
	private long mLastUpdateTime = 0;
	private boolean mStarted = false;
	private double mLastFirstPass = 0;
	private double mLastSecondPass = 0;
	private int mDelaySlots = WINDOW_SIZE;
	private LinkedList<Double> mHistory;
	private static final double DAMP = 0.95;
	private static final double UPPER_THRESHOLD = 1d;
	private static final double LOWER_THRESHOLD = 0.05d;
	private static final int WINDOW_SIZE = 125;
	private static final double FACTOR = 2;
	private static final String ACCELERATION_OFFSET = "acceleration_offset";
	private static final String PITCH_OFFSET = "pitch_offset";
	private static final String ACCELERATION_VARIANCE = "acceleration_variance";
	private static final String PITCH_FACTOR = "pitch_factor";
	private SharedPreferences mParameters;
	private CaptureSensorListener mCaptureListener;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHistory = new LinkedList<Double>();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mText = (TextView) findViewById(R.id.text);
        mDistanceText = (TextView) findViewById(R.id.distance);
        mStartButton = (Button) findViewById(R.id.start_button);
        mStopButton = (Button) findViewById(R.id.stop_button);
        mCaptureButton = (Button) findViewById(R.id.capture_button);
        mCaptureStopButton = (Button) findViewById(R.id.capture_stop_button);
        mPitchButton = (Button) findViewById(R.id.pitch_button);
        mCaptureButton.setOnClickListener(this);
        mCaptureStopButton.setOnClickListener(this);
        mStartButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mPitchButton.setOnClickListener(this);
        mParameters = getPreferences(Context.MODE_PRIVATE);
        mParameters.registerOnSharedPreferenceChangeListener(this);
        mParameterText = (TextView) findViewById(R.id.parameters);
        showParameters(mParameters);
        File rootDirectory = Environment.getExternalStorageDirectory();
        File directory = new File(rootDirectory.getAbsolutePath() + "/Track");
        if (!directory.exists()) {
        	directory.mkdir();
        }
    }

    @Override
    public void onPause() {
    	super.onPause();
    	mSensorManager.unregisterListener(this, mAccelerometer);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    		case R.id.actions_calibrate:
    			Calibrator calibrator = new Calibrator(this);
    			calibrator.start();
    			mText.setText("Calibrating...");
    			return true;
    		case R.id.action_distance:
    			DistanceDialogFragment distanceDialog = new DistanceDialogFragment();
    			distanceDialog.show(getSupportFragmentManager(), "distance");
    		default:
    			return super.onOptionsItemSelected(item);
    	}
    }


	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onSensorChanged(SensorEvent event) {
		float factor = mParameters.getFloat(PITCH_FACTOR, 1);
		double adjustedValue = (event.values[1] - mParameters.getFloat(ACCELERATION_OFFSET, 0)) * factor;
		double value = 0;
		// first update received
		if (mLastUpdateTime == 0) {
			mLastFirstPass = adjustedValue;
			mLastSecondPass = adjustedValue;
			value = adjustedValue;
		} else {
//			if (mStarted && event.timestamp - mFirstUpdateTime > 3000000000l) {
//				mStopButton.performClick();
//				return;
//			}
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
		mKalmanFilter.update(measurement, interval, value, varianceResult, adjustedValue);
		Matrix state = mKalmanFilter.getState();
		double distance = state.get(0, 0);
		String message = "Distance: " + Double.toString(distance);
		mDistanceText.setText(message);
	}

	@Override
	public void onClick(View element) {
		if (element.getId() == R.id.start_button) {
			mText.append("Tracking...\n");
			mKalmanFilter = new KalmanFilter(mParameters.getFloat(ACCELERATION_VARIANCE, 0), 
					new Matrix(new double[][] {{0d}, {0d}, {0d}}), 
					new Matrix(new double[][] {{0d, 0d, 1d}}),
					Integer.toString(mParameters.getInt(DistanceDialogFragment.DISTANCE, 0)) + "m");
			mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		}
		if (element.getId() == R.id.stop_button) {
			mKalmanFilter.closeWriter();
			mSensorManager.unregisterListener(this, mAccelerometer);
			Matrix state = mKalmanFilter.getState();
			mText.append("Result: " + Arrays.deepToString(state.getArray()) + "\n");
			mLastUpdateTime = 0;
			mStarted = false;
			mHistory.clear();
			mDelaySlots = WINDOW_SIZE;
		}
		if (element.getId() == R.id.capture_button) {
			mCaptureListener = new CaptureSensorListener();
			mText.setText("Capturing...");
			mSensorManager.registerListener(mCaptureListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		}
		if (element.getId() == R.id.capture_stop_button) {
			mSensorManager.unregisterListener(mCaptureListener, mAccelerometer);
			mText.append("Capture finished");
			mCaptureListener.closeWriter();
		}
		if (element.getId() == R.id.pitch_button) {
			mSensorManager.registerListener(new SensorEventListener() {

				@Override
				public void onAccuracyChanged(Sensor arg0, int arg1) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onSensorChanged(SensorEvent event) {
					// TODO Auto-generated method stub
					float[] values = new float[3];
					float[] rotationMatrix = new float[9];
					SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
					SensorManager.getOrientation(rotationMatrix, values);
					mParameters.edit().putFloat(PITCH_FACTOR, (float) Math.cos(values[1])).commit();
					mSensorManager.unregisterListener(this, mRotation);
				}
				
			}, mRotation, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	@Override
	public void onFinish(double[] results) {
		// TODO Auto-generated method stub
		mParameters.edit().
		putFloat(ACCELERATION_OFFSET, (float) results[0]).
		putFloat(ACCELERATION_VARIANCE, (float) results[1]).
		putFloat(PITCH_OFFSET, (float) results[2]).
		commit();
		mText.append("Calibration done\n");
	}

	@Override
	public SensorManager getSensorManager() {
		return mSensorManager;
	}

	@Override
	public LinkedList<Sensor> getSensors() {
		LinkedList<Sensor> sensors = new LinkedList<Sensor>();
		sensors.add(mAccelerometer);
		sensors.add(mRotation);
		return sensors;
	}
	
	private class CaptureSensorListener implements SensorEventListener {
		
		private FileWriter writer;
		private long lastUpdate;
		
		public CaptureSensorListener() {
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				Date time = new Date();
				DateFormat format = DateFormat.getDateTimeInstance();
				String fileName = "raw_" + format.format(time) + ".csv";
				File output = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
				try {
					writer = new FileWriter(output);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			lastUpdate = 0;
		}
		
		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			// TODO Auto-generated method stub
			if (lastUpdate == 0) {
				lastUpdate = event.timestamp;
				return;
			}
			double interval = ((double) event.timestamp - lastUpdate) / 1000000000;
			double reading = event.values[1];
			String line = String.format(Locale.US, "%f,%f\n", reading, interval);
			lastUpdate = event.timestamp;
			Log.d(TAG, line);
			try {
				writer.append(line);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void closeWriter() {
			try {
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		// TODO Auto-generated method stub
		showParameters(sharedPreferences);
	}
	
	private void showParameters(SharedPreferences sharedPreferences) {
		Map<String, ?> allParameters = sharedPreferences.getAll();
		String string = Joiner.on("\n").withKeyValueSeparator("=").join(allParameters);
		mParameterText.setText(string);
	}
}
