package com.lzf.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.VideoView;

import com.lzf.myapplication.dmc.GenerateXml;
import com.lzf.myapplication.dmp.ContentItem;
import com.lzf.myapplication.dmp.DeviceItem;
import com.lzf.myapplication.dmr.ZxtMediaRenderer;
import com.lzf.myapplication.dms.ContentNode;
import com.lzf.myapplication.dms.ContentTree;
import com.lzf.myapplication.dms.MediaServer;
import com.lzf.myapplication.util.FileUtil;
import com.lzf.myapplication.util.FixedAndroidHandler;
import com.lzf.myapplication.util.ImageUtil;
import com.lzf.myapplication.util.Utils;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.PersonWithRole;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.WriteStatus;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.ImageItem;
import org.fourthline.cling.support.model.item.MusicTrack;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;
import org.seamless.util.logging.LoggingUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.lzf.myapplication.MyApplication.mContext;

/**
 * Created by MJCoder on 2017-09-13.
 */

public class VideoActivity extends Activity {
    private String mLastDevice;
    private static final Logger log = Logger.getLogger(DevicesActivity.class
            .getName());

    private final static String LOGTAG = "设备";

    public static final int DMR_GET_NO = 0;

    public static final int DMR_GET_SUC = 1;

    private static boolean serverPrepared = false;

    private String fileName;

    private ListView mDevLv;

    private ListView mDmrLv;

    private ArrayList<DeviceItem> mDevList = new ArrayList<DeviceItem>();

    private ArrayList<DeviceItem> mDmrList = new ArrayList<DeviceItem>();

    private int mImageContaierId = Integer.valueOf(ContentTree.IMAGE_ID) + 1;


    private AndroidUpnpService upnpService;

    private DeviceListRegistryListener deviceListRegistryListener;

    private MediaServer mediaServer;


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            final String[] array = new String[mDmrList.size()];
            for (int i = 0; i < mDmrList.size(); i++) {
                array[i] = (String) mDmrList.get(i).toString();
            }
            AlertDialog alert = null;
            AlertDialog.Builder builder = new AlertDialog.Builder(VideoActivity.this);
            alert = builder.setTitle("设备")
                    .setSingleChoiceItems(array, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ContentItem localContentItem = null;
                            MyApplication myApplication = (MyApplication) VideoActivity.this.getApplication();
                            if (null == myApplication.deviceItem) {
                                Toast.makeText(mContext, "请选择设备",
                                        Toast.LENGTH_SHORT).show();
                            } else if (null == mLastDevice || "" == mLastDevice
                                    || mLastDevice != myApplication.deviceItem.toString()) {
                                upnpService = myApplication.upnpService;
                                Device device = myApplication.deviceItem.getDevice();
                                Service service = device.findService(new UDAServiceType(
                                        "ContentDirectory"));
                               /* upnpService.getControlPoint().execute(
                                        new ContentBrowseActionCallback(VideoActivity.this,
                                                service, createRootContainer(service), mContentList,
                                                mHandler));*/
                                mLastDevice = myApplication.deviceItem.toString();
                                localContentItem = new ContentItem(null, service, device);
                            }

                            Intent localIntent = new Intent("com.transport.info");
                            localIntent.putExtra("name", localContentItem.toString());
                            localIntent.putExtra("playURI", localContentItem.getItem().getFirstResource().getValue());
                            localIntent.putExtra("currentContentFormatMimeType", "video/mp4");
                            try {
                                localIntent.putExtra("metaData", new GenerateXml().generate(localContentItem));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            VideoActivity.this.sendBroadcast(localIntent);

                            Toast.makeText(getApplicationContext(), "你选择了" + array[which], Toast.LENGTH_SHORT).show();
                        }
                    }).create();
            alert.show();
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {

            mDevList.clear();
            mDmrList.clear();

            upnpService = (AndroidUpnpService) service;
            MyApplication.upnpService = upnpService;


            Log.v(LOGTAG, "Connected to UPnP Service");

            if (mediaServer == null
                    && SettingActivity.getDmsOn(VideoActivity.this)) {
                try {
                    mediaServer = new MediaServer(VideoActivity.this);
                    upnpService.getRegistry()
                            .addDevice(mediaServer.getDevice());
                    DeviceItem localDevItem = new DeviceItem(
                            mediaServer.getDevice());

                    deviceListRegistryListener.deviceAddOrRemove(1, localDevItem);
                    new Thread(new Runnable() {

                        @Override
                        public void run() {
                            prepareMediaServer();
                        }
                    }).start();

                } catch (Exception ex) {
                    // TODO: handle exception
                    log.log(Level.SEVERE, "Creating demo device failed", ex);
                    Toast.makeText(VideoActivity.this,
                            R.string.create_demo_failed, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
            }

            if (SettingActivity.getRenderOn(VideoActivity.this)) {
                ZxtMediaRenderer mediaRenderer = new ZxtMediaRenderer(1,
                        VideoActivity.this);
                upnpService.getRegistry().addDevice(mediaRenderer.getDevice());
                deviceListRegistryListener.deviceAddOrRemove(3, new DeviceItem(mediaRenderer.getDevice()));
            }

            // xgf
            for (Device device : upnpService.getRegistry().getDevices()) {
                if (device.getType().getNamespace().equals("schemas-upnp-org")
                        && device.getType().getType().equals("MediaServer")) {
                    final DeviceItem display = new DeviceItem(device, device
                            .getDetails().getFriendlyName(),
                            device.getDisplayString(), "(REMOTE) "
                            + device.getType().getDisplayString());
                    deviceListRegistryListener.deviceAddOrRemove(1, display);
                }
            }

            // Getting ready for future device advertisements
            upnpService.getRegistry().addListener(deviceListRegistryListener);
            // Refresh device list
            upnpService.getControlPoint().search();

            // select first device by default
            if (null != mDevList && mDevList.size() > 0
                    && null == MyApplication.deviceItem) {
                MyApplication.deviceItem = mDevList.get(0);
            }
            if (null != mDmrList && mDmrList.size() > 0
                    && null == MyApplication.dmrDeviceItem) {
                MyApplication.dmrDeviceItem = mDmrList.get(0);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            upnpService = null;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (upnpService != null) {
            upnpService.getRegistry()
                    .removeListener(deviceListRegistryListener);
        }
        getApplicationContext().unbindService(serviceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "搜索设备");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                LoggingUtil.resetRootHandler(new FixedAndroidHandler());
                Logger.getLogger("org.teleal.cling").setLevel(Level.INFO);
                if (null != mDevList && mDevList.size() > 0) {
                    MyApplication.deviceItem = mDevList.get(0);
                }
                if (null != mDmrList && mDmrList.size() > 0) {
                    if (null != mDmrList.get(0).getDevice()
                            && null != MyApplication.deviceItem
                            && null != mDmrList.get(0).getDevice()
                            .getDetails().getModelDetails()
                            && Utils.DMR_NAME.equals(mDmrList.get(0)
                            .getDevice().getDetails().getModelDetails()
                            .getModelName())
                            && Utils.getDevName(
                            mDmrList.get(0).getDevice().getDetails()
                                    .getFriendlyName()).equals(
                            Utils.getDevName(MyApplication.deviceItem
                                    .getDevice().getDetails()
                                    .getFriendlyName()))) {
                        MyApplication.isLocalDmr = true;
                    } else {
                        MyApplication.isLocalDmr = false;
                    }
                    MyApplication.dmrDeviceItem = mDmrList.get(0);
                }
                deviceListRegistryListener = new DeviceListRegistryListener();
                getApplicationContext().bindService(
                        new Intent(this, AndroidUpnpServiceImpl.class),
                        serviceConnection, Context.BIND_AUTO_CREATE);
                searchNetwork();
                break;
            default:
                break;
        }
        return false;
    }

    protected void searchNetwork() {
        if (upnpService == null)
            return;
        Toast.makeText(this, "正在搜索设备...", Toast.LENGTH_LONG).show();
        upnpService.getRegistry().removeAllRemoteDevices();
        upnpService.getControlPoint().search();
    }

    public class DeviceListRegistryListener extends DefaultRegistryListener {

		/* Discovery performance optimization for very slow Android devices! */

        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry,
                                                 RemoteDevice device) {
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry,
                                                final RemoteDevice device, final Exception ex) {
        }

		/*
         * End of optimization, you can remove the whole block if your Android
		 * handset is fast (>= 600 Mhz)
		 */

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            Log.e("DeviceListRegistryListener",
                    "remoteDeviceAdded:" + device.toString()
                            + device.getType().getType());

            if (device.getType().getNamespace().equals("schemas-upnp-org")
                    && device.getType().getType().equals("MediaServer")) {
                final DeviceItem display = new DeviceItem(device, device
                        .getDetails().getFriendlyName(),
                        device.getDisplayString(), "(REMOTE) "
                        + device.getType().getDisplayString());
                deviceAddOrRemove(1, display);
            }

            if (device.getType().getNamespace().equals("schemas-upnp-org")
                    && device.getType().getType().equals("MediaRenderer")) {
                final DeviceItem dmrDisplay = new DeviceItem(device, device
                        .getDetails().getFriendlyName(),
                        device.getDisplayString(), "(REMOTE) "
                        + device.getType().getDisplayString());
                deviceAddOrRemove(3, dmrDisplay);
            }
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            final DeviceItem display = new DeviceItem(device,
                    device.getDisplayString());
            deviceAddOrRemove(2, display);

            if (device.getType().getNamespace().equals("schemas-upnp-org")
                    && device.getType().getType().equals("MediaRenderer")) {
                final DeviceItem dmrDisplay = new DeviceItem(device, device
                        .getDetails().getFriendlyName(),
                        device.getDisplayString(), "(REMOTE) "
                        + device.getType().getDisplayString());
                deviceAddOrRemove(4, dmrDisplay);
            }
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            Log.e("DeviceListRegistryListener",
                    "localDeviceAdded:" + device.toString()
                            + device.getType().getType());

            final DeviceItem display = new DeviceItem(device, device
                    .getDetails().getFriendlyName(), device.getDisplayString(),
                    "(REMOTE) " + device.getType().getDisplayString());
            deviceAddOrRemove(1, display);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            Log.e("DeviceListRegistryListener",
                    "localDeviceRemoved:" + device.toString()
                            + device.getType().getType());

            final DeviceItem display = new DeviceItem(device,
                    device.getDisplayString());
            deviceAddOrRemove(2, display);
        }

        int i = 0;

        public void deviceAddOrRemove(int id, final DeviceItem di) {
            switch (id) {
                case 1:
                    if (!mDevList.contains(di)) {
                        mDevList.add(di);
                    }
                    break;
                case 2:
                    mDevList.remove(di);
                    break;
                case 3:
                    if (!mDmrList.contains(di)) {
                        mDmrList.add(di);
                    }
                    break;
                case 4:
                    mDmrList.remove(di);
                    break;
                default:
                    break;
            }
            int temp = i++;
            Log.v("lzf", "i++：" + temp);
            if (temp % 3 == 0 && temp != 0) {
                handler.sendEmptyMessage(6003);
            }
        }

    }

    private String[] imageThumbColumns = new String[]{
            MediaStore.Images.Thumbnails.IMAGE_ID,
            MediaStore.Images.Thumbnails.DATA};

    private void prepareMediaServer() {

        if (serverPrepared)
            return;
        ContentNode rootNode = ContentTree.getRootNode();
        // Video Container
        Container videoContainer = new Container();
        videoContainer.setClazz(new DIDLObject.Class("object.container"));
        videoContainer.setId(ContentTree.VIDEO_ID);
        videoContainer.setParentID(ContentTree.ROOT_ID);
        videoContainer.setTitle("Videos");
        videoContainer.setRestricted(true);
        videoContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);
        videoContainer.setChildCount(0);

        rootNode.getContainer().addContainer(videoContainer);
        rootNode.getContainer().setChildCount(
                rootNode.getContainer().getChildCount() + 1);
        ContentTree.addNode(ContentTree.VIDEO_ID, new ContentNode(
                ContentTree.VIDEO_ID, videoContainer));

        Cursor cursor;
        String[] videoColumns = {MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.RESOLUTION,
                MediaStore.Video.Media.DESCRIPTION};
        cursor = managedQuery(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoColumns, null, null, null);
        if (cursor.moveToFirst()) {
            do {
                String id = ContentTree.VIDEO_PREFIX
                        + cursor.getInt(cursor
                        .getColumnIndex(MediaStore.Video.Media._ID));
                String title = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Video.Media.TITLE));
                String creator = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST));
                String filePath = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Video.Media.DATA));

                String mimeType = cursor
                        .getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
                long size = cursor.getLong(cursor
                        .getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));
                long duration = cursor
                        .getLong(cursor
                                .getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                String resolution = cursor
                        .getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION));

                String description = cursor
                        .getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Video.Media.DESCRIPTION));

                Res res = new Res(new MimeType(mimeType.substring(0,
                        mimeType.indexOf('/')), mimeType.substring(mimeType
                        .indexOf('/') + 1)), size, "http://"
                        + mediaServer.getAddress() + "/" + id);

                res.setDuration(duration / (1000 * 60 * 60) + ":"
                        + (duration % (1000 * 60 * 60)) / (1000 * 60) + ":"
                        + (duration % (1000 * 60)) / 1000);
                res.setResolution(resolution);

                VideoItem videoItem = new VideoItem(id, ContentTree.VIDEO_ID,
                        title, creator, res);

                // add video thumb Property
                String videoSavePath = ImageUtil.getSaveVideoFilePath(filePath,
                        id);
                DIDLObject.Property albumArtURI = new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                        URI.create("http://" + mediaServer.getAddress()
                                + videoSavePath));
                DIDLObject.Property[] properties = {albumArtURI};
                videoItem.addProperties(properties);
                videoItem.setDescription(description);
                videoContainer.addItem(videoItem);
                videoContainer
                        .setChildCount(videoContainer.getChildCount() + 1);
                ContentTree.addNode(id,
                        new ContentNode(id, videoItem, filePath));

                // Log.v(LOGTAG, "added video item " + title + "from " +
                // filePath);
            } while (cursor.moveToNext());
        }

        // Audio Container
        Container audioContainer = new Container(ContentTree.AUDIO_ID,
                ContentTree.ROOT_ID, "Audios", "GNaP MediaServer",
                new DIDLObject.Class("object.container"), 0);
        audioContainer.setRestricted(true);
        audioContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);
        rootNode.getContainer().addContainer(audioContainer);
        rootNode.getContainer().setChildCount(
                rootNode.getContainer().getChildCount() + 1);
        ContentTree.addNode(ContentTree.AUDIO_ID, new ContentNode(
                ContentTree.AUDIO_ID, audioContainer));

        String[] audioColumns = {MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM};
        cursor = managedQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioColumns, null, null, null);
        if (cursor.moveToFirst()) {
            do {
                String id = ContentTree.AUDIO_PREFIX
                        + cursor.getInt(cursor
                        .getColumnIndex(MediaStore.Audio.Media._ID));
                String title = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                String creator = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                String filePath = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                String mimeType = cursor
                        .getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));
                long size = cursor.getLong(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
                long duration = cursor
                        .getLong(cursor
                                .getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                String album = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                Res res = null;
                try {
                    res = new Res(new MimeType(mimeType.substring(0,
                            mimeType.indexOf('/')), mimeType.substring(mimeType
                            .indexOf('/') + 1)), size, "http://"
                            + mediaServer.getAddress() + "/" + id);
                } catch (Exception e) {
                    Log.w(LOGTAG, "Exception1", e);
                }

                if (null == res) {
                    break;
                }
                res.setDuration(duration / (1000 * 60 * 60) + ":"
                        + (duration % (1000 * 60 * 60)) / (1000 * 60) + ":"
                        + (duration % (1000 * 60)) / 1000);

                // Music Track must have `artist' with role field, or
                // DIDLParser().generate(didl) will throw nullpointException
                MusicTrack musicTrack = new MusicTrack(id,
                        ContentTree.AUDIO_ID, title, creator, album,
                        new PersonWithRole(creator, "Performer"), res);
                audioContainer.addItem(musicTrack);
                audioContainer
                        .setChildCount(audioContainer.getChildCount() + 1);
                ContentTree.addNode(id, new ContentNode(id, musicTrack,
                        filePath));

                // Log.v(LOGTAG, "added audio item " + title + "from " +
                // filePath);
            } while (cursor.moveToNext());
        }

        // get image thumbnail
        Cursor thumbCursor = this.managedQuery(
                MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                imageThumbColumns, null, null, null);
        HashMap<Integer, String> imageThumbs = new HashMap<Integer, String>();
        if (null != thumbCursor && thumbCursor.moveToFirst()) {
            do {
                imageThumbs
                        .put(thumbCursor.getInt(0), thumbCursor.getString(1));
            } while (thumbCursor.moveToNext());

            if (Integer.parseInt(Build.VERSION.SDK) < 14) {
                thumbCursor.close();
            }
        }

        // Image Container
        Container imageContainer = new Container(ContentTree.IMAGE_ID,
                ContentTree.ROOT_ID, "Images", "GNaP MediaServer",
                new DIDLObject.Class("object.container"), 0);
        imageContainer.setRestricted(true);
        imageContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);
        rootNode.getContainer().addContainer(imageContainer);
        rootNode.getContainer().setChildCount(
                rootNode.getContainer().getChildCount() + 1);
        ContentTree.addNode(ContentTree.IMAGE_ID, new ContentNode(
                ContentTree.IMAGE_ID, imageContainer));

        String[] imageColumns = {MediaStore.Images.Media._ID,
                MediaStore.Images.Media.TITLE, MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DESCRIPTION};
        cursor = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageColumns, null, null, MediaStore.Images.Media.DATA);

        Container typeContainer = null;
        if (cursor.moveToFirst()) {
            do {
                int imageId = cursor.getInt(cursor
                        .getColumnIndex(MediaStore.Images.Media._ID));
                String id = ContentTree.IMAGE_PREFIX
                        + cursor.getInt(cursor
                        .getColumnIndex(MediaStore.Images.Media._ID));
                String title = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Images.Media.TITLE));
                String creator = "unkown";
                String filePath = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                String mimeType = cursor
                        .getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE));
                long size = cursor.getLong(cursor
                        .getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));

                String description = cursor
                        .getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Images.Media.DESCRIPTION));

                String url = "http://" + mediaServer.getAddress() + "/"
                        + filePath;
                Res res = new Res(new MimeType(mimeType.substring(0,
                        mimeType.indexOf('/')), mimeType.substring(mimeType
                        .indexOf('/') + 1)), size, url);

                Container tempTypeContainer = null;
                if (null != typeContainer) {
                    tempTypeContainer = typeContainer;
                }
                if (TextUtils.isEmpty(fileName)) {
                    fileName = FileUtil.getFoldName(filePath);
                    typeContainer = new Container(
                            String.valueOf(mImageContaierId),
                            ContentTree.IMAGE_ID, fileName, "GNaP MediaServer",
                            new DIDLObject.Class("object.container"), 0);
                    typeContainer.setRestricted(true);
                    typeContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);

                    tempTypeContainer = typeContainer;
                    imageContainer.addContainer(tempTypeContainer);
                    imageContainer
                            .setChildCount(imageContainer.getChildCount() + 1);
                    ContentTree.addNode(String.valueOf(mImageContaierId),
                            new ContentNode(String.valueOf(mImageContaierId),
                                    tempTypeContainer));

                    ImageItem imageItem = new ImageItem(id,
                            String.valueOf(mImageContaierId), title, creator,
                            res);

                    if (imageThumbs.containsKey(imageId)) {
                        String thumb = imageThumbs.get(imageId);
                        Log.i(LOGTAG, " image thumb:" + thumb);
                        // set albumArt Property
                        DIDLObject.Property albumArtURI = new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                                URI.create("http://" + mediaServer.getAddress()
                                        + thumb));
                        DIDLObject.Property[] properties = {albumArtURI};
                        imageItem.addProperties(properties);
                    }
                    imageItem.setDescription(description);

                    tempTypeContainer.addItem(imageItem);
                    tempTypeContainer.setChildCount(tempTypeContainer
                            .getChildCount() + 1);
                    ContentTree.addNode(id, new ContentNode(id, imageItem,
                            filePath));
                } else {
                    if (!fileName.equalsIgnoreCase(FileUtil
                            .getFoldName(filePath))) {
                        mImageContaierId++;
                        fileName = FileUtil.getFoldName(filePath);

                        typeContainer = new Container(
                                String.valueOf(mImageContaierId),
                                ContentTree.IMAGE_ID, fileName,
                                "GNaP MediaServer", new DIDLObject.Class(
                                "object.container"), 0);
                        typeContainer.setRestricted(true);
                        typeContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);

                        tempTypeContainer = typeContainer;
                        imageContainer.addContainer(tempTypeContainer);
                        imageContainer.setChildCount(imageContainer
                                .getChildCount() + 1);
                        ContentTree.addNode(
                                String.valueOf(mImageContaierId),
                                new ContentNode(String
                                        .valueOf(mImageContaierId),
                                        tempTypeContainer));

                        ImageItem imageItem = new ImageItem(id,
                                String.valueOf(mImageContaierId), title,
                                creator, res);

                        if (imageThumbs.containsKey(imageId)) {
                            String thumb = imageThumbs.get(imageId);
                            Log.i(LOGTAG, " image thumb:" + thumb);
                            // set albumArt Property
                            DIDLObject.Property albumArtURI = new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                                    URI.create("http://"
                                            + mediaServer.getAddress() + thumb));
                            DIDLObject.Property[] properties = {albumArtURI};
                            imageItem.addProperties(properties);
                        }
                        imageItem.setDescription(description);
                        tempTypeContainer.addItem(imageItem);
                        tempTypeContainer.setChildCount(typeContainer
                                .getChildCount() + 1);
                        ContentTree.addNode(id, new ContentNode(id, imageItem,
                                filePath));
                    } else {
                        ImageItem imageItem = new ImageItem(id,
                                String.valueOf(mImageContaierId), title,
                                creator, res);

                        if (imageThumbs.containsKey(imageId)) {
                            String thumb = imageThumbs.get(imageId);
                            Log.i(LOGTAG, " image thumb:" + thumb);
                            // set albumArt Property
                            DIDLObject.Property albumArtURI = new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                                    URI.create("http://"
                                            + mediaServer.getAddress() + thumb));
                            DIDLObject.Property[] properties = {albumArtURI};
                            imageItem.addProperties(properties);
                        }
                        imageItem.setDescription(description);
                        tempTypeContainer.addItem(imageItem);
                        tempTypeContainer.setChildCount(typeContainer
                                .getChildCount() + 1);
                        ContentTree.addNode(id, new ContentNode(id, imageItem,
                                filePath));
                    }
                }

                // imageContainer.addItem(imageItem);
                // imageContainer
                // .setChildCount(imageContainer.getChildCount() + 1);
                // ContentTree.addNode(id,
                // new ContentNode(id, imageItem, filePath));

                Log.v(LOGTAG, "added image item " + title + "from " + filePath);
            } while (cursor.moveToNext());
        }
        serverPrepared = true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        //==============播放视频=============//
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.FILL_PARENT,
                RelativeLayout.LayoutParams.FILL_PARENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        final VideoView videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setLayoutParams(layoutParams);
        videoView.setVideoURI(Uri.parse("android.resource://"
                + getPackageName() + "/" + R.raw.cepingjuaction));
        MediaController mediaController = new MediaController(this);
        videoView.setMediaController(mediaController);
        videoView.requestFocus();
        videoView.start();
        mediaController.setMediaPlayer(videoView);
        mediaController.setVisibility(MediaController.INVISIBLE);
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            public void onCompletion(MediaPlayer mp) {
                videoView.start();
            }

        });
        //==============播放视频=============//


    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

}
