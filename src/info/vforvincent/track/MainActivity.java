package info.vforvincent.track;

import info.vforvincent.track.calibration.Calibrator;
import info.vforvincent.track.calibration.CalibratorListener;
import info.vforvincent.track.ins.KalmanFilter;

import java.util.Arrays;
import java.util.Map;

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.google.common.collect.EvictingQueue;

public class MainActivity extends Activity implements SensorEventListener, OnClickListener, CalibratorListener{
	public static final String TAG = "Track";
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private TextView mText;
	private TextView mDistanceText;
	private TextView mParameterText;
	private Button mStartButton;
	private Button mStopButton;
	private KalmanFilter mKalmanFilter;
	private long mLastUpdate = 0;
	private double mLastValue = 0;
	private EvictingQueue<Double> mPreviousUpdates;
	private static final double DAMP = 0.95;
	private static final double UPPER_THRESHOLD = 0.1d;
	private static final double LOWER_THRESHOLD = 0.003d;
	private static final int WINDOW_SIZE = 20;
	private static final String OFFSET = "offset";
	private static final String VARIANCE = "variance";
	private SharedPreferences mParameters;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreviousUpdates = EvictingQueue.create(WINDOW_SIZE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mText = (TextView) findViewById(R.id.text);
        mDistanceText = (TextView) findViewById(R.id.distance);
        mStartButton = (Button) findViewById(R.id.start_button);
        mStopButton = (Button) findViewById(R.id.stop_button);
        mStartButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mParameters = getPreferences(Context.MODE_PRIVATE);
        mParameterText = (TextView) findViewById(R.id.parameters);
        mParameterText.setText("Parameters:\n" + getParameterText());
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
		double value = (1 - DAMP) * adjustedValue + DAMP * mLastValue;
		mLastValue = adjustedValue;
		mPreviousUpdates.add(adjustedValue);
		double interval = ((double) event.timestamp - mLastUpdate) / 1000000000;
		mLastUpdate = event.timestamp;
		// do nothing if it is the first update or don't have enough to calculate variance
		if (mLastUpdate == 0 || mPreviousUpdates.size() < WINDOW_SIZE) {
			return;
		}
		Variance movingVariance = new Variance();
		Double[] values = mPreviousUpdates.toArray(new Double[0]);
		double[] doubleValues = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			doubleValues[i] = values[i];
		}
		double varianceResult = movingVariance.evaluate(doubleValues);
		Matrix measurement = new Matrix(new double[][]{{ varianceResult < UPPER_THRESHOLD && varianceResult > LOWER_THRESHOLD ? value : 0d}});
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
			mLastUpdate = 0;
			mPreviousUpdates.clear();
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
}
