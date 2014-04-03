package info.vforvincent.track;

import info.vforvincent.track.app.MainActivity;
import info.vforvincent.track.calibration.Calibrator;
import info.vforvincent.track.ins.KalmanFilter;
import info.vforvincent.track.ins.ParticleFilter;
import info.vforvincent.track.ins.listener.LinearAccelerationListener;
import info.vforvincent.track.listener.OnTrackStateUpdateListener;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import yl.demo.rock.lbs.datatype.PointF;
import yl.demo.rock.lbs.process.ILocationUtil.OnGetLocationResultListener;
import yl.demo.rock.lbs.process.LocationUtil;
import Jama.Matrix;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

public class Track {
	private LocationUtil locationUtil;
	private Context context;
	private String siteName;
	private String dataFilePath;
	private PointF currentPosition;
	private PointF lastPosition;
	private long lastTimestamp;
	private double scale;
	private Sensor accelerometer;
	private SensorManager sensorManager;
	private SharedPreferences parameters;
	private boolean isFirstFix;
	private KalmanFilter kalmanFilter;
	private ParticleFilter particleFilter;
	private LinearAccelerationListener linearAccelerationListener;
	private Matrix estimatedState;
	private Activity activity;
	private OnTrackStateUpdateListener listener;
	public static final String IS_CALIBRATED = "is_calibrated";
	public static final String ACCELERATION_OFFSET = "acceleration_offset";
	public static final String PITCH_OFFSET = "pitch_offset";
	public static final String ACCELERATION_VARIANCE = "acceleration_variance";
	public static final String PITCH_FACTOR = "pitch_factor";
	private static final int[] SENSORS = {Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR};
	private final Calibrator calibrator;
	private WifiManager wifiManager;
	
	public Track(String siteName, String dataFilePath, Activity activity, Context context) {
		this.siteName = siteName;
		this.dataFilePath = dataFilePath;
		this.activity = activity;
		this.context = context;
		sensorManager = (SensorManager) this.activity.getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		parameters = this.activity.getPreferences(Context.MODE_PRIVATE);
		calibrator = new Calibrator(SENSORS, context);
		wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		locationUtil = new LocationUtil(this.context, this.siteName, this.dataFilePath);
	}

	public void start() {
		if (parameters.getBoolean(IS_CALIBRATED, false) == false) {
			FutureTask<double[]> calibrationTask = new FutureTask<double[]>(calibrator);
			Executor executor = Executors.newSingleThreadExecutor();
			executor.execute(calibrationTask);
			try {
				double[] results = calibrationTask.get();
				parameters.edit().
				putFloat(ACCELERATION_OFFSET, (float) results[0]).
				putFloat(ACCELERATION_VARIANCE, (float) results[1]).
				putFloat(PITCH_OFFSET, (float) results[2]).
				putFloat(PITCH_FACTOR, (float) results[3]).
				putBoolean(IS_CALIBRATED, true)
				.commit();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		isFirstFix = true;
		kalmanFilter = new KalmanFilter(parameters.getFloat(ACCELERATION_VARIANCE, 0), 
				new Matrix(new double[][] {{0d}, {0d}, {0d}}), 
				new Matrix(new double[][] {{0d, 0d, 1d}}));
		particleFilter = ParticleFilter.getInstance();
		locationUtil.setOnGetLocationResultListener(new OnGetLocationResultListener() {

			@Override
			public void onGetLocationResult(String areaId, PointF[] positions,
					Integer[] arg2, String arg3) {
				if (positions != null && positions.length > 0) {
					scale = ExtraLocationUtil.getBuildingScale(areaId);
					lastPosition = currentPosition;
					currentPosition = positions[0];
					if (isFirstFix == true) {
						lastPosition = positions[0];
						Matrix state = new Matrix(new double[][] {{0d}, {0d}, {0d}});
						startKalmanFilter(state);
						isFirstFix = false;
						return;
					}
					runParticleFilter();
				} else {
					Toast.makeText(context, "Site not supported", Toast.LENGTH_SHORT).show();
					locationUtil.stopLocation();
				}
			}
			
		});
		locationUtil.startLocation();
		
	}
	
	private void runParticleFilter() {
		Matrix kalmanFilterState = kalmanFilter.getState();
		Matrix kalmanFilterCovariance = kalmanFilter.getCovariance();
		double distance = getDistance(currentPosition, lastPosition);
		double time = ((double) System.currentTimeMillis() - lastTimestamp) / 1000;
		Matrix landmark = new Matrix(new double[][] {{distance}, {distance / time}, {linearAccelerationListener.getLastAcceleration()}});
		Log.d(MainActivity.TAG, "Landmark: " + Arrays.deepToString(landmark.getArray()));
		Log.d(MainActivity.TAG, "Kalman: " + Arrays.deepToString(kalmanFilterState.getArray()));
		Log.d(MainActivity.TAG, "Covariance: " + Arrays.deepToString(kalmanFilterCovariance.getArray()));
		particleFilter.initialize(kalmanFilterState, landmark, kalmanFilterCovariance);
		particleFilter.start();
		estimatedState = particleFilter.getEstimatedState();
		Matrix newState = new Matrix(new double[][] {{0d}, {estimatedState.getArray()[1][0]}, {estimatedState.getArray()[2][0]}});
		linearAccelerationListener.getKalmanFilter().setState(newState);
		lastTimestamp = System.currentTimeMillis();
		listener.onTrackStateUpdate(estimatedState, wifiManager.getConnectionInfo().getRssi());
	}
	
	public void stop() {
		locationUtil.stopLocation();
		sensorManager.unregisterListener(linearAccelerationListener, accelerometer);
	}
	
	public void setOnTrackStateUpdateListener(OnTrackStateUpdateListener listener) {
		this.listener = listener;
	}
	
	private void startKalmanFilter(Matrix initialState) {
		kalmanFilter.setState(initialState);
		linearAccelerationListener = new LinearAccelerationListener(kalmanFilter, parameters);
		sensorManager.registerListener(linearAccelerationListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	private double getDistance(PointF p1, PointF p2) {
		float x1 = p1.x;
		float y1 = p1.y;
		float x2 = p2.x;
		float y2 = p2.y;
		return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)) / scale;
	}

}
