package com.daodian.clock.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.net.ssl.*;

public final class MailClient implements AutoCloseable {
    private SSLSocket socket;
    private BufferedReader input;
    private BufferedWriter output;
    private String stage="connecting";
    public static final class Result {public final int code;public final String reply;public Result(int c,String r){code=c;reply=r;}}
    public static Result parseReply(String response){String[] lines=response.split("\\r?\\n");if(lines.length==0||!lines[0].matches("^[0-9]{3}[- ].*"))throw new IllegalArgumentException("SMTP响应无效");int code=Integer.parseInt(lines[0].substring(0,3));for(String line:lines)if(line.length()<3||!line.substring(0,3).equals(String.valueOf(code)))throw new IllegalArgumentException("SMTP多行状态不一致");return new Result(code,lines[lines.length-1].length()>4?lines[lines.length-1].substring(4):"");}
    private Result read()throws IOException{String first=input.readLine();if(first==null)throw new EOFException("SMTP连接关闭");StringBuilder text=new StringBuilder(first);String current=first;int count=0;while(current.length()>3&&current.charAt(3)=='-'){if(++count>100)throw new IOException("SMTP响应过长");current=input.readLine();if(current==null)throw new EOFException();text.append("\n").append(current);}try{return parseReply(text.toString());}catch(Exception e){throw new IOException("SMTP响应不可识别");}}
    private Result command(String command)throws IOException{output.write(command+"\r\n");output.flush();return read();}
    private static void require(Result result,int expected)throws IOException{if(result.code!=expected)throw new IOException("SMTP返回 "+result.code);}
    public void login(String address,String authorization)throws Exception{if(!address.matches("^[^\\s@<>\\r\\n]+@[^\\s@<>\\r\\n]+$"))throw new IllegalArgumentException("邮箱地址无效");socket=(SSLSocket)SSLSocketFactory.getDefault().createSocket();socket.connect(new java.net.InetSocketAddress("smtp.qq.com",465),25000);socket.setSoTimeout(25000);SSLParameters parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(parameters);socket.startHandshake();input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));output=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.US_ASCII));require(read(),220);require(command("EHLO daodian.local"),250);stage="authenticating";require(command("AUTH LOGIN"),334);require(command(Base64.getEncoder().encodeToString(address.getBytes(StandardCharsets.UTF_8))),334);require(command(Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8))),235);stage="ready";}
    public Result send(String address,byte[] message)throws Exception{require(command("MAIL FROM:<"+address+">"),250);require(command("RCPT TO:<"+address+">"),250);require(command("DATA"),354);stage="data";String content=new String(message,StandardCharsets.UTF_8);for(String line:content.split("\\r?\\n",-1)){if(line.startsWith("."))output.write(".");output.write(line+"\r\n");}output.write(".\r\n");output.flush();Result reply=read();stage="completed";require(reply,250);return reply;}
    public boolean resultUncertain(){return stage.equals("data");}
    public void close(){if(socket!=null){try{if(output!=null)command("QUIT");}catch(Exception ignored){}try{socket.close();}catch(Exception ignored){}}}
    private static String b64(byte[] data){return Base64.getMimeEncoder(76,new byte[]{13,10}).encodeToString(data);}
    private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    public static String encodedSubject(String text){StringBuilder out=new StringBuilder(),chunk=new StringBuilder();for(int offset=0;offset<text.length();){int point=text.codePointAt(offset);String character=new String(Character.toChars(point));if((chunk.toString()+character).getBytes(StandardCharsets.UTF_8).length>42){if(out.length()>0)out.append("\r\n ");out.append("=?UTF-8?B?").append(Base64.getEncoder().encodeToString(chunk.toString().getBytes(StandardCharsets.UTF_8))).append("?=");chunk.setLength(0);}chunk.append(character);offset+=Character.charCount(point);}if(chunk.length()>0){if(out.length()>0)out.append("\r\n ");out.append("=?UTF-8?B?").append(Base64.getEncoder().encodeToString(chunk.toString().getBytes(StandardCharsets.UTF_8))).append("?=");}return out.toString();}
    public static byte[] message(String address,String kind,String date,String arrived,byte[] png){
        String label=SafetyRules.kindLabel(kind);
        return compose(address,label+"界面测试报告 · "+date+" "+arrived+"（未提交打卡）",
            "测试类型："+label+"\n界面到达时间："+date+" "+arrived+"（北京时间）\n未拍照、未提交或修改打卡记录。",png,"interface-");
    }
    public static byte[] attendanceMessage(String address,AttendanceRules.Evidence evidence,byte[] png){
        if(evidence==null)throw new IllegalArgumentException("必须提供企微记录证据");
        String label=SafetyRules.kindLabel(evidence.kind);
        return compose(address,label+"打卡记录 · "+evidence.date+" "+evidence.time+" · "+evidence.status,
            "企微真实记录已核验\n类型："+label+"\n记录日期："+evidence.date+"\n记录时间："+evidence.time+"（北京时间）\n实际状态："+evidence.status+"\n证据来源：企业微信当日统计。截图显示同一天、同类型的记录。",png,"attendance-");
    }
    private static byte[] compose(String address,String subject,String plain,byte[] png,String prefix){
        if(!address.matches("^[^\\s@<>\\r\\n]+@[^\\s@<>\\r\\n]+$"))throw new IllegalArgumentException("邮箱地址无效");
        if(png.length<8||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71)throw new IllegalArgumentException("需要真实PNG截图");
        String related="r-"+UUID.randomUUID(),alternative="a-"+UUID.randomUUID(),cid=prefix+UUID.randomUUID()+"@daodian.local";
        String html="<!doctype html><html><body><h2>"+escape(subject)+"</h2><p>"+escape(plain).replace("\n","<br>")+"</p><img src=\"cid:"+cid+"\" style=\"max-width:540px;width:100%;height:auto\" alt=\"企微记录截图\"></body></html>";
        String body="From: "+address+"\r\nTo: "+address+"\r\nSubject: "+encodedSubject(subject)+"\r\nDate: "+DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ScheduleRules.ZONE))+"\r\nMessage-ID: <"+UUID.randomUUID()+"@daodian.local>\r\nMIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\""+related+"\"\r\n\r\n--"+related+"\r\nContent-Type: multipart/alternative; boundary=\""+alternative+"\"\r\n\r\n--"+alternative+"\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"+b64(plain.getBytes(StandardCharsets.UTF_8))+"\r\n--"+alternative+"\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"+b64(html.getBytes(StandardCharsets.UTF_8))+"\r\n--"+alternative+"--\r\n--"+related+"\r\nContent-Type: image/png\r\nContent-ID: <"+cid+">\r\nContent-Disposition: inline; filename=\""+prefix+"evidence.png\"\r\nContent-Transfer-Encoding: base64\r\n\r\n"+b64(png)+"\r\n--"+related+"--\r\n";
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
