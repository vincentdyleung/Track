package info.vforvincent.track.ins;

import info.vforvincent.track.app.MainActivity;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import Jama.Matrix;
import android.util.Log;

public class ParticleFilter {
	
	private List<Particle> particles;
	private static int BOUND_X = 1008;
	private static int BOUND_Y = 865;
	private static int PARTICLE_COUNT = 500;
	private int rows;
	private int cols;
	private static ParticleFilter instance;
	private double variance;
	
	public static ParticleFilter getInstance() {
		if (instance == null) {
			instance = new ParticleFilter();
		}
		return instance;
	}
	
	private ParticleFilter() {
		particles = new LinkedList<Particle>();

	}
	
	public void initialize() {
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			Particle p = generateRandomParticle(BOUND_X, BOUND_Y);
			particles.add(p);
		}
		rows = 2;
		cols = 1;
	}
	
	public void updateWeight(Matrix landmark) {
		for (Particle particle : particles) {
			particle.updateWeight(landmark, variance);
		}
	}
	
	public Matrix getEstimatedState() {
		Matrix sum = new Matrix(rows, cols);
		for (Particle p : particles) {
			sum.plusEquals(p.state);
		}
		Matrix estimate = sum.times((double) 1 / PARTICLE_COUNT);
		return estimate; 
	}
	
	public void resample() {
		Random random = new Random();
		int index = random.nextInt(PARTICLE_COUNT);
		double beta = 0;
		double maxWeight = getMaxWeight(particles);
		Log.d(MainActivity.TAG, "Max weight: " + Double.toString(maxWeight));
		List<Particle> resampledParticles = new LinkedList<Particle>();
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			beta += random.nextFloat() * 2 * maxWeight;
			double weight = particles.get(index).weight;
			while (beta > weight) {
				beta -= weight;
				index = (index + 1) % PARTICLE_COUNT;
				weight = particles.get(index).weight;
			}
			//Log.d(MainActivity.TAG, Double.toString(particles.get(index).weight));
			resampledParticles.add(particles.get(index));
		}
		particles.removeAll(particles);
		particles.addAll(resampledParticles);
	}
	
	public void setVariance(double variance) {
		this.variance = variance;
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
	
	private Particle generateRandomParticle(int boundX, int boundY) {
		Random random = new Random();
		int randomX = random.nextInt(boundX) + 1;
		int randomY = random.nextInt(boundY) + 1;
		Matrix randomState = new Matrix(new double[][] {{randomX}, {randomY}});
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
		
		public void updateWeight(Matrix landmark, double variance) {
			double distance = getDistance(landmark, state);
			//Log.d("PF", "Distance: " + Double.toString(distance));
			//Log.d("PF", "Variance: " + Double.toString(variance));
			double exponent = -0.5 * Math.pow(distance, 2) / variance;
			double result = Math.exp(exponent) / Math.sqrt(2 * Math.PI * variance);
			if (result < 0.000001 || Double.isNaN(result)) {
				weight = 0;
			} else {
				weight = result;
			}
			
		}
		
	}
	
	private double getDistance(Matrix v1, Matrix v2) {
		double sum = 0d;
		for (int i = 0; i < v1.getRowDimension(); i++) {
			sum += Math.pow(v1.get(i, 0) - v2.get(i, 0), 2);
		}
		return Math.sqrt(sum);
	}

}
