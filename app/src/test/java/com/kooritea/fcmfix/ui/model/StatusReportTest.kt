package com.kooritea.fcmfix.ui.model

import com.kooritea.fcmfix.util.SelfCheck
import com.kooritea.fcmfix.util.SelfCheck.EventKind
import com.kooritea.fcmfix.util.SelfCheck.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class StatusReportTest {
    private val now = 1_760_000_000_000L
    private val module = ModuleInfo("LSPosed 1.10.2", 101, emptyList())

    private fun env(module: ModuleInfo? = this.module, local: LocalConfig? = LocalConfig(2, false), sdk: Int = 36) = ReportEnv(
        module = module, sdkInt = sdk, display = "PLK110_17.0.0.105", versionName = "13.2",
        localConfig = local, now = now,
        label = { "Label($it)" },
        relativeTime = { "${(now - it) / 60_000} minutes ago" },
    )

    private val hooks = mapOf(
        "android" to HookGroups(listOf("BroadcastFix", "AutoStartFix"), emptyList(), emptyList()),
        "com.oplus.battery" to HookGroups(listOf("ControllerCenter"), emptyList(), null),
    )

    private val healthy = StatusSnapshot(
        answered = true, hooks = hooks, configLoaded = true, manualCount = 2,
        gmsPolicy = 0, gmsBucket = 10, gmsDoze = 1,
        fcmState = SelfCheck.FCM_CONNECTED, fcmRemote = "1.2.3.4:5228（Wi‑Fi）",
        fcmSince = now - 3 * 3_600_000L, fcmMonitorStart = now - 5 * 3_600_000L,
        apps = listOf("chat"),
        appChecks = mapOf("chat" to AppCheck(false, 1, 0, 10, true, false, now - 60_000L, 1)),
        events = emptyList(),
    )

    @Test
    fun inactiveModuleCountsLsposedAndBothHookGroups() {
        val report = StatusReport.build(env(module = null, local = null), StatusSnapshot())
        val summary = report.summary()
        assertEquals(Level.FAIL, summary.level)
        assertEquals("3 items need to be processed", summary.title)
        assertEquals("LSPosed: Module not activated", summary.detail)
        assertEquals(
            listOf("Module", "LSPosed", "System Framework Hook", "Battery Hook", "Google Services", "FCM Diagnostics"),
            report.rows.map { it.title },
        )
        assertEquals(RowAction.FcmDiagnostics, report.rows.last().action)
        assertFalse(report.rows.last().problem)
    }

    @Test
    fun healthySummaryShowsFcmAndFramework() {
        val summary = StatusReport.build(env(), healthy).summary()
        assertEquals(Level.OK, summary.level)
        assertEquals("ALL IS OK", summary.title)
        assertEquals("FCM connected to 1.2.3.4:5228 (Wi‑Fi) · For 3 hours and 0 minutes\\nNo disconnections or reconnections since power-on", summary.detail)
        assertEquals("LSPosed 1.10.2 · API 101 · System Framework 2 items · Battery 1 item", summary.footer)
    }

    @Test
    fun missingScopeRowAsksLsposed() {
        val report = StatusReport.build(env(module = module.copy(missingScopes = listOf("com.oplus.battery"))), healthy)
        val scope = report.rows.single { it.title == "general.domain" }
        assertEquals(Level.FAIL, scope.level)
        assertEquals("Battery missing", scope.detail)
        assertEquals(RowAction.RequestScope(listOf("com.oplus.battery")), scope.action)
        assertTrue(scope.problem)
    }

    @Test
    fun hookRowListsMismatchesAndKeepsSkippedForTheCopy() {
        val status = healthy.copy(
            hooks = hooks + ("android" to HookGroups(listOf("A"), listOf("B: no method"), listOf("C: absent"))),
        )
        val row = StatusReport.build(env(), status).rows.single { it.title == "System Framework Hook" }
        assertEquals(Level.WARN, row.level)
        assertEquals("1 active, 1 not applicable, 1 mismatch\\nB: no method", row.detail)
        assertEquals("The mismatched items do not match the current firmware. Please export the full report and provide feedback.", row.hint)
        assertEquals("Effective: A\\nNot applicable: C: absent", row.copyExtra)
    }

    @Test
    fun configSyncComparesTheConfigOfTheQuery() {
        val mismatch = StatusReport.build(env(local = LocalConfig(3, false)), healthy).rows.single { it.title == "Configuration_synchronization" }
        assertEquals(Level.WARN, mismatch.level)
        val noLocal = StatusReport.build(env(local = null), healthy).rows
        assertTrue(noLocal.none { it.title == "Configuration_synchronization" })
    }

    @Test
    fun appRowsPutProblemsFirstThenByLatestPush() {
        val status = healthy.copy(
            apps = listOf("ok.old", "muted", "off", "ok.new", "auto.off"),
            appChecks = mapOf(
                "ok.old" to AppCheck(false, 1, 0, 10, true, false, now - 30 * 60_000L, 3),
                "muted" to AppCheck(false, 1, 2, 10, false, false, now - 60_000L, 1),
                "off" to AppCheck(false, 0, 0, 10, false, true, 0, 0),
                "ok.new" to AppCheck(false, 1, 0, 45, false, false, now - 5 * 60_000L, 7),
                "auto.off" to AppCheck(true, 0, 0, 10, false, false, 0, 0),
            ),
        )
        val rows = StatusReport.build(env(), status).rows(Page.APPS)
        assertEquals(
            listOf(
                "Approved Applications (5)", "Points to Note (3)", "Label(off)", "Label(muted)", "Label(ok.new)",
                "Normal (2)", "Label(ok.old)", "Label (auto.off) (Auto)", "",
            ),
            rows.map { it.title },
        )
        val off = rows.single { it.packageName == "off" }
        assertEquals("Notifications are turned off\\nNo push notifications have been received since startup · Stopped; the module will be launched when a push notification is received", off.detail)
        assertTrue(off.problem)
        assertEquals(RowAction.AppDetails("off"), off.action)
        val muted = rows.single { it.packageName == "muted" }
        assertFalse("A muted channel is often deliberate", muted.problem)
        assertEquals("2 notification categories have been turned off\\nPush notification received 1 minute ago · 1 time since startup · Not running", muted.detail)
        assertEquals("Standby Group: Restricted", rows.single { it.packageName == "ok.new" }.detail.substringAfter(" · ").substringBefore('\n'))
        assertEquals(Level.INFO, rows.single { it.packageName == "auto.off" }.level)
        assertEquals(RowKind.NOTE, rows.last().kind)
    }

    @Test
    fun noAllowedAppIsAWarning() {
        val report = StatusReport.build(env(), healthy.copy(apps = emptyList(), appChecks = emptyMap()))
        val rows = report.rows(Page.APPS)
        assertEquals(listOf("Approved Applications (0)", "No apps have been approved yet"), rows.map { it.title })
        assertEquals("1 item needs to be addressed", report.summary().title)
        assertEquals(Level.WARN, rows[1].level)
    }

    @Test
    fun eventsAreGroupedByDayNewestFirst() {
        val status = healthy.copy(
            events = listOf("…(3 earlier messages deleted)", "10-08 23:59:01 Screen off", "10-09 00:01:02 FCM Disconnected"),
            pushEvents = listOf("10-09 00:00:30 Push → com.example"),
        )
        val rows = StatusReport.build(env(), status).rows(Page.EVENTS)
        assertEquals(
            listOf("Recent Events (Since Power-On, Most Recent First)", "", "October 9", "00:01:02", "00:00:30", "October 8", "23:59:01", ""),
            rows.map { it.title },
        )
        assertEquals(listOf(EventKind.FCM_DOWN, EventKind.PUSH, EventKind.SCREEN), rows.mapNotNull { it.eventKind })
        assertEquals("Connectivity, network, screen on/off, and Doze events: 3 earlier ones have been deleted", rows.last().detail)
    }

    @Test
    fun copiedTextKeepsTheIssueFormat() {
        val status = StatusSnapshot(hooks = hooks)
        val text = StatusReport.build(env(local = null), status).toText(Date(0))
        val expected = "FCMFix Self-Check" + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(Date(0)) +
            "\\n\\n[Module]" +
            "\\n✓ LSPosed: Activated · LSPosed 1.10.2" +
            "\\n✓ Scope: System framework, battery" +
            "\\n✓ System Framework Hooks: 2 enabled\\n  Enabled: BroadcastFix, AutoStartFix" +
            "\\n✓ Battery Hook: 1 enabled\\n  Enabled: ControllerCenter" +
            "\\n\\n[Google Services]" +
            "\\n• FCM Diagnostics: Click to open the diagnostics page provided by GMS to see if it is connected. If it is always disconnected even when the screen is on, it is usually a network issue (DNS is compromised or port 5228 is blocked), and the module cannot resolve it."
        assertEquals(expected, text)
    }

    @Test
    fun androidThirteenSkipsTheHookQuery() {
        val rows = StatusReport.build(env(sdk = 33), StatusSnapshot()).rows
        val check = rows.single { it.title == "WPControl" }
        assertEquals(Level.UNKNOWN, check.level)
        assertNull(rows.firstOrNull { it.title.endsWith(" Hook") })
    }

    @Test
    fun dayLabelFallsBackToTheRawText() {
        assertEquals("October 9", StatusReport.dayLabel("10-09"))
        assertEquals("ab-cd", StatusReport.dayLabel("ab-cd"))
    }
}
