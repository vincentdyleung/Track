package info.vforvincent.track.ins;

import info.vforvincent.track.ins.ParticleFilter.Particle;
import info.vforvincent.track.ins.ParticleFilter.ProbabilityModel;
import yl.demo.rock.lbs.datatype.PointF;
import Jama.Matrix;

public class WiFiINSProbabilityModel extends ProbabilityModel {

	private PointF lastPosition;
	private PointF currentPosition;
	private PointF lastEstimatePosition;
	private double distance;
	private MapConstraint mapConstraint;
	private final double STATIONARY_THRESHOLD = 0.1; //threshold to consider stationary
	private final double WIFI_DISTANCE_THRESHOLD = 30;
	private final double WIFI_VARIANCE = 40000; //pixel squared
	private final double KALMAN_VARIANCE = 1600; //pixel squared
	
	public WiFiINSProbabilityModel(PointF lastPosition, PointF currentPosition, double distance, MapConstraint mapConstraint, Matrix lastEstimate) {
		this.lastPosition = lastPosition;
		this.currentPosition = currentPosition;
		this.distance = distance;
		this.mapConstraint = mapConstraint;
		lastEstimatePosition = new PointF();
		lastEstimatePosition.x = (float) lastEstimate.get(0, 0);
		lastEstimatePosition.y = (float) lastEstimate.get(1, 0);
	}
	
	@Override
	public double evaluate(Particle p) {
		// TODO Auto-generated method stub
		double weight = 0;
		PointF particlePointF = new PointF();
		particlePointF.x = (float) p.state.get(0, 0);
		particlePointF.y = (float) p.state.get(1, 0);
		if (mapConstraint.isBetweenWalls(particlePointF)) {
			if (distance < STATIONARY_THRESHOLD) {
				weight = getGaussianWeight(particlePointF, lastEstimatePosition);
			} else {
				if (getDistance(lastPosition, currentPosition) > WIFI_DISTANCE_THRESHOLD) {
					weight = getGaussianWeight(particlePointF, getIntersect(lastPosition, currentPosition, distance));
				} else {
					double particleDistance = getDistance(currentPosition, particlePointF);
					double exponent = -0.5 * Math.pow(particleDistance, 2) / KALMAN_VARIANCE;
					weight = Math.exp(exponent) / Math.sqrt(2 * Math.PI * KALMAN_VARIANCE);
				}
			}
		}
		return weight;
	}
	
	private double getDistance(PointF p1, PointF p2) {
		return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
	}
	
	private double getGaussianWeight(PointF p, PointF landmark) {
		double distance = getDistance(p, landmark);
		double exponent = -0.5 * Math.pow(distance, 2) / WIFI_VARIANCE;
		return Math.exp(exponent) / Math.sqrt(2 * Math.PI * WIFI_VARIANCE);
	}
	
	private PointF getIntersect(PointF start, PointF end, double distance) {
		double wifiDistance = getDistance(currentPosition, lastPosition);
		double ratio = distance / wifiDistance;
		PointF intersect = new PointF();
		intersect.x = (float) (start.x + ratio * (end.x - start.x));
		intersect.y = (float) (start.y + ratio * (end.y - start.y));
		return intersect;
	}

}
