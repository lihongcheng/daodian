package com.daodian.clock;
import android.content.*;
public final class RestoreReceiver extends BroadcastReceiver {public void onReceive(Context c,Intent i){String a=i.getAction();if(!Intent.ACTION_BOOT_COMPLETED.equals(a)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)&&!Intent.ACTION_TIME_CHANGED.equals(a)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(a))return;new Prefs(c).storage.edit().putString("restoreAction",a).putLong("restoreAt",System.currentTimeMillis()).commit();
        if(TaskService.instance==null)Records.get(c).recoverInterrupted();DebugChecks.expire(c);Scheduler.reschedule(c);Records.get(c).cleanupScreenshots(c,new Prefs(c).retention());}}
