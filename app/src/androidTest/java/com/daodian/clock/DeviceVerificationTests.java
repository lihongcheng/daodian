package com.daodian.clock;
import android.test.InstrumentationTestCase;
import android.graphics.*;
import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;
import com.daodian.clock.core.AttendanceRules.Evidence;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class DeviceVerificationTests extends InstrumentationTestCase {
    public void testStaleCompletionCardIsNotAnActionableEntry(){
        AccessibilityNodeInfo card=AccessibilityNodeInfo.obtain();
        try{
            card.setBoundsInScreen(new Rect(400,720,680,790));
            card.setText("下班 · 正常");
            card.setVisibleToUser(true);
            assertTrue(WecomUi.completionCard(card));
            assertNull(WecomUi.visibleExact(card,"拍照打卡"));
            card.setText("拍照打卡");
            assertFalse(WecomUi.completionCard(card));
            assertNotNull(WecomUi.visibleExact(card,"拍照打卡"));
            card.setVisibleToUser(false);
            assertNull(WecomUi.visibleExact(card,"拍照打卡"));
        }finally{card.recycle();}
    }
    private JSONObject run(String date,String kind)throws Exception{return new JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("kind",kind).put("mode","REAL").put("status","RUNNING").put("startedAt",System.currentTimeMillis()).put("mailStatus","NOT_SENT");}
    public void testDurableReservationAcrossDatabaseReopen()throws Exception{
        Context c=getInstrumentation().getTargetContext();c.deleteDatabase("verification-tests.db");Records r=Records.isolatedForTest(c);JSONObject first=run("2026-11-16","clockout");r.put(first);assertTrue(r.reserve(first));r.close();
        r=Records.isolatedForTest(c);assertTrue(r.guarded("2026-11-16","clockout"));assertFalse(r.guarded("2026-11-16","clockin"));assertFalse(r.guarded("2026-11-17","clockout"));assertFalse(r.reserve(run("2026-11-16","clockout")));r.recoverInterrupted();assertEquals("UNKNOWN",r.get(first.getString("id")).getString("status"));r.close();
    }
    public void testRecoveryBeforeReservationDoesNotInventSubmission()throws Exception{
        Context c=getInstrumentation().getTargetContext();c.deleteDatabase("verification-tests.db");Records r=Records.isolatedForTest(c);JSONObject first=run("2026-11-16","clockin");r.put(first);r.recoverInterrupted();assertEquals("CHECK_REQUIRED",r.get(first.getString("id")).getString("status"));assertFalse(r.guarded("2026-11-16","clockin"));r.close();
    }
    public void testMailRecoveryAndFingerprintDedup()throws Exception{
        Context c=getInstrumentation().getTargetContext();c.deleteDatabase("verification-tests.db");Records r=Records.isolatedForTest(c);JSONObject row=run("2026-11-16","clockin");Evidence e=new Evidence("2026-11-16","clockin","10:23","正常");r.verified(row,e);row.put("status","ALREADY_DONE").put("mailStatus","SENDING");r.put(row);r.recoverInterrupted();assertEquals("UNKNOWN",r.get(row.getString("id")).getString("mailStatus"));assertTrue(r.evidenceMailed(e.fingerprint()));assertFalse(r.evidenceMailed(new Evidence("2026-11-16","clockout","10:23","正常").fingerprint()));r.close();
    }
    public void testMonthEndScreenshotHasVerifiedNativeDate()throws Exception{
        Bitmap image=BitmapFactory.decodeStream(getInstrumentation().getContext().getAssets().open("calendar-grid/2026-11-30.png"));
        String[] out=new String[1];CalendarReader reader=new CalendarReader();reader.readGrid(image,CalendarGridFixtures.layout(2026,11),(date,reason)->out[0]=date);
        assertEquals("2026-11-30",out[0]);image.recycle();reader.close();
    }
}
