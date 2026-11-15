package com.kooritea.fcmfix.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-check protocol and verdicts. The module app adds EXTRA_CHECK to the status query;
 * system_server then also answers the keys below, and the app turns them into verdicts.
 */
public final class SelfCheck {
    public static final String EXTRA_CHECK = "selfCheck";

    public static final String KEY_CONFIG_LOADED = "check.configLoaded";
    public static final String KEY_MANUAL_COUNT = "check.manualCount";
    public static final String KEY_AUTO_ALLOW = "check.autoAllow";
    /** Installed FCM apps system_server knows of, or UNKNOWN when it has not scanned. */
    public static final String KEY_FCM_PACKAGES = "check.fcmPackages";
    public static final String KEY_GMS_POLICY = "check.gmsPolicy";
    public static final String KEY_GMS_BUCKET = "check.gmsBucket";
    public static final String KEY_GMS_DOZE = "check.gmsDoze";
    /** "name=value" lines of the ColorOS settings the issue guide asks about. */
    public static final String KEY_SETTINGS = "check.settings";
    /** Packages system_server lets through, each with an APP_PREFIX + package Bundle. */
    public static final String KEY_APPS = "check.apps";
    /** GMS's FCM socket as system_server saw it: FCM_* state, remote, first-seen time. */
    public static final String KEY_FCM_STATE = "check.fcmState";
    public static final String KEY_FCM_REMOTE = "check.fcmRemote";
    /** Why the established FCM socket looks dead; absent when it looks alive. */
    public static final String KEY_FCM_STALL = "check.fcmStall";
    public static final String KEY_FCM_SINCE = "check.fcmSince";
    /** Connections replaced by a new one before the next sample; delivery was not interrupted. */
    public static final String KEY_FCM_RECONNECTS = "check.fcmReconnects";
    /** Samples that found no connection after one had been up, and the longest such gap. */
    public static final String KEY_FCM_OUTAGES = "check.fcmOutages";
    public static final String KEY_FCM_LONGEST_OUTAGE = "check.fcmLongestOutage";
    /** Wall time a connection was last seen up; 0 before the first one. */
    public static final String KEY_FCM_LAST_SEEN = "check.fcmLastSeen";
    public static final String KEY_FCM_MONITOR_START = "check.fcmMonitorStart";
    /** Timeline lines, oldest first: screen, deep Doze, network and FCM connection changes. */
    public static final String KEY_EVENTS = "check.events";
    /** Push lines, oldest first, kept apart so a busy chat cannot push the night out of KEY_EVENTS. */
    public static final String KEY_PUSH_EVENTS = "check.pushEvents";
    /** Timeline texts start with these; the app colours and filters lines by them. */
    public static final String EVENT_PUSH = "Pushing";
    public static final String EVENT_FCM_UP = "FCM Connected";
    public static final String EVENT_FCM_RECONNECT = "FCM Reconnection";
    public static final String EVENT_FCM_RESTORED = "FCM Reconnection";
    public static final String EVENT_FCM_DOWN = "FCM Disconnected";
    public static final String EVENT_FCM_STALL = "FCM not responding";
    public static final String EVENT_NETWORK = "networks";
    public static final String EVENT_SCREEN_ON = "Screen On";
    public static final String EVENT_SCREEN_OFF = "Screen Off";
    public static final String EVENT_DOZE_ENTER = "Enter Deep Doze";
    public static final String EVENT_DOZE_EXIT = "Exit Deep Doze";

    public enum EventKind { FCM_UP, FCM_RECONNECT, FCM_DOWN, NETWORK, SCREEN, DOZE, PUSH, OTHER }

    public static final int FCM_NONE = 0;
    public static final int FCM_CONNECTING = 1;
    public static final int FCM_CONNECTED = 2;
    public static final String APP_PREFIX = "check.app.";

    public static final String APP_AUTO = "auto";
    public static final String APP_NOTIFY = "notify";
    public static final String APP_BLOCKED_CHANNELS = "blockedChannels";
    public static final String APP_BUCKET = "bucket";
    public static final String APP_STOPPED = "stopped";
    public static final String APP_RUNNING = "running";
    public static final String APP_LAST_PUSH = "lastPush";
    public static final String APP_PUSH_COUNT = "pushCount";

    /** Value of an int key that could not be read on this firmware. */
    public static final int UNKNOWN = -1;

    public enum Level { OK, INFO, WARN, FAIL, UNKNOWN }

    public static final class Verdict {
        public final Level level;
        public final String text;
        /** What to do about it; null when nothing is needed. */
        public final String hint;

        Verdict(Level level, String text, String hint) {
            this.level = level;
            this.text = text;
            this.hint = hint;
        }
    }

    private SelfCheck() {
    }

    /** Oplus networking_control policy: a bitmask of 1 no mobile, 2 no Wi-Fi, 4 reject all. */
    public static Verdict gmsPolicy(int policy) {
        if (policy < 0) return new Verdict(Level.UNKNOWN, "Failed to check the data", "The current firmware may not have this service. You can export the full report to view it.");
        if (policy == 0) return new Verdict(Level.OK, "No Limit", null);
        String text = (policy & 4) != 0 ? "All Prohibited"
                : (policy & 3) == 3 ? "Disable Wi‑Fi and Mobile Data"
                : (policy & 2) != 0 ? "Disable Wi‑Fi" : "Mobile Data Disabled";
        return new Verdict(Level.FAIL, text + "（" + policy + "）",
                "Confirm that the battery scope has been selected and restart the device. If the issue persists, follow the instructions in the \"No Google Entry in Traffic Management\" section of the \"Submit Issue\" guide.");
    }

    public static String bucketName(int bucket) {
        switch (bucket) {
            case 5: return "Immunity";
            case 10: return "active";
            case 20: return "working set";
            case 30: return "Common Use(s)";
            case 40: return "Rarely used";
            case 45: return "No Products have been unmarked as public.";
            case 50: return " Never ";
            default: return String.valueOf(bucket);
        }
    }

    public static Verdict gmsBucket(int bucket) {
        if (bucket < 0) return new Verdict(Level.UNKNOWN, "Failed to check the data", null);
        String text = bucketName(bucket) + "（" + bucket + "）";
        if (bucket <= 30) return new Verdict(Level.OK, text, null);
        return new Verdict(Level.FAIL, text, "GMS has been downgraded by the system, and background networking and tasks will be delayed. Check again after restarting. If the issue persists, please export the report and provide feedback.");
    }

    public static Verdict gmsDoze(int state) {
        if (state < 0) return new Verdict(Level.UNKNOWN, "Failed to check the data", null);
        if (state > 0) return new Verdict(Level.OK, "Already in", null);
        return new Verdict(Level.FAIL, "Not yet joined.", "The Doze whitelist hook of the system framework may not be effective. Please export the report and provide feedback.");
    }

    public static Verdict configSync(boolean remoteLoaded, int remoteManual, boolean remoteAuto,
                                     int localManual, boolean localAuto) {
        if (!remoteLoaded) {
            return new Verdict(Level.FAIL, "The system framework did not read the configuration", "Please re-select the app in FCMFix or restart your phone.");
        }
        if (remoteManual != localManual || remoteAuto != localAuto) {
            return new Verdict(Level.WARN, "The configuration of the system framework is inconsistent with this",
                    "For Android versions below 14, a restart is required after modifying the configuration; in other cases, please reopen FCMFix and check again.");
        }
        return new Verdict(Level.OK, "ALREADY SYNCED", null);
    }

    public static Verdict appNotify(int notify, int blockedChannels) {
        return appNotify(notify, blockedChannels, false);
    }

    /**
     * An auto-allowed app was not picked by the user, so notifications switched off are most
     * likely deliberate: report them without counting the app as a problem.
     */
    public static Verdict appNotify(int notify, int blockedChannels, boolean auto) {
        if (notify == 0 && auto) {
            return new Verdict(Level.INFO, "Notifications are turned off",
                    "For apps that require automatic permission, enable notifications in System Settings when push notifications are needed. If not needed, you can disable automatic permission on the \"Apps\" page.");
        }
        if (notify == 0) return new Verdict(Level.FAIL, "Notifications are turned off", "Enable notifications for this app in System Settings");
        if (notify < 0) return new Verdict(Level.UNKNOWN, "Notification status unknown", null);
        if (blockedChannels > 0) {
            return new Verdict(Level.WARN, blockedChannels + "notification category(ies) have been closed",
                    "Messages in these categories will not pop up. You can check them in the app's notification settings.");
        }
        return new Verdict(Level.OK, "Notifications enabled", null);
    }

    public static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        if (minutes < 1) return "Less than 1 minute";
        if (minutes < 60) return minutes + "in minute(s)";
        return (minutes / 60) + " Hours" + (minutes % 60) + "in minute(s)";
    }

    /**
     * since is when system_server first saw the current socket and lastSeen when it last saw
     * one up; both come from samples about a minute apart while the CPU is awake.
     */
    public static Verdict fcmConnection(int state, String remote, long since, int reconnects, int outages,
                                        long longestOutage, long lastSeen, long monitorStart, long now) {
        return fcmConnection(state, remote, null, since, reconnects, outages, longestOutage, lastSeen, monitorStart, now);
    }

    public static Verdict fcmConnection(int state, String remote, String stall, long since, int reconnects, int outages,
                                        long longestOutage, long lastSeen, long monitorStart, long now) {
        String history = fcmHistory(reconnects, outages, longestOutage);
        if (state == FCM_CONNECTED && stall != null) {
            return new Verdict(Level.WARN, "TCP Connection" + remote + "It's still there, but..." + stall + "\n" + history,
                    "GMS may not be connected to FCM. Open FCM Diagnostics to confirm. If this persists, switch the network once when the screen is on to reconnect GMS.");
        }
        if (state == FCM_CONNECTED) {
            String age = since - monitorStart < 120_000
                    ? "FCMFix was connected when monitoring started, and remains so to date" + duration(now - since)
                    : "Ongoing" + duration(now - since);
            return new Verdict(Level.OK, "Already Connected" + remote + "\n" + age + "\n" + history, null);
        }
        String down = lastSeen > 0 ? ", disconnected for about" + duration(now - lastSeen) : "";
        if (state == FCM_CONNECTING) {
            return new Verdict(Level.WARN, "Connecting..." + remote + ", not yet successful" + down + "\n" + history,
                    "It keeps stopping here. Usually, port 5228 is blocked, or the proxy is not working.");
        }
        if (state == FCM_NONE) {
            return new Verdict(Level.WARN, "GMS is currently not connected to the FCM server (ports 5228–5230, or mtalk.google.com on port 443)"
                            + down + "\n" + history,
                    "When the screen has just turned on or the network has just been switched, the device may be reconnecting. Please check again later. If this persists, it is usually a network issue. It may not be recognized when going through port 443 via a proxy. The actual push notification received shall prevail.");
        }
        return new Verdict(Level.UNKNOWN, "Can't detect", null);
    }

    static String fcmHistory(int reconnects, int outages, long longestOutage) {
        if (reconnects == 0 && outages == 0) return "No disconnections or reconnections since power-on";
        StringBuilder text = new StringBuilder("Since power-on");
        if (reconnects > 0) text.append("Reconnection").append(reconnects).append(" times");
        if (outages > 0) {
            if (reconnects > 0) text.append("，");
            text.append("disconnected").append(outages).append("times (maximum approx.").append(duration(longestOutage)).append("）");
        }
        if (outages == 0) text.append(", a new connection is established immediately every time, without affecting push notifications.");
        return text.toString();
    }

    public static EventKind eventKind(String text) {
        if (text.startsWith(EVENT_PUSH)) return EventKind.PUSH;
        if (text.startsWith(EVENT_FCM_UP) || text.startsWith(EVENT_FCM_RESTORED)) return EventKind.FCM_UP;
        if (text.startsWith(EVENT_FCM_RECONNECT)) return EventKind.FCM_RECONNECT;
        if (text.startsWith(EVENT_FCM_DOWN) || text.startsWith(EVENT_FCM_STALL)) return EventKind.FCM_DOWN;
        if (text.startsWith(EVENT_NETWORK)) return EventKind.NETWORK;
        if (text.startsWith(EVENT_SCREEN_ON) || text.startsWith(EVENT_SCREEN_OFF)) return EventKind.SCREEN;
        if (text.startsWith(EVENT_DOZE_ENTER) || text.startsWith(EVENT_DOZE_EXIT)) return EventKind.DOZE;
        return EventKind.OTHER;
    }

    /** Events with an "MM-dd HH:mm:ss " prefix, merged newest first; LogRing's "…" lines go to notes. */
    public static final class Timeline {
        public final List<String> lines = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    public static Timeline mergeEvents(List<String> events, List<String> pushes) {
        Timeline timeline = new Timeline();
        List<String> a = withoutNotes(events, "Connectivity, Network, Screen On/Off, and Doze Events", timeline.notes);
        List<String> b = withoutNotes(pushes, "Push History", timeline.notes);
        int i = a.size() - 1;
        int j = b.size() - 1;
        while (i >= 0 || j >= 0) {
            if (j < 0 || (i >= 0 && a.get(i).compareTo(b.get(j)) > 0)) timeline.lines.add(a.get(i--));
            else timeline.lines.add(b.get(j--));
        }
        return timeline;
    }

    private static List<String> withoutNotes(List<String> lines, String what, List<String> notes) {
        List<String> result = new ArrayList<>();
        if (lines == null) return result;
        for (String line : lines) {
            if (line.startsWith("…")) notes.add(what + "：" + line.replaceAll("[…（）]", ""));
            else result.add(line);
        }
        return result;
    }

    /** null when the bucket needs no comment. */
    public static Verdict appBucket(int bucket) {
        if (bucket == 45 || bucket == 50) {
            return new Verdict(Level.WARN, "Standby Group:" + bucketName(bucket),
                    "The system will strictly postpone the app's background tasks, and it may not be able to retrieve message content. Allow background activity in the app's battery usage settings");
        }
        return null;
    }
}
