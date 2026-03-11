package com.hijitoko.notihook.service

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hijitoko.notihook.data.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class NotificationCatcherService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val safeSbn = sbn ?: return
        val notification = safeSbn.notification ?: return
        val extras = notification.extras ?: return

        val packageName = safeSbn.packageName
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }

        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val subText = extras.getCharSequence("android.subText")?.toString().orEmpty()
        val infoText = extras.getCharSequence("android.infoText")?.toString().orEmpty()

        val rawExtras = JSONObject().apply {
            extras.keySet().forEach { key ->
                put(key, extras.valueAsString(key))
            }
        }.toString()

        val repository = NotificationRepository(applicationContext)
        scope.launch {
            repository.ingestNotification(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                bigText = bigText,
                subText = subText,
                infoText = infoText,
                notificationKey = safeSbn.key.orEmpty(),
                postedAt = safeSbn.postTime,
                rawExtras = rawExtras,
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun Bundle.valueAsString(key: String): String {
        @Suppress("DEPRECATION")
        return get(key)?.toString().orEmpty()
    }
}
