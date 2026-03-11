package com.hijitoko.notihook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val enabled: Boolean = false,
    val forwardEnabled: Boolean = false,
    val apiUrl: String = "",
    val httpMethod: String = HttpMethod.POST,
    val payloadType: String = PayloadType.FORM,
    val additionalValuesJson: String = "{}"
)

object HttpMethod {
    const val GET = "GET"
    const val POST = "POST"
}

object PayloadType {
    const val FORM = "FORM"
    const val JSON = "JSON"
}
