package com.daodian.clock;
import android.content.Context;
import android.net.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import com.daodian.clock.core.*;

final class DebugTransport {
    static JSONObject latest(Context c){JSONArray all=Records.get(c).all();for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&r.optString("status").equals("PASSED")&&!r.optString("screenshot").isEmpty())return r;}return null;}
    static String export(Context c,String id)throws Exception{JSONObject r=id==null?latest(c):Records.get(c).get(id);if(r==null)throw new IOException("No test screenshot");File source=new File(new File(c.getFilesDir(),"screenshots"),r.getString("screenshot"));File directory=c.getExternalFilesDir("verification");if(directory==null)throw new IOException();directory.mkdirs();File dest=new File(directory,source.getName());Files.copy(source.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);return dest.getAbsolutePath();}
    static String mail(Context c,String address)throws Exception{if(!address.matches("^[^\\s@<>]+@[^\\s@<>]+$"))throw new IOException();JSONObject record=latest(c);if(record==null)throw new IOException();String name="daodian-mail-"+UUID.randomUUID();LocalServerSocket server=new LocalServerSocket(name);Handler timer=new Handler(Looper.getMainLooper());Runnable expiry=()->{try{server.close();}catch(Exception ignored){}};timer.postDelayed(expiry,60000);new Thread(()->{LocalSocket socket=null;String secret=null;try{socket=server.accept();int uid=socket.getPeerCredentials().getUid();if(uid!=2000&&uid!=0)throw new SecurityException();socket.setSoTimeout(25000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));secret=reader.readLine();if(secret==null||secret.length()>100)throw new IOException();JSONObject receipt;try(MailClient client=new MailClient()){byte[] image=Files.readAllBytes(new File(new File(c.getFilesDir(),"screenshots"),record.getString("screenshot")).toPath());client.login(address,secret);secret=null;ZonedDateTime at=Instant.ofEpochMilli(record.getLong("arrivedAt")).atZone(ScheduleRules.ZONE);MailClient.Result result=client.send(address,MailClient.message(address,record.getString("kind"),at.toLocalDate().toString(),at.format(DateTimeFormatter.ofPattern("HH:mm:ss")),image));record.put("mailStatus","SENT").put("mailRecipient",address).put("smtpCode",result.code).put("smtpReply",result.reply);Records.get(c).put(record);receipt=new JSONObject().put("sent",true).put("smtpDataCode",result.code).put("smtpReply",result.reply).put("testOnly",true).put("submitted",false);}
            socket.getOutputStream().write((receipt.toString()+"\n").getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();c.sendBroadcast(new android.content.Intent("com.daodian.clock.STATE_CHANGED").setPackage(c.getPackageName()));
        }catch(Exception e){try{if(socket!=null){socket.getOutputStream().write((new JSONObject().put("sent",false).put("error",e.getClass().getSimpleName()).toString()+"\n").getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();}}catch(Exception ignored){}}finally{secret=null;timer.removeCallbacks(expiry);if(socket!=null)try{socket.close();}catch(Exception ignored){}try{server.close();}catch(Exception ignored){}}},"ephemeral-debug-mail").start();return name;}
}
