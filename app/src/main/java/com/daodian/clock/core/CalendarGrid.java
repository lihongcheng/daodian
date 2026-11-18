package com.daodian.clock.core;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Binds a selected native calendar cell to the displayed month without OCR or a device date. */
public final class CalendarGrid {
    public static final class Box {
        public final int left,top,right,bottom;
        public Box(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        public int width(){return right-left;} public int height(){return bottom-top;}
        public int centerX(){return (left+right)/2;} public int centerY(){return (top+bottom)/2;}
        public boolean positive(){return width()>0&&height()>0;}
        public boolean contains(Box b){return b.left>=left&&b.top>=top&&b.right<=right&&b.bottom<=bottom;}
        @Override public String toString(){return left+","+top+","+right+","+bottom;}
    }
    public static final class Cell {
        public final Box bounds; public final boolean clickable,enabled,visible;
        public Cell(Box bounds,boolean clickable,boolean enabled,boolean visible){this.bounds=bounds;this.clickable=clickable;this.enabled=enabled;this.visible=visible;}
    }
    public static final class Layout {
        public final YearMonth month; public final int weekStart,offset; public final Box bounds;
        public final List<Cell> cells; public final String signature;
        private Layout(YearMonth month,int weekStart,Box bounds,List<Cell> cells,Box[] headers){
            this.month=month;this.weekStart=weekStart;this.bounds=bounds;
            offset=(month.atDay(1).getDayOfWeek().getValue()%7-weekStart+7)%7;
            this.cells=Collections.unmodifiableList(new ArrayList<>(cells));
            StringBuilder s=new StringBuilder(month+"|"+weekStart+"|"+bounds);
            for(Box header:headers)s.append('|').append(header);
            for(Cell cell:cells)s.append('|').append(cell.bounds).append(':').append(cell.clickable);
            signature=s.toString();
        }
        public Cell cellFor(LocalDate date){return month.equals(YearMonth.from(date))?cells.get(offset+date.getDayOfMonth()-1):null;}
        public LocalDate dateAt(Box selected){
            if(selected==null||!selected.positive())return null;
            LocalDate found=null;
            for(int i=0;i<cells.size();i++){
                Cell c=cells.get(i);int day=i-offset+1;
                if(day<1||day>month.lengthOfMonth()||!c.bounds.contains(selected))continue;
                if(Math.abs(selected.centerX()-c.bounds.centerX())>c.bounds.width()*.2||Math.abs(selected.centerY()-c.bounds.centerY())>c.bounds.height()*.2)return null;
                if(found!=null)return null;found=month.atDay(day);
            }
            return found;
        }
        public boolean acceptsTarget(int x,int y){
            for(int i=offset;i<offset+month.lengthOfMonth();i++){Box b=cells.get(i).bounds;if(x==b.centerX()&&y==b.centerY())return true;}
            return false;
        }
    }
    public static int cellCount(YearMonth month,int weekStart){
        if(month==null||weekStart<0||weekStart>6)return 0;
        int offset=(month.atDay(1).getDayOfWeek().getValue()%7-weekStart+7)%7;
        return ((offset+month.lengthOfMonth()+6)/7)*7;
    }
    public static Layout validate(YearMonth month,int weekStart,Box bounds,List<Cell> cells,Box[] headers){
        int count=cellCount(month,weekStart);
        if(count==0||bounds==null||!bounds.positive()||cells==null||cells.size()!=count||headers==null||headers.length!=7)return null;
        int offset=(month.atDay(1).getDayOfWeek().getValue()%7-weekStart+7)%7;
        Cell first=cells.get(0);if(first==null||first.bounds==null||!first.bounds.positive())return null;
        int width=first.bounds.width(),height=first.bounds.height(),tx=Math.max(2,Math.round(width*.025f)),ty=Math.max(2,Math.round(height*.025f));
        if(bounds.width()<width*7-tx||bounds.width()>width*7+tx*4||bounds.height()<height*(count/7)-ty||bounds.height()>height*(count/7)+ty*4)return null;
        for(int col=0;col<7;col++){
            Box header=headers[col];Cell c=cells.get(col);
            if(header==null||!header.positive()||c==null||c.bounds==null||header.bottom>bounds.top+ty||Math.abs(header.centerX()-c.bounds.centerX())>width*.1)return null;
            if(col>0&&header.centerX()<=headers[col-1].centerX())return null;
        }
        for(int i=0;i<count;i++){
            Cell c=cells.get(i);if(c==null||c.bounds==null||!c.bounds.positive()||!bounds.contains(c.bounds))return null;
            Box b=c.bounds;int row=i/7,col=i%7;
            if(Math.abs(b.width()-width)>tx||Math.abs(b.height()-height)>ty||Math.abs(b.left-(first.bounds.left+col*width))>tx||Math.abs(b.top-(first.bounds.top+row*height))>ty)return null;
            boolean dateCell=i>=offset&&i<offset+month.lengthOfMonth();
            if(c.clickable!=dateCell||dateCell&&(!c.visible||!c.enabled))return null;
        }
        return new Layout(month,weekStart,bounds,cells,headers);
    }
    private CalendarGrid(){}
}
