package io.github.zopulus.ffc.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One immutable view of one RemotePreferences read; partial updates are never published. */
public final class ConfigSnapshot {
<<<<<<< HEAD:app/src/main/java/com/kooritea/fcmfix/util/ConfigSnapshot.java
    public static final String AUTO_ALLOW_FCM = "autoAllowFcm";

    /** Apps the user ticked by hand. */
=======
    public final long revision;
>>>>>>> 53450a0 (Apply ColorOS17 customization and MD3E UI):app/src/main/java/io/github/zopulus/ffc/util/ConfigSnapshot.java
    public final Set<String> allowList;
    /** FCM apps the user unticked while auto-allow is on; they stay out of the automatic set. */
    public final Set<String> excludeList;
    public final Map<String, Boolean> options;

    public ConfigSnapshot(Map<String, ?> values) {
<<<<<<< HEAD:app/src/main/java/com/kooritea/fcmfix/util/ConfigSnapshot.java
        allowList = packageSet(values, "allowList");
        excludeList = packageSet(values, "excludeList");
        Map<String, Boolean> flags = new HashMap<>();
        for (String key : new String[]{"disableAutoCleanNotification", "includeIceBoxDisableApp", AUTO_ALLOW_FCM}) {
=======
        Object stamp = values.get("revision");
        if (stamp != null && (!(stamp instanceof Number) || ((Number) stamp).longValue() < 0)) throw new IllegalArgumentException("Invalid revision");
        revision = stamp == null ? 0 : ((Number) stamp).longValue();
        Set<String> packages = new HashSet<>();
        Object raw = values.get("allowList");
        if (raw != null) {
            if (!(raw instanceof Set<?>)) throw new IllegalArgumentException("Invalid allowList");
            for (Object name : (Set<?>) raw) {
                if (!(name instanceof String)) throw new IllegalArgumentException("Invalid package name");
                packages.add((String) name);
            }
        }
        allowList = Collections.unmodifiableSet(packages);
        Map<String, Boolean> flags = new HashMap<>();
        for (String key : new String[]{"disableAutoCleanNotification", "includeIceBoxDisableApp", "deepSleepGoogleWhitelist", "disableGoogleNetworkControl", "rootDeepSleepNetworkWhitelist"}) {
>>>>>>> 53450a0 (Apply ColorOS17 customization and MD3E UI):app/src/main/java/io/github/zopulus/ffc/util/ConfigSnapshot.java
            Object value = values.get(key);
            if (value != null && !(value instanceof Boolean)) throw new IllegalArgumentException("Invalid " + key);
            boolean defaultValue = "deepSleepGoogleWhitelist".equals(key) || "disableGoogleNetworkControl".equals(key);
            flags.put(key, value == null ? defaultValue : Boolean.TRUE.equals(value));
        }
        if (!flags.get("deepSleepGoogleWhitelist")) flags.put("rootDeepSleepNetworkWhitelist", false);
        options = Collections.unmodifiableMap(flags);
    }

    public boolean autoAllowFcm() {
        return options.get(AUTO_ALLOW_FCM);
    }

    /** Whether a push to this package is let through; fcmPackages are the installed FCM apps. */
    public boolean allows(String packageName, Set<String> fcmPackages) {
        if (packageName == null) return false;
        if (allowList.contains(packageName)) return true;
        return autoAllowFcm() && !excludeList.contains(packageName) && fcmPackages.contains(packageName);
    }

    private static Set<String> packageSet(Map<String, ?> values, String key) {
        Set<String> packages = new HashSet<>();
        Object raw = values.get(key);
        if (raw != null) {
            if (!(raw instanceof Set<?>)) throw new IllegalArgumentException("Invalid " + key);
            for (Object name : (Set<?>) raw) {
                if (!(name instanceof String)) throw new IllegalArgumentException("Invalid package name");
                packages.add((String) name);
            }
        }
        return Collections.unmodifiableSet(packages);
    }
}
