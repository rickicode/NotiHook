package com.hijitoko.notihook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_records")
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val infoText: String,
    val notificationKey: String,
    val postedAt: Long,
    val rawExtras: String,
    val forwardStatus: String = ForwardStatus.NOT_SENT,
    val forwardError: String = "",
    val forwardedAt: Long = 0L
)

object ForwardStatus {
    const val NOT_SENT = "NOT_SENT"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val SKIPPED = "SKIPPED"
}
