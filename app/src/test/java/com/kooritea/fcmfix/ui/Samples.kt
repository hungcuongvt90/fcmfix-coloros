package com.kooritea.fcmfix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kooritea.fcmfix.ui.data.IconSource
import com.kooritea.fcmfix.ui.data.InstalledApp
import com.kooritea.fcmfix.ui.data.ModuleConfig
import com.kooritea.fcmfix.ui.data.ModuleConnection
import com.kooritea.fcmfix.ui.model.AppCheck
import com.kooritea.fcmfix.ui.model.HookGroups
import com.kooritea.fcmfix.ui.model.LocalConfig
import com.kooritea.fcmfix.ui.model.ModuleInfo
import com.kooritea.fcmfix.ui.model.ReportEnv
import com.kooritea.fcmfix.ui.model.StatusReport
import com.kooritea.fcmfix.ui.model.StatusSnapshot
import com.kooritea.fcmfix.util.SelfCheck
import java.text.SimpleDateFormat
import java.util.Locale

/** States for the UI tests: a healthy phone, one with problems, and LSPosed still connecting. */
object Samples {
    val now = System.currentTimeMillis()
    private const val MINUTE = 60_000L

    fun relativeTime(time: Long): String {
        val minutes = (now - time) / MINUTE
        return if (minutes < 60) "{minutes} minutes ago [x_minutes_ago][0]" else "${minutes / 60} hours ago"
    }

    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalRelativeTime provides ::relativeTime,
            LocalIconSource provides IconSource.None,
            content = content,
        )
    }

    val apps = listOf(
        InstalledApp("org.telegram.messenger", "Telegram", true),
        InstalledApp("com.discord", "Discord", true),
        InstalledApp("com.google.android.gm", "Gmail", true),
        InstalledApp("com.twitter.android", "X", true),
        InstalledApp("com.whatsapp", "WhatsApp", true),
        InstalledApp("com.github.android", "GitHub", true),
        InstalledApp("com.android.chrome", "Chrome", true),
        InstalledApp("com.tencent.mm", "WeChat", false),
        InstalledApp("com.coloros.calculator", "Calculator", false),
        InstalledApp("com.oplus.notes", "notes", false),
    )

    private val allowed = setOf("org.telegram.messenger", "com.discord", "com.google.android.gm", "com.twitter.android")
    val config = ModuleConfig(allowList = allowed)

    private val hooks = mapOf(
        "android" to HookGroups(
            listOf(
                "BroadcastFix", "AutoStartFix", "KeepNotification", "OplusProxyFix", "OplusStartupFix#broadcast",
                "OplusStartupFix#service", "OplusGoogleServiceFix#network", "OplusGoogleServiceFix#alarm",
                "OplusDeviceIdleFix", "OsenseResManager", "HansJobExemption", "FcmConnectionMonitor", "SelfCheckCollector",
                "NetworkPolicy",
            ),
            emptyList(), emptyList(),
        ),
        "com.oplus.battery" to HookGroups(
            listOf("GoogleRestrictionController", "RestrictBroadcast", "IgnoreGmsUserSet", "DeepSleepWhitelist"),
            emptyList(), null,
        ),
    )

    private fun line(minutesAgo: Long, text: String) =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(now - minutesAgo * MINUTE) + " " + text

    private fun check(lastPushMinutes: Long?, notify: Int = 1, blocked: Int = 0, bucket: Int = 10, auto: Boolean = false, running: Boolean = false, count: Long = 0) =
        AppCheck(auto, notify, blocked, bucket, running, !running, lastPushMinutes?.let { now - it * MINUTE } ?: 0, count)

    private val healthySnapshot = StatusSnapshot(
        answered = true, hooks = hooks, configLoaded = true, manualCount = allowed.size,
        gmsPolicy = 0, gmsBucket = 10, gmsDoze = 1,
        settings = listOf("google_restric_info=1", "sleep_standby_optimize=0"),
        ignoreGmsUserSet = true,
        fcmState = SelfCheck.FCM_CONNECTED, fcmRemote = "142.250.157.188:5228（Wi‑Fi）",
        fcmSince = now - 192 * MINUTE, fcmReconnects = 1, fcmMonitorStart = now - 600 * MINUTE,
        apps = allowed.toList(),
        appChecks = mapOf(
            "org.telegram.messenger" to check(3, running = true, count = 12),
            "com.discord" to check(47, count = 3),
            "com.google.android.gm" to check(130, count = 5),
            "com.twitter.android" to check(null),
        ),
        events = listOf(
            line(600, "FCM connected to 142.250.157.188:5228 (Wi‑Fi)"),
            line(320, "Screen Off"),
            line(300, "Enter Deep Doze"),
            line(240, "Exit Deep Doze"),
            line(200, "Network: Mobile Data"),
            line(195, "FCM reconnection 142.250.157.188:5228 (Mobile Data) (Previous connection lasted 6 hours and 45 minutes)"),
            line(192, "Network: Wi‑Fi"),
            line(60, "Screen On"),
        ),
        pushEvents = listOf(
            line(130, SelfCheck.EVENT_PUSH + "com.google.android.gm"),
            line(47, SelfCheck.EVENT_PUSH + "com.discord"),
            line(3, SelfCheck.EVENT_PUSH + "org.telegram.messenger"),
        ),
    )

    private val problemSnapshot = healthySnapshot.copy(
        hooks = hooks + ("android" to hooks.getValue("android").copy(failed = listOf("OplusGoogleServiceFix#weakSignal: no method"))),
        gmsBucket = 45,
        fcmState = SelfCheck.FCM_CONNECTED,
        fcmStall = "No data received for approximately 42 minutes (excluding sleep time)",
        fcmOutages = 2, fcmLongestOutage = 18 * MINUTE,
        appChecks = healthySnapshot.appChecks + mapOf(
            "com.discord" to check(47, notify = 0, count = 3),
            "com.google.android.gm" to check(130, blocked = 2, count = 5),
        ),
    )

    private val module = ModuleInfo("LSPosed 1.10.2", 101, emptyList())

    private fun report(status: StatusSnapshot, module: ModuleInfo? = this.module) = StatusReport.build(
        ReportEnv(module, 36, "PLK110_17.0.0.105(CN01)", "13.2", LocalConfig(allowed.size, false), now, { name ->
            apps.firstOrNull { it.packageName == name }?.label ?: name
        }, ::relativeTime),
        status,
    )

    fun healthy() = AppUiState(
        ModuleConnection.State.Missing, config, apps, report(healthySnapshot), versionName = "13.2",
    )

    fun problems() = AppUiState(
        ModuleConnection.State.Missing, config, apps,
        report(problemSnapshot, module.copy(missingScopes = listOf("com.oplus.battery"))), versionName = "13.2",
    )

    fun inactive() = AppUiState(ModuleConnection.State.Missing, null, apps, report(StatusSnapshot(), null), versionName = "13.2")

    fun loading() = AppUiState(ModuleConnection.State.Connecting, null, null, null, versionName = "13.2")
}
