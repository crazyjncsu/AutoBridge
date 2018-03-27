package com.autobridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.util.*


class Service : PersistentService(), NetworkDiscoverer.Listener, BridgeRuntime.Listener {
    private var multicastLock: MulticastLock? = null

    private lateinit var bridgeRuntime: BridgeRuntime;

    private val ssdpServer = SsdpServer(object : SsdpServer.Listener {
        override fun onSearch(st: String): List<SsdpServer.Listener.SearchResponse> =
                this@Service.bridgeRuntime.getRuntimeIDs().map { SsdpServer.Listener.SearchResponse(st, UUID.fromString(it)) }
    })

    private val networkDiscoverer = NetworkDiscoverer(this)

    private var lastNetworkDiscoveryTickCount = 0L

    private val webServer = object : WebServer(1035) {
        override fun processJsonRequest(requestObject: JSONObject): JSONObject {
            this@Service.bridgeRuntime.processMessage(
                    requestObject.optString("sourceID", requestObject.optString("targetID")),
                    requestObject.getJSONObject("message")
            )

            return JSONObject()
        }
    }

    private fun tryGetJsonObjectFromFile(fileName: String) =
            File(this.filesDir, fileName).let { if (it.exists()) JSONObject(it.readText()) else JSONObject() }

    override fun onCreate() {
        super.onCreate()

        this.multicastLock = this.getSystemService(Context.WIFI_SERVICE).to<WifiManager>().createMulticastLock("default")
        this.multicastLock?.acquire()

        this.ssdpServer.start()

        this.webServer.start()

        this.bridgeRuntime = BridgeRuntime(
                RuntimeParameters(
                        "",
                        this.tryGetJsonObjectFromFile(com.autobridge.CONFIGURATION_FILE_NAME),
                        this.tryGetJsonObjectFromFile(com.autobridge.STATE_FILE_NAME)
                ),
                this
        )

        this.bridgeRuntime.startOrStop(true, this.baseContext)
    }

    override fun onDestroy() {
        super.onDestroy()

        this.ssdpServer.stop()

        this.webServer.stop()

        this.bridgeRuntime.startOrStop(false, this.baseContext)

        this.multicastLock?.release()

        // HMMM
        //File(this.filesDir, CONFIGURATION_FILE_NAME).writeText(this.bridgeRuntime.parameters.configuration.toString(4))
        File(this.filesDir, STATE_FILE_NAME).writeText(this.bridgeRuntime.parameters.state.toString(4))
    }

    override fun onNetworkDiscoveryNeeded() {
        synchronized(this.networkDiscoverer) {
            val ticksUntilDiscovery = this.lastNetworkDiscoveryTickCount - System.currentTimeMillis() + 300_000

            if (ticksUntilDiscovery <= 0) {
                this.networkDiscoverer.startDiscovery()
                this.lastNetworkDiscoveryTickCount = System.currentTimeMillis()
            }
        }
    }

    override fun onMacAddressDiscovered(ipAddress: InetAddress, macAddress: ByteArray) =
            this.bridgeRuntime.processMacAddressDiscovered(ipAddress, macAddress)
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
