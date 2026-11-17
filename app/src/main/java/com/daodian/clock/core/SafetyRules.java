package com.daodian.clock.core;

import java.util.Set;

public final class SafetyRules {
    public static final String WECOM_PACKAGE = "com.tencent.wework";
    private static final Set<String> ALLOWED = Set.of("工作台","打卡");
    public static boolean canClick(String label,boolean workbenchConfirmed,boolean landingDetected) {
        if(landingDetected || label==null || !ALLOWED.contains(label.trim())) return false;
        return label.trim().equals("工作台") || workbenchConfirmed;
    }
    public static String kindLabel(String kind){return "clockin".equals(kind)?"上班":"下班";}
}
