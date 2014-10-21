/*
 * Objective: Create a pedometer that counts steps. This particular app requires the user to calibrate
 * 			  their steps by first clearing the data then taking 5 to 10 steps and pressing the "Calibrate" button.
 */

package com.hamzaazeem.pedometer;

import java.util.Arrays;

import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

//Initiate project in MainActivity
public class MainActivity extends ActionBarActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	//Define and instantiate Accelerometer + graph + button to clear data
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		//Define Sensor Event Listeners
		SensorEventListener accelListener;

		@SuppressLint("InlinedApi")
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,	false);
			LinearLayout layout = (LinearLayout) rootView.findViewById(R.id.linLayout);
			layout.setOrientation(LinearLayout.VERTICAL);
			LineGraphView graph;

			graph = new LineGraphView(rootView.getContext(), 100, Arrays.asList("x", "y", "z")); //Define graph

			TextView accel = new TextView(rootView.getContext()); //Define Accelerometer placeholder

			Button calibrate = new Button(rootView.getContext()); //Define "Calibrate" button
			calibrate.setText("Calibrate");

			Button clearSteps = new Button(rootView.getContext()); //Define "Clear Steps" button
			clearSteps.setText("Clear Steps");

			Button clearAll = new Button(rootView.getContext()); //Define "Clear All" button
			clearAll.setText("Clear All");

			//Add objects created above to layout
			layout.addView(graph);
			graph.setVisibility(View.VISIBLE); //Make graph visible

			layout.addView(accel);
			layout.addView(calibrate);
			layout.addView(clearSteps);
			layout.addView(clearAll);

			//Create accelerometer
			SensorManager sensorManager = (SensorManager) rootView.getContext().getSystemService(SENSOR_SERVICE);
			Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION); //Define Accelerometer


			//Instantiate listener for accelerometer and register it
			accelListener = new AccelerometerListener(accel, graph);
			sensorManager.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_FASTEST);

			//Create listener for "Calibrate" button click to calibrate user's steps
			calibrate.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                	//Call object calibrate() method by casting to AllSensorsEventListener (subclass of SensorEventListener)
                	((AccelerometerListener) accelListener).calibrate();
                }
			});

			//Create listener for "Clear Steps" button click to reset step counter
			clearSteps.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                	//Call object reset() method by casting to AllSensorsEventListener (subclass of SensorEventListener)
                	((AccelerometerListener) accelListener).reset(1); //Pass in '1' to only clear steps
                }
			});

			//Create listener for "Clear All" button click to reset all data values
			clearAll.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                	//Call object reset() method by casting to AllSensorsEventListener (subclass of SensorEventListener)
                	((AccelerometerListener) accelListener).reset(0); //Pass in '0' to clear ALL data
                }
			});

			return rootView;
		}
	}
}

//Create subclass of SensorEventListener
class AccelerometerListener implements SensorEventListener {
	TextView output;
	LineGraphView graph;
	private float[] smoothedAccel = new float[3]; //Smoothed/Corrected values
	private int stepCount = 0; //Step counter
	private double maxVal = 0; //Calibrated maximum value
	private double minVal = 0; //Calibrated mininum value
	private boolean calibrated = false;
	int mode = 0;

	private double[] maxValues = new double[3]; //Arrays to hold max (record) values for each sensor
	private double[] minValues = new double[3]; //Arrays to hold min (record) values for each sensor

	public AccelerometerListener(TextView outputView, LineGraphView graphView){
		output = outputView;
		graph = graphView;
		smoothedAccel[2] = 0;
	}

	public void onAccuracyChanged(Sensor s, int i) {}
	public void onSensorChanged(SensorEvent se) {
		double previousValue = smoothedAccel[2];
		smoothedAccel[2] += (se.values[2] - smoothedAccel[2]) / 40;
		maxValues = getMaxValue(smoothedAccel, maxValues); //Get max values for passed in Sensor Event values
		minValues = getMinValue(smoothedAccel, minValues); //Get max values for passed in Sensor Event values
		graph.addPoint(smoothedAccel); //Plot Sensor Event values  for Accelerometer to graph

		switch (mode) {
		case 0: //Wait for step
			if (calibrated && smoothedAccel[2] >= 0.25*maxVal && !decreasing(smoothedAccel[2], previousValue)) {
				mode = 1;
			}
			break;
		case 1: //Detect for peak
			if(smoothedAccel[2] > 1.5*maxVal) {
				mode = 0;
				break;
			}
			if(smoothedAccel[2] >= 0.5*maxVal && smoothedAccel[2] <= 1.5*maxVal) {
				mode = 2;
			}
			break;
		case 2: //Detect for decline near minimum value
			if(smoothedAccel[2] < 1.3*minVal) {
				mode = 0;
				break;
			}
			if(smoothedAccel[2] <= 0.30*minVal && smoothedAccel[2] >= 1.2*minVal) {
				stepCount++;
				mode = 0; //Reset to state 1
			}
			break;
		default: break;
		}

		displayText("Accelerometer", "m/s^2", se.values, maxValues, minValues, mode, stepCount); //Display Accelerometer data
	}

	public double[] getMaxValue(float[] currVal, double[] maxVal){
		for(int i=0; i<3; i++) {
			if(Math.abs(currVal[i]) > maxVal[i]) { //Compare absolute values for current values and old max values
				maxVal[i] = currVal[i];
			}
		}
		return maxVal;
	}

	public double[] getMinValue(float[] currVal, double[] minVal){
		for(int i=0; i<3; i++) {
			if(currVal[i] < minVal[i]) { //Compare current values and min values to see which value is smaller
				minVal[i] = currVal[i];
			}
		}
		return minVal;
	}

	//Detect if graph is declining if difference between current and previous value is negative
	private boolean decreasing(double currVal, double preVal) {
		return (currVal - preVal) < 0;
	}

	public void displayText(String sensorName, String units, float[] sensorVal, double[] maxVal, double[] minVal, int mode, int numSteps) {
			//Print all relevant data to screen
			String display = String.format("\nCurrent %s Value (%s): \nX: %.3f \nY: %.3f \nZ: %.3f\n", sensorName, units, sensorVal[0], sensorVal[1], sensorVal[2]);
			display += String.format("Max Value (%s): \nZ: %.3f\n", units, maxVal[2]); //Maximum Value
			display += String.format("Min Value (%s): \nZ: %.3f\n", units, minVal[2]); //Minimum Value
			display += String.format("Mode: %s\n", mode, numSteps);
			display += String.format("Steps: %s\n", numSteps); //Step counter
			display += String.format("Calibrated: %s\n", calibrated); //Calibration state: true OR false
			output.setText(display);
	}

	public void calibrate() {
			maxVal = maxValues[2];
			minVal = minValues[2];
			calibrated = true;
	}

	//Purge graph and reset ALL current data values
	public void reset(int clearSteps) {
		if(clearSteps == 1){
			stepCount = 0;
		} else {
			graph.purge();
			maxValues = new double[3];
			minValues = new double[3];
			maxVal = 0;
			minVal = 0;
			mode = 0;
			stepCount = 0;
			calibrated = false;
		}
	}

}
