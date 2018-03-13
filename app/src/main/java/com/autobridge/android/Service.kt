package com.autobridge.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.IBinder
import android.util.Log

import org.json.JSONObject

import fi.iki.elonen.NanoHTTPD
import java.util.*

class Service : PersistentService(), DeviceSourceRuntime.Listener, DeviceTargetRuntime.Listener {
    private val timer = Timer()

    private val webServer = object : WebServer(1035) {
        override fun processJsonRequest(requestObject: JSONObject): JSONObject {
            this@Service.targetRuntimes
                    .filter { it.parameters.id == requestObject.optString("targetID") }
                    .forEach { it.processMessage(requestObject.getJSONObject("message")) }

            this@Service.sourceRuntimes
                    .filter { it.parameters.id == requestObject.optString("sourceID") }
                    .forEach { it.processMessage(requestObject.getJSONObject("message")) }

            return JSONObject()
        }
    }

    private val sourceRuntimes = arrayOf(
            MyQSourceRuntime(
                    RuntimeParameters(
                            "169b0eae-b913-4685-a9ec-b780c58944f9",
                            JSONObject(mapOf("username" to "jordanview@outlook.com", "password" to "Ncsu1ncsu")),
                            JSONObject()
                    ),
                    this
            ),
            OnboardSourceRuntime(
                    RuntimeParameters(
                            "348e978d-287d-43bc-9862-44a1418b33ae",
                            JSONObject(mapOf("exposeDevices" to "")),
                            JSONObject()
                    ),
                    this
            )
    )

    private val targetRuntimes = arrayOf(
            SmartThingsTargetRuntime(
                    RuntimeParameters(
                            "d2accd0f-eb6a-4393-bfe8-e380e8d857f9",
                            JSONObject(mapOf<String, String>()),
                            JSONObject(mapOf("authority" to "192.168.1.65:39500"))
                    ),
                    this
            )
    )

    private val runtimes = this.sourceRuntimes.asIterable<RuntimeBase>().plus(this.targetRuntimes).toList()

    private val sourceToTargetsMap = mapOf(
            this.sourceRuntimes[0] to arrayOf(this.targetRuntimes[0]),
            this.sourceRuntimes[1] to arrayOf(this.targetRuntimes[0])
    )

    private val targetToSourcesMap = mapOf(
            this.targetRuntimes[0] to this.sourceRuntimes
    )

    override fun onCreate() {
        super.onCreate()
        this.webServer.start()
        this.runtimes.forEach { it.startOrStop(true, this.baseContext) }
        this.sourceRuntimes.forEach { it.startDiscoverDevices() }
        this.timer.schedule(object : TimerTask() {
            override fun run() {
                this@Service.sourceRuntimes
                        .forEach { it.startDiscoverDevices() }

                this@Service.targetRuntimes
                        .forEach { targetRuntime ->
                            targetRuntime
                                    .syncSources(this@Service.targetToSourcesMap[targetRuntime]!!.map { it.parameters.id })
                        }
            }
        }, 0, 3_600_000)
    }

    override fun onDestroy() {
        super.onDestroy()
        this.webServer.stop()
        this.runtimes.forEach { it.startOrStop(false, this.baseContext) }
        this.timer.cancel()
    }

    override fun onDeviceSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String) {
        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startDiscoverDevices() }
    }

    override fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String) {
        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startSetDeviceState(deviceID, propertyName, propertyValue) }
    }

    override fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, propertyName: String, propertyValue: String) {
        this.sourceToTargetsMap[sourceRuntime]!!
                .forEach { it.syncDeviceState(sourceRuntime.parameters.id, deviceID, propertyName, propertyValue) }
    }

    override fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>) {
        Log.v("Bridge", "Discovered devices: $devices")
        this.sourceToTargetsMap[sourceRuntime]!!
                .forEach { it.syncSourceDevices(sourceRuntime.parameters.id, devices) }
    }
}

@SuppressLint("Registered")
open class PersistentService : android.app.Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return android.app.Service.START_STICKY
    }
}

abstract class WebServer(port: Int) : NanoHTTPD(port) {
    abstract fun processJsonRequest(requestObject: JSONObject): JSONObject

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        try {
            // this whole parsing with the parseBody is real crap API
            // as we tried to just use the input stream, but had issues with sockets over-reading, etc
            val map = HashMap<String, String>()
            session.parseBody(map)

            val requestObject = JSONObject(map["postData"])
            val responseObject = this.processJsonRequest(requestObject)

            return NanoHTTPD.newFixedLengthResponse(responseObject.toString())
        } catch (ex: Exception) {
            Log.e("WebServer", ex.message, ex)

            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "application/json",
                    JSONObject(mapOf(
                            "error" to ex.javaClass.name,
                            "message" to ex.message,
                            "detail" to ex.toString()
                    )).toString()
            )
        }
    }
}
