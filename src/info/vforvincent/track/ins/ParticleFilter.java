package info.vforvincent.track.ins;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import Jama.Matrix;

public class ParticleFilter {
	
	private List<Particle> particles;
	private static int PARTICLE_COUNT = 500;
	private double maxWeight;
	private int rows;
	private int cols;
	
	public ParticleFilter(Matrix state, Matrix covariance, Matrix landmark) {
		particles = new LinkedList<Particle>();
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			Particle p = generateRandomParticle(state, covariance);
			p.updateWeight(landmark);
			particles.add(p);
		}
		maxWeight = getMaxWeight(particles);
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
		return sum.times(1 / PARTICLE_COUNT);
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
		
		public void updateWeight(Matrix landmark) {
			weight = 0.5;
		}
		
	}

}
