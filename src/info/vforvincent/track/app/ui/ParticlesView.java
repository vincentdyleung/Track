package info.vforvincent.track.app.ui;

import info.vforvincent.track.R;
import info.vforvincent.track.Track;
import info.vforvincent.track.app.FileUtil;
import info.vforvincent.track.app.MainActivity;
import info.vforvincent.track.ins.ParticleFilter;
import info.vforvincent.track.ins.ParticleFilter.Particle;
import info.vforvincent.track.listener.OnTrackStateUpdateListener;

import java.util.List;

import Jama.Matrix;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;

public class ParticlesView extends SurfaceView
							implements SurfaceHolder.Callback, OnClickListener {

	private Track track;
	private static final int DOT_RADIUS = 10;
	private static final int PARTICLE_RADIUS = 5;
	
	class ParticlesViewThread extends Thread 
									implements OnTrackStateUpdateListener, OnGestureListener {

		private SurfaceHolder surfaceHolder;
		private Context context;
		private ParticlesView view;
		private Bitmap mapImage;
		private Drawable dot;
		private Drawable redDot;
		private int previousOffsetX;
		
		public ParticlesViewThread(SurfaceHolder surfaceHolder, Context context, ParticlesView view) {
			this.view = view;
			this.surfaceHolder = surfaceHolder;
			this.context = context;
			mapImage = BitmapFactory.decodeResource(this.context.getResources(), R.drawable.map);
			dot = this.context.getResources().getDrawable(R.drawable.dot);
			redDot = this.context.getResources().getDrawable(R.drawable.dot_red);
			redDot.setAlpha(200);
			dot.setAlpha(200);
		}
		
		private void draw(Canvas canvas, int offsetX, List<Particle> particles, Matrix estimate) {
			canvas.drawBitmap(mapImage, -offsetX, 0, null);
			int leftBound = offsetX;
			int rightBound = offsetX + canvas.getWidth();
			if (particles != null) {
				for (Particle particle : particles) {
					if (true) {
						Log.d(MainActivity.TAG, "weight: " + Double.toString(particle.getWeight()));
						int particleX = (int) Math.round(particle.getState().get(0, 0));
						int particleY = (int) Math.round(particle.getState().get(1, 0));
						int pLeft = particleX - PARTICLE_RADIUS;
						int pRight = particleX + PARTICLE_RADIUS;
						int pTop = particleY - PARTICLE_RADIUS;
						int pBottom = particleY + PARTICLE_RADIUS;
						if (pLeft >= leftBound && pRight <= rightBound) {
							Rect pBounds = new Rect(pLeft - offsetX, pTop, pRight - offsetX, pBottom);
							ShapeDrawable particleDrawable = new ShapeDrawable(new OvalShape());
							particleDrawable.setBounds(pBounds);
							particleDrawable.draw(canvas);
							Log.d(MainActivity.TAG, "Drawn particle at " + pBounds.toShortString());
						}
					}
				}
			}
			if (estimate != null) {
				drawDot(redDot, canvas, estimate, offsetX, leftBound, rightBound);
			}
			surfaceHolder.unlockCanvasAndPost(canvas);
		}
		
		private void drawDot(Drawable dotDrawable, Canvas canvas, Matrix dot, int offsetX, int leftBound, int rightBound) {
			int dotX = (int) Math.round(dot.get(0, 0));
			int dotY = (int) Math.round(dot.get(1, 0));
			int left = dotX - DOT_RADIUS;
			int right = dotX + DOT_RADIUS;
			int top = dotY - DOT_RADIUS;
			int bottom = dotY + DOT_RADIUS;
			if (left >= leftBound && right <= rightBound) {
				Rect bounds = new Rect(left - offsetX, top, right - offsetX, bottom);
				dotDrawable.setBounds(bounds);
				dotDrawable.draw(canvas);
			}
		}
		@Override 
		public void run() {
			final LayoutParams params = getLayoutParams();
			params.height = mapImage.getHeight();
			Activity activity = (Activity) context;
			activity.runOnUiThread(new Runnable() {

				@Override
				public void run() {
					// TODO Auto-generated method stub
					view.setLayoutParams(params);
				}
				
			});
			track.setOnTrackStateUpdateListener(this);
			track.start();
			Log.d(MainActivity.TAG, "thread started");
			Canvas canvas = surfaceHolder.lockCanvas();
			draw(canvas, 0, null, null);
		}
		
		@Override
		public void onTrackStateUpdate(Matrix state, 
				List<Particle> particles, int rssi) {
			// TODO Auto-generated method stub
			Canvas canvas = surfaceHolder.lockCanvas();
			draw(canvas, previousOffsetX, particles, state);
		}
		
		@Override
		public void onCalibrationStart() {
			// TODO Auto-generated method stub
		
		}
		
		@Override
		public void onCalibrationFinish() {
			// TODO Auto-generated method stub
		
		}

		@Override
		public boolean onDown(MotionEvent e) {
			// TODO Auto-generated method stub
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void onLongPress(MotionEvent e) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			// TODO Auto-generated method stub
			Canvas canvas = surfaceHolder.lockCanvas();
			previousOffsetX += Math.round(distanceX);
			int maxOffset = ParticleFilter.BOUND_X - canvas.getWidth();
			if (previousOffsetX < 0) {
				previousOffsetX = 0;
			} else if (previousOffsetX > maxOffset) {
				previousOffsetX = maxOffset;
			}
			draw(canvas, previousOffsetX, null, null);
			return true;
		}

		@Override
		public void onShowPress(MotionEvent e) {
			// TODO Auto-generated method stub			
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void onParticleFilterInitialize(List<Particle> particles) {
			// TODO Auto-generated method stub
			Canvas canvas = surfaceHolder.lockCanvas();
			draw(canvas, previousOffsetX, particles, null);
		}
		
	}

	private ParticlesViewThread thread;
	private GestureDetectorCompat detector;
	
	public ParticlesView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
		thread = new ParticlesViewThread(holder, context, this);
		detector = new GestureDetectorCompat(context, thread);
	}

	public void setTrack(Track track) {
		this.track = track;
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		stopThread();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if (v.getId() == R.id.start_button) {
			FileUtil.getInstance().open();
			FileUtil.getInstance().writeLine("wifiDistance,kalmanDistance,ratio,deltaX,deltaY,landmarkX,landmarkY");
			if (thread.isAlive() == false) {
				thread.start();
			}
		} else if (v.getId() == R.id.stop_button) {
			FileUtil.getInstance().close();
			track.stop();

		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return super.onTouchEvent(e) || detector.onTouchEvent(e);
	}
	
	private void stopThread() {
		boolean retry = true;
		while (retry == true) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
				retry = true;
				e.printStackTrace();
			}
		}
	}

}
