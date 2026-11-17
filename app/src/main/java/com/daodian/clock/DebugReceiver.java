package com.daodian.clock;
import android.content.*;
import org.json.*;
import java.time.LocalDate;

public final class DebugReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){if(!BuildConfig.DEBUG){setResultData("debug_disabled");return;}try{String action=i.getAction();
            if("com.daodian.clock.PREPARE_REBOOT_TEST".equals(action)){setResultData(DebugChecks.prepareReboot(c,i.getIntExtra("delay",240)));return;}
            if("com.daodian.clock.RESTORE_TEST_CONFIG".equals(action)){DebugChecks.restore(c);setResultData("restored");return;}
if("com.daodian.clock.INSPECT_UI".equals(action)){setResultData(WecomAccessService.instance==null?"disconnected":WecomAccessService.instance.inspectUi());return;}
            if("com.daodian.clock.RUN_REAL".equals(action)||"com.daodian.clock.RUN_TEST".equals(action)){
                boolean real=action.endsWith("RUN_REAL");String kind=i.getStringExtra("kind");
                if(!com.daodian.clock.core.AttendanceRules.validKind(kind)||real&&!i.getBooleanExtra("acknowledge_real_submission",false)){setResultData("invalid_explicit_request");return;}
                TaskService.start(c,kind,"真机验收",System.currentTimeMillis(),LocalDate.now(com.daodian.clock.core.ScheduleRules.ZONE).toString(),real?"REAL":"TEST");setResultData("started");return;
            }
            if("com.daodian.clock.FLEXIBLE_RULE".equals(action)){new Prefs(c).flexibleRule(i.getBooleanExtra("confirmed",false));setResultData("saved");return;}
if("com.daodian.clock.EXPORT_SCREENSHOT".equals(action)){setResultData(DebugTransport.export(c,i.getStringExtra("id")));return;}if("com.daodian.clock.EPHEMERAL_MAIL".equals(action)){setResultData(DebugTransport.mail(c,i.getStringExtra("address")));return;}if("com.daodian.clock.TEST_CRYPTO".equals(action)){if(TaskService.instance!=null){setResultData("busy");return;}setResultData(DebugCrypto.verify(c)?"crypto_passed":"crypto_failed");return;}if("com.daodian.clock.RESTORE".equals(action)){Scheduler.reschedule(c);setResultData("restored");return;}if("com.daodian.clock.GET_STATE".equals(action)){setResultData(AppState.read(c).toString());return;}if("com.daodian.clock.SCHEDULE_TEST".equals(action)){String kind=i.getStringExtra("kind");if(!"clockin".equals(kind)&&!"clockout".equals(kind))throw new IllegalArgumentException();Scheduler.debugOneShot(c,kind,Math.min(3600,Math.max(5,i.getIntExtra("delay",30))));setResultData("scheduled_test_only");return;}if("com.daodian.clock.CANCEL_TEST_ALARM".equals(action)){android.app.PendingIntent p=android.app.PendingIntent.getBroadcast(c,777,new Intent(c,AlarmReceiver.class).setAction("debug-run"),android.app.PendingIntent.FLAG_NO_CREATE|android.app.PendingIntent.FLAG_IMMUTABLE);if(p!=null)c.getSystemService(android.app.AlarmManager.class).cancel(p);setResultData("cancelled");return;}if("com.daodian.clock.STOP".equals(action)){TaskService.cancel(c);setResultData("stopped");return;}setResultData("unknown_action");}catch(Exception e){setResultData("debug_request_failed");}}
}
