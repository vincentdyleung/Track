package info.vforvincent.track.listener;

import Jama.Matrix;

public interface OnTrackStateUpdateListener {
	public void onTrackStateUpdate(Matrix state, int rssi);
	public void onCalibrationStart();
	public void onCalibrationFinish();
}
