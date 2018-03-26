package com.autobridge.android

import android.content.Context
import android.util.Log
import com.autobridge.android.sources.DeviceSourceRuntime
import com.autobridge.android.targets.DeviceTargetRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.*

class DeviceDefinition(val id: String, val type: DeviceType, val name: String)

open class RuntimeParameters(val id: String, val configuration: JSONObject, val state: JSONObject)

abstract class RuntimeBase(val parameters: RuntimeParameters) {
    open fun startOrStop(startOrStop: Boolean, context: Context) {}
    open fun processMessage(message: JSONObject) {}
    open fun processMacAddressDiscovered(ipAddress: InetAddress, macAddress: ByteArray) {}

    protected fun getOrCreateState(key: String): JSONObject {
        var state = this.parameters.state.optJSONObject(key)

        if (state == null) {
            state = JSONObject()
            this.parameters.state.put(key, state)
        }

        return state
    }
}

class BridgeRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters), DeviceSourceRuntime.Listener, DeviceTargetRuntime.Listener {
    private val timer = Timer()
    private val sourceRuntimes = this.createRuntimes<DeviceSourceRuntime, DeviceSourceRuntime.Listener>("sources", "sourceID")
    private val targetRuntimes = this.createRuntimes<DeviceTargetRuntime, DeviceTargetRuntime.Listener>("targets", "targetID")
    private val runtimes = sourceRuntimes.plus(targetRuntimes).toList()
    private val bridges = (this.parameters.configuration.optJSONArray("bridges") ?: JSONArray())
            .toJSONObjectSequence()
            .map { Pair(it.getString("sourceID"), it.getString("targetID")) }
            .toSet()

    private val sourceToTargetsMap = this.sourceRuntimes
            .associate { source -> Pair(source, this.targetRuntimes.filter { this.bridges.contains(Pair(source.parameters.id, it.parameters.id)) }.toList()) }

    private val targetToSourcesMap = this.targetRuntimes
            .associate { target -> Pair(target, this.sourceRuntimes.filter { this.bridges.contains(Pair(it.parameters.id, target.parameters.id)) }.toList()) }

    inline fun <reified TRuntime, reified TListener> createRuntimes(collectionKey: String, idKey: String) =
            (this.parameters.configuration.optJSONArray(collectionKey) ?: JSONArray())
                    .toJSONObjectSequence()
                    .map {
                        Class.forName(it.getString("type"))
                                .getDeclaredConstructor(RuntimeParameters::class.java, TListener::class.java)
                                .newInstance(RuntimeParameters(
                                        it.getString(idKey),
                                        it.getJSONObject("configuration"),
                                        this.getOrCreateState(it.getString(idKey))
                                ), this)
                                .to<TRuntime>()
                    }
                    .toList()

//    fun extractConfiguration() = JSONObject(mapOf(
//            "sources" to JSONArray(this.sourceRuntimes.map {
//                JSONObject(mapOf(
//                        "sourceID" to it.parameters.id,
//                        "type" to it.javaClass.name,
//                        "configuration" to it.parameters.configuration
//                ))
//            }),
//            "targets" to JSONArray(this.targetRuntimes.map {
//                JSONObject(mapOf(
//                        "targetID" to it.parameters.id,
//                        "type" to it.javaClass.name,
//                        "configuration" to it.parameters.configuration
//                ))
//            }),
//            "bridges" to JSONArray(this.sourceToTargetsMap.entries
//                    .flatMap { entry ->
//                        entry.value.map {
//                            JSONObject(mapOf(
//                                    "sourceID" to entry.key.parameters.id,
//                                    "targetID" to it.parameters.id
//                            ))
//                        }
//                    }
//            )
//    ))

    fun getRuntimeIDs() =
            this.runtimes.map { it.parameters.id }.toList()

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop) {
            this.runtimes.forEach { it.startOrStop(true, context) }

            this.timer.schedule(object : TimerTask() {
                override fun run() {
                    this@BridgeRuntime.sourceRuntimes
                            .forEach { it.startDiscoverDevices(true) }

                    this@BridgeRuntime.targetRuntimes
                            .forEach { targetRuntime ->
                                targetRuntime
                                        .startSyncSources(this@BridgeRuntime.targetToSourcesMap[targetRuntime]!!.map { it.parameters.id })
                            }
                }
            }, 0, 3_600_000)
        } else {
            this.timer.cancel()

            this.runtimes.forEach { it.startOrStop(false, context) }
        }
    }

    fun processMessage(runtimeID: String, message: JSONObject) =
            this.runtimes
                    .filter { it.parameters.id == runtimeID }
                    .forEach { it.processMessage(message) }

    override fun processMacAddressDiscovered(ipAddress: InetAddress, macAddress: ByteArray) =
            this.runtimes.forEach { it.processMacAddressDiscovered(ipAddress, macAddress) }

    override fun onSyncError(targetRuntime: DeviceTargetRuntime, mayNeedDiscovery: Boolean) {
        Log.v(TAG, "Received sync error for target runtime $targetRuntime")

        if (mayNeedDiscovery)
            this.listener.onNetworkDiscoveryNeeded()
    }

    override fun onRejuvenated(targetRuntime: DeviceTargetRuntime) {
        Log.v(TAG, "Rejuvenated target runtime $targetRuntime; starting to discover devices for all its sources...")

        this.targetToSourcesMap[targetRuntime]!!
                .forEach { it.startDiscoverDevices(true) }
    }

    override fun onDevicesSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String) {
        Log.v(TAG, "Received devices sync request; starting to discover devices state for source '$sourceID'...")

        this.targetToSourcesMap[targetRuntime]!!
                .filter { it.parameters.id == sourceID }
                .forEach { it.startDiscoverDevices(true) }
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

    override fun onRejuvenated(sourceRuntime: DeviceSourceRuntime) {
        Log.v(TAG, "Rejuvenated source runtime $sourceRuntime; starting to discover devices...")

        sourceRuntime.startDiscoverDevices(true)
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

    interface Listener {
        fun onNetworkDiscoveryNeeded()
    }
}
