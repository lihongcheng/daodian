package com.daodian.clock;
import android.content.Context;
import org.json.*;
import java.time.*;
import java.util.*;
import com.daodian.clock.core.ScheduleRules;

/** Shell-authorized diagnostics. The temporary normal plan is TEST-only and self-restores. */
final class DebugChecks {
    static synchronized String prepareReboot(Context c,int delay)throws Exception{
        if(!BuildConfig.DEBUG||TaskService.instance!=null)throw new IllegalStateException();
        Prefs p=new Prefs(c);if(p.storage.contains("verification.backup"))throw new IllegalStateException("verification already pending");
        long at=System.currentTimeMillis()+Math.max(60,Math.min(1800,delay))*1000L;
        JSONObject backup=new JSONObject().put("master",p.master()).put("mode",p.executionMode())
            .put("clockin",p.storage.getString("plan.clockin","{}")).put("clockout",p.storage.getString("plan.clockout","{}"))
            .put("completed",new JSONArray(p.storage.getStringSet("scheduled_attempted.v2.TEST",Collections.emptySet())))
            .put("at",at).put("expires",at+600000);
        if(!p.storage.edit().putString("verification.backup",backup.toString()).commit())throw new IllegalStateException();
        p.executionMode("TEST");
        p.storage.edit().putStringSet("scheduled_attempted.v2.TEST",Collections.emptySet()).commit();
        JSONObject plan=p.planJson("clockin");String time=Instant.ofEpochMilli(at).atZone(ScheduleRules.ZONE).toLocalTime().withNano(0).toString();
        plan.put("enabled",true).put("mode","fixed").put("begin",time).put("end",time).put("days",new JSONArray(Arrays.asList(0,1,2,3,4,5,6))).put("remind",false);
        p.savePlan("clockin",plan);JSONObject out=p.planJson("clockout");out.put("enabled",false);p.savePlan("clockout",out);p.master(true);
        Scheduler.reschedule(c);return Long.toString(at);
    }
    static synchronized void restore(Context c){
        if(!BuildConfig.DEBUG)return;Prefs p=new Prefs(c);String raw=p.storage.getString("verification.backup",null);if(raw==null)return;
        try{JSONObject j=new JSONObject(raw);Set<String> completed=new HashSet<>();JSONArray a=j.getJSONArray("completed");for(int i=0;i<a.length();i++)completed.add(a.getString(i));
            if(!p.storage.edit().putString("plan.clockin",j.getString("clockin")).putString("plan.clockout",j.getString("clockout"))
                .putBoolean("master",j.getBoolean("master")).putString("execution_mode",j.getString("mode"))
                .putStringSet("scheduled_attempted.v2.TEST",completed).remove("verification.backup").commit())throw new IllegalStateException();
            Scheduler.reschedule(c);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static void expire(Context c){if(!BuildConfig.DEBUG)return;String raw=new Prefs(c).storage.getString("verification.backup",null);if(raw!=null)try{if(System.currentTimeMillis()>new JSONObject(raw).getLong("expires"))restore(c);}catch(JSONException e){restore(c);}}
}
