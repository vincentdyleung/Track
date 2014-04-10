package info.vforvincent.track.app;

import info.vforvincent.track.R;
import info.vforvincent.track.listener.OnTrackStateUpdateListener;
import Jama.Matrix;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends FragmentActivity 
					implements 
					OnTrackStateUpdateListener {
	public static final String TAG = "Track";
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    		case R.id.actions_debug:
    			Intent intent = new Intent(this, DebugActivity.class);
    			startActivity(intent);
    		default:
    			return super.onOptionsItemSelected(item);
    	}
    }

	@Override
	public void onTrackStateUpdate(Matrix state, int rssi) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCalibrationStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCalibrationFinish() {
		// TODO Auto-generated method stub
		
	}

	
}
