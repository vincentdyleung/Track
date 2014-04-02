package info.vforvincent.track.ins;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import Jama.Matrix;

public class ParticleFilter {
	
	private List<Particle> particles;
	private static int PARTICLE_COUNT = 500;
	private int rows;
	private int cols;
	private static ParticleFilter instance;
	
	public static ParticleFilter getInstance() {
		if (instance == null) {
			instance = new ParticleFilter();
		}
		return instance;
	}
	
	private ParticleFilter() {
		particles = new LinkedList<Particle>();

	}
	
	public void initialize(Matrix state, Matrix landmark, Matrix covariance) {
		particles.removeAll(particles);
		double meanDistance = getDistance(state, landmark);
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			Particle p = generateRandomParticle(state, covariance);
			p.updateWeight(landmark, meanDistance, getVarianceFromCovariance(covariance));
			particles.add(p);
		}
		rows = state.getRowDimension();
		cols = state.getColumnDimension();
	}
	
	public void start() {
		resample();
	}
	
	public Matrix getEstimatedState() {
		Matrix sum = new Matrix(rows, cols);
		for (Particle p : particles) {
			sum.plusEquals(p.state);
		}
		Matrix estimate = sum.times((double) 1 / PARTICLE_COUNT);
		return estimate; 
	}
	
	private void resample() {
		Random random = new Random();
		int index = random.nextInt(PARTICLE_COUNT);
		double beta = 0;
		double maxWeight = getMaxWeight(particles);
		List<Particle> resampledParticles = new LinkedList<Particle>();
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			beta += random.nextFloat() * 2 * maxWeight;
			double weight = particles.get(index).weight;
			while (beta > weight) {
				beta -= weight;
				index = (index + 1) % PARTICLE_COUNT;
				weight = particles.get(index).weight;
			}
			resampledParticles.add(particles.get(index));
			//Log.d("PF", "Selected: " + Arrays.deepToString(particles.get(index).state.getArray()));
		}
		particles.removeAll(particles);
		particles.addAll(resampledParticles);
	}
	
	private double getMaxWeight(List<Particle> ps) {
		double mw = 0;
		for (Particle p : ps) {
			if (p.weight > mw) {
				mw = p.weight;
			}
		}
		return mw;
	}
	
	private Particle generateRandomParticle(Matrix state, Matrix covariance) {
		Matrix randomState = new Matrix(state.getRowDimension(), state.getColumnDimension());
		Random random = new Random();
		for (int i = 0; i < covariance.getRowDimension(); i++) {
			double sd = Math.sqrt(covariance.get(i, i));
			double mean = state.get(i, 0);
			double gaussian = random.nextGaussian();
			randomState.set(i, 0, gaussian * sd + mean);
		}
		Particle p = new Particle(randomState, 0);
		return p;
	}

	class Particle {
		Matrix state;
		double weight;
		
		public Particle(Matrix s, double w) {
			state = s;
			weight = w;
		}
		
		public void setWeight(double w) {
			weight = w;
		}
		
		public void setState(Matrix s) {
			state = s;
		}
		
		public void updateWeight(Matrix landmark, double mean, double variance) {
			double distance = getDistance(landmark, state);
			//Log.d("PF", Double.toString(distance) + "," + Double.toString(mean) + "," + Double.toString(variance));
			double exponent = -0.5 * Math.pow(distance - mean, 2) / variance;
			double result = Math.exp(exponent) / Math.sqrt(2 * Math.PI * variance);
			if (result < 0.000001 || Double.isNaN(result)) {
				weight = 0;
			} else {
				weight = result;
			}
			//Log.d("PF", "Weight: " + Double.toString(weight));
		}
		
	}
	
	private double getDistance(Matrix v1, Matrix v2) {
		double sum = 0d;
		for (int i = 0; i < v1.getRowDimension(); i++) {
			sum += Math.pow(v1.get(i, 0) - v2.get(i, 0), 2);
		}
		return Math.sqrt(sum);
	}
	
	private double getVarianceFromCovariance(Matrix covariance) {
		return covariance.get(0, 0);
	}

}
