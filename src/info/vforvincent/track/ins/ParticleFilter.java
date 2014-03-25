package info.vforvincent.track.ins;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import Jama.Matrix;
import android.util.Log;

public class ParticleFilter {
	
	private List<Particle> particles;
	private static int PARTICLE_COUNT = 500;
	private double maxWeight;
	private int rows;
	private int cols;
	
	public ParticleFilter(Matrix state, Matrix covariance, Matrix landmark) {
		particles = new LinkedList<Particle>();
		double meanDistance = getDistance(state, landmark);
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			Particle p = generateRandomParticle(state, covariance);
			p.updateWeight(landmark, meanDistance, getVarianceFromCovariance(covariance));
			particles.add(p);
		}
		maxWeight = getMaxWeight(particles);
		Log.d("PF", "Max weight: " + Double.toString(maxWeight));
		rows = state.getRowDimension();
		cols = state.getColumnDimension();
	}
	
	public void start() {
		particles = resample();
	}
	
	public Matrix getEstimatedState() {
		Matrix sum = new Matrix(rows, cols);
		for (Particle p : particles) {
			sum.plusEquals(p.state);
		}
		Matrix estimate = sum.times((double) 1 / PARTICLE_COUNT);
		return estimate; 
	}
	
	private List<Particle> resample() {
		Random random = new Random();
		int index = random.nextInt(PARTICLE_COUNT);
		double beta = 0;
		List<Particle> resampledParticles = new LinkedList<Particle>();
		for (int i = 0; i < particles.size(); i++) {
			beta += random.nextFloat() * 2 * maxWeight;
			while (beta > particles.get(i).weight) {
				beta -= particles.get(i).weight;
				index = (index + 1) % PARTICLE_COUNT;
			}
			resampledParticles.add(particles.get(index));
			//Log.d("PF", "Selected: " + Arrays.deepToString(particles.get(index).state.getArray()));
		}
		maxWeight = getMaxWeight(resampledParticles);
		return resampledParticles;
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
		for (int i = 0; i < covariance.getRowDimension(); i++) {
			double sd = Math.sqrt(covariance.get(i, i));
			double mean = state.get(i, 0);
			Random random = new Random();
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
			double exponent = -0.5 * Math.pow(distance - mean, 2) / variance;
			weight = Math.exp(exponent) / Math.sqrt(2 * Math.PI * variance);
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
