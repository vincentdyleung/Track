package info.vforvincent.track;

import info.vforvincent.track.calibration.Calibrator;
import info.vforvincent.track.calibration.CalibratorListener;
import info.vforvincent.track.ins.KalmanFilter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.math3.stat.descriptive.moment.Variance;

import Jama.Matrix;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Joiner;

public class MainActivity extends Activity implements SensorEventListener, OnClickListener, CalibratorListener{
	public static final String TAG = "Track";
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private TextView mText;
	private TextView mDistanceText;
	private TextView mParameterText;
	private Button mStartButton;
	private Button mStopButton;
	private Button mCaptureButton;
	private Button mCaptureStopButton;
	private KalmanFilter mKalmanFilter;
	private long mLastUpdateTime = 0;
	private double mLastFirstPass = 0;
	private double mLastSecondPass = 0;
	private int mDelaySlots = 0;
	private Queue<Double> mHistory;
	private static final double DAMP = 0.95;
	private static final double UPPER_THRESHOLD = 1d;
	private static final double LOWER_THRESHOLD = 0.018d;
	private static final int WINDOW_SIZE = 150;
	private static final String OFFSET = "offset";
	private static final String VARIANCE = "variance";
	private SharedPreferences mParameters;
	private CaptureSensorListener mCaptureListener;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHistory = new LinkedList<Double>();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mText = (TextView) findViewById(R.id.text);
        mDistanceText = (TextView) findViewById(R.id.distance);
        mStartButton = (Button) findViewById(R.id.start_button);
        mStopButton = (Button) findViewById(R.id.stop_button);
        mCaptureButton = (Button) findViewById(R.id.capture_button);
        mCaptureStopButton = (Button) findViewById(R.id.capture_stop_button);
        mCaptureButton.setOnClickListener(this);
        mCaptureStopButton.setOnClickListener(this);
        mStartButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mParameters = getPreferences(Context.MODE_PRIVATE);
        mParameterText = (TextView) findViewById(R.id.parameters);
        mParameterText.setText("Parameters:\n" + getParameterText());
        mCaptureListener = new CaptureSensorListener();
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
		double adjustedValue = event.values[1] - mParameters.getFloat(OFFSET, 0);
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
		if (mHistory.size() <= WINDOW_SIZE) {
			return;
		}
		Variance movingVariance = new Variance();
		Double[] values = mHistory.toArray(new Double[0]);
		double[] doubleValues = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			doubleValues[i] = values[i];
		}
		double varianceResult = movingVariance.evaluate(doubleValues);
		double filterInput = 0;
		if (varianceResult < UPPER_THRESHOLD && varianceResult > LOWER_THRESHOLD) {
			if (mDelaySlots >= WINDOW_SIZE) {
				mDelaySlots = 0;
			}
			filterInput = mHistory.poll();
		} else {
			if (mDelaySlots < WINDOW_SIZE) {
				filterInput = mHistory.poll();
				mDelaySlots++;
			}
		}
		Matrix measurement = new Matrix(new double[][]{{ filterInput }});
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
			mKalmanFilter = new KalmanFilter(mParameters.getFloat(VARIANCE, 0), 
					new Matrix(new double[][] {{0d}, {0d}, {0d}}), 
					new Matrix(new double[][] {{0d, 0d, 1d}}));
			mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		}
		if (element.getId() == R.id.stop_button) {
			mKalmanFilter.closeWriter();
			mSensorManager.unregisterListener(this, mAccelerometer);
			Matrix state = mKalmanFilter.getState();
			mText.append("Result: " + Arrays.deepToString(state.getArray()) + "\n");
			mLastUpdateTime = 0;
			mHistory.clear();
		}
		if (element.getId() == R.id.capture_button) {
			mText.setText("Capturing...");
			mSensorManager.registerListener(mCaptureListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		}
		if (element.getId() == R.id.capture_stop_button) {
			mSensorManager.unregisterListener(mCaptureListener, mAccelerometer);
			mText.append("Capture finished");
			mCaptureListener.closeWriter();
		}
	}

	@Override
	public void onFinish(double[] results) {
		// TODO Auto-generated method stub
		mParameters.edit().
		putFloat(OFFSET, (float) results[0]).
		putFloat(VARIANCE, (float) results[1]).
		commit();
		mParameterText.setText("Parameters: \n" + getParameterText());
		mText.append("Calibration done\n");
	}

	@Override
	public SensorManager getSensorManager() {
		return mSensorManager;
	}

	@Override
	public Sensor getSensor() {
		return mAccelerometer;
	}
    
	private String getParameterText() {
		Map<String, ?> allParameters = mParameters.getAll();
		return Joiner.on("\n").withKeyValueSeparator("=").join(allParameters);
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
}
