package com.daodian.clock;
import com.daodian.clock.core.MailClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.content.ContentValues;

/** Developer-only, app-UID mail verification; reads an ephemeral credential on stdin. */
public final class DebugMailMain {
    public static void main(String[] args)throws Exception{if(!BuildConfig.DEBUG||args.length<5)throw new IllegalArgumentException("Debug parameters unavailable");String secret=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8)).readLine();if(secret==null||secret.isEmpty())throw new IllegalArgumentException("No authorization supplied");try(MailClient client=new MailClient()){byte[] png=Files.readAllBytes(new File(args[0]).toPath());client.login(args[1],secret);secret=null;MailClient.Result r=client.send(args[1],MailClient.message(args[1],args[2],args[3],args[4],png));JSONObject receipt=new JSONObject().put("sent",r.code==250).put("smtpDataCode",r.code).put("smtpReply",r.reply).put("testOnly",true).put("submitted",false);if(args.length>=7){try(SQLiteDatabase db=SQLiteDatabase.openDatabase(args[6],null,SQLiteDatabase.OPEN_READWRITE);Cursor c=db.rawQuery("SELECT body FROM runs WHERE id=?",new String[]{args[5]})){if(c.moveToFirst()){JSONObject record=new JSONObject(c.getString(0));record.put("mailStatus","SENT").put("smtpCode",r.code).put("smtpReply",r.reply).put("mailRecipient",args[1]);ContentValues v=new ContentValues();v.put("body",record.toString());db.update("runs",v,"id=?",new String[]{args[5]});}}}System.out.println(receipt.toString());}finally{secret=null;}}
}
