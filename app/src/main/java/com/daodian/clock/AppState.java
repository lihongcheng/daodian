package com.daodian.clock;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import com.daodian.clock.core.*;
import org.json.*;
import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class AppState {
    public static JSONObject read(Context c){JSONObject j=new JSONObject();Prefs p=new Prefs(c);try{ZonedDateTime now=ZonedDateTime.now(ScheduleRules.ZONE);KeyguardManager guard=c.getSystemService(KeyguardManager.class);boolean access=WecomAccessService.instance!=null;boolean alarms=Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();boolean battery=c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName());boolean notifications=c.getSystemService(NotificationManager.class).areNotificationsEnabled();boolean wecom=true;try{c.getPackageManager().getPackageInfo(SafetyRules.WECOM_PACKAGE,0);}catch(PackageManager.NameNotFoundException e){wecom=false;}JSONObject permissions=new JSONObject().put("access",access).put("alarm",alarms).put("battery",battery).put("boot",p.bootConfirmed()).put("notification",notifications);JSONArray records=Records.get(c).all();for(int i=0;i<records.length();i++){JSONObject r=records.getJSONObject(i);r.put("shotAvailable",!r.optString("screenshot").isEmpty()&&new File(new File(c.getFilesDir(),"screenshots"),r.optString("screenshot")).isFile());}JSONObject active=TaskService.instance==null?null:TaskService.instance.activeRecord();if(active!=null&&!active.optString("status").equals("RUNNING"))active=null;j.put("onboardDone",p.storage.getBoolean("onboard_done",false)).put("today",now.toLocalDate().toString()).put("now",System.currentTimeMillis()).put("clock",now.format(DateTimeFormatter.ofPattern("HH:mm"))).put("weekday",now.getDayOfWeek().getValue()%7).put("master",p.master()).put("plans",new JSONObject().put("clockin",p.planJson("clockin")).put("clockout",p.planJson("clockout"))).put("next",Scheduler.next(c)).put("permissions",permissions).put("deviceLocked",guard.isDeviceLocked()).put("deviceSecure",guard.isDeviceSecure()).put("wecomInstalled",wecom).put("networkAvailable",TaskService.networkAvailable(c)).put("records",records).put("active",active==null?JSONObject.NULL:active).put("mail",p.mail()).put("reminders",p.reminders()).put("retention",p.retention()).put("theme",p.theme()).put("testOnly",false).put("executionMode",p.executionMode()).put("flexibleRule",p.flexibleRule()).put("version",BuildConfig.VERSION_NAME).put("sdk",Build.VERSION.SDK_INT).put("osVersion",Build.VERSION.RELEASE).put("model",Build.MODEL).put("restoreAction",p.storage.getString("restoreAction","")).put("restoreAt",p.storage.getLong("restoreAt",0)).put("verificationPending",p.storage.contains("verification.backup"));}catch(Exception ignored){}return j;}
}
