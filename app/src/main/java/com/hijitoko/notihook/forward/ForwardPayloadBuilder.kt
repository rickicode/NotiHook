package com.hijitoko.notihook.forward

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

data class ForwardPayloadInput(
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val infoText: String,
    val name: String,
    val pkg: String,
)

object ForwardPayloadBuilder {

    fun buildPayloadMap(
        input: ForwardPayloadInput,
        additionalValues: Map<String, String>
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        result.putAll(additionalValues)
        result["title"] = input.title
        result["text"] = input.text
        result["bigtext"] = input.bigText
        result["subtext"] = input.subText
        result["infotext"] = input.infoText
        result["name"] = input.name
        result["pkg"] = input.pkg
        return result
    }

    fun buildGetUrl(baseUrl: String, params: Map<String, String>): String {
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return baseUrl
        val builder = httpUrl.newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    fun toJsonBody(params: Map<String, String>): String {
        return JSONObject(params).toString()
    }
}

object AdditionalValuesCodec {
    fun decode(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        val objectValue = JSONObject(json)
        return objectValue.keys().asSequence().associateWith { key ->
            objectValue.optString(key)
        }
    }

    fun encode(values: Map<String, String>): String {
        return JSONObject(values).toString()
    }
}
