package com.daodian.clock;

import com.daodian.clock.core.CalendarGrid;
import java.time.YearMonth;
import java.util.*;

/** Native bounds observed on the MI 9 fixture captures, kept outside the production code. */
final class CalendarGridFixtures {
    static CalendarGrid.Layout layout(int year,int month){
        YearMonth m=YearMonth.of(year,month);int count=CalendarGrid.cellCount(m,0),offset=m.atDay(1).getDayOfWeek().getValue()%7;
        List<CalendarGrid.Cell> cells=new ArrayList<>();CalendarGrid.Box[] headers=new CalendarGrid.Box[7];
        for(int col=0;col<7;col++)headers[col]=new CalendarGrid.Box(33+col*144,406,177+col*144,444);
        for(int i=0;i<count;i++)cells.add(new CalendarGrid.Cell(new CalendarGrid.Box(33+i%7*144,461+i/7*138,177+i%7*144,599+i/7*138),i>=offset&&i<offset+m.lengthOfMonth(),true,true));
        return CalendarGrid.validate(m,0,new CalendarGrid.Box(33,461,1047,461+count/7*138+1),cells,headers);
    }
    private CalendarGridFixtures(){}
}
