package com.daodian.clock;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/** The user's switch and the OS service connection are independent states. */
final class AccessibilityStatus {
    static boolean enabled(Context context) {
        if (Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
        String services = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName target = new ComponentName(context, WecomAccessService.class);
        if (services != null) for (String service : services.split(":")) {
            if (target.equals(ComponentName.unflattenFromString(service))) return true;
        }
        return false;
    }
    static boolean connected() { return WecomAccessService.instance != null; }
    static String state(Context context) { return !enabled(context) ? "DISABLED" : connected() ? "CONNECTED" : "DISCONNECTED"; }
}
