package com.kooritea.fcmfix.ui.model

import com.kooritea.fcmfix.util.SelfCheck
import com.kooritea.fcmfix.util.SelfCheck.EventKind
import com.kooritea.fcmfix.util.SelfCheck.Level
import com.kooritea.fcmfix.util.SelfCheck.Verdict
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The self-check is shown across three tabs; the copied text has all of them. */
enum class Page { OVERVIEW, APPS, EVENTS }

enum class RowKind { SECTION, CHECK, EVENT, NOTE }

sealed interface RowAction {
    data class RequestScope(val scopes: List<String>) : RowAction
    data class AppDetails(val packageName: String) : RowAction
    data object FcmDiagnostics : RowAction
}

data class Row(
    val kind: RowKind,
    val page: Page,
    /** INFO for sections, events and notes. */
    val level: Level,
    /** Section name, check name, or an event's time of day. */
    val title: String,
    val detail: String = "",
    val hint: String? = null,
    val action: RowAction? = null,
    /** Counted by the status summary as "needs attention". */
    val problem: Boolean = false,
    /** Extra lines only for the copied text. */
    val copyExtra: String? = null,
    /** App rows: shown with the app's icon. */
    val packageName: String? = null,
    val eventKind: EventKind? = null,
)

/** LSPosed as the module app sees it. */
data class ModuleInfo(
    /** "LSPosed 1.10.2", or "LSPosed" when the framework could not be asked. */
    val framework: String,
    /** The libxposed API the module is built against. */
    val apiVersion: Int,
    /** Scopes FCMFix needs that are not ticked; null when the scope cannot be read. */
    val missingScopes: List<String>?,
)

/** The module app's own copy of the config, to compare with what system_server loaded. */
data class LocalConfig(val manualCount: Int, val autoAllow: Boolean)

/** What the report needs besides the status answer. */
class ReportEnv(
    /** Null while LSPosed has not bound the service. */
    val module: ModuleInfo?,
    val sdkInt: Int,
    val display: String,
    val versionName: String,
    /** Null when the config cannot be read (service not bound). */
    val localConfig: LocalConfig?,
    val now: Long,
    val label: (String) -> String,
    /** DateUtils.getRelativeTimeSpanString on a device, e.g. "5 minutes ago". */
    val relativeTime: (Long) -> String,
)

/** The top of the status tab: the first problem, or the FCM connection when all is well. */
data class Summary(val level: Level, val title: String, val detail: String?, val footer: String?)

/** Notification and standby-bucket verdicts of an allowed app; level is the worse of the two. */
class AppVerdict private constructor(val level: Level, val notify: Verdict, val bucket: Verdict?) {
    /** What the app row shows besides pushes, or null when all is well. */
    val text: String?
        get() = if (level == Level.OK) null else notify.text + (bucket?.let { " · " + it.text } ?: "")

    val hint: String? get() = notify.hint ?: bucket?.hint

    /** Counted under "Needs Attention". */
    val attention: Boolean get() = level == Level.FAIL || level == Level.WARN

    companion object {
        fun of(app: AppCheck): AppVerdict {
            val notify = SelfCheck.appNotify(app.notify, app.blockedChannels, app.auto)
            val bucket = SelfCheck.appBucket(app.bucket)
            var level = notify.level
            if (bucket != null && rank(bucket.level) < rank(level)) level = bucket.level
            return AppVerdict(level, notify, bucket)
        }

        internal fun rank(level: Level): Int = when (level) {
            Level.FAIL -> 0
            Level.WARN -> 1
            Level.UNKNOWN -> 2
            else -> 3
        }
    }
}

/**
 * Turns the self-check answer of the hooked processes into checklist rows. The status tab, the
 * app list and the copied text all use it, so they count the same problems.
 */
class StatusReport private constructor(
    private val env: ReportEnv,
    val status: StatusSnapshot,
    val rows: List<Row>,
) {
    val problems: List<Row> get() = rows.filter { it.problem }

    fun rows(page: Page): List<Row> = rows.filter { it.page == page }

    fun summary(): Summary {
        val problems = problems
        if (problems.isNotEmpty()) {
            val level = if (problems.any { it.level == Level.FAIL }) Level.FAIL else Level.WARN
            val title = if (problems.size == 1) "1 item needs to be addressed" else "${problems.size} issues need to be addressed"
            val first = problems[0]
            return Summary(level, title, first.title + "：" + first.detail.substringBefore('\n'), null)
        }
        if (!status.answered) return Summary(Level.UNKNOWN, "The system framework is not responding.", null, null)
        val fcm = status.fcmVerdict(env.now).text.split("\n")
        val detail = if (fcm.size >= 3) "FCM " + fcm[0] + " · " + fcm[1] + "\n" + fcm[2] else null
        return Summary(Level.OK, "ALL IS OK", detail, okFooter())
    }

    /** e.g. "LSPosed 1.10.2 · API 101 · System Framework 14 Items · Battery 4 Items". */
    private fun okFooter(): String? {
        val module = env.module ?: return null
        val parts = mutableListOf(module.framework + " · API " + module.apiVersion)
        for ((process, label) in StatusSnapshot.PROCESSES) {
            status.hooks(process).active?.let { parts += "$label ${it.size} items" }
        }
        return parts.joinToString(" · ")
    }

    fun toText(time: Date = Date()): String {
        val text = StringBuilder("FCMFix Self-Check")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(time))
        for (row in rows) {
            when (row.kind) {
                RowKind.SECTION -> text.append("\n\n【").append(row.title).append("】")
                RowKind.EVENT -> text.append('\n').append(row.title).append(' ').append(row.detail)
                RowKind.NOTE -> text.append("\n• ").append(row.detail)
                RowKind.CHECK -> {
                    text.append('\n').append(markOf(row.level)).append(' ').append(row.title).append("：")
                        .append(row.detail.replace("\n", "；"))
                    row.hint?.let { text.append("\n  → ").append(it) }
                    row.copyExtra?.let { text.append("\n  ").append(it) }
                }
            }
        }
        return text.toString()
    }

    companion object {
        fun build(env: ReportEnv, status: StatusSnapshot): StatusReport =
            StatusReport(env, status, Builder(env, status).build())

        fun markOf(level: Level): String = when (level) {
            Level.OK -> "✓"
            Level.WARN -> "!"
            Level.FAIL -> "✗"
            Level.UNKNOWN -> "?"
            else -> "•"
        }

        /** "10-09" → "October 9". */
        fun dayLabel(monthDay: String): String = try {
            "${monthDay.substring(0, 2).toInt()} Month ${monthDay.substring(3, 5).toInt()} Day"
        } catch (e: RuntimeException) {
            monthDay
        }
    }

    private class Builder(private val env: ReportEnv, private val status: StatusSnapshot) {
        private val rows = ArrayList<Row>()
        private var page = Page.OVERVIEW

        fun build(): List<Row> {
            section("Module")
            val module = env.module
            if (module == null) {
                add(Level.FAIL, "LSPosed", "Module not activated", "Enable FCMFix in LSPosed, check System Framework and Battery, then restart your phone.")
            } else {
                add(Level.OK, "LSPosed", "Activated" + module.framework, null)
                addScopeRow(module)
            }
            if (env.sdkInt < 34) {
                add(Level.UNKNOWN, "WPControl", "Requires Android 14 or later", null)
            } else {
                for ((process, label) in StatusSnapshot.PROCESSES) addHookRow(process, label)
            }
            if (status.answered) addConfigRows()

            section("Google Services")
            if (status.answered) {
                add("FCM Connection (System Framework Check)", status.fcmVerdict(env.now))
                add("GMS Networking Policy", SelfCheck.gmsPolicy(status.gmsPolicy))
                add("GMS Standby Group", SelfCheck.gmsBucket(status.gmsBucket))
                add("GMS Battery Optimization Whitelist", SelfCheck.gmsDoze(status.gmsDoze))
            }
            add(
                Level.INFO, "FCM Diagnostics", "Click to open the diagnostic page provided by GMS to see if it is connected." +
                    "When the screen is on, it is always disconnected. This is usually a network issue (DNS is polluted or port 5228 is blocked), and the module cannot resolve it.",
                null, RowAction.FcmDiagnostics, problem = false,
            )

            if (!status.answered) return rows
            section("System Information (to be attached when submitting feedback)")
            val info = StringBuilder()
                .append("FCMFix ").append(env.versionName)
                .append("\nsdk=").append(env.sdkInt)
                .append("\ndisplay=").append(env.display)
            status.settings?.forEach { info.append('\n').append(it) }
            status.ignoreGmsUserSet?.let { info.append("\nIgnoreGmsUserSet=").append(it) }
            add(Level.INFO, "Version and System Settings", info.toString(), null)

            page = Page.APPS
            addAppRows()
            page = Page.EVENTS
            addEventRows()
            return rows
        }

        private fun section(title: String) {
            rows += Row(RowKind.SECTION, page, Level.INFO, title)
        }

        private fun note(text: String) {
            rows += Row(RowKind.NOTE, page, Level.INFO, "", text)
        }

        private fun add(
            level: Level, title: String, detail: String, hint: String?, action: RowAction? = null,
            problem: Boolean = level == Level.FAIL || level == Level.WARN, copyExtra: String? = null,
        ) {
            rows += Row(RowKind.CHECK, page, level, title, detail, hint, action, problem, copyExtra)
        }

        private fun add(title: String, verdict: Verdict) = add(verdict.level, title, verdict.text, verdict.hint)

        private fun addScopeRow(module: ModuleInfo) {
            val missing = module.missingScopes
            when {
                missing == null -> add(Level.UNKNOWN, "general.domain", "Unable to read on", null)
                missing.isEmpty() -> add(Level.OK, "general.domain", "System frame, battery", null)
                else -> {
                    val labels = missing.map { if (it == "system") "System framework" else "BATTERY" }
                    add(
                        Level.FAIL, "general.domain", "missing? " + labels.joinToString("、"),
                        "Tap to request to add to LSPosed, and restart your phone after confirmation.", RowAction.RequestScope(missing), problem = true,
                    )
                }
            }
        }

        private fun addHookRow(process: String, label: String) {
            val (active, failed, skipped) = status.hooks(process)
            val skippedSummary = if (skipped.isNullOrEmpty()) "" else ", ${skipped.size} items not applicable"
            var copyExtra: String? = null
            if (!active.isNullOrEmpty()) copyExtra = "Valid:" + active.joinToString("、")
            if (!skipped.isNullOrEmpty()) {
                copyExtra = (if (copyExtra == null) "" else copyExtra + "\n") + "Not Applicable:" + skipped.joinToString("；")
            }
            val title = "$label Hook"
            when {
                active == null || failed == null -> add(
                    Level.FAIL, title, "GD not loaded", "Confirm that the ${label} scope has been selected and that the phone has been restarted after selection.", copyExtra = copyExtra,
                )
                failed.isNotEmpty() -> add(
                    Level.WARN, title,
                    "${active.size} items active$skippedSummary, ${failed.size} items mismatched\\n" + failed.joinToString("\n"),
                    "The mismatched items do not match the current firmware. Please export the full report and provide feedback.", copyExtra = copyExtra,
                )
                else -> add(Level.OK, title, "${active.size} items activated$skippedSummary", null, copyExtra = copyExtra)
            }
        }

        private fun addConfigRows() {
            env.localConfig?.let { local ->
                add(
                    "Configuration_synchronization", SelfCheck.configSync(
                        status.configLoaded, status.manualCount, status.autoAllow, local.manualCount, local.autoAllow,
                    ),
                )
            }
            if (status.autoAllow) {
                if (status.fcmPackages < 0) {
                    add(Level.WARN, "Automatic Clearance", "Enabled, but the system framework has not yet completed the app scan", "Check again later; if the issue persists, please restart your phone.")
                } else {
                    add(Level.INFO, "Automatic Clearance", "Enabled. The system framework has identified ${status.fcmPackages} apps containing FCM.", null)
                }
            }
        }

        /** Problems first, each group by the most recent push. */
        private fun addAppRows() {
            val apps = status.apps ?: return
            section("Allowed Apps (${apps.size})")
            if (apps.isEmpty()) {
                add(Level.WARN, "No apps have been approved yet", "On the \"Apps\" page, allow the apps that need to be pushed, or enable \"Automatically allow apps that contain FCM\"", null)
                return
            }
            val attention = ArrayList<Pair<Row, Long>>()
            val normal = ArrayList<Pair<Row, Long>>()
            for (name in apps) {
                val app = status.appChecks[name] ?: continue
                val verdict = AppVerdict.of(app)
                var label = env.label(name)
                if (app.auto) label += "(automatic)"
                val pushes = if (app.lastPush == 0L) "No push notifications received since startup"
                else env.relativeTime(app.lastPush) + "Push Notifications Received · Since Power-On" + app.pushCount + " times"
                val process = when {
                    app.running -> "In operation"
                    app.stopped -> "Stopped, pulled up by the module when pushed"
                    else -> "not running smoothly."
                }
                val detail = (verdict.text?.let { it + "\n" } ?: "") + pushes + " · " + process
                // Only a fully disabled app counts in the summary; a muted channel is often deliberate.
                val row = Row(
                    RowKind.CHECK, page, verdict.level, label, detail, verdict.hint, RowAction.AppDetails(name),
                    problem = verdict.level == Level.FAIL, packageName = name,
                )
                (if (verdict.attention) attention else normal) += row to app.lastPush
            }
            val order = compareBy<Pair<Row, Long>> { AppVerdict.rank(it.first.level) }.thenByDescending { it.second }
            if (attention.isNotEmpty()) {
                section("Attention required (${attention.size})")
                rows += attention.sortedWith(order).map { it.first }
            }
            if (normal.isNotEmpty()) {
                section("Normal (${normal.size})")
                rows += normal.sortedWith(order).map { it.first }
            }
            note("\"Push received\" means that GMS has delivered the message to the app. If a push notification is received but no notification pops up, the problem is usually with the app's own notification settings or the app itself.")
        }

        /** One row per event, newest first, under a section for each day. */
        private fun addEventRows() {
            val events = status.events ?: return
            section("Recent Events (Since Power-On, Most Recent First)")
            val timeline = SelfCheck.mergeEvents(events, status.pushEvents)
            if (timeline.lines.isEmpty()) {
                note("Screen off, Deep Doze, network switching, FCM connection changes, and received push notifications will be recorded here.")
                return
            }
            note("Whether the screen was just turned off, entered deep Doze, or switched networks before or after the FCM disconnection usually explains the cause. \"Reconnection\" means that the old connection is replaced by a new one, and the push notification is not affected in the meantime.")
            var day: String? = null
            for (line in timeline.lines) {
                // "MM-dd HH:mm:ss text"
                val stamped = line.length > 15 && line[5] == ' ' && line[14] == ' '
                val lineDay = if (stamped) line.substring(0, 5) else ""
                if (lineDay != day) {
                    day = lineDay
                    section(if (stamped) dayLabel(lineDay) else "Other")
                }
                val text = if (stamped) line.substring(15) else line
                rows += Row(
                    RowKind.EVENT, page, Level.INFO, if (stamped) line.substring(6, 14) else "", text,
                    eventKind = SelfCheck.eventKind(text),
                )
            }
            timeline.notes.forEach { note(it) }
        }
    }
}
