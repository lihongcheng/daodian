package com.daodian.clock;
import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.widget.TextView;

public final class WakeActivity extends Activity {
    protected void onCreate(Bundle b){
        super.onCreate(b);
        KeyguardManager guard=getSystemService(KeyguardManager.class);
        if(guard.isDeviceLocked()&&guard.isDeviceSecure()){finish();return;}
        setShowWhenLocked(true);setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView text=new TextView(this);text.setText("到点正在准备执行…");text.setTextColor(Color.WHITE);text.setBackgroundColor(Color.BLACK);text.setGravity(Gravity.CENTER);setContentView(text);
        // Keep this window until it has actually resumed and drawn. Launching WeCom from
        // TaskService immediately used to cover it before Android could turn the screen on.
        getWindow().getDecorView().postDelayed(this::finish,12000);
    }
    protected void onResume(){super.onResume();getWindow().getDecorView().postDelayed(()->{
        KeyguardManager guard=getSystemService(KeyguardManager.class);
        if(!guard.isDeviceSecure())guard.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback(){
            public void onDismissSucceeded(){getWindow().getDecorView().postDelayed(WakeActivity.this::finish,1500);}
        });
    },350);}
}
