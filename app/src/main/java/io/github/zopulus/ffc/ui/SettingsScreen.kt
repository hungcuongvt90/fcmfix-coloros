package com.kooritea.fcmfix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kooritea.fcmfix.R
import com.kooritea.fcmfix.util.SelfCheck.Level

private const val PROJECT_URL = "https://github.com/ByteJuno/fcmfix-coloros"
private const val REPORT_GUIDE_URL = "$PROJECT_URL/blob/master/docs/report-issue.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    actions: AppActions,
    nav: Navigation,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val config = state.config
    val editable = config != null
    var confirmAllowAll by rememberSaveable { mutableStateOf(false) }
    val fcmApps = state.apps?.filter { it.includeFcm }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("settings.ini") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "&Back\\tF7") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader("Pushing") }
            item {
                GroupCard {
                    SwitchRow(
                        R.drawable.ic_notifications, "Prevent automatic clearing of notifications when the app is stopped",
                        "Retain notifications already sent when the release application is stopped by the system",
                        config?.keepNotifications == true, editable, actions::setKeepNotifications,
                    )
                    RowDivider()
                    SwitchRow(
                        R.drawable.ic_icebox, "Allow waking up apps frozen by the fridge",
                        "Unfreeze the app via the Freezer SDK when the push notification arrives. The message that triggers the unfreezing may not be received. You will need to wait for the next message or for the app to synchronize itself.",
                        config?.iceBox == true, editable, actions::setIceBox,
                    )
                }
            }
            item { SectionHeader("Applications") }
            item {
                GroupCard {
                    val auto = config?.autoAllowFcm == true
                    ActionRow(
                        R.drawable.ic_select_all, "Allow all apps that contain FCM",
                        when {
                            auto -> "Automatic release is enabled. No need to release all."
                            fcmApps == null -> "Reading app list..."
                            else -> "Total ${fcmApps.size}; apps installed after this will not be allowed automatically"
                        },
                        enabled = editable && !auto && fcmApps != null,
                    ) { confirmAllowAll = true }
                }
            }
            item { SectionHeader("For") }
            item {
                GroupCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { LevelIcon(Level.INFO) },
                        headlineContent = { Text("FCMFix ColorOS ${state.versionName}", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Based on upstream fcmfix 53. LSPosed Scope: System Framework + Battery") },
                    )
                    RowDivider()
                    ActionRow(R.drawable.ic_code, "Study Homepage", "github.com/ByteJuno/fcmfix-coloros", trailing = R.drawable.ic_open_in_new) {
                        nav.openUrl(PROJECT_URL)
                    }
                    RowDivider()
                    ActionRow(R.drawable.ic_guide, "How to Submit Issues and Logs", "Troubleshooting Steps for When Push Notifications Are Not Received", trailing = R.drawable.ic_open_in_new) {
                        nav.openUrl(REPORT_GUIDE_URL)
                    }
                }
            }
        }
    }
    if (confirmAllowAll && fcmApps != null) {
        AlertDialog(
            onDismissRequest = { confirmAllowAll = false },
            title = { Text("Allow all apps that contain FCM?") },
            text = { Text("${fcmApps.size} apps will be allowed. Apps installed after this will need to be allowed manually, or you can enable \"Automatically allow apps that contain FCM.\"") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAllowAll = false
                    actions.allowAllFcm()
                }) { Text("Release") }
            },
            dismissButton = { TextButton(onClick = { confirmAllowAll = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun SettingIcon(icon: Int) {
    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(icon: Int, title: String, summary: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { SettingIcon(icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}

@Composable
private fun ActionRow(
    icon: Int,
    title: String,
    summary: String,
    enabled: Boolean = true,
    trailing: Int? = null,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        ),
        leadingContent = { SettingIcon(icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(summary) },
        trailingContent = trailing?.let { { SettingIcon(it) } },
    )
}
