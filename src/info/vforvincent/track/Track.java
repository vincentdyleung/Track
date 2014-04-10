package info.vforvincent.track;

import info.vforvincent.track.app.FileUtil;
import info.vforvincent.track.calibration.Calibrator;
import info.vforvincent.track.ins.KalmanFilter;
import info.vforvincent.track.ins.ParticleFilter;
import info.vforvincent.track.ins.listener.LinearAccelerationListener;
import info.vforvincent.track.listener.OnTrackStateUpdateListener;

import java.util.Locale;
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
import android.os.Handler;
import android.os.HandlerThread;
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
	private HandlerThread handlerThread;
	private Matrix lastEstimatedState;
	public static final int PIXEL_OFFSET_X = 4749;
	public static final int PIXEL_OFFSET_Y = 788;
	
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
			listener.onCalibrationStart();
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
				listener.onCalibrationFinish();
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
		particleFilter.initialize();
		locationUtil.setOnGetLocationResultListener(new OnGetLocationResultListener() {

			@Override
			public void onGetLocationResult(String areaId, PointF[] positions,
					Integer[] arg2, String arg3) {
				if (positions != null && positions.length > 0) {
					scale = ExtraLocationUtil.getBuildingScale(areaId);
					PointF adjustedPosition = new PointF();
					adjustedPosition.x = positions[0].x - PIXEL_OFFSET_X;
					adjustedPosition.y = positions[0].y - PIXEL_OFFSET_Y;
					lastPosition = currentPosition;
					currentPosition = adjustedPosition;
					if (isFirstFix == true) {
						lastTimestamp = System.currentTimeMillis();
						lastPosition = positions[0];
						Matrix state = new Matrix(new double[][] {{0d}, {0d}, {0d}});
						startKalmanFilter(state);
						isFirstFix = false;
						lastEstimatedState = new Matrix(new double[][] {{currentPosition.x}, {currentPosition.y}});
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
	
	public void clearCalibration() {
		parameters.edit().putBoolean(IS_CALIBRATED, false).commit();
	}
	
	private void runParticleFilter() {
		Matrix kalmanFilterState = kalmanFilter.getState();
		Matrix kalmanFilterCovariance = kalmanFilter.getCovariance();
		double time = ((double) System.currentTimeMillis() - lastTimestamp) / 1000;
		Matrix landmark = getLandmark(lastPosition, currentPosition, kalmanFilterState.get(0, 0));
		particleFilter.updateWeight(landmark);
		particleFilter.setVariance(kalmanFilterCovariance.get(0, 0));
		particleFilter.resample();
		estimatedState = particleFilter.getEstimatedState();
		Matrix newState = new Matrix(new double[][] {{0d}, {getEstimatedDistance() / time}, {linearAccelerationListener.getLastAcceleration()}});
		linearAccelerationListener.getKalmanFilter().setState(newState);
		lastTimestamp = System.currentTimeMillis();
		lastEstimatedState.set(0, 0, estimatedState.get(0, 0));
		lastEstimatedState.set(1, 0, estimatedState.get(1, 0));
		listener.onTrackStateUpdate(estimatedState, wifiManager.getConnectionInfo().getRssi());
	}
	
	private Matrix getLandmark(PointF lastPosition, PointF currentPosition, double kalmanDistance) {
		double wifiDistance = getDistance(currentPosition, lastPosition);
		double ratio = kalmanDistance / wifiDistance;
		double deltaX = ratio * (currentPosition.x - lastPosition.x);
		double deltaY = ratio * (currentPosition.y - lastPosition.y);
		double landmarkX = lastPosition.x + deltaX;
		double landmarkY = lastPosition.y + deltaY;
		String line = String.format(Locale.US, "%f,%f,%f,%f,%f,%f,%f", wifiDistance, kalmanDistance, ratio, deltaX, deltaY, landmarkX, landmarkY);
		FileUtil.getInstance().writeLine(line);
		return new Matrix(new double[][]{{landmarkX}, {landmarkY}});
	}
	
	private double getEstimatedDistance() {
		double currentX = estimatedState.get(0, 0);
		double currentY = estimatedState.get(1, 0);
		double lastX = lastEstimatedState.get(0, 0);
		double lastY = lastEstimatedState.get(1, 0);
		return Math.sqrt(Math.pow(currentX - lastX, 2) + Math.pow(currentY - lastY, 2)) / scale;
	}
	
	public void stop() {
		locationUtil.stopLocation();
		sensorManager.unregisterListener(linearAccelerationListener, accelerometer);
		handlerThread.quit();
	}
	
	public void setOnTrackStateUpdateListener(OnTrackStateUpdateListener listener) {
		this.listener = listener;
	}
	
	private void startKalmanFilter(Matrix initialState) {
		kalmanFilter.setState(initialState);
		linearAccelerationListener = new LinearAccelerationListener(kalmanFilter, parameters);
		handlerThread = new HandlerThread("KalmanFilter");
		handlerThread.start();
		Handler handler = new Handler(handlerThread.getLooper());
		sensorManager.registerListener(linearAccelerationListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST, handler);
	}
	
	private double getDistance(PointF p1, PointF p2) {
		float x1 = p1.x;
		float y1 = p1.y;
		float x2 = p2.x;
		float y2 = p2.y;
		return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)) / scale;
	}

}
