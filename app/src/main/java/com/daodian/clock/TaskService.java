package com.daodian.clock;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import com.daodian.clock.core.*;
import org.json.*;
import java.time.*;
import java.util.UUID;

public final class TaskService extends Service {
    public static volatile TaskService instance;
    private JSONObject record;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler=new Handler(Looper.getMainLooper());
    public static void start(Context c,String kind,String trigger,long at,String date){start(c,kind,trigger,at,date,new Prefs(c).executionMode());}
    public static void start(Context c,String kind,String trigger,long at,String date,String mode){
        c.startForegroundService(new Intent(c,TaskService.class).putExtra("kind",kind).putExtra("trigger",trigger).putExtra("plannedAt",at).putExtra("date",date).putExtra("mode",mode));
    }
    public static void verifyRecords(Context c,String kind){
        if(!BuildConfig.DEBUG||!AttendanceRules.validKind(kind))throw new IllegalArgumentException();
        c.startForegroundService(new Intent(c,TaskService.class).putExtra("kind",kind).putExtra("mode","REAL").putExtra("verificationOnly",true)
            .putExtra("trigger","只核验").putExtra("plannedAt",System.currentTimeMillis()).putExtra("date",LocalDate.now(ScheduleRules.ZONE).toString()));
    }
    static void probeAccess(Context c,String kind){
        if(!BuildConfig.DEBUG||!AttendanceRules.validKind(kind))throw new IllegalArgumentException();
        c.startForegroundService(new Intent(c,TaskService.class).putExtra("kind",kind).putExtra("mode","TEST")
            .putExtra("accessProbeOnly",true).putExtra("verificationOnly",true).putExtra("trigger","无障碍诊断")
            .putExtra("plannedAt",System.currentTimeMillis()).putExtra("date",LocalDate.now(ScheduleRules.ZONE).toString()));
    }
    public static void cancel(Context c){if(instance!=null)instance.finish("STOPPED","用户停止本次执行",null);else c.stopService(new Intent(c,TaskService.class));}
    public void onCreate(){super.onCreate();instance=this;Notifications.init(this);Records.get(this).recoverInterrupted();}
    public int onStartCommand(Intent i,int flags,int startId){
        if(i==null){stopSelf();return START_NOT_STICKY;}
        if(record!=null){Notifications.result(this,12,"已有任务正在执行","没有启动第二个并行任务");return START_NOT_STICKY;}
        startForeground(10,Notifications.task(this,"到点","检查执行条件",true));
        String kind=i.getStringExtra("kind"),mode=i.getStringExtra("mode");
        if(!AttendanceRules.validKind(kind)||(!"TEST".equals(mode)&&!"REAL".equals(mode))){stopSelf();return START_NOT_STICKY;}
        try{
            String date=i.getStringExtra("date");LocalDate.parse(date);
            record=new JSONObject().put("id",UUID.randomUUID().toString()).put("kind",kind).put("mode",mode).put("trigger",i.getStringExtra("trigger"))
                .put("date",date).put("plannedAt",i.getLongExtra("plannedAt",System.currentTimeMillis())).put("startedAt",System.currentTimeMillis())
                .put("status","RUNNING").put("step","检查手机状态").put("stepIndex",0).put("submissionPossible",false).put("submitted",false)
                .put("verificationOnly",i.getBooleanExtra("verificationOnly",false)).put("checkOnly","REAL".equals(mode)&&(i.getBooleanExtra("verificationOnly",false)||Records.get(this).guarded(date,kind))).put("mailStatus","NOT_SENT").put("timeline",new JSONArray());
            record.put("planRevision",new Prefs(this).plan(kind).revision).put("accessEnabledAtStart",AccessibilityStatus.enabled(this))
                .put("accessConnectedAtStart",AccessibilityStatus.connected()).put("accessProbeOnly",i.getBooleanExtra("accessProbeOnly",false));
            step(0,record.optBoolean("checkOnly")?"已有执行保护记录，本次只核查企微记录":"检查手机状态");
            KeyguardManager guard=getSystemService(KeyguardManager.class);
            if(!date.equals(LocalDate.now(ScheduleRules.ZONE).toString())){finish("CHECK_REQUIRED","执行日期已经变化，请核查目标日期的企微记录",null);return START_NOT_STICKY;}
            if(!record.optBoolean("accessProbeOnly")&&guard.isDeviceLocked()){finish("BLOCKED","手机处于安全锁屏，请先手动解锁；应用不会输入密码",null);return START_NOT_STICKY;}
            if("REAL".equals(mode)&&!networkAvailable()){finish("BLOCKED","当前没有可用网络，尚未进入提交阶段",null);return START_NOT_STICKY;}
            PowerManager power=getSystemService(PowerManager.class);wakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Daodian:Attendance");wakeLock.acquire(240000);
            handler.postDelayed(()->finish("CHECK_REQUIRED","执行超时，请核查企微记录",null),180000);
            waitForAccess(SystemClock.elapsedRealtime());
        }catch(Exception e){finish("FAILED","无法启动执行，请检查系统权限或本机存储",null);if(record==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}}
        return START_NOT_STICKY;
    }
    private void waitForAccess(long since){
        if(!running())return;
        try{
            if(!record.optString("date").equals(LocalDate.now(ScheduleRules.ZONE).toString())){finish("CHECK_REQUIRED","等待连接期间日期已变化",null);return;}
            boolean enabled=AccessibilityStatus.enabled(this),connected=enabled&&AccessibilityStatus.connected();
            if(connected){
                record.put("accessWaitMs",SystemClock.elapsedRealtime()-since);persist();
                if(record.optBoolean("accessProbeOnly")){finish("PASSED","无障碍连接已验证，仅诊断，未操作企微",null);return;}
                continueWithAccess();return;
            }
            if(SystemClock.elapsedRealtime()-since>=AccessRecoveryRules.CONNECTION_WAIT_MS){
                record.put("blockReason",AccessRecoveryRules.connectionFailure(enabled)).put("accessEnabledAtFailure",enabled)
                    .put("accessConnectedAtFailure",false).put("accessWaitMs",SystemClock.elapsedRealtime()-since);
                finish("BLOCKED",enabled?"无障碍开关已开启，但服务15秒内未连接；请在系统设置重新连接":"到点无障碍服务开关已关闭，请在系统设置开启",null);return;
            }
            if(record.optString("phase").isEmpty()){phase("WAIT_ACCESS");step(0,"等待系统连接到点无障碍服务（最多15秒）");}
            handler.postDelayed(()->waitForAccess(since),300);
        }catch(Exception e){finish("FAILED","无法读取无障碍连接状态，请检查系统设置",null);}
    }
    private void continueWithAccess(){
        KeyguardManager guard=getSystemService(KeyguardManager.class);PowerManager power=getSystemService(PowerManager.class);
        if(guard.isDeviceLocked()){finish("BLOCKED","等待连接后手机处于安全锁屏，请先手动解锁",null);return;}
        if(!power.isInteractive()||guard.isKeyguardLocked()){
            step(0,"唤醒手机并等待普通锁屏解除");startActivity(new Intent(this,WakeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final long wakeDeadline=SystemClock.elapsedRealtime()+10000;
            handler.postDelayed(new Runnable(){public void run(){
                if(!running())return;
                if(power.isInteractive()&&!guard.isKeyguardLocked()){
                    if(AccessibilityStatus.enabled(TaskService.this)&&WecomAccessService.instance!=null)WecomAccessService.instance.begin(TaskService.this,record.optString("id"));
                    else finish("BLOCKED","唤醒后无障碍服务未连接",null);
                }else if(SystemClock.elapsedRealtime()>wakeDeadline)finish("BLOCKED","系统未允许自动亮屏或解锁，请检查锁屏显示、后台弹出权限",null);
                else handler.postDelayed(this,300);
            }},600);
        }else if(WecomAccessService.instance!=null)WecomAccessService.instance.begin(this,record.optString("id"));
        else finish("BLOCKED","无障碍连接在启动前已断开",null);
    }
    boolean networkAvailable(){return networkAvailable(this);}
    static boolean networkAvailable(Context context){ConnectivityManager m=context.getSystemService(ConnectivityManager.class);Network n=m.getActiveNetwork();NetworkCapabilities c=n==null?null:m.getNetworkCapabilities(n);return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    public synchronized void step(int index,String text){if(!running())return;try{record.put("step",text).put("stepIndex",index);record.getJSONArray("timeline").put(new JSONObject().put("at",System.currentTimeMillis()).put("text",text));persist();Notifications.update(this,text);}catch(JSONException e){throw new IllegalStateException(e);}}
    synchronized void phase(String phase){if(!running())return;try{record.put("phase",phase);persist();}catch(JSONException e){throw new IllegalStateException(e);}}
    synchronized boolean reserve(){if(!running()||!"REAL".equals(record.optString("mode"))||record.optBoolean("checkOnly"))return false;return Records.get(this).reserve(record);}
    synchronized void submitted(){if(!running())return;try{record.put("submitted",true).put("submitTappedAt",System.currentTimeMillis());persist();}catch(JSONException e){throw new IllegalStateException(e);}}
    synchronized void evidence(AttendanceRules.Evidence proof){if(running())Records.get(this).verified(record,proof);}
    synchronized void preflight(String file){if(running())try{record.put("preflightScreenshot",file);persist();}catch(JSONException e){throw new IllegalStateException(e);}}
    private boolean running(){return record!=null&&"RUNNING".equals(record.optString("status"));}
    private void persist(){Records.get(this).put(record);sendBroadcast(new Intent("com.daodian.clock.STATE_CHANGED").setPackage(getPackageName()));}
    public synchronized JSONObject activeRecord(){try{return record==null?null:new JSONObject(record.toString());}catch(Exception e){return null;}}
    public synchronized void finish(String status,String message,String screenshot){
        if(!running())return;
        handler.removeCallbacksAndMessages(null);
        if(WecomAccessService.instance!=null)WecomAccessService.instance.halt();
        try{
            boolean real="REAL".equals(record.optString("mode"));
            if(real&&record.optBoolean("submissionPossible")&&!("SUCCESS".equals(status)||"ALREADY_DONE".equals(status))){status="UNKNOWN";message+="；提交可能已发生，再次运行只核查记录";}
            if(("SUCCESS".equals(status)||"ALREADY_DONE".equals(status))&&record.optJSONObject("evidence")==null){status="UNKNOWN";message="缺少匹配日期、类型、时间和状态的企微证据，请核查记录";}
            record.put("status",status).put("message",message).put("finishedAt",System.currentTimeMillis()).put("step",message);
            if("PASSED".equals(status))record.put("arrivedAt",System.currentTimeMillis());
            if(screenshot!=null)record.put("screenshot",screenshot);
            if(record.optString("trigger").equals("定时")||record.optString("trigger").equals("定时恢复"))new Prefs(this).markScheduledComplete(record.optString("kind"),record.optString("date"));
            persist();
            if(BuildConfig.DEBUG&&"TEST".equals(record.optString("mode"))&&"定时".equals(record.optString("trigger")))DebugChecks.restore(this);
            boolean verified="SUCCESS".equals(status)||"ALREADY_DONE".equals(status),report=verified||"PASSED".equals(status);
            Notifications.result(this,20,SafetyRules.kindLabel(record.optString("kind"))+(verified?"记录已核验":"PASSED".equals(status)?"界面测试完成":"执行未完成"),message);
            if(record.optString("blockReason").startsWith("ACCESS_"))Notifications.accessProblem(this,record.optBoolean("accessEnabledAtFailure"));
            JSONObject saved=new JSONObject(record.toString());Prefs p=new Prefs(this);
            if(report&&!record.optBoolean("verificationOnly")&&screenshot!=null&&p.mail().optBoolean("enabled")&&p.mail().optBoolean("configured")){
                new Thread(()->{MailJobs.send(this,saved,false);handler.post(this::stopAfterMail);},"attendance-report-mail").start();
            }else{if(report){record.put("mailStatus",record.optBoolean("verificationOnly")?"VERIFICATION_ONLY":screenshot==null?"NO_SCREENSHOT":p.mail().optBoolean("enabled")?"UNCONFIGURED":"DISABLED");persist();}stopAfterMail();}
        }catch(Exception e){stopAfterMail();}
    }
    private void stopAfterMail(){if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();Scheduler.reschedule(getApplicationContext());}
    public void onDestroy(){handler.removeCallbacksAndMessages(null);if(WecomAccessService.instance!=null)WecomAccessService.instance.halt();if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();if(instance==this)instance=null;super.onDestroy();}
    public IBinder onBind(Intent i){return null;}
}
