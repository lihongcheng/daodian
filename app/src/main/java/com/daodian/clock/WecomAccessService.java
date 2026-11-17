package com.daodian.clock;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.accessibility.*;
import com.daodian.clock.core.*;
import org.json.JSONObject;
import java.io.*;
import java.time.LocalDate;

public final class WecomAccessService extends AccessibilityService {
    public static volatile WecomAccessService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private CalendarReader calendar;
    private TaskService task;
    private String runId,kind,date,mode,phase;
    private boolean active,busy,reserved,returning,submitted,checkOnly,flexibleAllowed;
    private int generation,stableCount,verifyRefreshes,conditionRefreshes,dateSelections;
    private long deadline,lastClick,phaseAt,lastRead;
    private String stableSignature="";
    protected void onServiceConnected(){instance=this;calendar=new CalendarReader();}
    public void onInterrupt(){if(task!=null)task.finish("CHECK_REQUIRED","无障碍服务被中断",null);halt();}
    public void onDestroy(){if(task!=null)task.finish("CHECK_REQUIRED","无障碍服务已断开",null);instance=null;halt();if(calendar!=null)calendar.close();super.onDestroy();}
    public void onAccessibilityEvent(AccessibilityEvent e){if(active&&!busy&&!handler.hasCallbacks(tick))handler.postDelayed(tick,200);}
    public String inspectUi(){if(!WecomUi.wecom(getRootInActiveWindow()))return "[]";org.json.JSONArray a=new org.json.JSONArray();for(AccessibilityNodeInfo n:WecomUi.all(getRootInActiveWindow()))try{String id=n.getViewIdResourceName();if(id!=null)a.put(new JSONObject().put("id",id).put("visible",n.isVisibleToUser()).put("bounds",WecomUi.bounds(n).toShortString()).put("text",WecomUi.text(n.getText())));}catch(Exception ignored){}return a.toString();}
    public void begin(TaskService service,String id){
        halt();task=service;runId=id;JSONObject r=service.activeRecord();if(r==null)return;
        kind=r.optString("kind");date=r.optString("date");mode=r.optString("mode");checkOnly=r.optBoolean("checkOnly");
        active=true;reserved=false;returning=false;submitted=false;flexibleAllowed=false;stableCount=0;verifyRefreshes=0;conditionRefreshes=0;dateSelections=0;lastClick=0;lastRead=0;
        deadline=SystemClock.elapsedRealtime()+165000;transition("NAVIGATE",1,"打开企业微信");
        try{
            // Obfuscated camera IDs are version-specific. Navigation-only tests remain available on other versions.
            long version=getPackageManager().getPackageInfo(SafetyRules.WECOM_PACKAGE,0).getLongVersionCode();
            if("REAL".equals(mode)&&version!=84802){service.finish("BLOCKED","当前企微版本尚未适配真实提交，请使用界面测试并更新适配",null);return;}
            Intent launch=getPackageManager().getLaunchIntentForPackage(SafetyRules.WECOM_PACKAGE);
            if(launch==null)throw new IllegalStateException();launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);handler.postDelayed(tick,1000);
        }catch(Exception e){service.finish("BLOCKED","无法打开企业微信，请检查安装和后台弹出权限",null);}
    }
    public void halt(){active=false;busy=false;generation++;handler.removeCallbacksAndMessages(null);}
    private boolean current(){JSONObject r=task==null?null:task.activeRecord();return active&&r!=null&&runId.equals(r.optString("id"))&&"RUNNING".equals(r.optString("status"));}
    private void transition(String next,int step,String text){phase=next;phaseAt=SystemClock.elapsedRealtime();stableCount=0;stableSignature="";task.phase(next);task.step(step,text);}
    private long age(){return SystemClock.elapsedRealtime()-phaseAt;}
    private boolean act(AccessibilityNodeInfo node,String action){
        AccessibilityNodeInfo front=getRootInActiveWindow();
        if(!current()||!WecomUi.wecom(front)||getSystemService(KeyguardManager.class).isDeviceLocked())return false;
        if("确认照片".equals(action)&&!task.activeRecord().optBoolean("confirmationAttempted"))return false;
        if(!AttendanceRules.mayAct(mode,phase,action,reserved,true)||SystemClock.elapsedRealtime()-lastClick<1200)return false;
        if("返回".equals(action)){lastClick=SystemClock.elapsedRealtime();return performGlobalAction(GLOBAL_ACTION_BACK);}
        if(node==null||!node.refresh()||!node.isEnabled()||!SafetyRules.WECOM_PACKAGE.equals(WecomUi.text(node.getPackageName())))return false;
        AccessibilityNodeInfo candidate=node;
        for(int i=0;i<5&&candidate!=null;i++,candidate=candidate.getParent()){
            String text=WecomUi.text(candidate.getText())+WecomUi.text(candidate.getContentDescription());
            if(text.contains("更新打卡")||text.contains("补卡"))return false;
            if(candidate.isClickable()){
                Rect r=WecomUi.bounds(candidate);if(r.isEmpty()||r.width()>WecomUi.bounds(front).width()*.98&&r.height()>WecomUi.bounds(front).height()*.4)return false;
                lastClick=SystemClock.elapsedRealtime();
                // A returned false may still have side effects. Never add a second gesture for a submit action.
                return candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }return false;
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(!current()||busy)return;
        try{
            if(SystemClock.elapsedRealtime()>deadline){fail("CHECK_REQUIRED","超时，未取得完整企微记录证据");return;}
            if(getSystemService(KeyguardManager.class).isDeviceLocked()){fail("BLOCKED","执行过程中手机被锁定");return;}
            if(!date.equals(LocalDate.now(ScheduleRules.ZONE).toString())){fail("CHECK_REQUIRED","日期已经变化，停止本次操作");return;}
            AccessibilityNodeInfo root=getRootInActiveWindow();
            if(!WecomUi.wecom(root)){
                if(root!=null&&"com.miui.securitycenter".equals(WecomUi.text(root.getPackageName()))&&WecomUi.contains(root,"启动应用")){
                    task.finish("BLOCKED","请在小米系统弹窗中允许到点打开企业微信，再重新运行",null);return;
                }
                later();return;
            }
            if("REAL".equals(mode)&&WecomUi.contains(root,"打卡条件已变化")&&WecomUi.has(root,"我知道了")){
                if(act(WecomUi.exact(root,"我知道了"),"关闭条件变化提示")){
                    if(task.activeRecord().optBoolean("confirmationAttempted")){transition("VERIFY",5,"企微条件变化，核查提交结果");}
                    else if(conditionRefreshes++<1){returning=false;transition("NAVIGATE",1,"企微页面条件已过期，重新读取记录和入口");}
                    else {fail("BLOCKED","企微打卡条件持续变化，尚未确认提交");return;}
                }
                later();return;
            }
            if("NAVIGATE".equals(phase)){
                if("REAL".equals(mode)&&WecomUi.has(root,"暂不打卡")){act(WecomUi.exact(root,"暂不打卡"),"暂不打卡");later();return;}
                if(WecomUi.landing(root)){
                    if("TEST".equals(mode)){saveTest();return;}
                    if(act(WecomUi.tab(root,"统计"),"统计"))transition("READ_RECORDS",2,"读取企微当天上下班记录");
                }else if(WecomUi.statistics(root)){
                    act(WecomUi.tab(root,"打卡"),"打卡");
                }else if(WecomUi.camera(root)||WecomUi.preview(root)){act(null,"返回");}
                else if(WecomUi.workbench(root)){act(WecomUi.exact(root,"打卡"),"打卡");}
                else if(WecomUi.has(root,"工作台")){act(WecomUi.exact(root,"工作台"),"工作台");}
                else if("REAL".equals(mode)&&age()>5000){act(null,"返回");}
            }else if("READ_RECORDS".equals(phase)){
                if(returning){
                    if(WecomUi.landing(root)){transition("OPEN_CAMERA",3,"当天目标记录为空，核对拍照入口");}
                    else if(WecomUi.statistics(root))act(WecomUi.tab(root,"打卡"),"打卡");
                }else if(WecomUi.statistics(root)&&age()>2200)readRecords(root);
            }else if("OPEN_CAMERA".equals(phase)){
                if(!WecomUi.landing(root)){later();return;}
                if(WecomUi.error(root)||!WecomUi.has(root,"你已在打卡范围内")){if(age()>25000)fail("BLOCKED","企微定位或打卡范围未满足");later();return;}
                if(!task.networkAvailable()){fail("BLOCKED","进入相机前网络不可用");return;}
                String target=SafetyRules.kindLabel(kind)+"打卡",opposite=SafetyRules.kindLabel("clockin".equals(kind)?"clockout":"clockin")+"打卡";
                boolean explicit=WecomUi.has(root,target)&&!WecomUi.has(root,opposite);
                if(!explicit&&!flexibleAllowed){fail("CHECK_REQUIRED","无法确认本次类型的入口；通用拍照入口需核对无固定时段规则");return;}
                AccessibilityNodeInfo button=WecomUi.exact(root,"拍照打卡");
                if(button==null){if(age()>15000)fail("CHECK_REQUIRED","没有找到适配的拍照打卡入口");later();return;}
                reserved=true; // Fresh preflight permits photography; durable reservation happens at confirmation.
                if(act(button,"拍照打卡"))transition("CAMERA",3,"等待企微相机取景画面");
                else fail("CHECK_REQUIRED","拍照入口操作结果不明，已保留防重复保护");
            }else if("CAMERA".equals(phase)){
                if(WecomUi.camera(root)&&age()>2500){
                    if(!task.networkAvailable()){fail("CHECK_REQUIRED","拍照前网络断开");return;}
                    if(act(WecomUi.id(root,"c2i"),"拍摄"))transition("PREVIEW",4,"已拍摄当前画面，等待照片确认页");
                    else fail("CHECK_REQUIRED","拍摄动作结果不明");
                }else if(age()>18000){transition("VERIFY",5,"相机未正常返回，转为只查记录");}
            }else if("PREVIEW".equals(phase)){
                if(WecomUi.preview(root)&&age()>1800){
                    if(!task.networkAvailable()){transition("VERIFY",5,"确认前网络断开，转为只查记录");later();return;}
                    // Reserve atomically BEFORE issuing the irreversible confirmation. Camera/preview
                    // failures before this point have not submitted anything in this inspected version.
                    if(!task.reserve()){checkOnly=true;transition("VERIFY",5,"已有提交保护，只核查企微结果");later();return;}
                    task.submitted();submitted=true;
                    boolean sent=act(WecomUi.id(root,"c2i"),"确认照片");
                    transition("VERIFY",5,sent?"已确认照片，等待企微落库后核验":"确认操作结果不明，只核查企微记录");
                }else if(age()>18000){transition("VERIFY",5,"照片确认页未出现，只核查记录");}
            }else if("VERIFY".equals(phase)){
                if(WecomUi.statistics(root)&&age()>2200)readRecords(root);
                else if(WecomUi.landing(root)&&age()>2500)act(WecomUi.tab(root,"统计"),"统计");
                else if(WecomUi.has(root,"暂不打卡"))act(WecomUi.exact(root,"暂不打卡"),"暂不打卡");
                else if(age()>14000&&(WecomUi.preview(root)||WecomUi.camera(root)))act(null,"返回");
            }
            later();
        }catch(Exception e){fail("CHECK_REQUIRED","读取或保存执行状态失败，请检查企微记录");}
    }};
    private void later(){if(current()&&!busy&&!handler.hasCallbacks(tick))handler.postDelayed(tick,650);}
    private interface Frame {void accept(Bitmap bitmap,AccessibilityNodeInfo root);}
    private void frame(Frame callback){
        if(!current()||busy)return;busy=true;final int token=generation;
        takeScreenshot(0,getMainExecutor(),new TakeScreenshotCallback(){
            public void onSuccess(ScreenshotResult result){HardwareBuffer buffer=result.getHardwareBuffer();
                try{
                    if(token!=generation||!current())return;
                    AccessibilityNodeInfo root=getRootInActiveWindow();if(!WecomUi.wecom(root)){busy=false;later();return;}
                    Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());if(hardware==null)throw new IOException();
                    Bitmap bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);hardware.recycle();
                    callback.accept(bitmap,root);
                }catch(Exception e){busy=false;if(current())task.finish("CHECK_REQUIRED","截图或证据处理失败",null);}finally{buffer.close();}
            }
            public void onFailure(int code){if(token!=generation||!current())return;busy=false;task.finish("CHECK_REQUIRED","系统截图失败（"+code+"），未取得完整证据",null);}
        });
    }
    private String save(Bitmap bitmap,String prefix)throws IOException{
        File directory=new File(getFilesDir(),"screenshots");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException();
        File file=new File(directory,prefix+"-"+runId+"-"+System.currentTimeMillis()+".png");
        try(FileOutputStream output=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException();output.getFD().sync();}return file.getName();
    }
    private void saveTest(){task.step(3,"已到达打卡界面，保存界面测试截图");frame((bitmap,root)->{
        try{if(WecomUi.landing(root))task.finish("PASSED","已到达企微打卡页面；未拍照、未提交",save(bitmap,"interface"));else task.finish("CHECK_REQUIRED","截图时前台页面发生变化",null);}
        catch(Exception e){task.finish("CHECK_REQUIRED","界面截图保存失败",null);}finally{bitmap.recycle();busy=false;}
    });}
    private void readRecords(AccessibilityNodeInfo root){
        if(SystemClock.elapsedRealtime()-lastRead<1300)return;lastRead=SystemClock.elapsedRealtime();
        frame((bitmap,fresh)->{
            if(!WecomUi.statistics(fresh)){bitmap.recycle();busy=false;later();return;}
            final int token=generation;
            calendar.read(bitmap,fresh,(observedDate,reason)->{
                try{
                    if(token!=generation||!current())return;
                    AccessibilityNodeInfo live=getRootInActiveWindow();
                    if(!WecomUi.statistics(live)){stableCount=0;return;}
                    if(observedDate==null){if(age()>25000)task.finish("CHECK_REQUIRED",reason,save(bitmap,"check"));return;}
                    if(!date.equals(observedDate)){
                        Point today=calendar.sameWeekTarget(observedDate,date);
                        if(dateSelections++<1&&today!=null&&selectCalendarDay(today)){
                            stableCount=0;task.step(2,"已选择当天日期，等待企微重新加载记录");return;
                        }
                        task.finish("CHECK_REQUIRED","统计页选中日期为 "+observedDate+"，请选中 "+date+" 后重新运行",save(bitmap,"date-check"));return;
                    }
                    WecomUi.Daily daily=WecomUi.daily(fresh,observedDate),now=WecomUi.daily(live,observedDate);
                    if(daily.signature==null||!daily.signature.equals(now.signature)){stableCount=0;return;}
                    String signature=observedDate+"|"+daily.signature;
                    if(signature.equals(stableSignature))stableCount++;else{stableSignature=signature;stableCount=1;}
                    if(stableCount<(daily.empty?3:2)||daily.empty&&age()<6500)return;
                    AttendanceRules.Evidence target=daily.target(kind);
                    AttendanceRules.Decision decision=AttendanceRules.decide(date,observedDate,kind,daily.complete,target,checkOnly||"VERIFY".equals(phase),daily.complete);
                    if(decision==AttendanceRules.Decision.VERIFIED){
                        String shot=save(bitmap,"attendance");task.evidence(target);
                        task.finish(submitted?"SUCCESS":"ALREADY_DONE",SafetyRules.kindLabel(kind)+" "+target.time+" · "+target.status+(submitted?"，企微记录已核验":"，已有对应记录，本次未重复提交"),shot);
                    }else if(decision==AttendanceRules.Decision.MAY_SUBMIT){
                        if(!task.networkAvailable()){task.finish("BLOCKED","查记录后网络不可用，尚未进入提交阶段",save(bitmap,"preflight"));return;}
                        flexibleAllowed=new Prefs(WecomAccessService.this).flexibleRule()&&AttendanceRules.flexibleTargetAllowed(kind,daily.clockin,daily.clockout);
                        task.preflight(save(bitmap,"preflight"));returning=true;task.step(2,"已核对当天记录，返回本次类型的拍照入口");
                    }else if(decision==AttendanceRules.Decision.CHECK_ONLY){
                        if(age()>20000&&verifyRefreshes<2){verifyRefreshes++;act(WecomUi.tab(live,"打卡"),"打卡");phase="NAVIGATE";task.phase(phase);phaseAt=SystemClock.elapsedRealtime();returning=false;stableCount=0;}
                        else if(verifyRefreshes>=2&&age()>15000)task.finish("UNKNOWN","多次核查仍未找到当天目标记录，保护状态保留；不会再次提交",save(bitmap,"unknown"));
                    }else if(age()>25000)task.finish("CHECK_REQUIRED","当天记录列表不完整或尚未加载完成，未继续提交",save(bitmap,"check"));
                }catch(Exception e){if(current())task.finish("CHECK_REQUIRED","记录证据保存或核验失败",null);}
                finally{bitmap.recycle();if(token==generation){busy=false;later();}}
            });
        });
    }
    private boolean selectCalendarDay(Point point){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(!current()||!WecomUi.statistics(root)||!AttendanceRules.mayAct(mode,phase,"选择日期",false,true)||SystemClock.elapsedRealtime()-lastClick<1200)return false;
        Rect full=WecomUi.bounds(root),week=WecomUi.bounds(WecomUi.id(root,"c0b"));
        if(point.x<full.left||point.x>full.right||point.y<week.bottom||point.y>full.height()*.5)return false;
        Path path=new Path();path.moveTo(point.x,point.y);lastClick=SystemClock.elapsedRealtime();
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,70)).build(),null,null);
    }
    private void fail(String status,String message){if(!current())return;if(busy){task.finish(status,message,null);return;}frame((bitmap,root)->{try{task.finish(status,message,save(bitmap,"check"));}catch(Exception e){task.finish(status,message,null);}finally{bitmap.recycle();busy=false;}});}
}
