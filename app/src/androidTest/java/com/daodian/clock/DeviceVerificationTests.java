package com.daodian.clock;
import android.test.InstrumentationTestCase;
import android.graphics.*;
import android.content.Context;
import com.daodian.clock.core.AttendanceRules.Evidence;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class DeviceVerificationTests extends InstrumentationTestCase {
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
    public void testCalendarScreenshotAndWeekdayCrossCheck()throws Exception{
        Bitmap image=BitmapFactory.decodeStream(getInstrumentation().getContext().getAssets().open("calendar.png"));
        String correct=read(image,2026,11);assertEquals("2026-11-16",correct);
        String wrongMonth=read(image,2026,12);assertNull(wrongMonth);image.recycle();
    }
    private String read(Bitmap image,int year,int month)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] out=new String[1];CalendarReader[] reader=new CalendarReader[1];
        getInstrumentation().runOnMainSync(()->{reader[0]=new CalendarReader();reader[0].readSelected(image,year,month,new Rect(44,277,276,336),new Rect(33,406,177,444),new Rect(902,406,1047,444),(date,reason)->{out[0]=date;done.countDown();});});
        assertTrue("Bundled OCR timed out",done.await(35,TimeUnit.SECONDS));getInstrumentation().runOnMainSync(()->reader[0].close());return out[0];
    }
}
