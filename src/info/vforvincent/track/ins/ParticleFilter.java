package info.vforvincent.track.ins;

import info.vforvincent.track.app.MainActivity;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import yl.demo.rock.lbs.datatype.PointF;
import Jama.Matrix;
import android.util.Log;

public class ParticleFilter {
	
	private List<Particle> particles;
	public static int BOUND_X = 1008;
	public static int BOUND_Y = 865;
	public static int BOUND_LEFT = 187;
	public static int BOUND_TOP = 247;
	public static int BOUND_RIGHT = 952;
	public static int BOUND_BOTTOM = 362;
	private static int PARTICLE_COUNT = 500;
	private int rows;
	private int cols;
	private static ParticleFilter instance;
	private MapConstraint mapConstraint;
	private double variance;
	
	public static ParticleFilter getInstance() {
		if (instance == null) {
			instance = new ParticleFilter();
		}
		return instance;
	}
	
	private ParticleFilter() {
		particles = new LinkedList<Particle>();
		mapConstraint = MapConstraint.getInstance();
	}
	
	public void initialize() {
		particles.removeAll(particles);
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			Particle p = generateRandomParticle();
			particles.add(p);
		}
		rows = 2;
		cols = 1;
	}
	
	public void updateWeight(ProbabilityModel model) {
		double sumWeight = 0;
		for (Particle particle : particles) {
			particle.updateWeight(model);
			sumWeight += particle.weight;
		}
		Log.d(MainActivity.TAG, "Sum weight: " + Double.toString(sumWeight));
		for (Particle particle : particles) {
			particle.weight /= sumWeight;
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
		}
		particles.removeAll(particles);
		particles.addAll(resampledParticles);
	}
	
	public void setVariance(double variance) {
		this.variance = variance < 1 ? 1 : variance;
	}
	
	public double getVariance() {
		return this.variance;
	}
	
	public List<Particle> getParticles() {
		return this.particles;
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
	
	private Particle generateRandomParticle() {
		Random random = new Random();
		int randomX, randomY;
		PointF point;
		do {
			randomX = (int) Math.round(random.nextDouble() * (BOUND_RIGHT - BOUND_LEFT)) + BOUND_LEFT;
			randomY = (int) Math.round(random.nextDouble() * (BOUND_BOTTOM - BOUND_TOP)) + BOUND_TOP;
			point = new PointF();
			point.x = randomX;
			point.y = randomY;
		} while (mapConstraint.isBetweenWalls(point) == false);
		Matrix randomState = new Matrix(new double[][] {{randomX}, {randomY}});
		Particle p = new Particle(randomState, 0);
		return p;
	}

	public class Particle {
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
		
		public Matrix getState() {
			return state;
		}
		
		public double getWeight() {
			return weight;
		}
		
		public void updateWeight(ProbabilityModel model) {
			weight = model.evaluate(this);
		}
		
	}
	
	public static abstract class ProbabilityModel {
		public ProbabilityModel() {
			
		}
		public abstract double evaluate(Particle p);
	}
}
