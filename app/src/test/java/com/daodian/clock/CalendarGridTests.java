package com.daodian.clock;

import com.daodian.clock.core.CalendarGrid;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CalendarGridTests {
    private CalendarGrid.Box[] headers(){CalendarGrid.Box[] out=new CalendarGrid.Box[7];for(int i=0;i<7;i++)out[i]=new CalendarGrid.Box(33+i*144,406,177+i*144,444);return out;}
    private List<CalendarGrid.Cell> cells(YearMonth month,int start){
        DayOfWeek first=DayOfWeek.of(start==0?7:start),last=DayOfWeek.of(start==1?7:start==0?6:start-1);
        LocalDate from=month.atDay(1).with(TemporalAdjusters.previousOrSame(first));
        LocalDate to=month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(last));
        int count=(int)ChronoUnit.DAYS.between(from,to)+1;
        List<CalendarGrid.Cell> out=new ArrayList<>();
        for(int i=0;i<count;i++)out.add(new CalendarGrid.Cell(new CalendarGrid.Box(33+i%7*144,461+i/7*138,177+i%7*144,599+i/7*138),YearMonth.from(from.plusDays(i)).equals(month),true,true));
        return out;
    }
    private CalendarGrid.Layout layout(YearMonth month,int start,List<CalendarGrid.Cell> cells){return CalendarGrid.validate(month,start,new CalendarGrid.Box(33,461,1047,461+cells.size()/7*138+1),cells,headers());}
    private CalendarGrid.Layout layout(int year,int month){YearMonth m=YearMonth.of(year,month);return layout(m,0,cells(m,0));}
    @Test public void allGregorianDaysAndWeekStarts(){
        for(int year=2000;year<2400;year++)for(int month=1;month<=12;month++)for(int start=0;start<7;start++){
            YearMonth m=YearMonth.of(year,month);List<CalendarGrid.Cell> truth=cells(m,start);CalendarGrid.Layout grid=layout(m,start,truth);assertNotNull(grid);
            assertEquals(truth.size(),CalendarGrid.cellCount(m,start));
            LocalDate firstWeek=m.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.of(start==0?7:start)));
            for(int day=1;day<=m.lengthOfMonth();day++){
                LocalDate date=m.atDay(day);int index=(int)ChronoUnit.DAYS.between(firstWeek,date);CalendarGrid.Box b=truth.get(index).bounds;
                assertEquals(b.toString(),grid.cellFor(date).bounds.toString());
                assertEquals(date,grid.dateAt(new CalendarGrid.Box(b.centerX()-50,b.centerY()-50,b.centerX()+50,b.centerY()+50)));
            }
        }
    }
    @Test public void twoDateLastWeekIsUnambiguous(){CalendarGrid.Layout g=layout(2026,11);for(int day:new int[]{29,30}){CalendarGrid.Box b=g.cellFor(LocalDate.of(2026,11,day)).bounds;assertEquals(LocalDate.of(2026,11,day),g.dateAt(new CalendarGrid.Box(b.left+20,b.top+20,b.right-20,b.bottom-20)));}}
    @Test public void formerlyConflictingMarchWeeks(){CalendarGrid.Layout g=layout(2031,3);for(int day:new int[]{1,29}){CalendarGrid.Box b=g.cellFor(LocalDate.of(2031,3,day)).bounds;assertEquals(LocalDate.of(2031,3,day),g.dateAt(new CalendarGrid.Box(b.left+20,b.top+20,b.right-20,b.bottom-20)));}}
    @Test public void fourFiveSixRows(){assertEquals(28,layout(2026,2).cells.size());assertEquals(35,layout(2026,11).cells.size());assertEquals(42,layout(2026,8).cells.size());}
    @Test public void collapsedWeekCannotSubstituteForFullMonth(){YearMonth m=YearMonth.of(2026,11);assertNull(layout(m,0,cells(m,0).subList(0,7)));}
    @Test public void selectableAdjacentMonthCellRejected(){YearMonth m=YearMonth.of(2026,10);List<CalendarGrid.Cell> c=cells(m,0);CalendarGrid.Cell old=c.get(0);c.set(0,new CalendarGrid.Cell(old.bounds,true,true,true));assertNull(layout(m,0,c));}
    @Test public void invisibleOrDisabledDateCannotProveLayout(){YearMonth m=YearMonth.of(2026,11);List<CalendarGrid.Cell> c=cells(m,0);CalendarGrid.Cell old=c.get(10);c.set(10,new CalendarGrid.Cell(old.bounds,true,true,false));assertNull(layout(m,0,c));c.set(10,new CalendarGrid.Cell(old.bounds,true,false,true));assertNull(layout(m,0,c));}
    @Test public void swappedOrMovedCellsRejected(){YearMonth m=YearMonth.of(2026,11);List<CalendarGrid.Cell> c=cells(m,0);Collections.swap(c,9,10);assertNull(layout(m,0,c));c=cells(m,0);CalendarGrid.Cell old=c.get(9);CalendarGrid.Box b=old.bounds;c.set(9,new CalendarGrid.Cell(new CalendarGrid.Box(b.left+20,b.top,b.right+20,b.bottom),true,true,true));assertNull(layout(m,0,c));}
    @Test public void blankAndBoundaryPositionsCannotCreateDates(){CalendarGrid.Layout g=layout(2026,10);CalendarGrid.Box blank=g.cells.get(0).bounds;assertNull(g.dateAt(new CalendarGrid.Box(blank.left+20,blank.top+20,blank.right-20,blank.bottom-20)));CalendarGrid.Box a=g.cells.get(5).bounds;assertNull(g.dateAt(new CalendarGrid.Box(a.right-20,a.top+20,a.right+20,a.bottom-20)));}
    @Test public void targetMustBelongToDisplayedMonthAndBeCellCenter(){CalendarGrid.Layout g=layout(2026,11);assertNull(g.cellFor(LocalDate.of(2026,10,31)));CalendarGrid.Box b=g.cellFor(LocalDate.of(2026,11,18)).bounds;assertTrue(g.acceptsTarget(b.centerX(),b.centerY()));assertFalse(g.acceptsTarget(b.centerX()+1,b.centerY()));}
    @Test public void headerMisalignmentAndInvalidMetadataRejected(){YearMonth m=YearMonth.of(2026,11);CalendarGrid.Box[] h=headers();h[3]=new CalendarGrid.Box(0,406,144,444);assertNull(CalendarGrid.validate(m,0,new CalendarGrid.Box(33,461,1047,1152),cells(m,0),h));assertEquals(0,CalendarGrid.cellCount(m,7));assertEquals(0,CalendarGrid.cellCount(null,0));}
}
