package com.autobridge.android.sources

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.autobridge.android.RuntimeParameters
import com.autobridge.android.USB_PERMISSION
import com.autobridge.android.asyncTryLog
import com.autobridge.android.toUnsignedInt

abstract class UsbContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ContactClosureBoardSourceRuntime(parameters, listener) {
    //private val syncLock = java.lang.Object()
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null

    protected abstract fun getContactState(usbDeviceConnection: UsbDeviceConnection, contactID: String, callback: (openOrClosed: Boolean) -> Unit)
    protected abstract fun setContactState(usbDeviceConnection: UsbDeviceConnection, contactID: String, openOrClosed: Boolean, callback: () -> Unit)

    override fun getContactStateAsync(contactID: String, callback: (openOrClosed: Boolean) -> Unit) {
        asyncTryLog {
            if (this.usbDeviceConnection != null)
                this.getContactState(this.usbDeviceConnection!!, contactID, callback)
        }
    }

    override fun setContactStateAsync(contactID: String, openOrClosed: Boolean, callback: () -> Unit) {
        asyncTryLog {
            if (this.usbDeviceConnection != null)
                this.setContactState(this.usbDeviceConnection!!, contactID, openOrClosed, callback)
        }
    }

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            val usbDevice = usbManager.deviceList.values
                    .filter { it.productId == this.parameters.configuration.getInt("usbProductID") }
                    .filter { it.vendorId == this.parameters.configuration.getInt("usbVendorID") }
                    .firstOrNull()

            if (usbDevice != null) {
                usbManager.requestPermission(
                        usbDevice,
                        PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION), 0)
                )

                if (usbManager.hasPermission(usbDevice))
                    this.setupDevice(usbManager, usbDevice)

                context.registerReceiver(
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                if (intent!!.action == USB_PERMISSION && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                    this@UsbContactClosureBoardSourceRuntime.setupDevice(
                                            usbManager,
                                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                                    )
                                }
                            }

                        },
                        IntentFilter(USB_PERMISSION)
                )
            }
        } else {
            if (this.usbDeviceConnection != null) {
                this.usbDeviceConnection!!.releaseInterface(this.usbInterface)
                this.usbDeviceConnection!!.close()
            }
        }
    }

    private fun setupDevice(usbManager: UsbManager, usbDevice: UsbDevice) {
        this.usbDeviceConnection = usbManager.openDevice(usbDevice)
        this.usbInterface = usbDevice.getInterface(0)
        this.usbDeviceConnection!!.claimInterface(this.usbInterface, true)
    }
}

class UsbHidContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : UsbContactClosureBoardSourceRuntime(parameters, listener) {
    override fun getContactState(usbDeviceConnection: UsbDeviceConnection, contactID: String, callback: (openOrClosed: Boolean) -> Unit) {
        val pinNumber = contactID[1].toInt()
        val array = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)

        usbDeviceConnection.controlTransfer(
                0xA0,
                1,
                0,
                0,
                array,
                8,
                1000
        )

        val flags = array[7].toUnsignedInt()
        callback(flags shr (pinNumber - 1) and 1 == 1)
    }

    override fun setContactState(usbDeviceConnection: UsbDeviceConnection, contactID: String, openOrClosed: Boolean, callback: () -> Unit) {
        // requires looking here: https://github.com/pavel-a/usb-relay-hid/blob/master/lib/usb_relay_lib.c
        // and here: https://github.com/pavel-a/usb-digital-io16-hid/blob/master/lib/hiddata_libusb01.c
        // and deciphering its call to usbhidSetReport
        usbDeviceConnection.controlTransfer(
                0x20,
                9,
                0,
                0,
                byteArrayOf((if (openOrClosed) 0xFD else 0xFF).toByte(), contactID[1].toString().toByte()),
                2,
                1000
        )

        callback()
    }
}