package com.kooritea.fcmfix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kooritea.fcmfix.R
import com.kooritea.fcmfix.ui.data.InstalledApp
import com.kooritea.fcmfix.ui.data.ModuleConnection
import com.kooritea.fcmfix.ui.model.AppCheck
import com.kooritea.fcmfix.ui.model.AppVerdict
import com.kooritea.fcmfix.ui.theme.levelColor
import com.kooritea.fcmfix.util.SelfCheck.Level
import java.util.Locale

/** An installed app with its allow state and, once allowed, what system_server reports for it. */
data class AppItem(
    val app: InstalledApp,
    val allowed: Boolean,
    /** Allowed by the auto-allow switch rather than ticked by hand. */
    val auto: Boolean,
    /** Only for allowed apps system_server already reported. */
    val check: AppCheck?,
) {
    val verdict: AppVerdict? = check?.let { AppVerdict.of(it) }
    val attention: Boolean get() = allowed && verdict?.attention == true
}

fun appItems(state: AppUiState): List<AppItem> {
    val apps = state.apps ?: return emptyList()
    val config = state.config
    val checks = state.report?.status?.appChecks.orEmpty()
    return apps.map { app ->
        val allowed = config?.allows(app.packageName, app.includeFcm) == true
        AppItem(
            app, allowed,
            auto = config?.allowsAutomatically(app.packageName, app.includeFcm) == true,
            check = if (allowed) checks[app.packageName] else null,
        )
    }
}

enum class AppFilter(val label: String) {
    ALL("All"), FCM("Includes FCM"), ALLOWED("Released"), ATTENTION("Needs Attention");

    fun matches(item: AppItem): Boolean = when (this) {
        ALL -> true
        FCM -> item.app.includeFcm
        ALLOWED -> item.allowed
        ATTENTION -> item.attention
    }
}

fun AppItem.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase(Locale.ROOT)
    return app.label.lowercase(Locale.ROOT).contains(q) || app.packageName.lowercase(Locale.ROOT).contains(q)
}

/** "Received push notification 5 minutes ago". */
@Composable
fun pushText(lastPush: Long): String = LocalRelativeTime.current(lastPush) + "Push notification received"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsTab(state: AppUiState, actions: AppActions, nav: Navigation, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    val items = remember(state.apps, state.config, state.report) { appItems(state) }
    val shown = remember(items, query, filter) { items.filter { filter.matches(it) && it.matches(query) } }
    val editable = state.config != null
    val focus = remember { FocusRequester() }

    BackHandler(searching) {
        searching = false
        query = ""
    }
    TabScaffold(
        title = {
            if (searching) {
                LaunchedEffect(Unit) { focus.requestFocus() }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Search for app name or package name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            } else {
                Text("Applications")
            }
        },
        modifier = modifier,
        scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
        navigationIcon = {
            if (searching) {
                IconButton(onClick = {
                    searching = false
                    query = ""
                }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Close Search") }
            }
        },
        actions = {
            if (searching) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear") }
                }
            } else {
                IconButton(onClick = { searching = true }) { Icon(painterResource(R.drawable.ic_search), contentDescription = "Cari") }
                IconButton(onClick = nav.openSettings) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "settings.ini") }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.scanning && state.apps != null,
            onRefresh = actions::scanApps,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    AutoAllowCard(
                        checked = state.config?.autoAllowFcm == true,
                        enabled = editable,
                        onChange = actions::setAutoAllow,
                    )
                }
                item {
                    FilterRow(filter, items) { filter = it }
                }
                when {
                    state.apps == null -> item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    shown.isEmpty() -> item {
                        Text(
                            if (filter == AppFilter.ATTENTION && query.isBlank()) "There are no issues with the approved applications." else "No matching apps",
                            Modifier.fillMaxWidth().padding(48.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> itemsIndexed(shown, key = { _, item -> item.app.packageName }) { index, item ->
                        Column(Modifier.groupItem(index, shown.size, MaterialTheme.colorScheme.surfaceBright)) {
                            if (index > 0) RowDivider()
                            AppRowItem(
                                item,
                                enabled = editable,
                                onToggle = { actions.setAllowed(item.app, it) },
                                onClick = { nav.openApp(item.app.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoAllowCard(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    GroupCard(Modifier.padding(top = 8.dp)) {
        ListItem(
            modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Automatically allow apps that contain FCM", fontWeight = FontWeight.Medium) },
            supportingContent = { Text("Newly installed apps will also be allowed automatically; apps that are turned off in the list will be excluded.") },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        )
    }
}

@Composable
private fun FilterRow(selected: AppFilter, items: List<AppItem>, onSelect: (AppFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (filter in AppFilter.entries) {
            val count = items.count { filter.matches(it) }
            if (filter == AppFilter.ATTENTION && count == 0) continue
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(if (items.isEmpty()) filter.label else "${filter.label} $count") },
            )
        }
    }
}

@Composable
fun AppRowItem(item: AppItem, enabled: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val verdict = item.verdict
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { AppIcon(item.app.packageName, item.app.label) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.app.includeFcm) {
                    Spacer(Modifier.width(6.dp))
                    Pill("FCM", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        },
        supportingContent = {
            Column {
                Text(item.app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                val problem = verdict?.text
                val lastPush = item.check?.lastPush ?: 0
                when {
                    problem != null -> Text(problem, color = levelColor(verdict.level), style = MaterialTheme.typography.bodySmall)
                    item.allowed && lastPush > 0 -> Text(
                        pushText(lastPush), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = { Switch(checked = item.allowed, onCheckedChange = onToggle, enabled = enabled) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(item: AppItem, state: AppUiState, actions: AppActions, nav: Navigation, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        AppDetailContent(
            item,
            editable = state.config != null,
            onToggle = { actions.setAllowed(item.app, it) },
            onOpenSettings = { nav.openAppSettings(item.app.packageName) },
            onExport = {
                onDismiss()
                actions.export(item.app.packageName)
            },
            exportable = state.connection is ModuleConnection.State.Bound,
        )
    }
}

/** The sheet's content, apart so screenshot tests can render it without a window. */
@Composable
fun AppDetailContent(
    item: AppItem,
    editable: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
    exportable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(item.app.packageName, item.app.label, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(item.app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.app.includeFcm || item.auto) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.app.includeFcm) {
                            Pill("Includes FCM", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        if (item.auto) {
                            Pill("Automatic Clearance", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GroupCard {
            ListItem(
                modifier = Modifier.clickable(enabled = editable) { onToggle(!item.allowed) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Release", fontWeight = FontWeight.Medium) },
                supportingContent = { Text(allowSummary(item)) },
                trailingContent = { Switch(checked = item.allowed, onCheckedChange = onToggle, enabled = editable) },
            )
        }
        val check = item.check
        val verdict = item.verdict
        if (check != null && verdict != null) {
            SectionHeader("Since power-on")
            GroupCard {
                DetailRow(verdict.notify.level, "wpbakery notice", verdict.notify.text, verdict.notify.hint)
                verdict.bucket?.let {
                    RowDivider()
                    DetailRow(it.level, "Standby Group", it.text.removePrefix("Standby Group:"), it.hint)
                }
                RowDivider()
                DetailRow(
                    if (check.lastPush > 0) Level.OK else Level.INFO, "Pushing",
                    if (check.lastPush == 0L) "No push notifications received yet" else pushText(check.lastPush) + "· ${check.pushCount} times in total",
                    null,
                )
                RowDivider()
                DetailRow(
                    Level.INFO, "Process",
                    when {
                        check.running -> "In operation"
                        check.stopped -> "Stopped, pulled up by the module when pushed"
                        else -> "not running smoothly."
                    },
                    null,
                )
            }
            NoteText("\"Push received\" means that GMS has delivered the message to the app. If a push notification is received but no notification pops up, the problem is usually with the app's own notification settings or the app itself.")
        } else {
            NoteText(
                if (item.allowed) "The system framework has not yet reported this app. Refresh the \"Status\" later to check again." else "After approval, the notification status and push records will be displayed here",
                Modifier.padding(top = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Apply Settings")
            }
            if (item.allowed && exportable) {
                FilledTonalButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_report), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Report")
                }
            }
        }
    }
}

private fun allowSummary(item: AppItem): String = when {
    item.auto -> "Allowed by \"Automatically allow apps containing FCM.\" It will be excluded after being disabled."
    item.allowed -> "When a push notification is received, the module wakes up and allows the app to run."
    item.app.includeFcm -> "After allowing, the module will wake up this app when a push notification is received."
    else -> "No FCM component detected; release usually has no effect."
}

@Composable
private fun DetailRow(level: Level, title: String, text: String, hint: String?) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { LevelIcon(level, size = 36.dp) },
        overlineContent = { Text(title) },
        headlineContent = { Text(text) },
        supportingContent = hint?.let { { Text(it, color = levelColor(level), style = MaterialTheme.typography.bodySmall) } },
    )
}
