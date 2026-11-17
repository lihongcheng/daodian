package com.daodian.clock.core;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.IntUnaryOperator;

public final class ScheduleRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public interface ChoiceStore { Long get(String key); void put(String key,long timestamp); }
    public static final class Next { public final long at; public final String date, key; public final boolean late; public Next(long at,String date,String key,boolean late){this.at=at;this.date=date;this.key=key;this.late=late;} }
    public static Next next(Plan p, long now, ChoiceStore choices, Set<String> completed, IntUnaryOperator random) {
        p.validate();
        ZonedDateTime current=Instant.ofEpochMilli(now).atZone(ZONE);
        List<Next> candidates=new ArrayList<>();
        for(int offset=-1;offset<=14;offset++) {
            LocalDate base=current.toLocalDate().plusDays(offset);
            if(!p.days.contains(base.getDayOfWeek().getValue()%7)) continue;
            ZonedDateTime start=base.atTime(p.begin).atZone(ZONE);
            ZonedDateTime end=base.plusDays(p.overnight?1:0).atTime(p.end).atZone(ZONE);
            String key=p.kind+":"+base+":"+p.revision;
            Long stored=choices.get(key);
            long selected;
            if(stored!=null) selected=stored;
            else {long range=p.random?Duration.between(start,end).toMillis():0; selected=start.toInstant().toEpochMilli()+(range>0?random.applyAsInt((int)Math.min(Integer.MAX_VALUE,range+1)):0);choices.put(key,selected);}
            LocalDate executionDate=Instant.ofEpochMilli(selected).atZone(ZONE).toLocalDate();
            if(executionDate.isBefore(current.toLocalDate()) || completed.contains(p.kind+":"+executionDate)) continue;
            if(selected<now && !executionDate.equals(current.toLocalDate())) continue;
            candidates.add(new Next(selected,executionDate.toString(),key,selected<now));
        }
        return candidates.stream().min(Comparator.comparingLong(n->Math.max(now,n.at))).orElse(null);
    }
    public static Next next(Plan p,long now,ChoiceStore choices,Set<String> completed){SecureRandom random=new SecureRandom();return next(p,now,choices,completed,random::nextInt);}
}
