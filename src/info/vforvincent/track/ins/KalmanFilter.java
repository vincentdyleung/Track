package info.vforvincent.track.ins;

import info.vforvincent.track.MainActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import Jama.Matrix;
import android.os.Environment;
import android.util.Log;

public class KalmanFilter {

	private FileWriter writer;
	private double q;
	private Matrix x;
	private Matrix H;
	private Matrix F;
	private Matrix P;
	private Matrix R;
	private final Matrix I;
	
	public KalmanFilter(double variance_, Matrix state_, Matrix extraction_) {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			Date time = new Date();
			DateFormat format = DateFormat.getDateTimeInstance();
			String fileName = "output_" + format.format(time) + ".csv";
			File output = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
			try {
				writer = new FileWriter(output);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		q = variance_;
		x = state_;
		H = extraction_;
		double[][] transitionArray_ = {{1d, 0d, 0d}, {0d, 1d, 0d}, {0d, 0d, 1d}};
		F = new Matrix(transitionArray_);
		
		I = Matrix.identity(F.getRowDimension(), F.getColumnDimension());
		Log.d(MainActivity.TAG, Integer.toString(I.getRowDimension()));
		Log.d(MainActivity.TAG, Integer.toString(I.getColumnDimension()));
		
		double[][] stateUncertaintyArray_ = {{0d, 0d, 0d}, {0d, 0d, 0d}, {0d, 0d, q}};
		P = new Matrix(stateUncertaintyArray_);
		
		R = new Matrix(new double[][] {{q}});
		
	}
	
	public void update(Matrix measurement_, double interval_, double reading, double movingVariance, double raw) {
		Matrix y = measurement_.minus(H.times(x));
		Matrix S = H.times(P).times(H.transpose()).plus(R);
		double valS = S.get(0, 0);
		Matrix invS = new Matrix(new double [][] {{1 / valS}});
		Matrix K = P.times(H.transpose()).times(invS);
		x.plusEquals(K.times(y));
		P = I.minus(K.times(H)).times(P).copy();
		F.set(0, 1, interval_);
		F.set(0, 2, 0.5 * interval_ * interval_);
		F.set(1, 2, interval_);
		double[][] qArray = 
			{
				{q / 20 * Math.pow(interval_, 5), q / 8 * Math.pow(interval_, 4), q / 6 * Math.pow(interval_, 3)},
				{q / 8 * Math.pow(interval_, 4), q / 3 * Math.pow(interval_, 3), q / 2 * Math.pow(interval_, 2)},
				{q / 6 * Math.pow(interval_, 3), q / 2 * Math.pow(interval_, 2), q * interval_}
			};
		Matrix Q = new Matrix(qArray);
		x = F.times(x).copy();
		P = F.times(P).times(F.transpose()).plus(Q).copy();
		String line = String.format(Locale.US, "%f,%f,%f,%f,%f,%f,%f,%f\n", x.get(0, 0), x.get(1, 0), x.get(2, 0), measurement_.get(0, 0), reading, movingVariance, raw, interval_);
		try {
			writer.append(line);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	public Matrix getState() {
		return x;
	}
	
	public void setVariance(double variance) {
		q = variance;
		R.set(0, 0, q);
		P.set(2, 2, q);
	}
	
	
	public void closeWriter() {
		try {
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
