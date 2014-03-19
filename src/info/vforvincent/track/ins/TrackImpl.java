package info.vforvincent.track.ins;

import info.vforvincent.track.MainActivity;
import info.vforvincent.track.Track;
import info.vforvincent.track.ins.listener.LinearAccelerationListener;

import java.util.Arrays;
import java.util.Timer;
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
	private long timeInterval;  //in milliseconds
	private PointF lastPosition;
	private PointF firstPosition;
	private long lastTimestamp;
	private double scale;
	private Sensor accelerometer;
	private SensorManager sensorManager;
	private SharedPreferences parameters;
	private boolean isFirstFix;
	private KalmanFilter kalmanFilter;
	private LinearAccelerationListener linearAccelerationListener;
	private Matrix estimatedState;
	private Timer wifiTimer;
	
	public TrackImpl() {
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		kalmanFilter = new KalmanFilter(parameters.getFloat(MainActivity.ACCELERATION_VARIANCE, 0), 
				new Matrix(new double[][] {{0d}, {0d}, {0d}}), 
				new Matrix(new double[][] {{0d, 0d, 1d}}));
		locationUtil = new LocationUtil(context, siteName, dataFilePath);
		wifiTimer = new Timer("wifi");
		final ParticleFilterTask pfTask = new ParticleFilterTask();
		locationUtil.setOnGetLocationResultListener(new OnGetLocationResultListener() {

			@Override
			public void onGetLocationResult(String areaId, PointF[] positions,
					Integer[] arg2, String arg3) {
				// TODO Auto-generated method stub
				if (positions != null && positions.length > 0) {
					scale = ExtraLocationUtil.getBuildingScale(areaId);
					lastPosition = positions[0];
					if (isFirstFix == false) {
						firstPosition = positions[0];
						Matrix state = new Matrix(new double[][] {{0d}, {0d}, {0d}});
						startKalmanFilter(state);
						wifiTimer.schedule(pfTask, 0l, timeInterval);
						isFirstFix = true;
					}
				}
			}
			
		});
		locationUtil.startLocation();
		
	}

	private void startKalmanFilter(Matrix initialState) {
		kalmanFilter.setState(initialState);
		linearAccelerationListener = new LinearAccelerationListener(kalmanFilter, parameters);
		sensorManager.registerListener(linearAccelerationListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	@Override
	public Track setTimeInterval(long timeInterval) {
		// TODO Auto-generated method stub
		this.timeInterval = timeInterval;
		return this;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		locationUtil.stopLocation();
		sensorManager.unregisterListener(linearAccelerationListener, accelerometer);
		wifiTimer.cancel();
		wifiTimer.purge();
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
			Matrix kalmanFilterState = kalmanFilter.getState();
			Matrix kalmanFilterCovariance = kalmanFilter.getCovariance();
			double distance = getDistance(firstPosition, lastPosition);
			double time = (System.currentTimeMillis() - lastTimestamp) / 1000;
			Log.d(MainActivity.TAG, Double.toString(distance) + "," + Double.toString(time));
			Matrix landmark = new Matrix(new double[][] {{distance}, {distance / time}, {0}});
			ParticleFilter particleFilter = new ParticleFilter(kalmanFilterState, kalmanFilterCovariance, landmark);
			particleFilter.start();
			estimatedState = particleFilter.getEstimatedState();
			Log.d(MainActivity.TAG, Arrays.deepToString(estimatedState.getArrayCopy()));
			lastTimestamp = System.currentTimeMillis();
		}
		
	}

	@Override
	public Track setParameters(SharedPreferences parameters) {
		// TODO Auto-generated method stub
		this.parameters = parameters;
		return this;
	}

}
