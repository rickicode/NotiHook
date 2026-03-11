package com.hijitoko.notihook

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.hijitoko.notihook.data.ForwardStatus
import com.hijitoko.notihook.data.HttpMethod
import com.hijitoko.notihook.data.NotificationRecordEntity
import com.hijitoko.notihook.data.PayloadType
import com.hijitoko.notihook.ui.AppUiItem
import com.hijitoko.notihook.ui.MainViewModel
import com.hijitoko.notihook.ui.theme.NotiHookTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)
        enableEdgeToEdge()
        setContent {
            NotiHookTheme {
                MainScreen(
                    viewModel = vm,
                    onOpenNotificationAccess = {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    },
                    onOpenBatteryOptimization = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:$packageName"))
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshBackgroundVerification()
    }
}

private data class BottomDestination(
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private enum class AppListFilter {
    ALL,
    ACTIVE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onOpenNotificationAccess: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
) {
    val context = LocalContext.current
    val destinations = listOf(
        BottomDestination(R.string.tab_apps, Icons.Rounded.Home),
        BottomDestination(R.string.tab_histories, Icons.AutoMirrored.Rounded.List),
        BottomDestination(R.string.tab_settings, Icons.Rounded.Settings),
    )
    val tabIndex by viewModel.selectedTabIndex.collectAsState()
    val appItems by viewModel.appItems.collectAsState()
    val histories by viewModel.histories.collectAsState()
    val global by viewModel.globalSettings.collectAsState()
    val background by viewModel.backgroundVerificationState.collectAsState()
    val editingConfig by viewModel.editingConfigState.collectAsState()
    val hostActivity = context.findActivity()
    val interstitialController = remember(context) {
        InterstitialAdController(context.applicationContext)
    }

    LaunchedEffect(Unit) {
        interstitialController.preload()
    }

    if (editingConfig != null) {
        BackHandler(onBack = viewModel::closePerAppConfig)
        val currentItem = appItems.firstOrNull { it.packageName == editingConfig!!.packageName }
        PerAppConfigScreen(
            packageName = editingConfig!!.packageName,
            appName = editingConfig!!.appName,
            notificationCatchEnabled = editingConfig!!.notificationCatchEnabled,
            forwardEnabled = editingConfig!!.forwardEnabled,
            notificationAccessEnabled = background.notificationAccessEnabled,
            apiUrl = editingConfig!!.apiUrl,
            httpMethod = editingConfig!!.httpMethod,
            payloadType = editingConfig!!.payloadType,
            additionalValues = editingConfig!!.additionalValuesEditor,
            onUpdate = { forwardEnabled, apiUrl, httpMethod, payloadType, additionalValues ->
                viewModel.updateEditingConfig {
                    it.copy(
                        forwardEnabled = forwardEnabled,
                        apiUrl = apiUrl,
                        httpMethod = httpMethod,
                        payloadType = payloadType,
                        additionalValuesEditor = additionalValues,
                    )
                }
            },
            onToggleNotificationCatch = { enabled ->
                viewModel.updateEditingConfig {
                    it.copy(
                        notificationCatchEnabled = enabled,
                        forwardEnabled = if (enabled) it.forwardEnabled else false,
                    )
                }
                if (currentItem != null) {
                    viewModel.setAppEnabled(currentItem, enabled)
                }
            },
            onToggleForward = { enabled ->
                viewModel.updateEditingConfig { it.copy(forwardEnabled = enabled) }
            },
            onDismiss = viewModel::closePerAppConfig,
            onSave = viewModel::saveEditingConfig,
        )
        LaunchedEffect(Unit) {
            viewModel.refreshBackgroundVerification()
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        Text(
                            when (tabIndex) {
                                0 -> context.getString(R.string.topbar_apps_subtitle, appItems.size)
                                1 -> context.getString(R.string.topbar_histories_subtitle, histories.size)
                                else -> context.getString(R.string.topbar_settings_subtitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(innerPadding)
        ) {
            when (tabIndex) {
                0 -> AppsTab(
                    appItems = appItems,
                    notificationAccessEnabled = background.notificationAccessEnabled,
                    onOpenSettings = { viewModel.selectTab(2) },
                    onOpenConfig = { item ->
                        if (hostActivity == null) {
                            viewModel.openPerAppConfig(item)
                        } else {
                            interstitialController.showOrContinue(hostActivity) {
                                viewModel.openPerAppConfig(item)
                            }
                        }
                    },
                )

                1 -> HistoriesTab(histories = histories)
                else -> SettingsTab(
                    userAgent = global.userAgent,
                    notificationAccessEnabled = background.notificationAccessEnabled,
                    batteryIgnored = background.batteryOptimizationIgnored,
                    onSave = viewModel::saveGlobalSettings,
                    onRefreshVerification = viewModel::refreshBackgroundVerification,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                    onOpenBatteryOptimization = onOpenBatteryOptimization,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBackgroundVerification()
    }
}

@Composable
private fun AppsTab(
    appItems: List<AppUiItem>,
    notificationAccessEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenConfig: (AppUiItem) -> Unit,
) {
    val enabledCount = remember(appItems) { appItems.count { it.enabled } }
    val inactiveCount = remember(appItems) { appItems.size - enabledCount }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var appFilter by rememberSaveable { mutableStateOf(AppListFilter.ALL) }
    val filteredItems = remember(appItems, searchQuery, appFilter) {
        val query = searchQuery.trim().lowercase()
        val baseItems = when (appFilter) {
            AppListFilter.ALL -> appItems
            AppListFilter.ACTIVE -> appItems.filter { it.enabled }
        }
        if (query.isBlank()) {
            baseItems
        } else {
            baseItems.filter { item ->
                item.appName.lowercase().contains(query) || item.packageName.lowercase().contains(query)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeroCard(appCount = appItems.size)
        if (!notificationAccessEnabled) {
            Surface(
                modifier = Modifier.clickable(onClick = onOpenSettings),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.banner_notification_access_inactive_title),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            stringResource(R.string.banner_notification_access_inactive_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_apps_label)) },
            placeholder = { Text(stringResource(R.string.search_apps_placeholder)) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryChip(
                label = stringResource(R.string.summary_all),
                value = appItems.size.toString(),
                highlighted = appFilter == AppListFilter.ALL,
                onClick = { appFilter = AppListFilter.ALL }
            )
            SummaryChip(
                label = stringResource(R.string.summary_active),
                value = enabledCount.toString(),
                highlighted = appFilter == AppListFilter.ACTIVE,
                onClick = { appFilter = AppListFilter.ACTIVE }
            )
            SummaryChip(label = stringResource(R.string.summary_off), value = inactiveCount.toString())
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                appItems.isEmpty() -> CenterAlignedEmptyState(
                    title = stringResource(R.string.empty_apps_title),
                    body = stringResource(R.string.empty_apps_body)
                )

                filteredItems.isEmpty() -> CenterAlignedEmptyState(
                    title = stringResource(R.string.empty_search_result),
                    body = ""
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems, key = { it.packageName }) { item ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenConfig(item) },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (item.enabled) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (item.enabled) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppListIcon(packageName = item.packageName, appName = item.appName)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(item.appName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                item.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (item.enabled) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = if (item.enabled) stringResource(R.string.app_status_active) else stringResource(R.string.app_status_off),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (item.enabled) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            if (item.enabled) stringResource(R.string.app_catch_active) else stringResource(R.string.app_catch_inactive),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            if (notificationAccessEnabled) stringResource(R.string.app_list_tap_hint_ready)
                                            else stringResource(R.string.app_list_tap_hint_permission),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (item.forwardEnabled) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = stringResource(R.string.badge_api),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                value,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun CenterAlignedEmptyState(
    title: String,
    body: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (body.isNotBlank()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AppListIcon(packageName: String, appName: String) {
    val context = LocalContext.current
    val appIconBitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        if (appIconBitmap != null) {
            Image(
                bitmap = appIconBitmap.asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Icon(
                Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HistoriesTab(histories: List<NotificationRecordEntity>) {
    var selected by remember { mutableStateOf<NotificationRecordEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.histories_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.histories_body),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            items(histories, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = item },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (item.title.isBlank()) stringResource(R.string.history_no_title) else item.title, fontWeight = FontWeight.SemiBold)
                        Text(item.text.ifBlank { item.bigText }, maxLines = 2)
                        Spacer(Modifier.height(6.dp))
                        Text("${item.appName} • ${formatDate(item.postedAt)}", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.history_status_forward, item.forwardStatus), style = MaterialTheme.typography.bodySmall)
                        if (item.forwardStatus == ForwardStatus.FAILED && item.forwardError.isNotBlank()) {
                            Text(stringResource(R.string.history_error, item.forwardError), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        BannerAdSlot(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }

    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.history_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.field_title, selected!!.title))
                    Text(stringResource(R.string.field_text, selected!!.text))
                    Text(stringResource(R.string.field_bigtext, selected!!.bigText))
                    Text(stringResource(R.string.field_subtext, selected!!.subText))
                    Text(stringResource(R.string.field_infotext, selected!!.infoText))
                    Text(stringResource(R.string.field_name, selected!!.appName))
                    Text(stringResource(R.string.field_pkg, selected!!.packageName))
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun SettingsTab(
    userAgent: String,
    notificationAccessEnabled: Boolean,
    batteryIgnored: Boolean,
    onSave: (String) -> Unit,
    onRefreshVerification: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
) {
    val context = LocalContext.current
    val appLabel = remember {
        context.applicationInfo.loadLabel(context.packageManager)?.toString().orEmpty().ifBlank {
            context.getString(R.string.app_name)
        }
    }
    val repositoryUrl = stringResource(R.string.about_repository_url)
    val allReady = notificationAccessEnabled && batteryIgnored
    var ua by rememberSaveable(userAgent) { mutableStateOf(userAgent) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (allReady) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = if (allReady) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (allReady) stringResource(R.string.settings_ready_title) else stringResource(R.string.settings_check_title),
                                fontWeight = FontWeight.Bold,
                                color = if (allReady) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                            Text(
                                if (allReady) {
                                    stringResource(R.string.settings_ready_body)
                                } else {
                                    stringResource(R.string.settings_check_body)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (allReady) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.settings_permissions_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_notification_title),
                            description = stringResource(R.string.permission_notification_body),
                            active = notificationAccessEnabled
                        )
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_battery_title),
                            description = stringResource(R.string.permission_battery_body),
                            active = batteryIgnored
                        )
                        if (!notificationAccessEnabled || !batteryIgnored) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!notificationAccessEnabled) {
                                    OutlinedButton(onClick = onOpenNotificationAccess) {
                                        Text(stringResource(R.string.allow_notification))
                                    }
                                }
                                if (!batteryIgnored) {
                                    OutlinedButton(onClick = onOpenBatteryOptimization) {
                                        Text(stringResource(R.string.allow_battery))
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = onRefreshVerification) {
                            Text(stringResource(R.string.recheck))
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.settings_forward_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.settings_user_agent_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = ua,
                            onValueChange = { ua = it },
                            label = { Text(stringResource(R.string.global_user_agent)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(stringResource(R.string.default_format, com.hijitoko.notihook.data.GlobalSettingsEntity.DEFAULT_USER_AGENT))
                            }
                        )
                        Button(onClick = { onSave(ua) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.save_forward_settings))
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(appLabel, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.about_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.about_features),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.about_open_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            repositoryUrl,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(repositoryUrl))
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        BannerAdSlot(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ) {
            Text(
                text = if (active) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

@Composable
private fun AppHeroCard(appCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.hero_apps_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.hero_apps_body, appCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerAppConfigScreen(
    packageName: String,
    appName: String,
    notificationCatchEnabled: Boolean,
    forwardEnabled: Boolean,
    notificationAccessEnabled: Boolean,
    apiUrl: String,
    httpMethod: String,
    payloadType: String,
    additionalValues: String,
    onUpdate: (forwardEnabled: Boolean, apiUrl: String, httpMethod: String, payloadType: String, additionalValues: String) -> Unit,
    onToggleNotificationCatch: (Boolean) -> Unit,
    onToggleForward: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    var localForwardEnabled by rememberSaveable(forwardEnabled) { mutableStateOf(forwardEnabled) }
    var localApiUrl by rememberSaveable(apiUrl) { mutableStateOf(apiUrl) }
    var localMethod by rememberSaveable(httpMethod) { mutableStateOf(httpMethod) }
    var localPayloadType by rememberSaveable(payloadType) { mutableStateOf(payloadType) }
    var localAdditionalValues by rememberSaveable(additionalValues) { mutableStateOf(additionalValues) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.app_settings_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            appName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (notificationCatchEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = if (notificationCatchEnabled) stringResource(R.string.app_catch_enabled_badge) else stringResource(R.string.app_catch_off_badge),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (notificationCatchEnabled) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (localForwardEnabled) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.badge_api),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.app_settings_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        DialogSection(title = stringResource(R.string.app_settings_catch_section)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(stringResource(R.string.app_settings_catch_toggle), fontWeight = FontWeight.Medium)
                                    Text(
                                        if (notificationAccessEnabled) {
                                            stringResource(R.string.app_settings_catch_body_ready)
                                        } else {
                                            stringResource(R.string.app_settings_catch_body_permission)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = notificationCatchEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled && !notificationAccessEnabled) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.permission_toast_notification_required),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            if (!enabled) {
                                                localForwardEnabled = false
                                                onToggleForward(false)
                                                onUpdate(false, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                            }
                                            onToggleNotificationCatch(enabled)
                                        }
                                    }
                                )
                            }
                        }
                        if (notificationCatchEnabled) {
                            DialogSection(title = stringResource(R.string.app_settings_forward_section)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(stringResource(R.string.app_settings_forward_toggle), fontWeight = FontWeight.Medium)
                                        Text(
                                            stringResource(R.string.app_settings_forward_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Switch(
                                        checked = localForwardEnabled,
                                        onCheckedChange = { enabled ->
                                            if (!enabled) {
                                                localForwardEnabled = false
                                                onToggleForward(false)
                                                onUpdate(false, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                            } else {
                                                val validationMessage = validateForwardConfig(context, localApiUrl, localAdditionalValues)
                                                if (validationMessage != null) {
                                                    Toast.makeText(context, validationMessage, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    localForwardEnabled = true
                                                    onToggleForward(true)
                                                    onUpdate(true, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            DialogSection(title = stringResource(R.string.app_settings_api_section)) {
                                OutlinedTextField(
                                    value = localApiUrl,
                                    onValueChange = {
                                        localApiUrl = it
                                        if (localForwardEnabled && validateForwardConfig(context, it, localAdditionalValues) != null) {
                                            localForwardEnabled = false
                                            onToggleForward(false)
                                        }
                                        onUpdate(localForwardEnabled, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                    },
                                    label = { Text(stringResource(R.string.api_url)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(stringResource(R.string.api_url_example))
                                    }
                                )
                            }
                            DialogSection(title = stringResource(R.string.request_format)) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        stringResource(R.string.http_method),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    MinimalSegmentRow(
                                        firstLabel = "GET",
                                        firstSelected = localMethod == HttpMethod.GET,
                                        onFirstClick = {
                                            localMethod = HttpMethod.GET
                                            onUpdate(localForwardEnabled, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                        },
                                        secondLabel = "POST",
                                        secondSelected = localMethod == HttpMethod.POST,
                                        onSecondClick = {
                                            localMethod = HttpMethod.POST
                                            onUpdate(localForwardEnabled, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                        }
                                    )
                                    Text(
                                        stringResource(R.string.payload_type),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    MinimalSegmentRow(
                                        firstLabel = "FORM",
                                        firstSelected = localPayloadType == PayloadType.FORM,
                                        onFirstClick = {
                                            localPayloadType = PayloadType.FORM
                                            onUpdate(localForwardEnabled, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                        },
                                        secondLabel = "JSON",
                                        secondSelected = localPayloadType == PayloadType.JSON,
                                        onSecondClick = {
                                            localPayloadType = PayloadType.JSON
                                            onUpdate(localForwardEnabled, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                        }
                                    )
                                }
                            }
                            DialogSection(title = stringResource(R.string.additional_values)) {
                                OutlinedTextField(
                                    value = localAdditionalValues,
                                    onValueChange = {
                                        localAdditionalValues = it
                                        if (localForwardEnabled && validateForwardConfig(context, localApiUrl, it) != null) {
                                            localForwardEnabled = false
                                            onToggleForward(false)
                                        }
                                        onUpdate(localForwardEnabled, localApiUrl, localMethod, localPayloadType, localAdditionalValues)
                                    },
                                    label = { Text(stringResource(R.string.key_value_per_line)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 6,
                                    supportingText = {
                                        Text(stringResource(R.string.additional_values_example))
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (notificationCatchEnabled) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val validationMessage = validateForwardConfig(context, localApiUrl, localAdditionalValues)
                            if (localForwardEnabled && validationMessage != null) {
                                Toast.makeText(context, validationMessage, Toast.LENGTH_SHORT).show()
                            } else {
                                onSave()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.app_settings_api_save))
                    }
                }
            }
            }
            BannerAdSlot(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun BannerAdSlot(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    if (adUnitId.isBlank()) return

    AndroidView(
        modifier = modifier,
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { view ->
            if (view.adUnitId != adUnitId) {
                view.adUnitId = adUnitId
                view.loadAd(AdRequest.Builder().build())
            }
        }
    )
}

private class InterstitialAdController(
    private val appContext: Context,
) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun preload() {
        val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID
        if (adUnitId.isBlank() || isLoading || interstitialAd != null) return

        isLoading = true
        InterstitialAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                }
            }
        )
    }

    fun showOrContinue(
        activity: Activity,
        onComplete: () -> Unit,
    ) {
        val ad = interstitialAd
        if (ad == null) {
            preload()
            onComplete()
            return
        }

        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload()
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                preload()
                onComplete()
            }
        }
        ad.show(activity)
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun validateForwardConfig(context: android.content.Context, apiUrl: String, additionalValues: String): String? {
    val trimmedUrl = apiUrl.trim()
    if (trimmedUrl.isBlank()) return context.getString(R.string.validation_api_url_required)

    val parsedUrl = Uri.parse(trimmedUrl)
    val hasValidScheme = parsedUrl.scheme == "http" || parsedUrl.scheme == "https"
    if (!hasValidScheme || parsedUrl.host.isNullOrBlank()) {
        return context.getString(R.string.validation_api_url_invalid)
    }

    additionalValues
        .lineSequence()
        .mapIndexed { index, line -> index + 1 to line.trim() }
        .filter { (_, line) -> line.isNotBlank() }
        .forEach { (lineNumber, line) ->
            if (!line.contains("=")) {
                return context.getString(R.string.validation_additional_value_format, lineNumber)
            }

            val parts = line.split("=", limit = 2)
            if (parts[0].trim().isBlank()) {
                return context.getString(R.string.validation_additional_value_key, lineNumber)
            }
        }

    return null
}

@Composable
private fun DialogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun MinimalSegmentRow(
    firstLabel: String,
    firstSelected: Boolean,
    onFirstClick: () -> Unit,
    secondLabel: String,
    secondSelected: Boolean,
    onSecondClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onFirstClick,
            modifier = Modifier.weight(1f),
            colors = if (firstSelected) {
                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            }
        ) {
            Text(firstLabel)
        }
        OutlinedButton(
            onClick = onSecondClick,
            modifier = Modifier.weight(1f),
            colors = if (secondSelected) {
                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            }
        ) {
            Text(secondLabel)
        }
    }
}

private fun formatDate(time: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(time))
}
