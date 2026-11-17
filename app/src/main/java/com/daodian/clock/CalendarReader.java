package com.daodian.clock;

import android.graphics.*;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Reads the selected day from pixels because WeCom's calendar exposes no accessibility day nodes. */
final class CalendarReader {
    interface Callback { void done(String date,String reason); }
    private Rect lastCircle;
    private int firstColumn,lastColumn;
    private final TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    void close() {recognizer.close();}
    void read(Bitmap image,AccessibilityNodeInfo root,Callback callback) {
        AccessibilityNodeInfo monthNode=WecomUi.id(root,"atz");
        String monthText=monthNode==null?"":WecomUi.text(monthNode.getText());
        Matcher month=Pattern.compile("^(20[0-9]{2})年([0-9]{1,2})月$").matcher(monthText);
        if(!month.matches()){callback.done(null,"统计页年月无法识别");return;}
        Rect monthRect=WecomUi.bounds(monthNode);
        Rect sunday=WecomUi.bounds(WecomUi.id(root,"c0b")),saturday=WecomUi.bounds(WecomUi.id(root,"c0h"));
        readSelected(image,Integer.parseInt(month.group(1)),Integer.parseInt(month.group(2)),monthRect,sunday,saturday,callback);
    }
    void readSelected(Bitmap image,int year,int mon,Rect monthRect,Rect sunday,Rect saturday,Callback callback) {
        lastCircle=null;
        if(sunday.isEmpty()||saturday.isEmpty()){callback.done(null,"日历星期栏无法识别");return;}
        int top=Math.max(monthRect.bottom,sunday.bottom),bottom=Math.min(image.getHeight()/2,image.getHeight()-1);
        Rect circle=selectedCircle(image,top,bottom);
        if(circle==null){callback.done(null,"未找到唯一选中日期");return;}
        float column=(saturday.centerX()-sunday.centerX())/6f;
        int weekday=Math.round((circle.centerX()-sunday.centerX())/column);
        if(weekday<0||weekday>6||Math.abs(circle.centerX()-(sunday.centerX()+weekday*column))>column*.22){callback.done(null,"选中日与星期列不一致");return;}
        // Remove the blue fill; only the white numeral in the central part remains dark.
        Rect digits=new Rect(circle.left+circle.width()/5,circle.top+circle.height()/5,
                circle.right-circle.width()/5,circle.bottom-circle.height()/5);
        Bitmap binary=Bitmap.createBitmap(digits.width(),digits.height(),Bitmap.Config.ARGB_8888);
        for(int y=0;y<digits.height();y++)for(int x=0;x<digits.width();x++){
            int c=image.getPixel(digits.left+x,digits.top+y);
            binary.setPixel(x,y,Color.red(c)>200&&Color.green(c)>200&&Color.blue(c)>200?Color.BLACK:Color.WHITE);
        }
        Bitmap scaled=Bitmap.createScaledBitmap(binary,binary.getWidth()*4,binary.getHeight()*4,true);
        binary.recycle();
        recognizer.process(InputImage.fromBitmap(scaled,0)).addOnSuccessListener(result->{
            String day=result.getText().replaceAll("\\s+","");
            try {
                if(!day.matches("[0-9]{1,2}"))throw new IllegalArgumentException();
                LocalDate date=LocalDate.of(year,mon,Integer.parseInt(day));
                if(date.getDayOfWeek().getValue()%7!=weekday)throw new IllegalArgumentException();
                lastCircle=new Rect(circle);firstColumn=sunday.centerX();lastColumn=saturday.centerX();
                callback.done(date.toString(),"统计年月、选中日和星期列一致");
            }catch(Exception e){callback.done(null,"选中日期数字或星期校验失败");}
        }).addOnFailureListener(e->callback.done(null,"本地日期识别失败")).addOnCompleteListener(t->scaled.recycle());
    }
    Point sameWeekTarget(String selected,String expected){
        if(lastCircle==null)return null;
        try{
            LocalDate a=LocalDate.parse(selected),b=LocalDate.parse(expected);
            if(!a.withDayOfMonth(1).equals(b.withDayOfMonth(1))||!a.minusDays(a.getDayOfWeek().getValue()%7).equals(b.minusDays(b.getDayOfWeek().getValue()%7)))return null;
            return new Point(Math.round(firstColumn+(lastColumn-firstColumn)/6f*(b.getDayOfWeek().getValue()%7)),lastCircle.centerY());
        }catch(Exception e){return null;}
    }
    private static boolean blue(int c) {return Color.blue(c)>180&&Color.red(c)<100&&Color.green(c)>75&&Color.green(c)<195&&Color.blue(c)-Color.green(c)>40;}
    private static Rect selectedCircle(Bitmap b,int top,int bottom) {
        int step=3,w=b.getWidth()/step,h=(bottom-top)/step;
        if(w<=0||h<=0)return null;
        boolean[] seen=new boolean[w*h];ArrayDeque<Integer> queue=new ArrayDeque<>();List<Rect> found=new ArrayList<>();
        for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){
            int at=yy*w+xx;if(seen[at]||!blue(b.getPixel(xx*step,top+yy*step)))continue;
            int left=xx,right=xx,up=yy,down=yy,count=0;queue.add(at);seen[at]=true;
            while(!queue.isEmpty()){
                int p=queue.removeFirst(),x=p%w,y=p/w;count++;left=Math.min(left,x);right=Math.max(right,x);up=Math.min(up,y);down=Math.max(down,y);
                for(int n:new int[]{p-1,p+1,p-w,p+w}){
                    if(n<0||n>=seen.length||seen[n]||Math.abs(n%w-x)+Math.abs(n/w-y)!=1)continue;
                    seen[n]=true;if(blue(b.getPixel((n%w)*step,top+(n/w)*step)))queue.add(n);
                }
            }
            int width=(right-left+1)*step,height=(down-up+1)*step;
            if(width>b.getWidth()*.045&&width<b.getWidth()*.17&&height>width*.8&&height<width*1.2&&count*9>width*height*.45)
                found.add(new Rect(left*step,top+up*step,(right+1)*step,top+(down+1)*step));
        }
        return found.size()==1?found.get(0):null;
    }
}
