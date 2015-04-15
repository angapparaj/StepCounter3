package com.example.angappak.myapplication;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.androidplot.xy.*;

public class MainActivity extends Activity implements SensorEventListener {

    private static final int HISTORY_SIZE = 500;            // number of points to plot in history
    private static final int SMOOTHING_RANGE = 10;
    private static final int PEAK_THRESHOLD = 2;
    private int WINDOW_SIZE = 200;
    private int WINDOW_OVERLAP = 10;

    private SensorManager sensorMgr = null;
    private Sensor accelerometerSensor = null;
    private Sensor gyroSensor = null;
    private XYPlot accelHistoryPlot = null;
    float accelerometerRange = 0;
    float gyroRange = 0;

    private TextView stepsTextView;
    private Button btnPause;
    private Button btnReset;

    private SimpleXYSeries rawDataSeries = null;
    private SimpleXYSeries smoothedDataSeries = null;
    private SimpleXYSeries demeanedDataSeries = null;

    private boolean paused = false;

    private int sensorAxisIndex = 1; // y axis

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // register for orientation sensor events:
        sensorMgr = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);

        for (Sensor sensor : sensorMgr.getSensorList(Sensor.TYPE_ACCELEROMETER)) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelerometerSensor = sensor;
                accelerometerRange = accelerometerSensor.getMaximumRange();
            }
        }

        for (Sensor sensor : sensorMgr.getSensorList(Sensor.TYPE_GYROSCOPE)) {
            if(sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                gyroSensor = sensor;
                gyroRange = gyroSensor.getMaximumRange();
            }
        }

        // if we can't access the accelerometer or gyro sensor then exit:
        if (accelerometerSensor == null) {
            System.out.println("Failed to attach to accelerometer.");
            cleanup();
        }

        if (gyroSensor == null) {
            System.out.println("Failed to attach to gyro sensor.");
            cleanup();
        }

        // setup the accelerometer plot:
        accelHistoryPlot = (XYPlot) findViewById(R.id.accelrometerHistoryPlot);

        rawDataSeries = new SimpleXYSeries("Raw Signal");
        rawDataSeries.useImplicitXVals();
        smoothedDataSeries = new SimpleXYSeries("Smoothed Signal");
        smoothedDataSeries.useImplicitXVals();
        demeanedDataSeries = new SimpleXYSeries("Demeaned Signal");
        demeanedDataSeries.useImplicitXVals();

        accelHistoryPlot.setRangeBoundaries(accelerometerRange * -1, accelerometerRange, BoundaryMode.FIXED);
        accelHistoryPlot.setDomainBoundaries(0, 500, BoundaryMode.FIXED);

        accelHistoryPlot.addSeries(rawDataSeries, new LineAndPointFormatter(Color.rgb(100, 100, 200), null, null, null));
        accelHistoryPlot.addSeries(smoothedDataSeries, new LineAndPointFormatter(Color.rgb(100, 200, 100), null,null, null));
        accelHistoryPlot.addSeries(demeanedDataSeries, new LineAndPointFormatter(Color.rgb(200, 100, 100), null, null,null));
        accelHistoryPlot.setDomainStepValue(5);
        accelHistoryPlot.setTicksPerRangeLabel(3);
        accelHistoryPlot.setDomainLabel("Acceleration");
        accelHistoryPlot.getDomainLabelWidget().pack();
        accelHistoryPlot.setRangeLabel("Time)");
        accelHistoryPlot.getRangeLabelWidget().pack();

        stepsTextView = (TextView) findViewById(R.id.textViewLog);
        btnPause = (Button) findViewById(R.id.btnPause);
        btnReset = (Button) findViewById(R.id.btnReset);

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepCount = 0;
                stepsTextView.setText("0");
            }
        });

        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(paused)
                {
                    paused = false;
                    btnPause.setText("Pause");
                }
                else {
                    paused = true;
                    btnPause.setText("Resume");
                }
            }
        });

        sensorMgr.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private void cleanup() {
        // unregister with the orientation sensor before exiting:
        sensorMgr.unregisterListener(this);
        finish();
    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radioButtonX:
                if (checked)
                    sensorAxisIndex = 0;
                    break;
            case R.id.radioButtonY:
                if (checked)
                    sensorAxisIndex = 1;
                    break;

            case R.id.radioButtonZ:
                if (checked)
                    sensorAxisIndex = 2;
                    break;
        }
    }

    int currentWindow = 0;
    int runningSum = 0;
    float[] windowValues = new float[WINDOW_SIZE];

    int stepCount = 0;

    // Called whenever a new orSensor reading is taken.
    // new comment
    @Override
    public synchronized void onSensorChanged(SensorEvent sensorEvent) {

        if(paused) {
            return;
        }

        // get rid the oldest sample in history:
        if (rawDataSeries.size() > HISTORY_SIZE) {
            rawDataSeries.removeFirst();
            smoothedDataSeries.removeFirst();
            demeanedDataSeries.removeFirst();
        }

        rawDataSeries.addLast(null, sensorEvent.values[sensorAxisIndex]);

        if(smoothedDataSeries.size() < SMOOTHING_RANGE)
        {
            smoothedDataSeries.addLast(null, sensorEvent.values[sensorAxisIndex]);

            //accelXSeries.addLast(null, sensorEvent.values[1]);
            runningSum+=sensorEvent.values[1];
        }
        else
        {
            float sum = 0;
            for(int i=rawDataSeries.size(); i > rawDataSeries.size() - SMOOTHING_RANGE; i--)
            {
                sum += rawDataSeries.getY(i-1).floatValue();
            }

            float smoothedAvg = sum / SMOOTHING_RANGE;
            runningSum+=smoothedAvg;
            windowValues[currentWindow] = smoothedAvg;

           // smoothedZSeries.addLast(null, smoothedAvg);
            smoothedDataSeries.addLast(null, smoothedAvg);
        }

        currentWindow++;

        if(currentWindow>= WINDOW_SIZE)
        {
            // make calculations for this window

            // Demean the data
            float mean = runningSum / WINDOW_SIZE;

            float prevSlope = 0;
            float currentSlope = 0;
            boolean zeroCrossed = false;
            float zeroCrossedIndex = 0;
            for(int i=0; i< WINDOW_SIZE; i++)
            {

                float demeanedValue = windowValues[i] - mean;
                windowValues[i] = demeanedValue;

                if(i>0) {

                    // Check for zero crossing
                    if(windowValues[i-1]<=0 && windowValues[i]>0)
                    {
                        zeroCrossed = true;
                        zeroCrossedIndex = i;
                    }

                    prevSlope = currentSlope;
                    currentSlope = windowValues[i] - windowValues[i-1];

                    //Now detect peaks by checking for change in slope from positive to negative
                    // Also use a threshold to look for peaks more than certain value.
                    // ZeroCrossed check ensures that we only count one peak after every zero crossing.
                    if(currentSlope < 0 && prevSlope>0 && windowValues[i-1] > PEAK_THRESHOLD && zeroCrossed)
                    {
                        // Check to ensure that we have a sharper peak. So make sure there is less than 100 ticks between peak and last zero crossing. Approx 1 sec at 100 hz
                        if(i-zeroCrossedIndex < 100) {
                            zeroCrossed = false;
                            stepCount++;
                            stepsTextView.setText(Integer.toString(stepCount));
                        }
                    }
                 }
                demeanedDataSeries.addLast(null, demeanedValue);
            }

            // reset window back to 0
            currentWindow = 0;
            runningSum = 0;
        }

        // redraw the Plots:
        accelHistoryPlot.redraw();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // Not interested in this event
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
