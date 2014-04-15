package info.vforvincent.track.app;

import info.vforvincent.track.R;
import info.vforvincent.track.Track;
import info.vforvincent.track.app.ui.ParticlesView;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

public class MainActivity extends FragmentActivity {
	public static final String TAG = "Track";
	private Button startButton;
	private Button stopButton;
	private Track track;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ParticlesView particlesView = (ParticlesView) findViewById(R.id.particles_view);
		track = new Track("hkust", Environment.getExternalStorageDirectory() + "/wherami", this, this);
		particlesView.setTrack(track);
		startButton = (Button) findViewById(R.id.start_button);
		startButton.setOnClickListener(particlesView);
		stopButton = (Button) findViewById(R.id.stop_button);
		stopButton.setOnClickListener(particlesView);
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
	
}
