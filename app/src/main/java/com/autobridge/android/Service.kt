package com.autobridge.android

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.IBinder
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log

import org.json.JSONObject

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

import fi.iki.elonen.NanoHTTPD

class Service : PersistentService() {
    private val webServer: WebServer = WebServer();

    var myq: MyQSourceRuntime = MyQSourceRuntime(RuntimeParameters("asdf", JSONObject(mapOf("username" to "jordanview@outlook.com", "password" to "Ncsu1ncsu")), JSONObject()), this);


    override fun onCreate() {
        super.onCreate()

        try {
            this.webServer.start()
            Log.i("Service", "Started")
        } catch (ex: IOException) {
            // don't care?
            Log.e("Service", ex.message)
        }

        this.myq.start();
    }

    override fun onDestroy() {
        super.onDestroy()

        this.webServer.stop()
    }

    private inner class WebServer : NanoHTTPD(1035) {
        override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            try {
                val requestObject = JSONObject(InputStreamReader(session.inputStream).readText());

                val runtimeID = requestObject.getString("runtimeID");
                val commandName = requestObject.getString("commandName");
                var commandArgument = requestObject.get("commandArgument");

                // TODO dispatch

                return NanoHTTPD.newFixedLengthResponse("");
            } catch (ex: Exception) {
                return fi.iki.elonen.NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.INTERNAL_ERROR,
                        "application/json",
                        JSONObject(mapOf(
                                "message" to ex.message
                        )).toString()
                );
            }
        }
    }
}

open class PersistentService : android.app.Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null;
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return android.app.Service.START_STICKY;
    }
}
