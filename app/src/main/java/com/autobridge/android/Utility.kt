package com.autobridge.android

import android.util.Log
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.message.BasicNameValuePair
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.experimental.buildSequence

fun Map<String, String>.toQueryString() = URLEncodedUtils.format(this.entries.map { BasicNameValuePair(it.key, it.value) }, "UTF-8")

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
                        .let { if (it.isNullOrEmpty()) JSONObject() else JSONObject(it) }
            }
        } finally {
            disconnect()
        }
    }
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
        proc();
}