package com.daodian.clock;
import com.daodian.clock.core.*;
import com.daodian.clock.core.AttendanceRules.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AttendanceTests {
    private final String day="2026-11-16";
    private Evidence in(){return new Evidence(day,"clockin","10:23","正常");}
    @Test public void existingRecordWinsOverLocalGuard(){assertEquals(Decision.VERIFIED,AttendanceRules.decide(day,day,"clockin",true,in(),true,true));}
    @Test public void yesterdayCannotDeduplicateToday(){assertEquals(Decision.WRONG_DATE,AttendanceRules.decide(day,"2026-11-15","clockin",true,in(),false,true));}
    @Test public void wrongKindCannotSucceed(){assertEquals(Decision.WRONG_TYPE,AttendanceRules.decide(day,day,"clockout",true,in(),false,true));}
    @Test public void emptyLoadingPageNeverAllowsSubmission(){assertEquals(Decision.WAIT,AttendanceRules.decide(day,day,"clockout",false,null,false,true));}
    @Test public void absenceNeedsCompleteList(){assertEquals(Decision.WAIT,AttendanceRules.decide(day,day,"clockout",true,null,false,false));}
    @Test public void completedAbsenceAllowsOnlyFirstAttempt(){assertEquals(Decision.MAY_SUBMIT,AttendanceRules.decide(day,day,"clockout",true,null,false,true));assertEquals(Decision.CHECK_ONLY,AttendanceRules.decide(day,day,"clockout",true,null,true,true));}
    @Test public void unknownDateNeverSubmits(){assertEquals(Decision.WAIT,AttendanceRules.decide(day,null,"clockout",true,null,false,true));}
    @Test public void missingClockinCannotBecomeGenericClockout(){assertFalse(AttendanceRules.flexibleTargetAllowed("clockout",null,null));assertTrue(AttendanceRules.flexibleTargetAllowed("clockout",in(),null));}
    @Test public void genericClockinNeverUpdatesExistingEntry(){assertFalse(AttendanceRules.flexibleTargetAllowed("clockin",in(),null));assertTrue(AttendanceRules.flexibleTargetAllowed("clockin",null,null));}
    @Test public void testModeNeverTakesPhotoOrConfirms(){for(String phase:new String[]{"OPEN_CAMERA","CAMERA","PREVIEW","VERIFY"})for(String a:new String[]{"拍照打卡","拍摄","确认照片"})assertFalse(AttendanceRules.mayAct("TEST",phase,a,true,true));}
    @Test public void confirmationRequiresReservationAndCorrectScreen(){assertFalse(AttendanceRules.mayAct("REAL","CAMERA","确认照片",true,true));assertFalse(AttendanceRules.mayAct("REAL","PREVIEW","确认照片",false,true));assertFalse(AttendanceRules.mayAct("REAL","PREVIEW","确认照片",true,false));assertTrue(AttendanceRules.mayAct("REAL","PREVIEW","确认照片",true,true));}
    @Test public void verifyPhaseNeverResubmits(){for(String a:new String[]{"拍照打卡","拍摄","确认照片","更新打卡"})assertFalse(AttendanceRules.mayAct("REAL","VERIFY",a,true,true));}
    @Test public void staleEntryRecoveryAllowsNavigationOnlyBeforePhotography(){
        assertTrue(AttendanceRules.mayAct("REAL","OPEN_CAMERA","退出打卡页",false,true));
        assertFalse(AttendanceRules.mayAct("REAL","OPEN_CAMERA","退出打卡页",true,true));
        assertTrue(AttendanceRules.mayAct("REAL","REOPEN_ATTENDANCE","重新进入打卡",false,true));
        assertTrue(AttendanceRules.mayAct("REAL","REOPEN_ATTENDANCE","统计",false,true));
        for(String action:new String[]{"拍照打卡","拍摄","确认照片","更新打卡","返回","选择日期"})
            assertFalse(AttendanceRules.mayAct("REAL","REOPEN_ATTENDANCE",action,true,true));
        assertFalse(AttendanceRules.mayAct("REAL","VERIFY","退出打卡页",false,true));
        assertFalse(AttendanceRules.mayAct("TEST","REOPEN_ATTENDANCE","重新进入打卡",false,true));
    }
    @Test(expected=IllegalArgumentException.class)public void missingRecordStatusRejected(){new Evidence(day,"clockin","10:23","");}
    @Test(expected=IllegalArgumentException.class)public void notClockedIsNotEvidence(){new Evidence(day,"clockin","10:23","未打卡");}
    @Test(expected=IllegalArgumentException.class)public void invalidTimeRejected(){new Evidence(day,"clockin","25:01","正常");}
    @Test(expected=IllegalArgumentException.class)public void multilineMissingStateIsNotEvidence(){new Evidence(day,"clockin","10:23","未打卡\n等待处理");}
    @Test(expected=IllegalArgumentException.class)public void unicodeWhitespaceIsNotEvidence(){new Evidence(day,"clockin","10:23","\u3000 \n");}
    @Test public void exceptionalStatePreserved(){assertEquals("早退",new Evidence(day,"clockout","15:10","早退").status);}
    @Test public void evidenceFingerprintSeparatesKindsAndTimes(){assertNotEquals(in().fingerprint(),new Evidence(day,"clockout","10:23","正常").fingerprint());}
    @Test public void realMailUsesEvidenceNotWallClock(){
        byte[] png={(byte)137,80,78,71,13,10,26,10};
        String mime=new String(MailClient.attendanceMessage("x@example.com",new Evidence(day,"clockout","15:10","早退"),png),StandardCharsets.UTF_8);
        StringBuilder decoded=new StringBuilder();
        for(String part:mime.split("Content-Transfer-Encoding: base64\\r\\n\\r\\n")){
            if(part.startsWith("From:"))continue;
            String encoded=part.substring(0,part.indexOf("\r\n--"));decoded.append(new String(Base64.getMimeDecoder().decode(encoded),StandardCharsets.UTF_8));
        }
        assertTrue(decoded.toString().contains("2026-11-16"));assertTrue(decoded.toString().contains("15:10"));assertTrue(decoded.toString().contains("早退"));assertTrue(decoded.toString().contains("类型：下班"));assertFalse(decoded.toString().contains("界面到达时间"));assertTrue(mime.contains("Content-Disposition: inline"));assertFalse(mime.contains("Content-Disposition: attachment"));
    }
}
