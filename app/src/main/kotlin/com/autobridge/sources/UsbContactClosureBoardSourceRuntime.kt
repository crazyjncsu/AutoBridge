package com.autobridge.sources

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import com.android.*
import com.autobridge.*

abstract class UsbContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ContactClosureBoardSourceRuntime(parameters, listener) {
    private var usbInfo: UsbInfo? = null

    protected data class UsbInfo(val manager: UsbManager, val deviceConnection: UsbDeviceConnection, val usbInterface: UsbInterface, val readEndpoint: UsbEndpoint?, val writeEndpoint: UsbEndpoint?)

    private var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent!!.action == USB_PERMISSION && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                if (this@UsbContactClosureBoardSourceRuntime.setupConnectionsIfNecessary(context!!.getSystemService(Context.USB_SERVICE) as UsbManager, intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)))
                    this@UsbContactClosureBoardSourceRuntime.listener.onRejuvenated(this@UsbContactClosureBoardSourceRuntime)
        }
    }

    protected abstract fun getContactState(usbInfo: UsbInfo, contactID: String): Boolean
    protected abstract fun setContactState(usbInfo: UsbInfo, contactID: String, openOrClosed: Boolean)

    override fun tryGetContactStateAsync(contactID: String, callback: (openOrClosed: Boolean) -> Unit) {
        asyncTryLog {
            if (this.usbInfo != null) {
                val openOrClosed = this.getContactState(this.usbInfo!!, contactID)
                callback(openOrClosed)
            }
        }
    }

    override fun trySetContactStateAsync(contactID: String, openOrClosed: Boolean, callback: () -> Unit) {
        asyncTryLog {
            if (this.usbInfo != null) {
                this.setContactState(this.usbInfo!!, contactID, openOrClosed)
                callback()
            }
        }
    }

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop) {
            context.registerReceiver(this.broadcastReceiver, IntentFilter(USB_PERMISSION))

            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            val usbDevice = usbManager.deviceList.values
                    .filter { it.productId == this.getProductID() }
                    .filter { it.vendorId == this.getVendorID() }
                    .firstOrNull()

            if (usbDevice != null) {
                usbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION), 0))

                if (usbManager.hasPermission(usbDevice))
                    this.setupConnectionsIfNecessary(usbManager, usbDevice)
            }
        } else {
            context.unregisterReceiver(this.broadcastReceiver)

            if (this.usbInfo != null) {
                this.usbInfo!!.deviceConnection.releaseInterface(this.usbInfo!!.usbInterface)
                this.usbInfo!!.deviceConnection.close()
                this.usbInfo = null
            }
        }
    }

    private fun setupConnectionsIfNecessary(usbManager: UsbManager, usbDevice: UsbDevice): Boolean {
        if (this.usbInfo != null)
            return false

        val usbDeviceConnection = usbManager.openDevice(usbDevice)
        val usbInterface = this.getInterface(usbDevice)

        usbDeviceConnection!!.claimInterface(this.usbInfo!!.usbInterface, true)

        this.usbInfo = UsbInfo(
                usbManager,
                usbDeviceConnection,
                usbInterface,
                usbInterface.getEndpoints().filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }.firstOrNull(),
                usbInterface.getEndpoints().filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }.firstOrNull()
        )

        return true
    }

    protected open fun getInterface(usbDevice: UsbDevice) = usbDevice.getInterface(0)
    protected abstract fun getVendorID(): Int
    protected abstract fun getProductID(): Int
}

class UsbHidContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : UsbContactClosureBoardSourceRuntime(parameters, listener) {
    override fun getVendorID() = 5824
    override fun getProductID() = 1503

    override fun getContactState(usbInfo: UsbInfo, contactID: String): Boolean {
        val pinNumber = contactID[1].toInt()
        val array = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)

        usbInfo.deviceConnection.controlTransfer(
                0xA0,
                1,
                0,
                0,
                array,
                8,
                USB_TIMEOUT
        )

        val flags = array[7].toUnsignedInt()
        return array[7].toUnsignedInt() shr (pinNumber - 1) and 1 == 1
    }

    override fun setContactState(usbInfo: UsbInfo, contactID: String, openOrClosed: Boolean) {
        // requires looking here: https://github.com/pavel-a/usb-relay-hid/blob/master/lib/usb_relay_lib.c
        // and here: https://github.com/pavel-a/usb-digital-io16-hid/blob/master/lib/hiddata_libusb01.c
        // and deciphering its call to usbhidSetReport
        usbInfo.deviceConnection.controlTransfer(
                0x20,
                9,
                0,
                0,
                byteArrayOf((if (openOrClosed) 0xFD else 0xFF).toByte(), contactID[1].toString().toByte()),
                2,
                USB_TIMEOUT
        )
    }
}

class UsbCdcContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : UsbContactClosureBoardSourceRuntime(parameters, listener) {
    override fun getVendorID() = 0x2A19
    override fun getProductID() = 0x0800

    override fun getInterface(usbDevice: UsbDevice) =
            usbDevice.getInterfaces().firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }
                    ?: super.getInterface(usbDevice)

    private fun performSynchronizedRequest(usbInfo: UsbInfo, requestText: String): String {
        synchronized(usbInfo) {
            val writeBytes = requestText.toByteArray(DEFAULT_CHARSET)
            usbInfo.deviceConnection.bulkTransfer(usbInfo.writeEndpoint, writeBytes, writeBytes.count(), USB_TIMEOUT)

            val readBytes = ByteArray(1024)
            val readCount = usbInfo.deviceConnection.bulkTransfer(usbInfo.readEndpoint, readBytes, readBytes.count(), USB_TIMEOUT)
            return readBytes.copyOfRange(0, readCount).toString(DEFAULT_CHARSET)
        }
    }

    private fun getContactIndexString(contactID: String) = contactID.substring(2).toInt().toString(16)

    override fun getContactState(usbInfo: UsbInfo, contactID: String): Boolean {
        val responseText = this.performSynchronizedRequest(usbInfo, "gpio read ${this.getContactIndexString(contactID)}\r")

        return when (responseText.split("\n\r")[1]) {
            "1" -> true
            "0" -> false
            else -> throw IllegalStateException()
        }
    }

    override fun setContactState(usbInfo: UsbInfo, contactID: String, openOrClosed: Boolean) {
        this.performSynchronizedRequest(usbInfo, "gpio ${if (openOrClosed) "clear" else "set"} ${this.getContactIndexString(contactID)}\r")
    }
}