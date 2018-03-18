package com.autobridge.android.source

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.autobridge.android.RuntimeParameters
import com.autobridge.android.TAG

class BluetoothLowEnergyContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ContactClosureBoardSourceRuntime(parameters, listener) {
    override fun getContactStateAsync(contactID: String, callback: (openOrClosed: Boolean) -> Unit) {

    }

    override fun setContactStateAsync(contactID: String, openOrClosed: Boolean, callback: () -> Unit) {
    }

    @SuppressLint("NewApi")
    override fun startOrStop(startOrStop: Boolean, context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        bluetoothManager.adapter.bluetoothLeScanner.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                Log.v(TAG, "got scan result: " + result?.device)

                val gatt: BluetoothGatt? = result?.device?.connectGatt(context, true, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        Log.v(TAG, "connection state changed: " + newState)
                    }

                    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                        Log.v(TAG, "wrote characteristic")
                    }
                })

                gatt?.services?.forEach {
                    Log.v(TAG, "got service: " + it.uuid)

                    it.characteristics.forEach {
                        Log.v(TAG, "got characteristic: " + it.uuid)

                        it.setValue(byteArrayOf(
                                if (Math.random() > 0.5) 0 else 1,
                                if (Math.random() > 0.5) 0 else 1,
                                if (Math.random() > 0.5) 0 else 1,
                                if (Math.random() > 0.5) 0 else 1
                        ))
                    }
                }
            }
        })
    }
}