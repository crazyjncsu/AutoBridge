package com.autobridge.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.util.*

class Service : PersistentService(), DeviceSourceRuntime.Listener, DeviceTargetRuntime.Listener {
    private val timer = Timer()

    private val ssdpServer = SsdpServer(object : SsdpServer.Listener {

    })

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
            ContactClosureBoardSourceRuntime(
                    RuntimeParameters(
                            "7b7b88c4-740e-4341-885f-77a4a2bf1342",
                            JSONObject(),
                            JSONObject()
                    ),
                    this
            ),
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
                            JSONObject(mapOf("devices" to arrayOf(
                                    JSONObject(mapOf(
                                            "id" to "speechSynthesizer",
                                            "name" to "Android Speech Synthesizer",
                                            "configuration" to JSONObject()
                                    )),
                                    JSONObject(mapOf(
                                            "id" to "flashlight",
                                            "name" to "Android Light",
                                            "configuration" to JSONObject()
                                    )),
                                    JSONObject(mapOf(
                                            "id" to "illuminanceSensor",
                                            "name" to "Android Light Sensor",
                                            "configuration" to JSONObject(mapOf(
                                                    "reportIntervalMillisecondCount" to 60_000,
                                                    "reportPercentageChange" to 0.5
                                            ))
                                    )),
                                    JSONObject(mapOf(
                                            "id" to "soundPressureLevelSensor",
                                            "name" to "Android SPL Meter",
                                            "configuration" to JSONObject(mapOf(
                                                    "reportIntervalMillisecondCount" to 60_000,
                                                    "reportValueChange" to 10.0
                                            ))
                                    )),
                                    JSONObject(mapOf(
                                            "id" to "soundSensor",
                                            "name" to "Android Sound Sensor",
                                            "configuration" to JSONObject(mapOf(
                                                    "reportValueChange" to 1,
                                                    "threshold" to 50
                                            ))
                                    ))
                            ))),
                            JSONObject()
                    ),
                    this
            )
    )

    private val targetRuntimes = arrayOf(
            SmartThingsTargetRuntime(
                    RuntimeParameters(
                            "d2accd0f-eb6a-4393-bfe8-e380e8d857f9",
                            JSONObject(),
                            JSONObject(mapOf("authority" to "192.168.1.65:39500"))
                    ),
                    this
            )
    )

    private val runtimes = this.sourceRuntimes.asIterable<RuntimeBase>().plus(this.targetRuntimes).toList()

    private val sourceToTargetsMap = mapOf(
            this.sourceRuntimes[0] to arrayOf(this.targetRuntimes[0]),
            this.sourceRuntimes[1] to arrayOf(this.targetRuntimes[0]),
            this.sourceRuntimes[2] to arrayOf(this.targetRuntimes[0])
    )

    private val targetToSourcesMap = mapOf(
            this.targetRuntimes[0] to this.sourceRuntimes
    )

    override fun onCreate() {
        super.onCreate()
        this.ssdpServer.start();
        this.webServer.start()
        this.runtimes.forEach { it.startOrStop(true, this.applicationContext) }
        this.timer.schedule(object : TimerTask() {
            override fun run() {
                this@Service.sourceRuntimes
                        .forEach { it.startDiscoverDevices() }

                this@Service.targetRuntimes
                        .forEach { targetRuntime ->
                            targetRuntime
                                    .startSyncSources(this@Service.targetToSourcesMap[targetRuntime]!!.map { it.parameters.id })
                        }
            }
        }, 0, 3_600_000)
    }

    override fun onDestroy() {
        super.onDestroy()
        this.ssdpServer.stop()
        this.webServer.stop()
        this.runtimes.forEach { it.startOrStop(false, this.baseContext) }
        this.timer.cancel()
    }

    override fun onDeviceSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String) {
        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startDiscoverDevices() }
    }

    override fun onDeviceRefreshRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String) {
        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startDiscoverDeviceState(deviceID) }
    }

    override fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String) {
        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startSetDeviceState(deviceID, propertyName, propertyValue) }
    }

    override fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, propertyName: String, propertyValue: String) {
        this.sourceToTargetsMap[sourceRuntime]!!
                .forEach { it.startSyncDeviceState(sourceRuntime.parameters.id, deviceID, propertyName, propertyValue) }
    }

    override fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>) {
        Log.v("Bridge", "Discovered devices: $devices")
        this.sourceToTargetsMap[sourceRuntime]!!
                .forEach { it.startSyncSourceDevices(sourceRuntime.parameters.id, devices) }
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
