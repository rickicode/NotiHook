package com.hijitoko.notihook.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hijitoko.notihook.forward.AdditionalValuesCodec
import com.hijitoko.notihook.worker.ForwardNotificationWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
)

class NotificationRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).notificationCatcherDao()

    fun observeEnabledHistories(): Flow<List<NotificationRecordEntity>> = dao.observeEnabledHistories()

    fun observeGlobalSettings(): Flow<GlobalSettingsEntity> {
        return dao.observeGlobalSettings().map { value ->
            when {
                value == null -> GlobalSettingsEntity()
                value.userAgent.isBlank() -> value.copy(userAgent = GlobalSettingsEntity.DEFAULT_USER_AGENT)
                else -> value
            }
        }
    }

    fun observeAppConfigs(): Flow<List<AppConfigEntity>> = dao.observeAppConfigs()

    suspend fun saveGlobalSettings(userAgent: String) {
        dao.upsertGlobalSettings(
            GlobalSettingsEntity(
                userAgent = userAgent.trim().ifBlank { GlobalSettingsEntity.DEFAULT_USER_AGENT }
            )
        )
    }

    suspend fun setAppEnabled(packageName: String, appName: String, enabled: Boolean) {
        val existing = dao.getAppConfig(packageName)
        val updated = if (existing == null) {
            AppConfigEntity(
                packageName = packageName,
                appName = appName,
                enabled = enabled,
                forwardEnabled = false,
                additionalValuesJson = AdditionalValuesCodec.encode(mapOf("apikey" to "hijilabs"))
            )
        } else {
            existing.copy(
                enabled = enabled,
                forwardEnabled = if (enabled) existing.forwardEnabled else false,
                appName = appName
            )
        }
        dao.upsertAppConfig(updated)
    }

    suspend fun savePerAppForwardConfig(
        packageName: String,
        appName: String,
        forwardEnabled: Boolean,
        apiUrl: String,
        httpMethod: String,
        payloadType: String,
        additionalValues: Map<String, String>
    ) {
        val existing = dao.getAppConfig(packageName)
        val mergedEnabled = existing?.enabled ?: false
        val config = AppConfigEntity(
            packageName = packageName,
            appName = appName,
            enabled = mergedEnabled,
            forwardEnabled = if (mergedEnabled) forwardEnabled else false,
            apiUrl = apiUrl.trim(),
            httpMethod = httpMethod,
            payloadType = payloadType,
            additionalValuesJson = AdditionalValuesCodec.encode(additionalValues)
        )
        dao.upsertAppConfig(config)
    }

    suspend fun getAppConfig(packageName: String): AppConfigEntity? = dao.getAppConfig(packageName)

    suspend fun ingestNotification(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        bigText: String,
        subText: String,
        infoText: String,
        notificationKey: String,
        postedAt: Long,
        rawExtras: String,
    ) {
        if (!dao.isPackageEnabled(packageName)) return

        val id = dao.insertNotification(
            NotificationRecordEntity(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                bigText = bigText,
                subText = subText,
                infoText = infoText,
                notificationKey = notificationKey,
                postedAt = postedAt,
                rawExtras = rawExtras,
            )
        )

        dao.pruneToLimit(5000)

        val appConfig = dao.getAppConfig(packageName)
        if (appConfig?.forwardEnabled == true && appConfig.apiUrl.isNotBlank()) {
            enqueueForward(id)
        }
    }

    suspend fun getNotificationById(id: Long): NotificationRecordEntity? = dao.getNotificationById(id)

    suspend fun updateForwardResult(id: Long, status: String, error: String, forwardedAt: Long) {
        dao.updateForwardResult(id, status, error, forwardedAt)
    }

    suspend fun getGlobalSettingsSync(): GlobalSettingsEntity {
        val value = dao.getGlobalSettings() ?: return GlobalSettingsEntity()
        return if (value.userAgent.isBlank()) {
            value.copy(userAgent = GlobalSettingsEntity.DEFAULT_USER_AGENT)
        } else {
            value
        }
    }

    fun getInstalledApps(): List<InstalledAppItem> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { appInfo ->
                shouldIncludeApp(
                    packageName = appInfo.packageName,
                    appFlags = appInfo.flags,
                    currentPackageName = context.packageName
                )
            }
            .map { appInfo ->
                InstalledAppItem(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo)?.toString().orEmpty().ifBlank {
                        appInfo.packageName
                    }
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
            .toList()

        return apps
    }

    private fun enqueueForward(notificationId: Long) {
        val request = OneTimeWorkRequestBuilder<ForwardNotificationWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .setInputData(workDataOf(ForwardNotificationWorker.KEY_NOTIFICATION_ID to notificationId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "forward_notification_$notificationId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        fun shouldIncludeApp(
            packageName: String,
            appFlags: Int,
            currentPackageName: String,
        ): Boolean {
            if (packageName == currentPackageName) return false

            val isSystemApp = appFlags and ApplicationInfo.FLAG_SYSTEM != 0
            val isUpdatedSystemApp = appFlags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            return !isSystemApp || isUpdatedSystemApp
        }

        fun fromWorker(worker: CoroutineWorker): NotificationRepository {
            return NotificationRepository(worker.applicationContext)
        }
    }
}
