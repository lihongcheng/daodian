package com.daodian.clock;
import android.app.*;
import android.content.*;
import android.os.Build;
import com.daodian.clock.core.SafetyRules;

public final class Notifications {
    public static final String CHANNEL="tasks";
    public static void init(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);m.createNotificationChannel(new NotificationChannel(CHANNEL,"执行进度与结果",NotificationManager.IMPORTANCE_DEFAULT));}
    public static Notification task(Context c,String title,String text,boolean ongoing){init(c);Intent open=new Intent(c,MainActivity.class);PendingIntent p=PendingIntent.getActivity(c,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_app).setContentTitle(title).setContentText(text).setContentIntent(p).setOngoing(ongoing).setAutoCancel(!ongoing);if(ongoing){Intent stop=new Intent(c,AlarmReceiver.class).setAction("stop");b.addAction(new Notification.Action.Builder(null,"停止本次",PendingIntent.getBroadcast(c,9,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());}return b.build();}
    public static void result(Context c,int id,String title,String text){try{c.getSystemService(NotificationManager.class).notify(id,task(c,title,text,false));}catch(SecurityException ignored){}}
    public static void update(Context c,String text){try{c.getSystemService(NotificationManager.class).notify(10,task(c,"到点 · 执行中",text,true));}catch(SecurityException ignored){}}
}
