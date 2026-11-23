package com.daodian.clock;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import com.daodian.clock.core.AttendanceRules.Evidence;
import com.daodian.clock.core.AccessRecoveryRules;
import com.daodian.clock.core.Plan;
import org.json.*;
import java.io.File;

public final class Records extends SQLiteOpenHelper {
    private static volatile Records instance;
    private Records(Context c){this(c,"records.db");}
    private Records(Context c,String name){super(c.getApplicationContext()==null?c:c.getApplicationContext(),name,null,3);}
    static Records isolatedForTest(Context c){if(!BuildConfig.DEBUG)throw new IllegalStateException();return new Records(c,"verification-tests.db");}
    public static Records get(Context c){if(instance==null)synchronized(Records.class){if(instance==null)instance=new Records(c);}return instance;}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE runs(id TEXT PRIMARY KEY,created INTEGER NOT NULL,body TEXT NOT NULL)");createGuards(db);}
    private void createGuards(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS attendance_guard(date TEXT NOT NULL,kind TEXT NOT NULL,run_id TEXT NOT NULL,state TEXT NOT NULL,evidence TEXT,PRIMARY KEY(date,kind))");}
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2)createGuards(db);
        if(oldVersion<3){
            // RC1 reserved before opening the camera. It durably wrote submitted=true BEFORE
            // confirmation. A terminal RC1 run without that marker provably never confirmed.
            try(Cursor c=db.rawQuery("SELECT g.date,g.kind,r.body FROM attendance_guard g JOIN runs r ON g.run_id=r.id WHERE g.state='UNCERTAIN'",null)){
                while(c.moveToNext())try{
                    JSONObject r=new JSONObject(c.getString(2));
                    if(!r.optBoolean("submitted")&&!r.optBoolean("confirmationAttempted")&&!r.has("submitTappedAt")){
                        db.execSQL("UPDATE attendance_guard SET state='ABORTED_BEFORE_CONFIRMATION' WHERE date=? AND kind=?",new Object[]{c.getString(0),c.getString(1)});
                        r.put("submissionPossible",false).put("status","CHECK_REQUIRED").put("message","旧版在照片确认前停止，尚未请求提交；再次执行仍先核对企微记录");
                        ContentValues v=new ContentValues();v.put("body",r.toString());db.update("runs",v,"id=?",new String[]{r.optString("id")});
                    }
                }catch(JSONException e){throw new SQLiteException("旧版记录迁移失败");}
            }
        }
    }
    public synchronized void put(JSONObject record){ContentValues v=new ContentValues();v.put("id",record.optString("id"));v.put("created",record.optLong("startedAt",System.currentTimeMillis()));v.put("body",record.toString());if(getWritableDatabase().insertWithOnConflict("runs",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("记录未保存");}
    public synchronized JSONObject get(String id){try(Cursor c=getReadableDatabase().query("runs",new String[]{"body"},"id=?",new String[]{id},null,null,null)){if(c.moveToFirst())return new JSONObject(c.getString(0));}catch(JSONException ignored){}return null;}
    private boolean accessRecoveryEligible(JSONObject r, Plan plan, boolean master, String mode, long now) {
        return r != null && plan.kind.equals(r.optString("kind")) && r.has("planRevision") &&
                !guarded(r.optString("date"), plan.kind) && AccessRecoveryRules.eligible(
                r.optString("trigger"), r.optString("status"), r.optString("blockReason"), r.optString("date"),
                r.optString("mode"), r.optLong("plannedAt"), r.optBoolean("submitted"), r.optBoolean("submissionPossible"),
                r.optBoolean("accessRecoveryUsed"), r.optInt("planRevision"), plan, master, mode, now);
    }
    public synchronized JSONObject accessRecovery(Plan plan, boolean master, String mode, long now) {
        String today = java.time.Instant.ofEpochMilli(now).atZone(com.daodian.clock.core.ScheduleRules.ZONE).toLocalDate().toString();
        // A later attempt supersedes an earlier failure, including manual runs and uncertain submissions.
        try (Cursor c = getReadableDatabase().rawQuery("SELECT body FROM runs ORDER BY created DESC", null)) {
            while (c.moveToNext()) try {
                JSONObject r = new JSONObject(c.getString(0));
                if (plan.kind.equals(r.optString("kind")) && mode.equals(r.optString("mode")) && today.equals(r.optString("date")))
                    return accessRecoveryEligible(r, plan, master, mode, now) ? r : null;
            } catch (JSONException ignored) {}
        }
        return null;
    }
    public synchronized JSONObject claimAccessRecovery(String id, Plan plan, boolean master, String mode, long now) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            JSONObject r = accessRecovery(plan, master, mode, now);
            if (r == null || !id.equals(r.optString("id"))) return null;
            r.put("accessRecoveryUsed", true).put("accessRecoveryClaimedAt", now);
            put(r); db.setTransactionSuccessful(); return r;
        } catch (JSONException e) { throw new IllegalStateException(e); }
        finally { db.endTransaction(); }
    }
    public synchronized JSONArray all(){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM runs ORDER BY created DESC LIMIT 300",null)){while(c.moveToNext()){try{a.put(new JSONObject(c.getString(0)));}catch(JSONException ignored){}}}return a;}
    public synchronized boolean guarded(String date,String kind){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM attendance_guard WHERE date=? AND kind=? AND state IN ('UNCERTAIN','VERIFIED')",new String[]{date,kind})){return c.moveToFirst();}}
    /** Insert and fsync through SQLite before photo confirmation. No replace and no deletion path. */
    public synchronized boolean reserve(JSONObject run){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("date",run.optString("date"));v.put("kind",run.optString("kind"));v.put("run_id",run.optString("id"));v.put("state","UNCERTAIN");
            if(db.insertWithOnConflict("attendance_guard",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0 &&
               db.update("attendance_guard",v,"date=? AND kind=? AND state='ABORTED_BEFORE_CONFIRMATION'",new String[]{run.optString("date"),run.optString("kind")})!=1)return false;
            run.put("submissionPossible",true).put("confirmationAttempted",true).put("guardVersion",2).put("reservedAt",System.currentTimeMillis());put(run);db.setTransactionSuccessful();return true;
        }catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}
    }
    public synchronized void verified(JSONObject run,Evidence evidence){
        try{
            JSONObject proof=new JSONObject().put("date",evidence.date).put("kind",evidence.kind).put("time",evidence.time).put("state",evidence.status).put("source","企微当日统计").put("fingerprint",evidence.fingerprint());
            run.put("evidence",proof).put("verifiedAt",System.currentTimeMillis());
            SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
            try{
                ContentValues v=new ContentValues();v.put("date",evidence.date);v.put("kind",evidence.kind);v.put("run_id",run.optString("id"));v.put("state","VERIFIED");v.put("evidence",proof.toString());
                db.insertWithOnConflict("attendance_guard",null,v,SQLiteDatabase.CONFLICT_REPLACE);put(run);db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    public synchronized void recoverInterrupted(){
        // Recovery is independent of the 300-row presentation limit.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM runs",null)){
            while(c.moveToNext())try{
                JSONObject r=new JSONObject(c.getString(0));boolean changed=false;
                if("RUNNING".equals(r.optString("status"))){
                    r.put("status",r.optBoolean("submissionPossible")?"UNKNOWN":"CHECK_REQUIRED")
                        .put("message",r.optBoolean("submissionPossible")?"上次执行中断，提交结果不明；再次运行只核查企微记录":"上次执行中断，尚未进入提交阶段")
                        .put("finishedAt",System.currentTimeMillis());changed=true;
                }
                if("SENDING".equals(r.optString("mailStatus"))){r.put("mailStatus","UNKNOWN").put("mailError","邮件进程中断，请先核对邮箱；不会自动重发");changed=true;}
                if(changed)put(r);
            }catch(JSONException ignored){}
        }
    }
    public synchronized boolean evidenceMailed(String fingerprint){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM runs",null)){
            while(c.moveToNext())try{JSONObject r=new JSONObject(c.getString(0));JSONObject p=r.optJSONObject("evidence");if(p!=null&&fingerprint.equals(p.optString("fingerprint"))&&("SENT".equals(r.optString("mailStatus"))||"UNKNOWN".equals(r.optString("mailStatus"))||"SENDING".equals(r.optString("mailStatus"))))return true;}catch(JSONException ignored){}
        }return false;
    }
    public synchronized void cleanupScreenshots(Context context,int days){long before=System.currentTimeMillis()-days*86400000L;File[] files=new File(context.getFilesDir(),"screenshots").listFiles();if(files!=null)for(File f:files)if(f.isFile()&&f.lastModified()<before)f.delete();}
}
