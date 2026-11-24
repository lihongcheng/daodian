package com.daodian.clock.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** Decisions contain no Android input code. An unknown observation never permits submission. */
public final class AttendanceRules {
    public enum Decision { VERIFIED, MAY_SUBMIT, CHECK_ONLY, WAIT, WRONG_DATE, WRONG_TYPE }
    public static final class Evidence {
        public final String date, kind, time, status;
        public Evidence(String date, String kind, String time, String status) {
            LocalDate.parse(date);
            if (!validKind(kind) || !time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?"))
                throw new IllegalArgumentException("记录类型或时间不完整");
            LocalTime.parse(time);
            if (status == null || blank(status) || status.matches("(?s).*(未打卡|缺卡|待打卡|加载|失败).*"))
                throw new IllegalArgumentException("这不是已落库打卡记录");
            this.date=date; this.kind=kind; this.time=time; this.status=status;
        }
        public String fingerprint() { return date+"|"+kind+"|"+time+"|"+status; }
        @Override public boolean equals(Object other) { return other instanceof Evidence && fingerprint().equals(((Evidence)other).fingerprint()); }
        @Override public int hashCode() { return Objects.hash(fingerprint()); }
    }
    private static boolean blank(String text){for(int i=0;i<text.length();){int c=text.codePointAt(i);if(!Character.isWhitespace(c))return false;i+=Character.charCount(c);}return true;}
    public static boolean validKind(String kind) { return "clockin".equals(kind)||"clockout".equals(kind); }
    public static Decision decide(String requestedDate, String observedDate, String kind,
                                  boolean loaded, Evidence target, boolean priorAttempt,
                                  boolean absenceProven) {
        if (!validKind(kind)) return Decision.WRONG_TYPE;
        if (observedDate==null || !loaded) return Decision.WAIT;
        if (!requestedDate.equals(observedDate)) return Decision.WRONG_DATE;
        if (target!=null) return target.date.equals(requestedDate)&&target.kind.equals(kind)
                ? Decision.VERIFIED : Decision.WRONG_TYPE;
        if (priorAttempt) return Decision.CHECK_ONLY;
        return absenceProven ? Decision.MAY_SUBMIT : Decision.WAIT;
    }
    /** Flexible attendance: first record starts work; a later record can end work.
     * Only enabled for the explicitly selected flexible rule, with a fresh complete daily list. */
    public static boolean flexibleTargetAllowed(String kind, Evidence clockin, Evidence clockout) {
        if ("clockin".equals(kind)) return clockin==null && clockout==null;
        return "clockout".equals(kind) && clockin!=null && clockout==null;
    }
    public static boolean mayAct(String mode, String phase, String action, boolean reserved, boolean fresh) {
        if (!fresh) return false;
        if ("TEST".equals(mode)) return ("NAVIGATE".equals(phase) && ("工作台".equals(action)||"打卡".equals(action)));
        if (!"REAL".equals(mode)) return false;
        if("关闭条件变化提示".equals(action))return true;
        switch (phase) {
            case "NAVIGATE": return "工作台".equals(action)||"打卡".equals(action)||"统计".equals(action)||"暂不打卡".equals(action)||"返回".equals(action);
            case "READ_RECORDS": return "统计".equals(action)||"打卡".equals(action)||"选择日期".equals(action)||"展开日历".equals(action);
            case "OPEN_CAMERA": return reserved ? "拍照打卡".equals(action) : "退出打卡页".equals(action);
            case "REOPEN_ATTENDANCE": return "重新进入打卡".equals(action)||"统计".equals(action);
            case "CAMERA": return reserved && "拍摄".equals(action);
            case "PREVIEW": return reserved && "确认照片".equals(action);
            case "VERIFY": return "统计".equals(action)||"选择日期".equals(action)||"展开日历".equals(action)||"返回".equals(action)||"暂不打卡".equals(action);
            default: return false;
        }
    }
    private AttendanceRules() {}
}
