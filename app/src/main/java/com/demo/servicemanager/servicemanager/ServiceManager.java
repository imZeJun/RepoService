package com.demo.servicemanager.servicemanager;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import dalvik.system.DexClassLoader;

public class ServiceManager {

    private static final String KEY_ORIGINAL_INTENT = "original_intent";
    private static final String PLUG_SERVICE_PATH = "/Plugin/service.jar";
    public static final String PLUG_SERVICE_PKG = "com.demo.service";
    public static final String PLUG_SERVICE_NAME = "com.demo.service.PlugService";
    //保存所有存活的插件Service实例。
    private Map<ComponentName, Service> mAliveServices = new HashMap<>();
    //保存从插件中加载的Service信息。
    private Map<ComponentName, Class> mLoadedServices = new HashMap<>();
    //占坑的Service信息。
    private ComponentName mStubComponentName;

    public static ServiceManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final ServiceManager INSTANCE = new ServiceManager();
    }

    public void setup(Context context) {
        try {
            //1.通过反射获取到ActivityManagerNative类。
            Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
            Field gDefaultField = activityManagerNativeClass.getDeclaredField("gDefault");
            gDefaultField.setAccessible(true);
            Object gDefault = gDefaultField.get(activityManagerNativeClass);

            //2.获取mInstance变量。
            Class<?> singleton = Class.forName("android.util.Singleton");
            Field instanceField = singleton.getDeclaredField("mInstance");
            instanceField.setAccessible(true);

            //3.获取原始的对象。
            Object original = instanceField.get(gDefault);

            //4.动态代理，用于拦截Intent。
            Class<?> iActivityManager = Class.forName("android.app.IActivityManager");
            Object proxy = Proxy.newProxyInstance(context.getClassLoader(), new Class[]{ iActivityManager }, new IActivityManagerInvocationHandler(original));
            instanceField.set(gDefault, proxy);

            //5.读取插件当中的Service。
            loadService();

            //6.占坑的Component。
            mStubComponentName = new ComponentName(ServiceManagerApp.getAppContext().getPackageName(), StubService.class.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onStartCommand(Intent intent, int flags, int startId) {
        Intent matchIntent = intent.getParcelableExtra(KEY_ORIGINAL_INTENT);
        ComponentName componentName = matchIntent.getComponent();
        Class loadServiceInfo = mLoadedServices.get(componentName);
        if (loadServiceInfo != null) {
            Service realService = mAliveServices.get(componentName);
            if (realService == null) {
                //创建插件Service的实例。
                realService = createService(loadServiceInfo);
                if (realService != null) {
                    //调用它的onCreate()方法。
                    realService.onCreate();
                    mAliveServices.put(matchIntent.getComponent(), realService);
                }
            }
            if (realService != null) {
                realService.onStartCommand(matchIntent, flags, startId);
            }
        }
    }

    private boolean onStopService(Intent intent) {
        ComponentName component = intent.getComponent();
        if (component != null) {
            Service service = mAliveServices.get(component);
            if (service != null) {
                service.onDestroy();
            }
            mAliveServices.remove(component);
        }
        return mAliveServices.isEmpty();
    }

    public void onDestroy() {
        for (Service aliveService : mAliveServices.values()) {
            aliveService.onDestroy();
        }
    }

    private void loadService() {
        try {
            //从插件中加载Service类。
            File dexOutputDir = ServiceManagerApp.getAppContext().getDir("dex2", 0);
            String dexPath = Environment.getExternalStorageDirectory().toString() + PLUG_SERVICE_PATH;
            DexClassLoader loader = new DexClassLoader(dexPath, dexOutputDir.getAbsolutePath(), null, ServiceManagerApp.getAppContext().getClassLoader());
            try {
                Class clz = loader.loadClass(PLUG_SERVICE_NAME);
                mLoadedServices.put(new ComponentName(PLUG_SERVICE_PKG, PLUG_SERVICE_NAME), clz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Service createService(Class clz) {
        Service service = null;
        try {
            //1.实例化service。
            service = (Service) clz.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return service;
    }

    private ComponentName getStubComponentName() {
        return mStubComponentName;
    }

    public boolean isPlugService(ComponentName componentName) {
        return componentName != null && mLoadedServices.containsKey(componentName);
    }

    private class IActivityManagerInvocationHandler implements InvocationHandler {

        private Object mOriginal;


        public IActivityManagerInvocationHandler(Object original) {
            mOriginal = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            switch (methodName) {
                case "startService":
                    Intent matchIntent = null;
                    int matchIndex = 0;
                    for (Object object : args) {
                        if (object instanceof Intent) {
                            matchIntent = (Intent) object;
                            break;
                        }
                        matchIndex++;
                    }
                    if (matchIntent != null && ServiceManager.getInstance().isPlugService(matchIntent.getComponent())) {
                        Intent stubIntent = new Intent(matchIntent);
                        stubIntent.setComponent(getStubComponentName());
                        stubIntent.putExtra(KEY_ORIGINAL_INTENT, matchIntent);
                        //将插件的Service替换成占坑的Service。
                        args[matchIndex] = stubIntent;
                    }
                    break;
                case "stopService":
                    Intent stubIntent = null;
                    int stubIndex = 0;
                    for (Object object : args) {
                        if (object instanceof Intent) {
                            stubIntent = (Intent) object;
                            break;
                        }
                        stubIndex++;
                    }
                    if (stubIntent != null) {
                        boolean destroy = onStopService(stubIntent);
                        if (destroy) {
                            //如果需要销毁占坑的Service，那么就替换掉Intent进行处理。
                            Intent destroyIntent = new Intent(stubIntent);
                            destroyIntent.setComponent(getStubComponentName());
                            args[stubIndex] = destroyIntent;
                        } else {
                            //由于在onStopService中已经手动调用了onDestroy，因此这里什么也不需要做，直接返回就可以。
                            return null;
                        }
                    }
                    break;
                default:
                    break;
            }
            Log.d("ServiceManager", "call invoke, methodName=" + method.getName());
            return method.invoke(mOriginal, args);
        }
    }


}
