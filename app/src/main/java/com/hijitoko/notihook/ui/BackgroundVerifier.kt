package com.hijitoko.notihook.ui

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.hijitoko.notihook.service.NotificationCatcherService

data class BackgroundVerificationState(
    val notificationAccessEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
)

object BackgroundVerifier {

    fun verify(context: Context): BackgroundVerificationState {
        return BackgroundVerificationState(
            notificationAccessEnabled = isNotificationAccessEnabled(context),
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(context),
        )
    }

    private fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

        val component = ComponentName(context, NotificationCatcherService::class.java)
        return enabledListeners.contains(component.flattenToString())
    }

    private fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
