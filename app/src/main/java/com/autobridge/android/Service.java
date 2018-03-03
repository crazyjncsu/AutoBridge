package com.autobridge.android;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class Service extends android.app.Service {
    WebServer webServer;
    TextToSpeech textToSpeech;
    MediaRecorder mediaRecorder;
    Sensor lightSensor;
    float lightSensorValue;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Device.Companion.createDevices();

        this.textToSpeech = new TextToSpeech(this, null);
        this.textToSpeech.setSpeechRate(0.9f);

        SensorManager sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

        this.lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        //this.lightSensor

        sensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }

            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                Service.this.lightSensorValue = sensorEvent.values[0];

                Log.i("LIGHT", "VAL: " + Service.this.lightSensorValue);
            }
        }, this.lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

        Sensor linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        Log.i("ACCELL_OBJ", "VAL: " + linearAccelerometer);

        sensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }

            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                Log.i("ACCELL", "VAL: " + sensorEvent.values[0]);
            }
        }, linearAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);


        try {
            this.mediaRecorder = new MediaRecorder();
            this.mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            this.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            this.mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            this.mediaRecorder.setOutputFile("/dev/null");
            this.mediaRecorder.prepare();
            this.mediaRecorder.start();
        } catch (Exception ex) {
            Log.e("MEdiaREcoder", ex.getMessage());
        }

        try {
            this.webServer = new WebServer();
            this.webServer.start();
            Log.i("Service", "Started");
        } catch (IOException ex) {
            // don't care?
            Log.e("Service", ex.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.webServer.stop();
    }

    private void speak(String message) {
        this.textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null);
    }

    private class WebServer extends NanoHTTPD {
        public WebServer() {
            super(1035);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String message = session.getParms().get("Message");

            // LANNOUNCER is such a POS
            if (message == null) {
                try {
                    InputStream inputStream = session.getInputStream();
                    inputStream.reset();
                    message = new BufferedReader(new InputStreamReader(inputStream)).readLine().replaceFirst("GET /&SPEAK=(.*)&@DONE@ HTTP/1.1", "$1");
                } catch (Exception ex) {
                }
            }

            Service.this.speak(message);

            double max = 20 * Math.log10((double) Service.this.mediaRecorder.getMaxAmplitude() / 32767);
            Log.i("mediarecorder", "amplitude: " + max);

            return NanoHTTPD.newFixedLengthResponse("OK");
        }
    }

    private static class ServiceRequest {
        String responseUrl;
        DeviceRequest[] deviceRequests;
    }

    private static class DeviceRequest {
        String deviceID;
        DeviceCommand[] commandsToExecute;
        String[] propertiesToMonitor;

    }

    private static class DeviceCommand {
        String name;
        String[] arguments;
    }

    private static class PropertyMonitor {
        String name;
        double reportChangedByValue;
        double reportChangedByPercent;
    }

    private static class DerivedPropertyMonitor {
        String name;
        double derivedFromValue;
        double changeDurationMilliseconds;
    }

    class DerivedPropertyType {
        GreaterThanValue,
        LessThanValue,
        ChangeByPercentageOverDuration,
        ChangeByValueOverDuration,
    }
}
