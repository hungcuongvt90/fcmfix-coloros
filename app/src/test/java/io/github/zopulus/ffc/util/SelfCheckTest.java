package com.kooritea.fcmfix.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class SelfCheckTest {
    @Test public void gmsPolicyNamesEachRestriction() {
        assertEquals(SelfCheck.Level.OK, SelfCheck.gmsPolicy(0).level);
        assertEquals(SelfCheck.Level.UNKNOWN, SelfCheck.gmsPolicy(SelfCheck.UNKNOWN).level);
        assertTrue(SelfCheck.gmsPolicy(4).text.startsWith("All Prohibited"));
        assertTrue(SelfCheck.gmsPolicy(1).text.startsWith("Mobile Data Disabled"));
        assertTrue(SelfCheck.gmsPolicy(2).text.startsWith("Disable Wi‑Fi"));
        assertTrue(SelfCheck.gmsPolicy(3).text.startsWith("Disable Wi‑Fi and Mobile Data"));
        assertEquals(SelfCheck.Level.FAIL, SelfCheck.gmsPolicy(4).level);
    }
    @Test public void gmsBucketFailsFromRare() {
        assertEquals(SelfCheck.Level.OK, SelfCheck.gmsBucket(5).level);
        assertEquals(SelfCheck.Level.OK, SelfCheck.gmsBucket(30).level);
        assertEquals(SelfCheck.Level.FAIL, SelfCheck.gmsBucket(40).level);
        assertEquals(SelfCheck.Level.UNKNOWN, SelfCheck.gmsBucket(SelfCheck.UNKNOWN).level);
    }
    @Test public void configSyncComparesBothSides() {
        assertEquals(SelfCheck.Level.FAIL, SelfCheck.configSync(false, 0, false, 0, false).level);
        assertEquals(SelfCheck.Level.WARN, SelfCheck.configSync(true, 2, false, 3, false).level);
        assertEquals(SelfCheck.Level.WARN, SelfCheck.configSync(true, 3, false, 3, true).level);
        assertEquals(SelfCheck.Level.OK, SelfCheck.configSync(true, 3, true, 3, true).level);
    }
    @Test public void appNotifyPrefersAppSwitchOverChannels() {
        assertEquals(SelfCheck.Level.FAIL, SelfCheck.appNotify(0, 2).level);
        assertEquals(SelfCheck.Level.WARN, SelfCheck.appNotify(1, 2).level);
        assertEquals(SelfCheck.Level.OK, SelfCheck.appNotify(1, 0).level);
        assertEquals(SelfCheck.Level.UNKNOWN, SelfCheck.appNotify(SelfCheck.UNKNOWN, 0).level);
    }

    @Test public void stalledSocketIsAWarning() {
        SelfCheck.Verdict stalled = SelfCheck.fcmConnection(SelfCheck.FCM_CONNECTED, "1.2.3.4:5228（Wi‑Fi）",
                "No data received for approximately 45 minutes (excluding sleep time)", 0, 0, 0, 0, 0, 0, 1000);
        assertEquals(SelfCheck.Level.WARN, stalled.level);
        assertTrue(stalled.text.startsWith("TCP connection 1.2.3.4:5228 (Wi‑Fi) is still there"));
        assertEquals(SelfCheck.EventKind.FCM_DOWN, SelfCheck.eventKind(SelfCheck.EVENT_FCM_STALL + " 1.2.3.4:5228"));
    }

    @Test public void appNotifyOffIsOnlyInfoForAutoAllowedApps() {
        assertEquals(SelfCheck.Level.INFO, SelfCheck.appNotify(0, 2, true).level);
        assertEquals(SelfCheck.Level.FAIL, SelfCheck.appNotify(0, 2, false).level);
        assertEquals(SelfCheck.Level.WARN, SelfCheck.appNotify(1, 2, true).level);
    }
    @Test public void appBucketOnlyFlagsRestricted() {
        assertNull(SelfCheck.appBucket(40));
        assertNull(SelfCheck.appBucket(SelfCheck.UNKNOWN));
        assertEquals(SelfCheck.Level.WARN, SelfCheck.appBucket(45).level);
    }
    @Test public void durationRoundsDownToMinutes() {
        assertEquals("Less than 1 minute", SelfCheck.duration(59_000));
        assertEquals("5 minutes", SelfCheck.duration(5 * 60_000 + 30_000));
        assertEquals("2 hours 3 minutes", SelfCheck.duration(123 * 60_000));
        assertEquals("Less than 1 minute", SelfCheck.duration(-1));
    }
    @Test public void fcmConnectionVerdicts() {
        long start = 1_000_000;
        SelfCheck.Verdict connected = SelfCheck.fcmConnection(SelfCheck.FCM_CONNECTED, "1.2.3.4:5228",
                start + 600_000, 3, 1, 4 * 60_000, start + 600_000 + 30 * 60_000, start,
                start + 600_000 + 30 * 60_000);
        assertEquals(SelfCheck.Level.OK, connected.level);
        assertTrue(connected.text.contains("Has lasted 30 minutes"));
        assertTrue(connected.text.contains("3 reconnects, 1 disconnect (up to about 4 minutes)"));
        SelfCheck.Verdict sinceBoot = SelfCheck.fcmConnection(SelfCheck.FCM_CONNECTED, "1.2.3.4:5228",
                start + 1000, 0, 0, 0, start + 3_600_000, start, start + 3_600_000);
        assertTrue(sinceBoot.text.contains("Connected when monitoring started"));
        assertTrue(sinceBoot.text.contains("No disconnection or reconnection"));
        SelfCheck.Verdict down = SelfCheck.fcmConnection(SelfCheck.FCM_NONE, null, 0, 0, 1, 0,
                start, start, start + 5 * 60_000);
        assertEquals(SelfCheck.Level.WARN, down.level);
        assertTrue(down.text.contains("Disconnected for about 5 minutes"));
        assertEquals(SelfCheck.Level.WARN, SelfCheck.fcmConnection(SelfCheck.FCM_CONNECTING, "x", 0, 0, 0, 0, 0, start, start).level);
        assertEquals(SelfCheck.Level.UNKNOWN, SelfCheck.fcmConnection(SelfCheck.UNKNOWN, null, 0, 0, 0, 0, 0, start, start).level);
    }
    @Test public void reconnectsAloneAreReassuring() {
        assertEquals("Reconnected 12 times since power-on, with a new connection established immediately each time, without affecting push notifications.", SelfCheck.fcmHistory(12, 0, 0));
        assertEquals("Disconnected twice since power-on (longest duration approximately 3 minutes)", SelfCheck.fcmHistory(0, 2, 3 * 60_000));
    }
    @Test public void eventKindsFollowMonitorPrefixes() {
        assertEquals(SelfCheck.EventKind.PUSH, SelfCheck.eventKind(SelfCheck.EVENT_PUSH + "org.telegram.messenger"));
        assertEquals(SelfCheck.EventKind.FCM_UP, SelfCheck.eventKind(SelfCheck.EVENT_FCM_RESTORED + " 1.2.3.4:5228"));
        assertEquals(SelfCheck.EventKind.FCM_RECONNECT, SelfCheck.eventKind(SelfCheck.EVENT_FCM_RECONNECT + " x"));
        assertEquals(SelfCheck.EventKind.FCM_DOWN, SelfCheck.eventKind(SelfCheck.EVENT_FCM_DOWN + "（x）"));
        assertEquals(SelfCheck.EventKind.NETWORK, SelfCheck.eventKind("Disconnected"));
        assertEquals(SelfCheck.EventKind.DOZE, SelfCheck.eventKind(SelfCheck.EVENT_DOZE_EXIT));
        assertEquals(SelfCheck.EventKind.SCREEN, SelfCheck.eventKind(SelfCheck.EVENT_SCREEN_OFF));
    }
    @Test public void mergeEventsInterleavesNewestFirst() {
        SelfCheck.Timeline timeline = SelfCheck.mergeEvents(
                Arrays.asList("…(96 earlier messages were discarded)", "10-10 09:10:36 Screen On", "10-10 09:10:49 FCM Disconnected"),
                Arrays.asList("10-10 09:07:08 Push → a", "10-10 09:11:26 Push → b"));
        assertEquals(Arrays.asList("10-10 09:11:26 Push → b", "10-10 09:10:49 FCM Disconnected",
                "10-10 09:10:36 Screen On", "10-10 09:07:08 Push → a"), timeline.lines);
        assertEquals(Collections.singletonList("Connectivity, network, screen on/off, and Doze events: 96 earlier events discarded"), timeline.notes);
        assertTrue(SelfCheck.mergeEvents(null, null).lines.isEmpty());
    }
}
