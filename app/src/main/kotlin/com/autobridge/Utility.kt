package com.autobridge

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.*
import kotlin.coroutines.experimental.buildSequence

fun Map<String, String>.toQueryString() = this.entries
        .map { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        .joinToString("&")

fun JSONArray.toJSONObjectSequence(): Sequence<JSONObject> {
    val length = this.length()

    return buildSequence {
        var i = 0

        while (i++ < length)
            yield(this@toJSONObjectSequence.getJSONObject(i - 1))
    }
}

fun performJsonHttpRequest(
        method: String,
        scheme: String,
        authority: String,
        path: String? = null,
        headerMap: Map<String, String>? = null,
        queryStringMap: Map<String, String>? = null,
        bodyObject: JSONObject? = null
): JSONObject {
    with(URI(scheme, authority, path, queryStringMap?.toQueryString(), null).toURL().openConnection() as HttpURLConnection) {
        try {
            requestMethod = method
            doInput = true

            headerMap?.forEach { addRequestProperty(it.key, it.value); }

            if (bodyObject != null) {
                addRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(bodyObject.toString().toByteArray()); }
            }

            if (responseCode >= 400)
                throw IOException("Received response code from server: $responseCode")

            (if (responseCode >= 400) errorStream else inputStream).use {
                return InputStreamReader(it)
                        .readText()
                        .let { if (it.isEmpty()) JSONObject() else JSONObject(it) }
            }
        } finally {
            disconnect()
        }
    }
}

fun async(proc: () -> Unit) {
    THREAD_POOL.execute(object : Runnable {
        override fun run() = proc()
    })
}

fun asyncTryLog(proc: () -> Unit) {
    async { tryLog(proc) }
}

fun tryLog(proc: () -> Unit) {
    try {
        proc()
    } catch (ex: Exception) {
        Log.e("Exception", "Encountered exception", ex)
    }
}

fun ifApiLevel(api: Int, proc: () -> Unit) {
    if (api <= android.os.Build.VERSION.SDK_INT)
        proc()
}

fun Byte.toUnsignedInt() = toInt() and 0xFF

fun <T> T.mutate(proc: (T) -> Unit): T {
    proc(this)
    return this
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.to() = this as T

fun isAddressValid(address: InetAddress) = !address.isLoopbackAddress && !address.isLinkLocalAddress

fun getActiveNetworkInterface() = NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.inetAddresses.asSequence().filter { isAddressValid(it) }.any() }
        .first()

fun UsbInterface.getEndpoints() = (0 until this.endpointCount).map { this.getEndpoint(it) }
fun UsbDevice.getInterfaces() = (0 until this.interfaceCount).map { this.getInterface(it) }