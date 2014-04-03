package info.vforvincent.track.app.ui;

import info.vforvincent.track.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;

public class DistanceDialogFragment extends DialogFragment {
	
	public static final String DISTANCE = "distance";
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_numberpicker, null);
		final NumberPicker distancePicker = (NumberPicker) view.findViewById(R.id.distance_picker);
		distancePicker.setMinValue(0);
		distancePicker.setMaxValue(100);
		builder.setView(view)
		.setPositiveButton("Set", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				getActivity().getPreferences(Context.MODE_PRIVATE).edit()
				.putInt(DISTANCE, distancePicker.getValue())
				.commit();
			}
			
		})
		.setNegativeButton("Cancel", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				dismiss();
			}
			
		});
		return builder.create();
	}

}
