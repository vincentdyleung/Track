package info.vforvincent.track;

import info.vforvincent.track.calibration.Calibrator;
import info.vforvincent.track.calibration.CalibratorListener;
import info.vforvincent.track.ins.KalmanFilter;

import java.util.Arrays;

import Jama.Matrix;
import android.app.Activity;
import android.content.Context;
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

public class MainActivity extends Activity implements SensorEventListener, OnClickListener, CalibratorListener{
	public static final String TAG = "Track";
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private TextView mText;
	private Button mStartButton;
	private Button mStopButton;
	private KalmanFilter mKalmanFilter;
	private long mLastUpdate = 0;
	private double mLastValue = 0;
	private double offset;
	private double variance;
	
	private static final double DAMP = 0.95;
	private static final double THRESHOLD = 0d;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mText = (TextView) findViewById(R.id.text);
        mStartButton = (Button) findViewById(R.id.start_button);
        mStopButton = (Button) findViewById(R.id.stop_button);
        mStartButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mKalmanFilter = new KalmanFilter(variance, 
        								new Matrix(new double[][] {{0d}, {0d}, {0d}}), 
        								new Matrix(new double[][] {{0d, 0d, 1d}}));
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
		double adjustedValue = event.values[1] - offset;
		if (mLastUpdate == 0) {
			mLastUpdate = event.timestamp;
			mLastValue = adjustedValue;
			return;
		}
		double interval = ((double) event.timestamp - mLastUpdate) / 1000000000;
		double value = (1 - DAMP) * adjustedValue + DAMP * mLastValue;
		Matrix measurement = new Matrix(new double[][]{{ Math.abs(value) < THRESHOLD ? 0d : value}});
		mKalmanFilter.update(measurement, interval);
		mLastUpdate = event.timestamp;
		mLastValue = value;
	}

	@Override
	public void onClick(View element) {
		if (element.getId() == R.id.start_button) {
			mText.append("Tracking...\n");
			mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		}
		if (element.getId() == R.id.stop_button) {
			mSensorManager.unregisterListener(this, mAccelerometer);
			Matrix state = mKalmanFilter.getState();
			mText.append("Result: " + Arrays.deepToString(state.getArray()) + "\n");
			mKalmanFilter.reset();
			mLastUpdate = 0;
			mKalmanFilter.closeWriter();
		}
	}

	@Override
	public void onFinish(double[] results) {
		// TODO Auto-generated method stub
		offset = results[0];
		variance = results[1];
		mKalmanFilter.setVariance(variance);
		mText.setText("Calibration result:\n" + "Offset: " + Double.toString(offset) + "\nVariance: " + Double.toString(variance) + "\n");
	}

	@Override
	public SensorManager getSensorManager() {
		return mSensorManager;
	}

	@Override
	public Sensor getSensor() {
		return mAccelerometer;
	}
    
}