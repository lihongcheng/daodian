package com.daodian.clock;
import android.content.*;
import com.daodian.clock.core.SafetyRules;
import java.time.*;

public final class AlarmReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){String action=i.getAction(),kind=i.getStringExtra("kind");if("stop".equals(action)){TaskService.cancel(c);return;}if(kind==null||(!kind.equals("clockin")&&!kind.equals("clockout")))return;
        if("access-recovery".equals(action)){
            if(TaskService.instance!=null || !AccessibilityStatus.connected() || !AccessibilityStatus.enabled(c))return;
            Prefs p=new Prefs(c);org.json.JSONObject r=Records.get(c).claimAccessRecovery(i.getStringExtra("id"),p.plan(kind),p.master(),p.executionMode(),System.currentTimeMillis());
            if(r!=null)try{TaskService.start(c,kind,"定时恢复",r.optLong("plannedAt"),r.optString("date"),r.optString("mode"));}
            catch(RuntimeException e){Notifications.result(c,22,"恢复启动未完成","请核查当天企微记录，系统未允许启动任务");}
            return;
        }
        if("reminder".equals(action)){Notifications.result(c,kind.equals("clockin")?101:201,SafetyRules.kindLabel(kind)+"计划将在1分钟后开始","请保持手机可解锁、网络和定位可用");if(!AccessibilityStatus.connected())Notifications.accessProblem(c,AccessibilityStatus.enabled(c));return;}Prefs p=new Prefs(c);boolean debug="debug-run".equals(action);if(!debug&&(!p.master()||!p.plan(kind).enabled))return;String date=i.getStringExtra("date");if(date==null)date=LocalDate.now(com.daodian.clock.core.ScheduleRules.ZONE).toString();if(!debug&&p.completed().contains(kind+":"+date)){Scheduler.reschedule(c);return;}TaskService.start(c,kind,debug?"调试定时":"定时",i.getLongExtra("at",System.currentTimeMillis()),date,debug?"TEST":p.executionMode());}
}
