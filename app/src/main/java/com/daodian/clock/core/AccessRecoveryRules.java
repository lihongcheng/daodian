package com.daodian.clock.core;

import java.time.Instant;

/** One recovery of a scheduled attempt that stopped before any UI operation. */
public final class AccessRecoveryRules {
    public static final long CONNECTION_WAIT_MS = 15000;
    public static final long RECOVERY_WINDOW_MS = 2 * 60 * 60 * 1000;
    public static String connectionFailure(boolean enabled) {
        return enabled ? "ACCESS_DISCONNECTED" : "ACCESS_DISABLED";
    }
    public static boolean eligible(String trigger, String status, String reason, String date, String mode,
            long plannedAt, boolean submitted, boolean submissionPossible, boolean recoveryUsed,
            int originalRevision, Plan plan, boolean master, String currentMode, long now) {
        if (!master || !plan.enabled || !plan.kind.equals("clockin") && !plan.kind.equals("clockout")) return false;
        if (!"定时".equals(trigger) || !"BLOCKED".equals(status) ||
                !("ACCESS_DISABLED".equals(reason) || "ACCESS_DISCONNECTED".equals(reason))) return false;
        if (submitted || submissionPossible || recoveryUsed || originalRevision != plan.revision || !mode.equals(currentMode)) return false;
        if (plannedAt <= 0 || now < plannedAt || now - plannedAt > RECOVERY_WINDOW_MS) return false;
        return Instant.ofEpochMilli(now).atZone(ScheduleRules.ZONE).toLocalDate().toString().equals(date)
                && Instant.ofEpochMilli(plannedAt).atZone(ScheduleRules.ZONE).toLocalDate().toString().equals(date);
    }
}
