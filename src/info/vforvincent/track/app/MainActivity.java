package info.vforvincent.track.app;

import info.vforvincent.track.R;
import info.vforvincent.track.Track;
import info.vforvincent.track.app.ui.DistanceDialogFragment;
import info.vforvincent.track.listener.OnTrackStateUpdateListener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

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
					implements OnClickListener,
					SharedPreferences.OnSharedPreferenceChangeListener,
					OnTrackStateUpdateListener {
	public static final String TAG = "Track";
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private TextView mText;
	private TextView mParameterText;
	private Button mStartButton;
	private Button mStopButton;
	private Button mCaptureButton;
	private Button mCaptureStopButton;

	private SharedPreferences mParameters;
	private CaptureSensorListener mCaptureListener;
	private Track track;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mText = (TextView) findViewById(R.id.text);
        mStartButton = (Button) findViewById(R.id.start_button);
        mStopButton = (Button) findViewById(R.id.stop_button);
        mCaptureButton = (Button) findViewById(R.id.capture_button);
        mCaptureStopButton = (Button) findViewById(R.id.capture_stop_button);
        mCaptureButton.setOnClickListener(this);
        mCaptureStopButton.setOnClickListener(this);
        mStartButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    		case R.id.actions_calibrate:
    			return true;
    		case R.id.action_distance:
    			DistanceDialogFragment distanceDialog = new DistanceDialogFragment();
    			distanceDialog.show(getSupportFragmentManager(), "distance");
    		default:
    			return super.onOptionsItemSelected(item);
    	}
    }

	@Override
	public void onClick(View element) {
		if (element.getId() == R.id.start_button) {
			FileUtil.getInstance().open();
			track = new Track("hkust", Environment.getExternalStorageDirectory() + "/wherami", this, this);
			track.setOnTrackStateUpdateListener(this);
			track.start();
		}
		if (element.getId() == R.id.stop_button) {
			track.stop();
			FileUtil.getInstance().close();
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

	@Override
	public void onTrackStateUpdate(Matrix state, int rssi) {
		// TODO Auto-generated method stub
		String line = String.format(Locale.US, "%f,%f", state.get(0, 0), state.get(1, 0));
		//FileUtil.getInstance().writeLine(line);
		Log.d(TAG, "Estimate: " + Arrays.deepToString(state.getArray()));
	}

	@Override
	public void onCalibrationStart() {
		// TODO Auto-generated method stub
		mText.setText("Calibrating...\n");
	}

	@Override
	public void onCalibrationFinish() {
		// TODO Auto-generated method stub
		mText.append("Calibration done\n");
	}
}
