package info.vforvincent.track.ins;

import info.vforvincent.track.FileUtil;
import info.vforvincent.track.MainActivity;
import info.vforvincent.track.Track;
import info.vforvincent.track.ins.listener.LinearAccelerationListener;

import java.util.Arrays;
import java.util.Locale;
import java.util.TimerTask;

import yl.demo.rock.lbs.datatype.PointF;
import yl.demo.rock.lbs.process.ILocationUtil.OnGetLocationResultListener;
import yl.demo.rock.lbs.process.LocationUtil;
import Jama.Matrix;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

public class TrackImpl implements Track {
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
	
	public TrackImpl() {
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		isFirstFix = true;
		kalmanFilter = new KalmanFilter(parameters.getFloat(MainActivity.ACCELERATION_VARIANCE, 0), 
				new Matrix(new double[][] {{0d}, {0d}, {0d}}), 
				new Matrix(new double[][] {{0d, 0d, 1d}}));
		particleFilter = ParticleFilter.getInstance();
		locationUtil = new LocationUtil(context, siteName, dataFilePath);
		locationUtil.setOnGetLocationResultListener(new OnGetLocationResultListener() {

			@Override
			public void onGetLocationResult(String areaId, PointF[] positions,
					Integer[] arg2, String arg3) {
				// TODO Auto-generated method stub
				if (positions != null && positions.length > 0) {
					scale = ExtraLocationUtil.getBuildingScale(areaId);
					lastPosition = currentPosition;
					currentPosition = positions[0];
					if (isFirstFix == true) {
						lastPosition = positions[0];
						Matrix state = new Matrix(new double[][] {{0d}, {0d}, {0d}});
						startKalmanFilter(state);
						//wifiTimer.schedule(pfTask, 0l, timeInterval);
						isFirstFix = false;
						return;
					}
					runParticleFilter();
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
		String line = String.format(Locale.US, "%f,%f,%f", estimatedState.getArray()[0][0], estimatedState.getArray()[1][0], estimatedState.getArray()[2][0]);
		FileUtil.getInstance().writeLine(line);
		Log.d(MainActivity.TAG, "Estimate: " + Arrays.deepToString(estimatedState.getArrayCopy()));
		Matrix newState = new Matrix(new double[][] {{0d}, {estimatedState.getArray()[1][0]}, {estimatedState.getArray()[2][0]}});
		linearAccelerationListener.getKalmanFilter().setState(newState);
		lastTimestamp = System.currentTimeMillis();
	}

	private void startKalmanFilter(Matrix initialState) {
		kalmanFilter.setState(initialState);
		linearAccelerationListener = new LinearAccelerationListener(kalmanFilter, parameters);
		sensorManager.registerListener(linearAccelerationListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		locationUtil.stopLocation();
		sensorManager.unregisterListener(linearAccelerationListener, accelerometer);
	}

	@Override
	public Track setContext(Context context) {
		// TODO Auto-generated method stub
		this.context = context;
		return this;
	}

	@Override
	public Track setSiteName(String siteName) {
		// TODO Auto-generated method stub
		this.siteName = siteName;
		return this;
	}

	@Override
	public Track setDataFilePath(String dataFilePath) {
		// TODO Auto-generated method stub
		this.dataFilePath = dataFilePath;
		return this;
	}

	@Override
	public Track setAccelerometer(Sensor accelerometer) {
		// TODO Auto-generated method stub
		this.accelerometer = accelerometer;
		return this;
	}

	@Override
	public Track setSensorManager(SensorManager sensorManager) {
		// TODO Auto-generated method stub
		this.sensorManager = sensorManager;
		return this;
	}
	
	private double getDistance(PointF p1, PointF p2) {
		float x1 = p1.x;
		float y1 = p1.y;
		float x2 = p2.x;
		float y2 = p2.y;
		return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)) / scale;
	}
	
	class ParticleFilterTask extends TimerTask {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			//sensorManager.unregisterListener(linearAccelerationListener, accelerometer);
			Log.d("Track", "Run task");
			runParticleFilter();
		}
		
	}

	@Override
	public Track setParameters(SharedPreferences parameters) {
		// TODO Auto-generated method stub
		this.parameters = parameters;
		return this;
	}

}
