package com.autobridge.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.util.*
import android.net.wifi.WifiManager.MulticastLock
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager


class Service : PersistentService(), DeviceSourceRuntime.Listener, DeviceTargetRuntime.Listener {
    private val timer = Timer()

    private var multicastLock: MulticastLock? = null;

    private val ssdpServer = SsdpServer(object : SsdpServer.Listener {
        override fun onSearch(st: String): List<SsdpServer.Listener.SearchResponse> =
                this@Service.runtimes.map { SsdpServer.Listener.SearchResponse(st, UUID.fromString(it.parameters.id)) }
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
            UsbHidContactClosureBoardSourceRuntime(
                    RuntimeParameters(
                            "7b7b88c4-740e-4341-885f-77a4a2bf1342",
                            JSONObject(mapOf(
                                    "usbProductID" to 1503,
                                    "usbVendorID" to 5824,
                                    "devices" to arrayOf(
                                            JSONObject(mapOf(
                                                    "id" to "7110d9e3-7161-4aec-a080-9a989d91d107",
                                                    "type" to DeviceType.LIGHT.ocfDeviceType,
                                                    "name" to "Relay Light 4",
                                                    "configuration" to JSONObject(mapOf(
                                                            "contactID" to "R4"
                                                    ))
                                            )),
                                            JSONObject(mapOf(
                                                    "id" to "03f4559b-5436-4440-8506-6bcaa486cf48",
                                                    "type" to DeviceType.LIGHT.ocfDeviceType,
                                                    "name" to "Relay Light 3",
                                                    "configuration" to JSONObject(mapOf(
                                                            "contactID" to "R3"
                                                    ))
                                            )),
                                            JSONObject(mapOf(
                                                    "id" to "ec68af29-726a-41b0-bd87-c90e9d507301",
                                                    "type" to DeviceType.DOOR_OPENER.ocfDeviceType,
                                                    "name" to "Relay Door 1 and 2",
                                                    "configuration" to JSONObject(mapOf(
                                                            "openContactID" to "R1",
                                                            "closeContactID" to "R2",
                                                            "openDurationSeconds" to 4,
                                                            "closeDurationSeconds" to 4
                                                    ))
                                            ))
                                    ))),
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
                                                    "reportValueChange" to 30.0
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

        this.multicastLock = this.getSystemService(Context.WIFI_SERVICE).to<WifiManager>().createMulticastLock("default")
        this.multicastLock?.acquire()

        this.ssdpServer.start()
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

        this.multicastLock?.release();
    }

    override fun onDevicesSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String) {
        Log.v(TAG, "Received devices sync request; starting to discover devices state for source '$sourceID'...")

        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startDiscoverDevices() }
    }

    override fun onDeviceRefreshRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String) {
        Log.v(TAG, "Received device refresh request; starting to discover device state for device '$deviceID'...")

        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startDiscoverDeviceState(deviceID) }
    }

    override fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String) {
        Log.v(TAG, "Received device state change request; starting to set device '$deviceID' property '$propertyName' to '$propertyValue'...")

        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startSetDeviceState(deviceID, propertyName, propertyValue) }
    }

    override fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, deviceType: DeviceType, propertyName: String, propertyValue: String) {
        Log.v(TAG, "Discovered device '$deviceID' property '$propertyName' is '$propertyValue'; syncing...")

        this.sourceToTargetsMap[sourceRuntime]!!
                .forEach { it.startSyncDeviceState(sourceRuntime.parameters.id, deviceID, deviceType, propertyName, propertyValue) }
    }

    override fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>) {
        Log.v(TAG, "Discovered ${devices.count()} devices for runtime '$sourceRuntime'; syncing...")

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
