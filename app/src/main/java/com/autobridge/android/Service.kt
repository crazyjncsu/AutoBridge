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

class Service : PersistentService(), DeviceSourceRuntime.Listener, DeviceTargetRuntime.Listener {
    override fun onDeviceSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String) {
        // TODO
    }

    override fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String) {
        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.setDeviceState(deviceID, propertyName, propertyValue) }
    }

    override fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, propertyName: String, propertyValue: String) {
        this.sourceToTargetsMap[sourceRuntime]!!
                .forEach { it.syncDeviceState(sourceRuntime.parameters.id, deviceID, propertyName, propertyValue) };
    }

    override fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>) {
        Log.v("Bridge", "Discovered devices: $devices")
        this.sourceToTargetsMap[sourceRuntime]!!
                .forEach { it.syncDevices(sourceRuntime.parameters.id, devices) }
    }

    private val webServer: WebServer = WebServer();

    private val sources = arrayOf(
            MyQSourceRuntime(
                    RuntimeParameters(
                            "2345",
                            JSONObject(mapOf("username" to "jordanview@outlook.com", "password" to "Ncsu1ncsu")),
                            JSONObject()
                    ),
                    this
            )
    );

    private val targets = arrayOf(
            SmartThingsTargetRuntime(
                    RuntimeParameters(
                            "3242",
                            JSONObject(mapOf<String, String>()),
                            JSONObject(mapOf("authority" to "192.168.1.65:39500"))
                    ),
                    this
            )
    );

    private val runtimes = this.sources.asIterable<RuntimeBase>().plus(this.targets).toList();

    private val sourceToTargetsMap = mapOf(
            this.sources[0] to arrayOf(this.targets[0])
    );

    private val targetToSourcesMap = mapOf(
            this.targets[0] to arrayOf(this.sources[0])
    )

    override fun onCreate() {
        super.onCreate()

        this.webServer.start()

        this.runtimes.forEach { it.start() }
    }

    override fun onDestroy() {
        super.onDestroy()

        this.webServer.stop()

        this.runtimes.forEach { it.stop() }
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
                return NanoHTTPD.newFixedLengthResponse(
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
