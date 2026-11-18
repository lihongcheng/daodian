package com.daodian.clock;

import android.graphics.*;
import android.view.accessibility.AccessibilityNodeInfo;
import com.daodian.clock.core.CalendarGrid;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Reads native month text and the selected position in a verified complete month grid. */
final class CalendarReader {
    interface Callback { void done(String date,String reason); }
    private CalendarGrid.Layout lastLayout;
    private String lastDate;
    void close(){lastLayout=null;lastDate=null;}
    private static final class Snapshot {CalendarGrid.Layout layout;boolean collapsed;}
    private static CalendarGrid.Box box(Rect r){return new CalendarGrid.Box(r.left,r.top,r.right,r.bottom);}
    private static Snapshot snapshot(AccessibilityNodeInfo root){
        Snapshot result=new Snapshot();if(!WecomUi.statistics(root))return result;
        AccessibilityNodeInfo monthNode=WecomUi.visibleId(root,"atz");
        Matcher match=Pattern.compile("^(20[0-9]{2})年([0-9]{1,2})月$").matcher(monthNode==null?"":WecomUi.text(monthNode.getText()));
        if(!match.matches())return result;
        final YearMonth month;
        try{month=YearMonth.of(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)));}catch(DateTimeException e){return result;}
        CalendarGrid.Box[] headers=new CalendarGrid.Box[7];String[] labels={"日","一","二","三","四","五","六"};int weekStart=-1;
        for(int i=0;i<7;i++){
            AccessibilityNodeInfo n=WecomUi.visibleId(root,"c0"+(char)('b'+i));
            if(n==null)return result;int weekday=Arrays.asList(labels).indexOf(WecomUi.text(n.getText()));
            if(i==0)weekStart=weekday;
            if(weekday<0||weekday!=(weekStart+i)%7)return result;
            headers[i]=box(WecomUi.bounds(n));if(WecomUi.bounds(monthNode).bottom>headers[i].top)return result;
        }
        AccessibilityNodeInfo container=WecomUi.visibleId(root,"c18"),grid=null;
        if(container==null)return result;
        for(AccessibilityNodeInfo n:WecomUi.all(container))if(n.isVisibleToUser()&&"android.widget.GridView".equals(WecomUi.text(n.getClassName()))){if(grid!=null)return result;grid=n;}
        if(grid==null)return result;
        Rect gridRect=WecomUi.bounds(grid),window=WecomUi.bounds(root);
        if(gridRect.isEmpty()||!window.contains(gridRect))return result;
        List<CalendarGrid.Cell> cells=new ArrayList<>();List<Rect> visible=new ArrayList<>();
        for(int i=0;i<grid.getChildCount();i++){
            AccessibilityNodeInfo n=grid.getChild(i);if(n==null||!WecomUi.wecom(n))return result;
            Rect b=WecomUi.bounds(n);boolean onScreen=n.isVisibleToUser()&&gridRect.contains(b);
            cells.add(new CalendarGrid.Cell(box(b),n.isClickable(),n.isEnabled(),onScreen));
            if(gridRect.contains(b)&&!b.isEmpty())visible.add(b);
        }
        result.layout=CalendarGrid.validate(month,weekStart,box(gridRect),cells,headers);
        if(result.layout==null&&visible.size()==7&&CalendarGrid.cellCount(month,weekStart)>7){
            Rect first=visible.get(0);boolean row=true;
            for(int i=0;i<7;i++){Rect b=visible.get(i);row&=Math.abs(b.top-first.top)<=3&&Math.abs(b.bottom-first.bottom)<=3&&Math.abs(b.centerX()-headers[i].centerX())<first.width()*.1;}
            AccessibilityNodeInfo toggle=WecomUi.visibleId(root,"bzw");Rect t=WecomUi.bounds(toggle);
            result.collapsed=row&&gridRect.height()<first.height()*1.25&&toggle!=null&&toggle.isClickable()&&t.top>=gridRect.bottom-3&&t.top-gridRect.bottom<first.height()*.5;
        }
        return result;
    }
    boolean needsExpansion(AccessibilityNodeInfo root){return snapshot(root).collapsed;}
    boolean matchesLastLayout(AccessibilityNodeInfo root){
        CalendarGrid.Layout live=snapshot(root).layout;
        return lastLayout!=null&&live!=null&&lastLayout.signature.equals(live.signature);
    }
    void read(Bitmap image,AccessibilityNodeInfo root,Callback callback){
        Snapshot s=snapshot(root);
        if(s.layout==null){close();callback.done(null,s.collapsed?"需要展开整月日历":"整月日历原生布局无法核验");return;}
        readGrid(image,s.layout,callback);
    }
    void readGrid(Bitmap image,CalendarGrid.Layout layout,Callback callback){
        close();
        if(layout==null||layout.bounds.left<0||layout.bounds.top<0||layout.bounds.right>image.getWidth()||layout.bounds.bottom>image.getHeight()){
            callback.done(null,"整月日历未完整显示");return;
        }
        Rect selected=selectedCircle(image,layout);
        if(selected==null){callback.done(null,"未找到唯一选中日期");return;}
        LocalDate date=layout.dateAt(box(selected));
        if(date==null){callback.done(null,"选中位置与整月原生格不一致");return;}
        lastLayout=layout;lastDate=date.toString();callback.done(lastDate,"统计年月、整月原生格和选中位置一致");
    }
    Point targetInMonth(String observed,String expected){
        if(lastLayout==null||!Objects.equals(lastDate,observed))return null;
        try{CalendarGrid.Cell cell=lastLayout.cellFor(LocalDate.parse(expected));return cell==null?null:new Point(cell.bounds.centerX(),cell.bounds.centerY());}
        catch(DateTimeException e){return null;}
    }
    boolean acceptsTarget(AccessibilityNodeInfo root,Point target){return target!=null&&matchesLastLayout(root)&&lastLayout.acceptsTarget(target.x,target.y);}
    private static boolean blue(int c){return Color.blue(c)>180&&Color.red(c)<100&&Color.green(c)>75&&Color.green(c)<195&&Color.blue(c)-Color.green(c)>40;}
    private static Rect selectedCircle(Bitmap image,CalendarGrid.Layout layout){
        CalendarGrid.Box area=layout.bounds;int cellWidth=layout.cells.get(0).bounds.width();
        int step=Math.max(1,Math.round(Math.min(cellWidth,layout.cells.get(0).bounds.height())/48f));
        int w=(area.width()+step-1)/step,h=(area.height()+step-1)/step;
        boolean[] seen=new boolean[w*h];ArrayDeque<Integer> queue=new ArrayDeque<>();List<Rect> found=new ArrayList<>();
        for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){
            int at=yy*w+xx,x=area.left+xx*step,y=area.top+yy*step;
            if(seen[at]||x>=area.right||y>=area.bottom||!blue(image.getPixel(x,y)))continue;
            int left=xx,right=xx,up=yy,down=yy,count=0;queue.add(at);seen[at]=true;
            while(!queue.isEmpty()){
                int p=queue.removeFirst(),px=p%w,py=p/w;count++;left=Math.min(left,px);right=Math.max(right,px);up=Math.min(up,py);down=Math.max(down,py);
                for(int n:new int[]{p-1,p+1,p-w,p+w}){
                    if(n<0||n>=seen.length||seen[n]||Math.abs(n%w-px)+Math.abs(n/w-py)!=1)continue;
                    seen[n]=true;int nx=area.left+(n%w)*step,ny=area.top+(n/w)*step;
                    if(nx<area.right&&ny<area.bottom&&blue(image.getPixel(nx,ny)))queue.add(n);
                }
            }
            int width=(right-left+1)*step,height=(down-up+1)*step;
            if(width>cellWidth*.5&&width<cellWidth*.85&&height>width*.8&&height<width*1.2&&count*step*step>width*height*.45)
                found.add(new Rect(area.left+left*step,area.top+up*step,Math.min(area.right,area.left+(right+1)*step),Math.min(area.bottom,area.top+(down+1)*step)));
        }
        return found.size()==1?found.get(0):null;
    }
}
