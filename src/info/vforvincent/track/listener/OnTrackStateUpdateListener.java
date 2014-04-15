package info.vforvincent.track.listener;

import info.vforvincent.track.ins.ParticleFilter.Particle;

import java.util.List;

import Jama.Matrix;

public interface OnTrackStateUpdateListener {
	public void onTrackStateUpdate(Matrix state, List<Particle> particles, int rssi);
	public void onCalibrationStart();
	public void onCalibrationFinish();
	public void onParticleFilterInitialize(List<Particle> particles);
}
