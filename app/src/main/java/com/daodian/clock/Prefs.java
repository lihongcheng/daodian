package com.daodian.clock;

import android.content.*;
import android.security.keystore.*;
import com.daodian.clock.core.*;
import org.json.*;
import java.security.KeyStore;
import java.time.LocalTime;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public final class Prefs {
    final SharedPreferences storage;
    public Prefs(Context context){storage=context.getSharedPreferences("daodian",Context.MODE_PRIVATE);migrate();}
    private void migrate(){synchronized(Prefs.class){if(storage.getInt("schema",1)>=2)return;
        SharedPreferences.Editor e=storage.edit().putInt("schema",2).putBoolean("master",false).putString("execution_mode","REAL");
        for(String kind:new String[]{"clockin","clockout"})try{JSONObject p=new JSONObject(storage.getString("plan."+kind,"{}"));p.put("enabled",false);e.putString("plan."+kind,p.toString());}catch(Exception ignored){}
        if(!e.commit())throw new IllegalStateException("配置迁移失败");
    }}
    public String executionMode(){return storage.getString("execution_mode","REAL");}
    public void executionMode(String mode){if(!mode.equals("REAL")&&!mode.equals("TEST"))throw new IllegalArgumentException();storage.edit().putString("execution_mode",mode).putBoolean("master",false).commit();}
    public boolean flexibleRule(){return storage.getBoolean("flexible_rule",false);}
    public void flexibleRule(boolean value){storage.edit().putBoolean("flexible_rule",value).commit();}
    public synchronized Plan plan(String kind){Plan p=new Plan(kind);try{JSONObject j=new JSONObject(storage.getString("plan."+kind,"{}"));p.enabled=j.optBoolean("enabled",false);p.random=j.optString("mode","random").equals("random");p.begin=LocalTime.parse(j.optString("begin",p.begin.toString()));p.end=LocalTime.parse(j.optString("end",p.end.toString()));p.overnight=j.optBoolean("overnight",false);p.remind=j.optBoolean("remind",true);p.revision=j.optInt("revision",0);if(j.has("days")){p.days.clear();JSONArray a=j.getJSONArray("days");for(int i=0;i<a.length();i++)p.days.add(a.getInt(i));}}catch(Exception ignored){}return p;}
    public synchronized JSONObject planJson(String kind){Plan p=plan(kind);JSONObject j=new JSONObject();try{j.put("kind",kind).put("enabled",p.enabled).put("mode",p.random?"random":"fixed").put("begin",p.begin.toString()).put("end",p.end.toString()).put("overnight",p.overnight).put("remind",p.remind).put("revision",p.revision);List<Integer> sorted=new ArrayList<>(p.days);Collections.sort(sorted);j.put("days",new JSONArray(sorted));}catch(Exception ignored){}return j;}
    public synchronized void savePlan(String kind,JSONObject j)throws Exception{Plan p=new Plan(kind);p.enabled=j.optBoolean("enabled");p.random=j.optString("mode").equals("random");p.begin=LocalTime.parse(j.getString("begin"));p.end=LocalTime.parse(j.optString("end",j.getString("begin")));p.overnight=p.random&&j.optBoolean("overnight");p.remind=j.optBoolean("remind",true);p.days.clear();JSONArray days=j.getJSONArray("days");for(int i=0;i<days.length();i++)p.days.add(days.getInt(i));p.validate();j.put("revision",plan(kind).revision+1);storage.edit().putString("plan."+kind,j.toString()).commit();}
    public boolean master(){return storage.getBoolean("master",false);}
    public void master(boolean enabled){storage.edit().putBoolean("master",enabled).commit();}
    public boolean bootConfirmed(){return storage.getBoolean("boot_confirmed",false);}
    public void bootConfirmed(boolean value){storage.edit().putBoolean("boot_confirmed",value).commit();}
    public int retention(){return storage.getInt("retention",30);}
    public void retention(int days){storage.edit().putInt("retention",days).commit();}
    public boolean reminders(){return storage.getBoolean("reminders",true);}
    public void reminders(boolean value){storage.edit().putBoolean("reminders",value).commit();}
    public String theme(){return storage.getString("theme","system");}
    public void theme(String value){storage.edit().putString("theme",value).commit();}
    public ScheduleRules.ChoiceStore choices(){return new ScheduleRules.ChoiceStore(){public Long get(String key){return storage.contains("choice."+key)?storage.getLong("choice."+key,0):null;}public void put(String key,long value){storage.edit().putLong("choice."+key,value).commit();}};}
    public Set<String> completed(){return new HashSet<>(storage.getStringSet("scheduled_attempted.v2."+executionMode(),Collections.emptySet()));}
    public synchronized void markScheduledComplete(String kind,String date){Set<String> set=completed();set.add(kind+":"+date);storage.edit().putStringSet("scheduled_attempted.v2."+executionMode(),set).commit();}
    public JSONObject mail(){JSONObject j=new JSONObject();try{j.put("enabled",storage.getBoolean("mail.enabled",false)).put("address",storage.getString("mail.address","")).put("configured",storage.contains("mail.cipher")).put("connection",storage.getString("mail.connection","")).put("server","smtp.qq.com").put("port",465);}catch(Exception ignored){}return j;}
    public synchronized void saveMail(String address,boolean enabled,String secret)throws Exception{if(!address.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("邮箱地址无效");SharedPreferences.Editor edit=storage.edit().putString("mail.address",address).putBoolean("mail.enabled",enabled);if(secret!=null&&!secret.isEmpty()){KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="daodian.mail.secret";if(!store.containsAlias(alias)){KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,(javax.crypto.SecretKey)store.getKey(alias,null));byte[] encrypted=cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));edit.putString("mail.cipher",Base64.getEncoder().encodeToString(encrypted)).putString("mail.iv",Base64.getEncoder().encodeToString(cipher.getIV()));}edit.commit();}
    public String mailSecret()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,store.getKey("daodian.mail.secret",null),new GCMParameterSpec(128,Base64.getDecoder().decode(storage.getString("mail.iv",""))));return new String(cipher.doFinal(Base64.getDecoder().decode(storage.getString("mail.cipher",""))),java.nio.charset.StandardCharsets.UTF_8);}
    public void mailConnection(String value){storage.edit().putString("mail.connection",value).commit();}
}
