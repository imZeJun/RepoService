package com.demo.servicemanager.servicemanager;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class StubService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("StubService", "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("StubService", "onStartCommand");
        ServiceManager.getInstance().onStartCommand(intent, flags, startId);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ServiceManager.getInstance().onDestroy();
        Log.d("StubService", "onDestroy");
    }
}
