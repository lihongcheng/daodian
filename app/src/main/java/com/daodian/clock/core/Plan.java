package com.daodian.clock.core;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public final class Plan {
    public String kind;
    public boolean enabled, random, overnight, remind = true;
    public LocalTime begin, end;
    public Set<Integer> days = new HashSet<>();
    public int revision;
    public Plan(String kind) { this.kind = kind; begin = LocalTime.of(kind.equals("clockin") ? 9 : 19, 0); end = begin.plusMinutes(30); random = true; for (int d=1;d<=5;d++) days.add(d); }
    public void validate() {
        if (!kind.equals("clockin") && !kind.equals("clockout")) throw new IllegalArgumentException("请选择上班或下班");
        if (days.isEmpty() || days.stream().anyMatch(d -> d < 0 || d > 6)) throw new IllegalArgumentException("请至少选择一个有效星期");
        if (begin == null || end == null) throw new IllegalArgumentException("时间不完整");
        if (random && !overnight && !end.isAfter(begin)) throw new IllegalArgumentException("结束时间须晚于开始时间");
        if (random && overnight && !end.isBefore(begin)) throw new IllegalArgumentException("跨日结束时间须早于开始时间");
    }
}
