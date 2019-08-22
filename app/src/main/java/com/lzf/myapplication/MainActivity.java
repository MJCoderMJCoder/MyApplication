package com.lzf.myapplication;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.lzf.myapplication.dms.ContentTree;
import com.lzf.myapplication.util.FileUtil;
import com.lzf.myapplication.util.ImageUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private ResolveInfo systemResolveInfo; //WiFi-Display


    //===============DLNA=================//
    public static final int GET_IP_FAIL = 0;

    public static final int GET_IP_SUC = 1;

    private Context mContext;

    //    private ProgressDialog progDialog = null;

    private String hostName;

    private String hostAddress;

    private List<Map<String, String>> mVideoFilePaths;

    private Handler mHandle = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GET_IP_FAIL: {
                    Toast.makeText(mContext, "获取IP失败", Toast.LENGTH_SHORT);
                    break;
                }
                case GET_IP_SUC: {
                    if (null != msg.obj) {
                        InetAddress inetAddress = (InetAddress) msg.obj;
                        if (null != inetAddress) {
                            setIp(inetAddress);
                            setIpInfo();
                            jumpToMain();
                        }
                    } else {
                        Toast.makeText(mContext, "获取IP失败", Toast.LENGTH_SHORT);
                    }
                    break;
                }

            }

            super.handleMessage(msg);
        }

    };

    private void createFolder() {
        FileUtil.createSDCardDir(true);
    }

    private void getIp() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                WifiManager wifiManager = (WifiManager) mContext.getSystemService(WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();

                InetAddress inetAddress;
                Message message = new Message();
                try {
                    inetAddress = InetAddress.getByName(String.format("%d.%d.%d.%d",
                            (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff),
                            (ipAddress >> 24 & 0xff)));

                    hostName = inetAddress.getHostName();
                    hostAddress = inetAddress.getHostAddress();
                    message.obj = inetAddress;
                    message.what = GET_IP_SUC;
                    mHandle.sendMessage(message);
                } catch (UnknownHostException e) {
                    mHandle.sendEmptyMessage(GET_IP_FAIL);
                }
            }
        }).start();

    }

    private void getVideoFilePaths() {
        mVideoFilePaths = new ArrayList<Map<String, String>>();
        Cursor cursor;
        String[] videoColumns = {
                MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA, MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.RESOLUTION
        };
        cursor = mContext.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoColumns, null, null, null);
        if (null != cursor && cursor.moveToFirst()) {
            do {
                String id = ContentTree.VIDEO_PREFIX
                        + cursor.getInt(cursor.getColumnIndex(MediaStore.Video.Media._ID));
                String filePath = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                Map<String, String> fileInfoMap = new HashMap<String, String>();
                fileInfoMap.put(id, filePath);
                mVideoFilePaths.add(fileInfoMap);
                // Log.v(LOGTAG, "added video item " + title + "from " +
                // filePath);
            } while (cursor.moveToNext());
        }
        if (null != cursor) {
            cursor.close();
        }

    }

    private void createVideoThumb() {
        if (null != mVideoFilePaths && mVideoFilePaths.size() > 0) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    for (int i = 0; i < mVideoFilePaths.size(); i++) {
                        Set entries = mVideoFilePaths.get(i).entrySet();
                        if (entries != null) {
                            Iterator iterator = entries.iterator();
                            while (iterator.hasNext()) {
                                Map.Entry entry = (Map.Entry) iterator.next();
                                Object id = entry.getKey();
                                Object filePath = entry.getValue();

                                Bitmap videoThumb = ImageUtil.getThumbnailForVideo(filePath
                                        .toString());
                                String videoSavePath = ImageUtil.getSaveVideoFilePath(
                                        filePath.toString(), id.toString());
                                try {
                                    ImageUtil.saveBitmapWithFilePathSuffix(videoThumb,
                                            videoSavePath);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

            }).start();
        }
    }

    private void setIp(InetAddress inetAddress) {
        MyApplication.setLocalIpAddress(inetAddress);
    }

    private void setIpInfo() {
        MyApplication.setHostName(hostName);
        MyApplication.setHostAddress(hostAddress);
    }

    private void jumpToMain() {
        Intent intent = new Intent(MainActivity.this, IndexActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        //DLNA
        TextView dlan = (TextView) findViewById(R.id.DLNA);
        dlan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createFolder();
                getVideoFilePaths();
                createVideoThumb();
                getIp();
            }
        });


        //WIFI_DISPLAY_SETTINGS和CAST_SETTINGS
        TextView wifiDisplay = (TextView) findViewById(R.id.wifiDisplay);
        wifiDisplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent wifiActionIntent = new Intent("android.settings.WIFI_DISPLAY_SETTINGS");
                systemResolveInfo = getSystemResolveInfo(wifiActionIntent);
                if (systemResolveInfo != null) {
                    try {
                        Intent systemWifiIntent = new Intent();
                        systemWifiIntent.setClassName(systemResolveInfo.activityInfo.applicationInfo.packageName,
                                systemResolveInfo.activityInfo.name);
                        startSettingsActivity(systemWifiIntent);
                        Toast.makeText(MainActivity.this, "WIFI_DISPLAY", Toast.LENGTH_SHORT).show();
                        return;
                    } catch (ActivityNotFoundException exception) {
                        Log.e("lzf", "Intent不能被启动", exception);
                    }
                }

                Intent castActionIntent = new Intent("android.settings.CAST_SETTINGS");
                systemResolveInfo = getSystemResolveInfo(castActionIntent);
                if (systemResolveInfo != null) {
                    try {
                        Intent systemCastIntent = new Intent();
                        systemCastIntent.setClassName(systemResolveInfo.activityInfo.applicationInfo.packageName,
                                systemResolveInfo.activityInfo.name);
                        startSettingsActivity(systemCastIntent);
                        Toast.makeText(MainActivity.this, "CAST", Toast.LENGTH_SHORT).show();
                        return;
                    } catch (ActivityNotFoundException exception) {
                        Toast.makeText(MainActivity.this, "很抱歉，此设备不支持镜像投屏。", Toast.LENGTH_SHORT).show();
                        Log.e("lzf", "ActivityNotFoundException", exception);
                    }
                }
            }
        });

    }

    //==============WIFI_DISPLAY_SETTINGS和CAST_SETTINGS=================//
    private ResolveInfo getSystemResolveInfo(Intent intent) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : list) {
            try {
                ApplicationInfo activityInfo = pm.getApplicationInfo(info.activityInfo.packageName,
                        0);
                if ((activityInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    return info;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // 继续下一个解析
                Log.e("lzf", "PackageManager.NameNotFoundException", e);
            }
        }
        return null;
    }

    private void startSettingsActivity(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (SecurityException e) {
            Toast.makeText(MainActivity.this, "没有权限启动这个活动", Toast.LENGTH_SHORT).show();
            Log.e("lzf", "没有权限启动这个活动", e);
            return;
        }
    }
    //===============WIFI_DISPLAY_SETTINGS和CAST_SETTINGS=================//

}
