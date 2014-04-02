package info.vforvincent.track;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.os.Environment;

public class FileUtil {
	private static FileUtil instance;
	private FileWriter writer;
	
	private FileUtil() {
	}
	
	public static FileUtil getInstance() {
		if (instance == null) {
			instance = new FileUtil();
		}
		return instance;
	}
	
	public void writeLine(String line) {
		try {
			writer.append(line + "\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void close() {
		try {
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void open() {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			String directoryPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Track/";
			File directory = new File(directoryPath);
			if (!directory.exists()) {
				directory.mkdir();
			}
			int previousRunCount = directory.list().length;
			String fileName = "run_" + Integer.toString(previousRunCount + 1) + ".csv";
			File output = new File(directoryPath, fileName);
			try {
				writer = new FileWriter(output);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
