package com.kooritea.fcmfix.ui

import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.kooritea.fcmfix.R
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.kooritea.fcmfix.FcmfixConfig
import com.kooritea.fcmfix.FcmfixUiState
import com.kooritea.fcmfix.InstalledApp

private enum class MainTab(val label: String, val appBarTitle: String = label) {
    HOME("Home", "Fcmfix"),
    APPS("Applications"),
    SETTINGS("settings.ini")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FcmfixApp(
    state: FcmfixUiState,
    apps: List<InstalledApp>,
    versionName: String,
    launcherIconHidden: Boolean,
    onConfigChange: (FcmfixConfig) -> Unit,
    onLauncherIconHiddenChange: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProject: () -> Unit,
    onReloadApps: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = MainTab.entries[pagerState.currentPage]
    var appFilter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    val appListSession = remember(pagerState.settledPage) { AppListSession() }
    val onAppConfigChange: (FcmfixConfig) -> Unit = {
        appListSession.onUserEdit()
        onConfigChange(it)
    }
    val haptics = LocalHapticFeedback.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal
        ),
        bottomBar = {
            ShortNavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            ) {
                MainTab.entries.forEach { tab ->
                    val selectedIcon = when (tab) {
                        MainTab.HOME -> Icons.Filled.Home
                        MainTab.APPS -> Icons.AutoMirrored.Filled.List
                        MainTab.SETTINGS -> Icons.Filled.Settings
                    }
                    val unselectedIcon = when (tab) {
                        MainTab.HOME -> Icons.Outlined.Home
                        MainTab.APPS -> Icons.AutoMirrored.Outlined.List
                        MainTab.SETTINGS -> Icons.Outlined.Settings
                    }
                    ShortNavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            if (selectedTab != tab) {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                            }
                        },
                        icon = {
                            Icon(
                                if (selectedTab == tab) selectedIcon else unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { contentPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
        ) { page ->
            val pageScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
            Column(Modifier.fillMaxSize().nestedScroll(pageScrollBehavior.nestedScrollConnection)) {
                TwoRowsTopAppBar(
                    expandedHeight = TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight,
                    title = { expanded ->
                        val titleStyle = if (expanded) MaterialTheme.typography.displaySmall
                            else MaterialTheme.typography.titleLarge
                        if (MainTab.entries[page] == MainTab.HOME && expanded) {
                            Row(
                                modifier = Modifier.padding(start = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Fcmfix", modifier = Modifier.alignByBaseline(), style = titleStyle)
                                Text(
                                    "for ColorOS17",
                                    modifier = Modifier.alignByBaseline(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                MainTab.entries[page].appBarTitle,
                                modifier = Modifier.padding(start = 4.dp),
                                style = titleStyle
                            )
                        }
                    },
                    subtitle = if (MainTab.entries[page] == MainTab.HOME) {
                        { expanded ->
                            if (!expanded) {
                                Text(
                                    "for ColorOS17",
                                    modifier = Modifier.padding(start = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else null,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = pageScrollBehavior,
                    actions = {
                        if (MainTab.entries[page] == MainTab.APPS) {
                            AppListActions(
                                state = state,
                                apps = apps,
                                selectedFilter = appFilter,
                                onFilterChange = { appFilter = it },
                                onConfigChange = onAppConfigChange
                            )
                        }
                    }
                )
                val pageModifier = Modifier.weight(1f).fillMaxWidth()
                when (MainTab.entries[page]) {
                MainTab.HOME -> HomeScreen(
                    modifier = pageModifier,
                    state = state,
                    apps = apps,
                    versionName = versionName,
                    onOpenApps = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.APPS.ordinal) } },
                    onOpenSettings = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.SETTINGS.ordinal) } },
                    onOpenDiagnostics = onOpenDiagnostics,
                    onOpenProject = onOpenProject
                )
                MainTab.APPS -> AppsScreen(
                    modifier = pageModifier,
                    session = appListSession,
                    selectedFilter = appFilter,
                    state = state,
                    apps = apps,
                    onConfigChange = onAppConfigChange,
                    onReloadApps = onReloadApps
                )
                MainTab.SETTINGS -> SettingsScreen(
                    modifier = pageModifier,
                    state = state,
                    launcherIconHidden = launcherIconHidden,
                    onConfigChange = onConfigChange,
                    onLauncherIconHiddenChange = onLauncherIconHiddenChange
                )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: FcmfixUiState,
    apps: List<InstalledApp>,
    versionName: String,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProject: () -> Unit
) {
    val config = state.config
    val allowedFcmCount = apps.count { it.packageName in config.allowedPackages }
    val enabledGoogleProtections = listOf(
        config.deepSleepGoogleWhitelist,
        config.dozeGoogleWhitelist
    ).count { it }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeStatusCard(connected = state.xposedConnected, versionName = versionName)
        SystemInfoCard()
        SegmentedInfoList(
            items = listOf(
                HomeInfoItem(
                    icon = ImageVector.vectorResource(R.drawable.ic_notifications),
                    title = "FCM App Wake-up",
                    summary = if (state.appsLoading && apps.isEmpty()) "Reading app list..." else if (state.appsLoadFailed && apps.isEmpty()) {
                        "Unable to read app list"
                    } else {
                        "$allowedFcmCount / ${apps.size} apps allowed"
                    },
                    onClick = onOpenApps
                ),
                HomeInfoItem(
                    icon = ImageVector.vectorResource(R.drawable.ic_shield),
                    title = "Google Network Protection",
                    summary = "$enabledGoogleProtections / 2 enabled",
                    onClick = onOpenSettings
                )
            )
        )
        SegmentedInfoList(
            items = listOf(
                HomeInfoItem(
                    icon = ImageVector.vectorResource(R.drawable.ic_troubleshoot),
                    title = "Google Play Services Diagnostics",
                    onClick = onOpenDiagnostics
                ),
                HomeInfoItem(
                    icon = Icons.Outlined.Info,
                    title = "About fcmfix",
                    onClick = onOpenProject
                )
            )
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeStatusCard(connected: Boolean, versionName: String) {
    val containerColor = if (connected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (connected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = if (connected) Icons.Rounded.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null
                )
            },
            trailingContent = {
                Surface(
                    modifier = Modifier.padding(end = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        "API 102",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmallEmphasized
                    )
                }
            },
            supportingContent = {
                Text("Version $versionName", style = MaterialTheme.typography.bodyMedium)
            },
            verticalAlignment = Alignment.CenterVertically,
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = contentColor,
                leadingContentColor = contentColor,
                trailingContentColor = contentColor,
                supportingContentColor = contentColor.copy(alpha = 0.7f)
            ),
            elevation = ListItemDefaults.elevation(),
            content = {
                Text(
                    if (connected) "Activated" else "Unactivated",
                    style = MaterialTheme.typography.titleMediumEmphasized
                )
            }
        )
    }
}

@Composable
private fun SystemInfoCard() {
    val systemInfo = remember {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val device = if (model.startsWith(manufacturer, ignoreCase = true)) model
            else listOf(manufacturer, model).filter(String::isNotBlank).joinToString(" ")
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
        val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrNull()
        val architecture = if (pageSize != null && pageSize > 0) {
            "$abi (${pageSize / 1024}K)"
        } else abi
        listOf(
            "System version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Equipment" to device.ifBlank { "Unknown" },
            "Structure of the System" to architecture
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            systemInfo.forEach { (title, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class HomeInfoItem(
    val icon: ImageVector,
    val title: String,
    val summary: String? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun expressiveSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceBright,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SegmentedInfoList(items: List<HomeInfoItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            key(item.title) {
                val interactionSource = remember { MutableInteractionSource() }
                val haptics = LocalHapticFeedback.current
                SegmentedListItem(
                    verticalAlignment = Alignment.CenterVertically,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        item.onClick()
                    },
                    shapes = ListItemDefaults.segmentedShapes(index, items.size),
                    interactionSource = interactionSource,
                    colors = expressiveSegmentedColors(),
                    leadingContent = {
                        Icon(item.icon, contentDescription = null)
                    },
                    supportingContent = item.summary?.let { summary ->
                        { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    },
                    content = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

@Composable
private fun AppListActions(
    state: FcmfixUiState,
    apps: List<InstalledApp>,
    selectedFilter: AppFilter,
    onFilterChange: (AppFilter) -> Unit,
    onConfigChange: (FcmfixConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        IconButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            expanded = true
        }) {
            Icon(ImageVector.vectorResource(R.drawable.ic_more_vert), contentDescription = "Apply Filters and Select All")
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 2),
                modifier = Modifier.width(200.dp)
            ) {
                AppFilter.entries.forEachIndexed { index, filter ->
                    SelectableDropdownMenuItem(
                        selected = selectedFilter == filter,
                        shapes = MenuDefaults.itemShape(index, AppFilter.entries.size),
                        text = { Text(filter.title) },
                        selectedLeadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onFilterChange(filter)
                            expanded = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 1, count = 2),
                modifier = Modifier.width(200.dp)
            ) {
                SelectableDropdownMenuItem(
                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                    selected = apps.isNotEmpty() && apps.all { it.packageName in state.config.allowedPackages },
                    text = { Text("Select all") },
                    enabled = !state.appsLoading && !state.appsLoadFailed && apps.isNotEmpty(),
                    selectedLeadingIcon = {
                        Icon(Icons.Filled.Check, contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize))
                    },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        val detected = apps.mapTo(mutableSetOf(), InstalledApp::packageName)
                        onConfigChange(state.config.copy(allowedPackages = state.config.allowedPackages + detected))
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AppsScreen(
    modifier: Modifier,
    session: AppListSession,
    selectedFilter: AppFilter,
    state: FcmfixUiState,
    apps: List<InstalledApp>,
    onConfigChange: (FcmfixConfig) -> Unit,
    onReloadApps: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val allowed = state.config.allowedPackages
    val allowedFcmCount = apps.count { it.packageName in allowed }
    val snapshots = session.snapshots(allowed, selectedFilter)
    val visit = snapshots.order
    val filterSnapshot = snapshots.filter
    val orderedApps = remember(apps, visit) {
        visit.grouped(apps, InstalledApp::packageName)
    }
    val visibleApps = remember(orderedApps, query, selectedFilter, filterSnapshot) {
        orderedApps.asSequence()
            .filter { filterSnapshot.includes(it.packageName, selectedFilter) }
            .filter { app ->
                query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
            .toList()
    }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search for app name or package name") },
                shape = MaterialTheme.shapes.extraLarge
            )

            Text(
                "$allowedFcmCount / ${apps.size} enabled" + if (state.appsLoading) "Refreshing…" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }

        if (state.appsLoadFailed && apps.isNotEmpty()) {
            TextButton(onClick = onReloadApps) { Text("Refresh failed. Tap to retry.") }
        }

        when {
            state.appsLoading && apps.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.appsLoadFailed && apps.isEmpty() -> EmptyAppsState(
                title = "Unable to read FCM app",
                message = "Please check the app list permissions and try again.",
                onReload = onReloadApps
            )
            visibleApps.isEmpty() -> EmptyAppsState(
                title = when {
                    query.isNotBlank() -> "No Search Results"
                    selectedFilter == AppFilter.ALLOWED -> "No FCM apps enabled yet"
                    selectedFilter == AppFilter.DISABLED -> "No closed FCM apps"
                    else -> "No FCM app detected"
                },
                message = when {
                    query.isNotBlank() -> "Check if the app name or package name is correct."
                    selectedFilter == AppFilter.ALLOWED -> "Turn on the app switch in the list to allow FCM wake-up."
                    selectedFilter == AppFilter.DISABLED -> "All current FCM apps are enabled."
                    else -> "The list only shows apps that contain Firebase Cloud Messaging receivers."
                },
                onReload = onReloadApps,
                showButton = query.isBlank() && selectedFilter == AppFilter.ALL
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                    AppRow(
                        app = app,
                        allowed = app.packageName in allowed,
                        index = index,
                        count = visibleApps.size,
                        onAllowedChange = { enabled ->
                            val next = if (enabled) allowed + app.packageName else allowed - app.packageName
                            onConfigChange(state.config.copy(allowedPackages = next))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAppsState(
    title: String,
    message: String,
    onReload: () -> Unit,
    showButton: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showButton) {
            TextButton(onClick = onReload) { Text("Refresh list") }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    allowed: Boolean,
    index: Int,
    count: Int,
    onAllowedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onAllowedChange(!allowed)
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        interactionSource = interactionSource,
        colors = expressiveSegmentedColors(),
        leadingContent = {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = "${app.label} Icon",
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
            )
        },
        supportingContent = {
            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Switch(
                checked = allowed,
                onCheckedChange = null,
                interactionSource = interactionSource,
                thumbContent = {
                    Icon(
                        imageVector = if (allowed) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        },
        content = {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    state: FcmfixUiState,
    launcherIconHidden: Boolean,
    onConfigChange: (FcmfixConfig) -> Unit,
    onLauncherIconHiddenChange: (Boolean) -> Unit
) {
    val config = state.config
    var showRootWarning by rememberSaveable { mutableStateOf(false) }
    var showHideLauncherIconWarning by rememberSaveable { mutableStateOf(false) }

    if (showRootWarning) {
        AlertDialog(
            onDismissRequest = { showRootWarning = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
            title = { Text("Enable Root Network Whitelist?") },
            text = {
                Text("UID 0 may contain all processes running as Root. Once enabled, these processes will be added to the ColorOS sleep standby optimization whitelist.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRootWarning = false
                    if (config.deepSleepGoogleWhitelist) onConfigChange(config.copy(rootDeepSleepNetworkWhitelist = true))
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { showRootWarning = false }) { Text("CANCEL") }
            }
        )
    }

    if (showHideLauncherIconWarning) {
        AlertDialog(
            onDismissRequest = { showHideLauncherIconWarning = false },
            title = { Text("Hide desktop icons?") },
            text = {
                Text(
                    "After hiding, you can open fcmfix from the LSPosed module page. Turn off this option in the settings to restore the desktop icon."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showHideLauncherIconWarning = false
                    onLauncherIconHiddenChange(true)
                }) { Text("Hiding") }
            },
            dismissButton = {
                TextButton(onClick = { showHideLauncherIconWarning = false }) { Text("CANCEL") }
            }
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SettingsGroup(
                title = "ColorOS Network Protection",
                items = listOf(
                    SettingEntry(
                        icon = ImageVector.vectorResource(R.drawable.ic_bedtime),
                        title = "Sleep Standby Optimization Whitelist",
                        summary = "Add GMS and GSF to the network whitelist",
                        checked = config.deepSleepGoogleWhitelist,
                        onCheckedChange = { enabled ->
                            onConfigChange(config.copy(
                                deepSleepGoogleWhitelist = enabled,
                                rootDeepSleepNetworkWhitelist = enabled && config.rootDeepSleepNetworkWhitelist
                            ))
                        }
                    ),
                    SettingEntry(
                        icon = ImageVector.vectorResource(R.drawable.ic_terminal),
                        title = "Standby Optimization Whitelist UID 0",
                        summary = if (config.deepSleepGoogleWhitelist) "Add all processes under Root to the network whitelist" else "Please enable the sleep standby optimization whitelist first",
                        checked = config.deepSleepGoogleWhitelist && config.rootDeepSleepNetworkWhitelist,
                        enabled = config.deepSleepGoogleWhitelist,
                        onCheckedChange = { enabled ->
                            if (enabled) showRootWarning = true
                            else onConfigChange(config.copy(rootDeepSleepNetworkWhitelist = false))
                        }
                    ),
                    SettingEntry(
                        icon = ImageVector.vectorResource(R.drawable.ic_hourglass),
                        title = "Doze Standby Optimization Whitelist",
                        summary = "Add GMS and GSF to the standby optimization whitelist",
                        checked = config.dozeGoogleWhitelist,
                        onCheckedChange = { enabled ->
                            onConfigChange(config.copy(dozeGoogleWhitelist = enabled))
                        }
                    )
                )
            )
        }

        item {
            SettingsGroup(
                title = "App Wake-up and Notifications",
                items = listOf(
                    SettingEntry(
                        icon = ImageVector.vectorResource(R.drawable.ic_shield),
                        title = "Automatically approve new applications",
                        summary = "Newly installed FCM apps are enabled by default",
                        checked = config.autoAllowNewApps,
                        onCheckedChange = { onConfigChange(config.copy(autoAllowNewApps = it)) }
                    ),
                    SettingEntry(
                        icon = ImageVector.vectorResource(R.drawable.ic_notifications_active),
                        title = "Retain notifications prior to app termination",
                        summary = "Retain existing notifications when the app stops",
                        checked = config.disableAutoCleanNotification,
                        onCheckedChange = { onConfigChange(config.copy(disableAutoCleanNotification = it)) }
                    ),
                    SettingEntry(
                        icon = ImageVector.vectorResource(R.drawable.ic_ac_unit),
                        title = "Allow waking up frozen apps",
                        summary = "Attempt to unfreeze when FCM arrives",
                        checked = config.includeIceBoxDisabledApps,
                        onCheckedChange = { onConfigChange(config.copy(includeIceBoxDisabledApps = it)) }
                    )
                )
            )
        }

        item {
            SettingsGroup(
                title = "Other settings",
                items = listOf(
                    SettingEntry(
                        icon = ImageVector.vectorResource(R.drawable.ic_visibility_off),
                        title = "Hide Desktop Icons",
                        summary = "Hide the fcmfix icon in the launcher without affecting the operation of the module",
                        checked = launcherIconHidden,
                        onCheckedChange = { hidden ->
                            if (hidden) showHideLauncherIconWarning = true
                            else onLauncherIconHiddenChange(false)
                        }
                    )
                )
            )
        }
    }
}

private data class SettingEntry(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsGroup(title: String, items: List<SettingEntry>) {
    Column {
        Text(
            title,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                key(item.title) {
                    SettingRow(item = item, index = index, count = items.size)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingRow(item: SettingEntry, index: Int, count: Int, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        onClick = {
            if (item.enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                item.onCheckedChange(!item.checked)
            }
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        interactionSource = interactionSource,
        colors = expressiveSegmentedColors(),
        leadingContent = {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        supportingContent = { Text(item.summary, color = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
        trailingContent = {
            Switch(
                checked = item.checked,
                enabled = item.enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
                thumbContent = {
                    Icon(
                        imageVector = if (item.checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        },
        content = { Text(item.title, color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) }
    )
}
