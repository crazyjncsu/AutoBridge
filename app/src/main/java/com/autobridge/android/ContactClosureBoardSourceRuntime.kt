package com.autobridge.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import java.nio.ByteBuffer

class ContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener) {
    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop) {
            val usbManager = context.getSystemService("usb") as UsbManager; // Context.USB_SERVICE won't compile
            var usbDevice = usbManager.deviceList.entries.first().value;

            usbManager.requestPermission(
                    usbDevice,
                    PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION), 0)
            )

            if (usbManager.hasPermission(usbDevice))
                this.runDevice(usbManager, usbDevice);

            context.registerReceiver(
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent!!.action == USB_PERMISSION && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                this@ContactClosureBoardSourceRuntime.runDevice(
                                        usbManager,
                                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                                )
                            }
                        }

                    },
                    IntentFilter(USB_PERMISSION)
            )
        }
    }

    private fun runDevice(usbManager: UsbManager, usbDevice: UsbDevice) {
        val usbDeviceConnection = usbManager.openDevice(usbDevice)
        val usbInterface = usbDevice.getInterface(0)

        usbDeviceConnection.claimInterface(usbInterface, true)

        for (i in 0..20) {
            for (j in 1..8) {
                // requires looking here: https://github.com/pavel-a/usb-relay-hid/blob/master/lib/usb_relay_lib.c
                // and here: https://github.com/pavel-a/usb-digital-io16-hid/blob/master/lib/hiddata_libusb01.c
                // and deciphering its call to usbhidSetReport
                usbDeviceConnection.controlTransfer(
                        0x20,
                        9,
                        0,
                        0,
                        byteArrayOf((if (i.rem(2) == 0) 0xFF else 0xFD).toByte(), j.toByte()),
                        2,
                        1000
                )

                Thread.sleep(200);
            }
        }
    }

    override fun startDiscoverDevices() {
    }

    override fun startDiscoverDeviceState(deviceID: String) {
    }

    override fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String) {
    }
}