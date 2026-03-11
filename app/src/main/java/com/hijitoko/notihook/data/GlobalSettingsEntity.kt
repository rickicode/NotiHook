package com.hijitoko.notihook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_settings")
data class GlobalSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val userAgent: String = DEFAULT_USER_AGENT
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_USER_AGENT = "NotiHook/1.0"
    }
}
