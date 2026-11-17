package com.daodian.clock;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

final class DebugCrypto {
    static boolean verify(Context c)throws Exception{Prefs p=new Prefs(c);String[] keys={"mail.address","mail.enabled","mail.cipher","mail.iv","mail.connection"};Map<String,?> all=p.storage.getAll();Map<String,Object> original=new HashMap<>();for(String key:keys)if(all.containsKey(key))original.put(key,all.get(key));try{String dummy="temporary-crypto-verification";p.saveMail("test@example.invalid",false,dummy);return dummy.equals(p.mailSecret())&&!p.storage.getString("mail.cipher","").contains(dummy);}finally{SharedPreferences.Editor e=p.storage.edit();for(String key:keys){if(!original.containsKey(key))e.remove(key);else if(original.get(key) instanceof Boolean)e.putBoolean(key,(Boolean)original.get(key));else e.putString(key,String.valueOf(original.get(key)));}e.commit();}}
}
