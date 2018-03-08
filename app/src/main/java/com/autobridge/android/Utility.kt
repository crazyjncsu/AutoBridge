package com.autobridge.android

import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.message.BasicNameValuePair
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.experimental.buildSequence

fun Map<String, String>.toQueryString(): String {
    return URLEncodedUtils.format(this.entries.map { BasicNameValuePair(it.key, it.value) }, "UTF-8");
}

fun JSONArray.toJSONObjectSequence(): Sequence<JSONObject> {
    var length = this.length();

    return buildSequence {
        var i = 0;

        while (i++ < length)
            yield(this@toJSONObjectSequence.getJSONObject(i - 1));
    }
}