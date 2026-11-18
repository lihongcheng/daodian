package com.daodian.clock.core;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves a selected calendar cell from observed day numbers, never from the device clock. */
public final class CalendarWeek {
    public static LocalDate resolve(int year, int month, int selectedColumn,
                                    Map<Integer, Set<Integer>> readings) {
        if (selectedColumn < 0 || selectedColumn > 6 || readings == null) return null;
        final YearMonth displayed;
        try { displayed = YearMonth.of(year, month); }
        catch (DateTimeException e) { return null; }

        Map<LocalDate, Set<Integer>> votes = new HashMap<>();
        int observedColumns = 0;
        for (Map.Entry<Integer, Set<Integer>> entry : readings.entrySet()) {
            int column = entry.getKey();
            Set<Integer> values = entry.getValue();
            // Conflicting OCR fragments in one cell cannot serve as independent evidence.
            if (column < 0 || column > 6 || values == null || values.size() != 1) continue;
            int day = values.iterator().next();
            if (day < 1 || day > 31) continue;
            observedColumns++;
            // The first/last week can contain days from an adjacent month or year.
            for (int offset = -1; offset <= 1; offset++) {
                YearMonth anchorMonth = displayed.plusMonths(offset);
                if (day > anchorMonth.lengthOfMonth()) continue;
                LocalDate anchor = anchorMonth.atDay(day);
                if (anchor.getDayOfWeek().getValue() % 7 != column) continue;
                LocalDate selected = anchor.plusDays(selectedColumn - column);
                if (!YearMonth.from(selected).equals(displayed)
                        || selected.getDayOfWeek().getValue() % 7 != selectedColumn) continue;
                votes.computeIfAbsent(selected, unused -> new HashSet<>()).add(column);
            }
        }

        // At least three different cells must describe the same week. Allow at most one
        // erroneous unambiguous OCR cell; multiple conflicting date sequences fail closed.
        int required = Math.max(3, observedColumns - 1);
        LocalDate winner = null;
        for (Map.Entry<LocalDate, Set<Integer>> entry : votes.entrySet()) {
            if (entry.getValue().size() < required) continue;
            if (winner != null && !winner.equals(entry.getKey())) return null;
            winner = entry.getKey();
        }
        return winner;
    }

    private CalendarWeek() {}
}
