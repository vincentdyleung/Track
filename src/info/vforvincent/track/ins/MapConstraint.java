package info.vforvincent.track.ins;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import yl.demo.rock.lbs.datatype.PointF;
import android.graphics.Rect;
import android.os.Environment;

public class MapConstraint {
	
	private List<Rect> constraintRects;
	private static MapConstraint instance;
	
	private MapConstraint() {
		String filePath = Environment.getExternalStorageDirectory() + "/Track/mapConstraint.txt";
		constraintRects = new LinkedList<Rect>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(filePath));
			String line;
			try {
				while ((line = reader.readLine()) != null && line.length() > 0) {
					String[] valStrings = line.split(",");
					int[] corners = new int[4];
					for(int i = 0; i < 4; i++) {
						int val = Integer.parseInt(valStrings[i]);
						corners[i] = val;
					}
					Rect rect = new Rect(corners[0], corners[1], corners[2], corners[3]);
					constraintRects.add(rect);
				}
				reader.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static MapConstraint getInstance() {
		if (instance == null) {
			instance = new MapConstraint();
		}
		return instance;
	}
	
	public boolean isBetweenWalls(PointF point) {
		for (Rect rect : constraintRects) {
			if (isInsideRect(rect, point) == true) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isInsideRect(Rect rect, PointF point) {
		int pointX = Math.round(point.x);
		int pointY = Math.round(point.y);
		if (pointX > rect.left && pointX < rect.right && pointY > rect.top && pointY < rect.bottom) {
			return true;
		} else {
			return false;
		}
	}
	
}
