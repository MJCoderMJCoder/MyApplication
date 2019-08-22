package com.lzf.myapplication;

import android.app.Application;
import android.content.Context;

import com.lzf.myapplication.dmp.ContentItem;
import com.lzf.myapplication.dmp.DeviceItem;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.support.model.DIDLContent;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by MJCoder on 2017-09-13.
 */

public class MyApplication extends Application {

    public static DeviceItem deviceItem;

    public DIDLContent didl;

    public static DeviceItem dmrDeviceItem;

    public static boolean isLocalDmr = true;

    public ArrayList<ContentItem> listMusic;

    public ArrayList<ContentItem> listPhoto;

    public ArrayList<ContentItem> listPlayMusic = new ArrayList();

    public ArrayList<ContentItem> listVideo;

    public ArrayList<ContentItem> listcontent;

    public HashMap<String, ArrayList<ContentItem>> map;

    // public MediaUtils mediaUtils;

    public int position;

    public static AndroidUpnpService upnpService;

    public static Context mContext;

    private static InetAddress inetAddress;

    private static String hostAddress;

    private static String hostName;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        initImageLoader(getApplicationContext());
    }

    public static Context getContext() {
        return mContext;
    }

    public static void setLocalIpAddress(InetAddress inetAddr) {
        inetAddress = inetAddr;

    }

    public static InetAddress getLocalIpAddress() {
        return inetAddress;
    }

    public static String getHostAddress() {
        return hostAddress;
    }

    public static void setHostAddress(String hostAddress) {
        MyApplication.hostAddress = hostAddress;
    }

    public static String getHostName() {
        return hostName;
    }

    public static void setHostName(String hostName) {
        MyApplication.hostName = hostName;
    }

    public static void initImageLoader(Context context) {
        // This configuration tuning is custom. You can tune every option, you may tune some of them,
        // or you can create default configuration by
        //  ImageLoaderConfiguration.createDefault(this);
        // method.
        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(context)
                .threadPriority(Thread.NORM_PRIORITY - 2)
                .denyCacheImageMultipleSizesInMemory()
                .discCacheFileNameGenerator(new Md5FileNameGenerator())
                .tasksProcessingOrder(QueueProcessingType.LIFO)
                .enableLogging() // Not necessary in common
                .build();
        // Initialize ImageLoader with configuration.
        ImageLoader.getInstance().init(config);
    }
}
