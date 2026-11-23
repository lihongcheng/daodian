package com.daodian.clock;

import com.daodian.clock.core.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;

public class AccessRecoveryTests {
    private long at(String value) { return LocalDateTime.parse(value).atZone(ScheduleRules.ZONE).toInstant().toEpochMilli(); }
    private Plan plan() { Plan p=new Plan("clockout");p.enabled=true;p.revision=2;return p; }
    private boolean check(String trigger,String status,String reason,String date,String mode,long plannedAt,
            boolean submitted,boolean possible,boolean used,int revision,Plan p,boolean master,String currentMode,long now) {
        return AccessRecoveryRules.eligible(trigger,status,reason,date,mode,plannedAt,submitted,possible,used,revision,p,master,currentMode,now);
    }
    @Test public void disabledAndDisconnectedFailuresAreDistinct() {
        assertEquals("ACCESS_DISABLED",AccessRecoveryRules.connectionFailure(false));
        assertEquals("ACCESS_DISCONNECTED",AccessRecoveryRules.connectionFailure(true));
    }
    @Test public void onlyPreSubmissionAccessFailuresCanRecover() {
        long selected=at("2026-11-20T19:20:19"),now=at("2026-11-20T19:45:00");
        for(String reason:new String[]{"ACCESS_DISABLED","ACCESS_DISCONNECTED"})
            assertTrue(check("定时","BLOCKED",reason,"2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",now));
        for(String status:new String[]{"SUCCESS","ALREADY_DONE","UNKNOWN","RUNNING","STOPPED","FAILED"})
            assertFalse(check("定时",status,"ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",now));
        assertFalse(check("定时","BLOCKED","NETWORK","2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",now));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,true,false,false,2,plan(),true,"REAL",now));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,true,false,2,plan(),true,"REAL",now));
    }
    @Test public void noManualRepeatedOrChangedPlanRecovery() {
        long selected=at("2026-11-20T19:20:19"),now=selected+60000;
        for(String trigger:new String[]{"手动","只核验","定时恢复","无障碍诊断"})
            assertFalse(check(trigger,"BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",now));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,true,2,plan(),true,"REAL",now));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,1,plan(),true,"REAL",now));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,plan(),false,"REAL",now));
        Plan disabled=plan();disabled.enabled=false;
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,disabled,true,"REAL",now));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","TEST",selected,false,false,false,2,plan(),true,"REAL",now));
    }
    @Test public void recoveryKeepsOriginalDateAndExpiresAfterTwoHours() {
        long selected=at("2026-11-20T19:20:19");
        assertTrue(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",selected+AccessRecoveryRules.RECOVERY_WINDOW_MS));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",selected+AccessRecoveryRules.RECOVERY_WINDOW_MS+1));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",at("2026-11-23T09:00:00")));
        long beforeMidnight=at("2026-11-20T23:50:00");
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",beforeMidnight,false,false,false,2,plan(),true,"REAL",at("2026-11-21T00:01:00")));
        assertFalse(check("定时","BLOCKED","ACCESS_DISABLED","2026-11-20","REAL",selected,false,false,false,2,plan(),true,"REAL",selected-1));
    }
}
