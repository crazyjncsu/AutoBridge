package com.autobridge.sources

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import com.autobridge.RuntimeParameters
import java.util.*

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class BluetoothLowEnergyContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ContactClosureBoardSourceRuntime(parameters, listener) {
    private var context: Context? = null
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private val getContactStateRequestList = mutableListOf<GetContactStateRequest>()
    private val setContactStateRequestList = mutableListOf<SetContactStateRequest>()

    data class GetContactStateRequest(val contactID: String, val callback: (openOrClosed: Boolean) -> Unit)
    data class SetContactStateRequest(val contactID: String, val openOrClosed: Boolean, val callback: () -> Unit)

    // these were first here because of unreliability in issuing the read request when other read requests were finishing,
    // but this became extra useful when we realize that it takes a while and there can be issues with connecting, reconnecting, etc
    private val timer = Timer()
    private var timerTask: TimerTask? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            this@BluetoothLowEnergyContactClosureBoardSourceRuntime.scanner?.stopScan(this)

            this@BluetoothLowEnergyContactClosureBoardSourceRuntime.gatt = result?.device?.connectGatt(this@BluetoothLowEnergyContactClosureBoardSourceRuntime.context, true, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED)
                        gatt?.discoverServices()
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    this@BluetoothLowEnergyContactClosureBoardSourceRuntime.characteristic = gatt
                            ?.services
                            ?.flatMap { it.characteristics!! }
                            ?.filter { (it.properties and (BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE)) == (BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE) }
                            ?.firstOrNull()

                    this@BluetoothLowEnergyContactClosureBoardSourceRuntime.characteristic?.let { gatt?.readCharacteristic(it) }
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                    synchronized(this@BluetoothLowEnergyContactClosureBoardSourceRuntime.getContactStateRequestList) {
                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.timerTask?.cancel()

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.getContactStateRequestList.forEach {
                            val contactIndex = it.contactID[1].toString().toInt() - 1
                            this@BluetoothLowEnergyContactClosureBoardSourceRuntime.asyncTryLog { it.callback(characteristic!!.value[contactIndex].toInt() == 0) }
                        }

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.getContactStateRequestList.clear()

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.setContactStateRequestList.forEach {
                            val contactIndex = it.contactID[1].toString().toInt() - 1
                            characteristic!!.value[contactIndex] = if (it.openOrClosed) 0 else 1
                            this@BluetoothLowEnergyContactClosureBoardSourceRuntime.asyncTryLog { it.callback() }
                        }

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.setContactStateRequestList.clear()

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.gatt?.writeCharacteristic(characteristic)
                    }
                }
            })
        }
    }

    // all of this stuff is kind of funny, because we can't read contacts but all at once
    // and it may be worth refactoring, except we really need to write the pins individually which
    // truly requires the read-first weird logic anyway
    override fun tryGetContactStateAsync(contactID: String, callback: (openOrClosed: Boolean) -> Unit) =
            this.performGetOrSet { this.getContactStateRequestList.add(GetContactStateRequest(contactID, callback)) }

    override fun trySetContactStateAsync(contactID: String, openOrClosed: Boolean, callback: () -> Unit) =
            this.performGetOrSet { this.setContactStateRequestList.add(SetContactStateRequest(contactID, openOrClosed, callback)) }

    private fun performGetOrSet(proc: () -> Unit) =
            synchronized(this.getContactStateRequestList) {
                if (this.getContactStateRequestList.count() == 0 && this.setContactStateRequestList.count() == 0) {
                    this.timerTask = object : TimerTask() {
                        override fun run() {
                            if (this@BluetoothLowEnergyContactClosureBoardSourceRuntime.gatt != null && this@BluetoothLowEnergyContactClosureBoardSourceRuntime.characteristic != null) {
                                this@BluetoothLowEnergyContactClosureBoardSourceRuntime.gatt?.readCharacteristic(this@BluetoothLowEnergyContactClosureBoardSourceRuntime.characteristic)
                            }
                        }
                    }

                    this.timer.schedule(this.timerTask, 0, 2000)
                }

                proc()
            }

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

            this.scanner = bluetoothManager.adapter.bluetoothLeScanner

            this.scanner?.startScan(
                    listOf(ScanFilter.Builder().setDeviceAddress(this.parameters.configuration.getString("bluetoothAddress")).build()),
                    ScanSettings.Builder().setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH).build(),
                    this.callback
            )
        } else {
            this.gatt?.disconnect()
            this.gatt?.close()

            this.scanner?.stopScan(this.callback)
        }
    }
}