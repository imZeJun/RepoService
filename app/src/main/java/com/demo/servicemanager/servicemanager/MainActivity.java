package com.demo.servicemanager.servicemanager;

import android.content.ComponentName;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ServiceManager.getInstance().setup(getApplicationContext());
    }

    public void startService(View view) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(ServiceManager.PLUG_SERVICE_PKG, ServiceManager.PLUG_SERVICE_NAME));
        startService(intent);
    }

    public void stopService(View view) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(ServiceManager.PLUG_SERVICE_PKG, ServiceManager.PLUG_SERVICE_NAME));
        stopService(intent);
    }


}
