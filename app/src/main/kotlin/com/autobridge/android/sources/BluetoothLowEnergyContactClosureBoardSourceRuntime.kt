package com.autobridge.android.sources

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import com.autobridge.android.RuntimeParameters
import com.autobridge.android.TAG
import com.autobridge.android.asyncTryLog
import java.util.*

@SuppressLint("NewApi")
class BluetoothLowEnergyContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ContactClosureBoardSourceRuntime(parameters, listener) {
    private var context: Context? = null;
    private var scanner: BluetoothLeScanner? = null;
    private var gatt: BluetoothGatt? = null;
    private var characteristic: BluetoothGattCharacteristic? = null;
    private val getContactStateRequestList = mutableListOf<GetContactStateRequest>()
    private val setContactStateRequestList = mutableListOf<SetContactStateRequest>()

    data class GetContactStateRequest(val contactID: String, val callback: (openOrClosed: Boolean) -> Unit);
    data class SetContactStateRequest(val contactID: String, val openOrClosed: Boolean, val callback: () -> Unit);

    // these were first here because of unreliability in issuing the read request when other read requests were finishing,
    // but this became extra useful when we realize that it takes a while and there can be issues with connecting, reconnecting, etc
    private val timer = Timer()
    private var timerTask: TimerTask? = null;

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
                            ?.firstOrNull();

                    this@BluetoothLowEnergyContactClosureBoardSourceRuntime.characteristic?.let { gatt?.readCharacteristic(it) }
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                    Log.i(TAG, "Contact state read complete")

                    synchronized(this@BluetoothLowEnergyContactClosureBoardSourceRuntime.getContactStateRequestList) {
                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.timerTask?.cancel();

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.getContactStateRequestList.forEach {
                            val contactIndex = it.contactID[1].toString().toInt() - 1;
                            asyncTryLog { it.callback(characteristic!!.value[contactIndex].toInt() == 0) }
                        }

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.getContactStateRequestList.clear();

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.setContactStateRequestList.forEach {
                            val contactIndex = it.contactID[1].toString().toInt() - 1;
                            characteristic!!.value[contactIndex] = if (it.openOrClosed) 0 else 1
                            asyncTryLog { it.callback() }
                        }

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.setContactStateRequestList.clear();

                        this@BluetoothLowEnergyContactClosureBoardSourceRuntime.gatt?.writeCharacteristic(characteristic)
                    }
                }
            })
        }
    }

    // all of this stuff is kind of funny, because we can't read contacts but all at once
    // and it may be worth refactoring, except we really need to write the pins individually which
    // truly requires the read-first weird logic anyway
    override fun getContactStateAsync(contactID: String, callback: (openOrClosed: Boolean) -> Unit) =
            this.performGetOrSet { this.getContactStateRequestList.add(GetContactStateRequest(contactID, callback)) }

    override fun setContactStateAsync(contactID: String, openOrClosed: Boolean, callback: () -> Unit) =
            this.performGetOrSet { this.setContactStateRequestList.add(SetContactStateRequest(contactID, openOrClosed, callback)) }

    private fun performGetOrSet(proc: () -> Unit) =
            synchronized(this.getContactStateRequestList) {
                if (this.getContactStateRequestList.count() == 0 && this.setContactStateRequestList.count() == 0) {
                    this.timerTask = object : TimerTask() {
                        override fun run() {
                            if (this@BluetoothLowEnergyContactClosureBoardSourceRuntime.gatt != null && this@BluetoothLowEnergyContactClosureBoardSourceRuntime.characteristic != null) {
                                Log.i(TAG, "Reading contact state")
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