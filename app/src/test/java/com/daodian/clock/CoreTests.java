package com.daodian.clock;
import com.daodian.clock.core.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class CoreTests {
    long at(String value){return ZonedDateTime.parse(value+"+08:00[Asia/Shanghai]").toInstant().toEpochMilli();}
    static class Choices implements ScheduleRules.ChoiceStore{Map<String,Long> data=new HashMap<>();public Long get(String k){return data.get(k);}public void put(String k,long v){data.put(k,v);}}
    @Test public void fixedIndependentAndLate(){Plan p=new Plan("clockout");p.enabled=true;p.random=false;p.begin=LocalTime.of(19,0);Choices c=new Choices();ScheduleRules.Next n=ScheduleRules.next(p,at("2026-11-16T18:00:00"),c,Set.of(),bound->0);assertEquals(at("2026-11-16T19:00:00"),n.at);assertFalse(n.late);n=ScheduleRules.next(p,at("2026-11-16T20:00:00"),c,Set.of(),bound->0);assertTrue(n.late);assertEquals("2026-11-16",n.date);}
    @Test public void randomChoiceSurvivesReentry(){Plan p=new Plan("clockin");Choices c=new Choices();long now=at("2026-11-16T08:00:00");long first=ScheduleRules.next(p,now,c,Set.of(),bound->600000).at;long again=ScheduleRules.next(p,now,c,Set.of(),bound->0).at;assertEquals(first,again);assertEquals(at("2026-11-16T09:10:00"),first);}
    @Test public void weekdaysAndCrossDay(){Plan p=new Plan("clockout");p.begin=LocalTime.of(23,50);p.end=LocalTime.of(0,20);p.overnight=true;Choices c=new Choices();ScheduleRules.Next n=ScheduleRules.next(p,at("2026-11-16T20:00:00"),c,Set.of(),bound->900000);assertEquals("2026-11-17",n.date);assertEquals(at("2026-11-17T00:05:00"),n.at);}
    @Test public void localDedupContainsDateAndType(){Plan p=new Plan("clockout");Choices c=new Choices();ScheduleRules.Next n=ScheduleRules.next(p,at("2026-11-16T18:00:00"),c,Set.of("clockin:2026-11-16"),bound->0);assertEquals("2026-11-16",n.date);n=ScheduleRules.next(p,at("2026-11-16T18:00:00"),c,Set.of("clockout:2026-11-16"),bound->0);assertEquals("2026-11-17",n.date);}
    @Test public void interfaceNavigationRejectsSubmitLabels(){for(String label:List.of("拍照打卡","提交","确认提交","更新打卡","拍照","确认","打卡记录")){assertFalse(SafetyRules.canClick(label,true,false));}assertFalse(SafetyRules.canClick("打卡",false,false));assertFalse(SafetyRules.canClick("打卡",true,true));assertTrue(SafetyRules.canClick("工作台",false,false));assertTrue(SafetyRules.canClick("打卡",true,false));}
    @Test public void smtpReplyParsing(){MailClient.Result r=MailClient.parseReply("250-smtp.qq.com\r\n250 OK: queued as.");assertEquals(250,r.code);assertEquals("OK: queued as.",r.reply);}
    @Test public void mimeIsTestOnlyInlineImage(){byte[] png={(byte)137,80,78,71,13,10,26,10};String mime=new String(MailClient.message("test@example.com","clockout","2026-11-16","19:10:00",png),StandardCharsets.UTF_8);assertTrue(mime.contains("Content-Disposition: inline"));assertFalse(mime.contains("Content-Disposition: attachment"));assertTrue(mime.contains("Content-ID: <interface-"));assertTrue(mime.contains(Base64.getEncoder().encodeToString(png)));}
    @Test(expected=IllegalArgumentException.class)public void invalidRangeRejected(){Plan p=new Plan("clockin");p.end=LocalTime.of(8,0);p.validate();}
    @Test public void utf8SubjectFoldsWithoutBreakingCharacters(){String original="下班界面测试报告 2026-11-16 19:00:00 未提交打卡";String encoded=MailClient.encodedSubject(original);StringBuilder decoded=new StringBuilder();for(String part:encoded.split("\\r?\\n ")){assertTrue(part.length()<=75);decoded.append(new String(Base64.getDecoder().decode(part.substring(10,part.length()-2)),StandardCharsets.UTF_8));}assertEquals(original,decoded.toString());}
}
