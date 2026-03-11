package com.hijitoko.notihook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hijitoko.notihook.data.GlobalSettingsEntity
import com.hijitoko.notihook.data.HttpMethod
import com.hijitoko.notihook.data.InstalledAppItem
import com.hijitoko.notihook.data.NotificationRecordEntity
import com.hijitoko.notihook.data.NotificationRepository
import com.hijitoko.notihook.data.PayloadType
import com.hijitoko.notihook.forward.AdditionalValuesCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiItem(
    val packageName: String,
    val appName: String,
    val enabled: Boolean,
    val forwardEnabled: Boolean,
)

data class PerAppConfigForm(
    val packageName: String,
    val appName: String,
    val notificationCatchEnabled: Boolean,
    val forwardEnabled: Boolean,
    val apiUrl: String,
    val httpMethod: String,
    val payloadType: String,
    val additionalValuesEditor: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NotificationRepository(application.applicationContext)

    private val installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val backgroundVerification = MutableStateFlow(BackgroundVerificationState(false, false))
    private val selectedTab = MutableStateFlow(0)
    private val editingConfig = MutableStateFlow<PerAppConfigForm?>(null)

    val globalSettings: StateFlow<GlobalSettingsEntity> = repository.observeGlobalSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettingsEntity())

    val histories: StateFlow<List<NotificationRecordEntity>> = repository.observeEnabledHistories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appItems: StateFlow<List<AppUiItem>> = combine(
        installedApps,
        repository.observeAppConfigs()
    ) { apps, configs ->
        val byPackage = configs.associateBy { it.packageName }
        apps.map { app ->
            val config = byPackage[app.packageName]
            AppUiItem(
                packageName = app.packageName,
                appName = app.appName,
                enabled = config?.enabled == true,
                forwardEnabled = config?.forwardEnabled == true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedTabIndex: StateFlow<Int> = selectedTab
    val editingConfigState: StateFlow<PerAppConfigForm?> = editingConfig
    val backgroundVerificationState: StateFlow<BackgroundVerificationState> = backgroundVerification

    init {
        loadInstalledApps()
        refreshBackgroundVerification()
    }

    fun selectTab(index: Int) {
        selectedTab.value = index
    }

    fun refreshBackgroundVerification() {
        backgroundVerification.value = BackgroundVerifier.verify(getApplication())
    }

    fun setAppEnabled(item: AppUiItem, enabled: Boolean) {
        viewModelScope.launch {
            repository.setAppEnabled(item.packageName, item.appName, enabled)
        }
    }

    fun openPerAppConfig(item: AppUiItem) {
        viewModelScope.launch {
            val existing = repository.getAppConfig(item.packageName)
            editingConfig.value = if (existing == null) {
                PerAppConfigForm(
                    packageName = item.packageName,
                    appName = item.appName,
                    notificationCatchEnabled = item.enabled,
                    forwardEnabled = false,
                    apiUrl = "",
                    httpMethod = HttpMethod.POST,
                    payloadType = PayloadType.FORM,
                    additionalValuesEditor = "apikey=hijilabs"
                )
            } else {
                PerAppConfigForm(
                    packageName = existing.packageName,
                    appName = existing.appName,
                    notificationCatchEnabled = existing.enabled,
                    forwardEnabled = existing.forwardEnabled,
                    apiUrl = existing.apiUrl,
                    httpMethod = existing.httpMethod,
                    payloadType = existing.payloadType,
                    additionalValuesEditor = mapToEditor(AdditionalValuesCodec.decode(existing.additionalValuesJson))
                )
            }
        }
    }

    fun closePerAppConfig() {
        editingConfig.value = null
    }

    fun updateEditingConfig(transform: (PerAppConfigForm) -> PerAppConfigForm) {
        editingConfig.update { current ->
            if (current == null) null else transform(current)
        }
    }

    fun saveEditingConfig() {
        val current = editingConfig.value ?: return
        viewModelScope.launch {
            repository.savePerAppForwardConfig(
                packageName = current.packageName,
                appName = current.appName,
                forwardEnabled = current.forwardEnabled,
                apiUrl = current.apiUrl,
                httpMethod = current.httpMethod,
                payloadType = current.payloadType,
                additionalValues = editorToMap(current.additionalValuesEditor)
            )
            editingConfig.value = null
        }
    }

    fun saveGlobalSettings(userAgent: String) {
        viewModelScope.launch {
            repository.saveGlobalSettings(userAgent)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            installedApps.value = repository.getInstalledApps()
        }
    }

    private fun editorToMap(editor: String): Map<String, String> {
        return editor
            .lineSequence()
            .map { it.trim() }
            .filter { it.contains("=") }
            .map {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
            .filter { it.first.isNotBlank() }
            .toMap()
    }

    private fun mapToEditor(values: Map<String, String>): String {
        return values.entries.joinToString("\n") { (key, value) -> "$key=$value" }
    }
}
