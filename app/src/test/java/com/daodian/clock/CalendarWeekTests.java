package com.daodian.clock;

import com.daodian.clock.core.CalendarWeek;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CalendarWeekTests {
    private Map<Integer,Set<Integer>> cells(Integer... days){Map<Integer,Set<Integer>> out=new HashMap<>();for(int i=0;i<days.length;i++)if(days[i]!=null)out.put(i,new HashSet<>(Collections.singleton(days[i])));return out;}
    @Test public void selectedEighteenMatchesWeek(){assertEquals(LocalDate.of(2026,11,18),CalendarWeek.resolve(2026,11,3,cells(15,16,17,18,19,20,21)));}
    @Test public void droppedEightIsRecoveredFromNeighbours(){assertEquals(LocalDate.of(2026,11,18),CalendarWeek.resolve(2026,11,3,cells(15,16,17,1,19,20,21)));}
    @Test public void unreadableSelectedDigitIsRecovered(){assertEquals(LocalDate.of(2026,11,18),CalendarWeek.resolve(2026,11,3,cells(15,16,17,null,19,20,21)));}
    @Test public void duplicateFragmentsAreNotExtraVotes(){Map<Integer,Set<Integer>> m=cells(15,null,17);m.put(3,new HashSet<>(Arrays.asList(1,18)));assertNull(CalendarWeek.resolve(2026,11,3,m));}
    @Test public void conflictingSelectedFragmentsUseOtherCells(){Map<Integer,Set<Integer>> m=cells(15,16,17,18,19,20,21);m.put(3,new HashSet<>(Arrays.asList(1,18)));assertEquals(LocalDate.of(2026,11,18),CalendarWeek.resolve(2026,11,3,m));}
    @Test public void twoCellsAreInsufficient(){assertNull(CalendarWeek.resolve(2026,11,3,cells(15,16)));}
    @Test public void twoConflictingWeeksAreRejected(){assertNull(CalendarWeek.resolve(2026,11,3,cells(1,2,3,18,19,20,null)));}
    @Test public void multipleWrongCellsAreRejected(){assertNull(CalendarWeek.resolve(2026,11,3,cells(1,2,17,18,19,20,21)));}
    @Test public void wrongMonthWeekdayAlignmentIsRejected(){assertNull(CalendarWeek.resolve(2026,12,1,cells(15,16,17,18,19,20,21)));}
    @Test public void monthBoundaryUsesAdjacentMonth(){assertEquals(LocalDate.of(2026,12,2),CalendarWeek.resolve(2026,12,3,cells(29,30,1,2,3,4,5)));}
    @Test public void yearBoundaryLastDay(){assertEquals(LocalDate.of(2026,12,31),CalendarWeek.resolve(2026,12,4,cells(27,28,29,30,31,1,2)));}
    @Test public void yearBoundaryFirstDay(){assertEquals(LocalDate.of(2027,1,1),CalendarWeek.resolve(2027,1,5,cells(27,28,29,30,31,1,2)));}
    @Test public void invalidMetadataCannotProduceADate(){assertNull(CalendarWeek.resolve(2026,13,3,cells(15,16,17,18)));assertNull(CalendarWeek.resolve(2026,11,7,cells(15,16,17,18)));}
    @Test public void noObservationCannotUseCurrentClock(){assertNull(CalendarWeek.resolve(2026,11,3,Collections.emptyMap()));}
}
