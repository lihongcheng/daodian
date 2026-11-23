package com.daodian.clock;
import android.app.*;
import android.content.*;
import android.os.Build;
import com.daodian.clock.core.*;
import org.json.*;
import java.time.*;

public final class Scheduler {
    private static int code(String kind,boolean reminder){return (kind.equals("clockin")?100:200)+(reminder?1:0);}
    private static PendingIntent pending(Context c,String kind,boolean reminder,long at,String date){Intent intent=new Intent(c,AlarmReceiver.class).setAction(reminder?"reminder":"run").putExtra("kind",kind).putExtra("at",at).putExtra("date",date);return PendingIntent.getBroadcast(c,code(kind,reminder),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static synchronized JSONObject reschedule(Context c){Prefs p=new Prefs(c);AlarmManager alarms=c.getSystemService(AlarmManager.class);JSONObject next=new JSONObject();for(String kind:new String[]{"clockin","clockout"}){alarms.cancel(pending(c,kind,false,0,""));alarms.cancel(pending(c,kind,true,0,""));Plan plan=p.plan(kind);if(!p.master()||!plan.enabled)continue;try{long now=System.currentTimeMillis();ScheduleRules.Next n=ScheduleRules.next(plan,now,p.choices(),p.completed());if(n==null)continue;JSONObject item=new JSONObject().put("at",n.at).put("date",n.date).put("late",n.late).put("time",Instant.ofEpochMilli(n.at).atZone(ScheduleRules.ZONE).toLocalTime().toString()).put("kind",kind);next.put(kind,item);boolean can=Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms();item.put("armed",can);if(can){alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(now+1500,n.at),pending(c,kind,false,n.at,n.date));if(plan.remind&&p.reminders()&&n.at-now>60000)alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,n.at-60000,pending(c,kind,true,n.at,n.date));}}catch(Exception ignored){}}p.storage.edit().putString("next",next.toString()).commit();return next;}
    static void accessConnected(Context c) {
        Prefs p = new Prefs(c);
        if (!AccessibilityStatus.connected() || !AccessibilityStatus.enabled(c)) return;
        for (String kind : new String[]{"clockin", "clockout"}) {
            JSONObject recovery = Records.get(c).accessRecovery(p.plan(kind), p.master(), p.executionMode(), System.currentTimeMillis());
            if (recovery == null) continue;
            Intent intent = new Intent(c, AlarmReceiver.class).setAction("access-recovery").putExtra("kind", kind).putExtra("id", recovery.optString("id"));
            PendingIntent pending = PendingIntent.getBroadcast(c, kind.equals("clockin") ? 900 : 901, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = c.getSystemService(AlarmManager.class);
            try { if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 2000, pending);
            } catch (SecurityException ignored) { Notifications.result(c, 22, "连接已恢复，任务待处理", "系统未允许恢复闹钟，请核查当天企微记录"); }
        }
    }
    public static JSONObject next(Context c){try{return new JSONObject(new Prefs(c).storage.getString("next","{}"));}catch(Exception e){return new JSONObject();}}
    public static void debugOneShot(Context c,String kind,int delaySeconds){AlarmManager a=c.getSystemService(AlarmManager.class);long at=System.currentTimeMillis()+Math.max(5,delaySeconds)*1000L;String date=Instant.ofEpochMilli(at).atZone(ScheduleRules.ZONE).toLocalDate().toString();Intent i=new Intent(c,AlarmReceiver.class).setAction("debug-run").putExtra("kind",kind).putExtra("at",at).putExtra("date",date);PendingIntent pi=PendingIntent.getBroadcast(c,777,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}
}
