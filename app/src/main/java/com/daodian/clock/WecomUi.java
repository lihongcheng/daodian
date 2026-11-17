package com.daodian.clock;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import com.daodian.clock.core.AttendanceRules.Evidence;
import com.daodian.clock.core.SafetyRules;
import java.util.*;

/** UI adapter for the inspected WeCom 5.0.13 (84802). Never falls back to fixed screen coordinates. */
final class WecomUi {
    static String text(CharSequence c) { return c==null ? "" : c.toString().trim(); }
    static Rect bounds(AccessibilityNodeInfo n) { Rect r=new Rect(); if(n!=null)n.getBoundsInScreen(r); return r; }
    static boolean wecom(AccessibilityNodeInfo n) { return n!=null && SafetyRules.WECOM_PACKAGE.equals(text(n.getPackageName())); }
    static List<AccessibilityNodeInfo> all(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> list=new ArrayList<>(); collect(root,list); return list;
    }
    private static void collect(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out) {
        if(n==null || out.size()>2500)return; out.add(n);
        for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out);
    }
    static AccessibilityNodeInfo exact(AccessibilityNodeInfo root,String value) {
        for(AccessibilityNodeInfo n:all(root))if(text(n.getText()).equals(value)||text(n.getContentDescription()).equals(value))return n;
        return null;
    }
    static AccessibilityNodeInfo id(AccessibilityNodeInfo root,String id) {
        for(AccessibilityNodeInfo n:all(root))if((SafetyRules.WECOM_PACKAGE+":id/"+id).equals(n.getViewIdResourceName()))return n;
        return null;
    }
    static AccessibilityNodeInfo visibleId(AccessibilityNodeInfo root,String value) {
        for(AccessibilityNodeInfo n:all(root))if((SafetyRules.WECOM_PACKAGE+":id/"+value).equals(n.getViewIdResourceName())&&n.isVisibleToUser()&&!bounds(n).isEmpty())return n;
        return null;
    }
    static boolean has(AccessibilityNodeInfo root,String value) { return exact(root,value)!=null; }
    static boolean contains(AccessibilityNodeInfo root,String value) {
        for(AccessibilityNodeInfo n:all(root))if(text(n.getText()).contains(value))return true; return false;
    }
    static boolean landing(AccessibilityNodeInfo r) { return wecom(r)&&has(r,"上下班打卡")&&has(r,"外出打卡")&&has(r,"统计")&&has(r,"考勤机"); }
    static boolean statistics(AccessibilityNodeInfo r) { return wecom(r)&&has(r,"统计")&&id(r,"atz")!=null&&has(r,"我的考勤汇总"); }
    static boolean workbench(AccessibilityNodeInfo r) {
        Rect full=bounds(r), title=bounds(exact(r,"工作台"));
        return wecom(r)&&!title.isEmpty()&&title.centerY()<full.top+full.height()*.3 &&
            ((has(r,"公告")?1:0)+(has(r,"审批")?1:0)+(has(r,"日程")?1:0)+(has(r,"微盘")?1:0)>=2);
    }
    static boolean camera(AccessibilityNodeInfo r) { return wecom(r)&&visibleId(r,"q7w")!=null&&visibleId(r,"c2i")!=null&&visibleId(r,"c2l")!=null&&visibleId(r,"khn")==null; }
    static boolean preview(AccessibilityNodeInfo r) { return wecom(r)&&visibleId(r,"khn")!=null&&visibleId(r,"c2i")!=null&&visibleId(r,"q7w")==null&&visibleId(r,"c2l")==null; }
    static boolean error(AccessibilityNodeInfo r) {
        for(String s:new String[]{"网络异常","网络不可用","网络连接失败","加载失败","连接超时","定位失败","不在打卡范围","打卡时间间隔"})if(contains(r,s))return true;
        return false;
    }
    static AccessibilityNodeInfo tab(AccessibilityNodeInfo r,String label) {
        return id(r,"统计".equals(label)?"au1":"aqp");
    }
    static final class Daily {
        Evidence clockin,clockout;
        boolean complete,empty;
        String signature;
        Evidence target(String kind) {return "clockin".equals(kind)?clockin:clockout;}
    }
    static Daily daily(AccessibilityNodeInfo root,String date) {
        Daily d=new Daily();
        if(!statistics(root)||error(root))return d;
        boolean card=contains(root,"上下班打卡（工时"), invalid=false;
        int count=0;
        StringBuilder signature=new StringBuilder();
        for(AccessibilityNodeInfo type:all(root)) {
            if(!(SafetyRules.WECOM_PACKAGE+":id/icg").equals(type.getViewIdResourceName()))continue;
            // Several ancestors reuse lq4. Find the nearest row around an actual type label,
            // otherwise enabling full accessibility retrieval counts one record three times.
            AccessibilityNodeInfo row=type;
            for(int depth=0;depth<6&&row!=null&&id(row,"dg6")==null;depth++)row=row.getParent();
            if(row==null){invalid=true;continue;}
            count++;
            AccessibilityNodeInfo time=id(row,"dg6"),state=id(row,"n4q");
            String kind=type==null?"":text(type.getText()).equals("上班")?"clockin":text(type.getText()).equals("下班")?"clockout":"";
            try {
                Evidence e=new Evidence(date,kind,text(time==null?null:time.getText()),text(state==null?null:state.getText()));
                // An offscreen/clipped record cannot serve as screenshot evidence or prove a complete list.
                Rect rr=bounds(row),full=bounds(root);
                if(rr.isEmpty()||rr.top<full.top||rr.bottom>full.bottom-100)invalid=true;
                if("clockin".equals(kind)){if(d.clockin!=null)invalid=true;d.clockin=e;}
                else {if(d.clockout!=null)invalid=true;d.clockout=e;}
                signature.append(e.fingerprint());
            }catch(Exception e){invalid=true;}
        }
        boolean empty=has(root,"暂无打卡记录")||has(root,"无打卡记录")||has(root,"暂无记录");
        boolean loading=contains(root,"加载中");
        for(AccessibilityNodeInfo n:all(root))if(n.isVisibleToUser()&&text(n.getClassName()).endsWith("ProgressBar"))loading=true;
        // The empty flexible-rule day is a fully loaded rule card with zero record rows,
        // not a text saying "no records". Confirm this shape repeatedly before allowing an entry.
        d.empty=count==0&&(empty||card&&visibleId(root,"dzw")!=null);
        d.complete=!invalid&&!loading&&(card&&count>0||d.empty);
        d.signature=signature.toString()+"|"+d.complete;
        return d;
    }
    private WecomUi() {}
}
