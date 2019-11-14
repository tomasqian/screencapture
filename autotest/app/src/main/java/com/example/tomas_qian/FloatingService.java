package com.example.tomas_qian;

import android.app.Instrumentation;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;

import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FloatingService extends Service {
    private static final String TAG = "FloatingService";
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private View floatView;
    private Process process;

    private MediaProjectionManager mMediaProjectionManager;
    private SimpleDateFormat dateFormat;
    private String pathImage;
    private WindowManager mWindowManager;
    private ImageReader mImageReader;
    private MediaProjection mMediaProjection;
    private int mResultCode;
    private Intent mResultData;
    private VirtualDisplay mVirtualDisplay;
    private String strDate;
    private int windowWidth;
    private int windowHeight;
    private String nameImage;
    private int mScreenDensity;
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            process = Runtime.getRuntime().exec("su");
            mMediaProjectionManager = (MediaProjectionManager) getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            createVirtualEnvironment();
        }catch(Exception e){
            e.printStackTrace();
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.gravity = Gravity.LEFT;//悬浮框在布局的位置
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;//悬浮窗的宽，不指定则无法滑动
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;//悬浮窗的高，不指定则无法滑动
        //layoutParams.x = 0; //初始位置的x坐标
        //layoutParams.y = 0; //初始位置的y坐标
        floatView = new View(getApplicationContext()); // 不依赖activity的生命周期
        floatView = View.inflate(getApplicationContext(), R.layout.float_view, null);
        final ImageView ivClose = floatView.findViewById(R.id.close);
        final ImageView ivStart = floatView.findViewById(R.id.start);
        ivClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //stopSelf();//关闭当前服务
                captureArea();
            }
        });
        ivStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                execShellCmd("input keyevent 3");
                //execShellCmd("sleep 1");
                autosleep(5);
                execShellCmd("input keyevent 3");
                autosleep(5);

               // execShellCmd("sleep 1  ");
                execShellCmd("input tap 280 760");
                autosleep(10);
                Log.e("tomas_test","wait ant sleep 10");
                login();
            }
        });
        floatView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ivClose.getVisibility() == View.GONE) {
                    ivClose.setVisibility(View.VISIBLE);
                    ivStart.setVisibility(View.VISIBLE);
                } else {
                    ivClose.setVisibility(View.GONE);
                    ivStart.setVisibility(View.GONE);
                }
            }
        });
    }
    private void createVirtualEnvironment() {
        dateFormat = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss");
        strDate = dateFormat.format(new Date());
        pathImage = Environment.getExternalStorageDirectory().getPath() + "/Pictures/";
        nameImage = pathImage + strDate + ".png";
        mMediaProjectionManager = (MediaProjectionManager) getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mWindowManager = (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowWidth = mWindowManager.getDefaultDisplay().getWidth();
        windowHeight = mWindowManager.getDefaultDisplay().getHeight();
        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mImageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 2); //ImageFormat.RGB_565

        Log.i(TAG, "prepared the virtual environment"+windowWidth+","+windowHeight);
    }
    public void startVirtual() {
        if (mMediaProjection != null) {
            Log.i(TAG, "want to display virtual");
            virtualDisplay();
        } else {
            Log.i(TAG, "start screen capture intent");
            Log.i(TAG, "want to build mediaprojection and display virtual");
            setUpMediaProjection();
            virtualDisplay();
        }
    }
    public void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
        Log.i(TAG, "mMediaProjection defined");
    }
    private void virtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                windowWidth, windowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
        Log.i(TAG, "virtual displayed");
    }
    private void startCapture() {
        strDate = dateFormat.format(new java.util.Date());
        nameImage = pathImage + strDate + ".png";

        Image image = mImageReader.acquireLatestImage();
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        image.close();
        Log.i(TAG, "image data captured");

        //保存截屏结果，如果要裁剪图片，在这里处理bitmap
        if (bitmap != null) {
            try {
                File fileImage = new File(nameImage);
                if (!fileImage.exists()) {
                    fileImage.createNewFile();
                    Log.i(TAG, "image file created");
                }
                FileOutputStream out = new FileOutputStream(fileImage);
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.flush();
                    out.close();
                    Intent media = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(fileImage);
                    media.setData(contentUri);
                    this.sendBroadcast(media);
                    Log.i(TAG, "screen image saved");
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void captureArea(){
        startVirtual();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startCapture();
            }
        },100);
    }

    private void autosleep(int time){
        try {
            Thread.sleep(time*1000);
        } catch (InterruptedException e) {
            return;
        }
    }
    private void login() {
        execShellCmd("input tap 658 167");
        autosleep(5);
        //点两次进入
        Log.e("tomas_test","wait for auto -> login sleep 5");
        execShellCmd("input tap 709 531");
        autosleep(5);
        Log.e("tomas_test","wait for login");
    }

    private void execShellCmd(String cmd) {
        try {
            Log.e(TAG,"qf cmd:"+cmd);
            DataOutputStream os =null;
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(cmd);
            os.writeBytes("\n");
            os.flush();
            //os.writeBytes("exit\n");
            //os.flush();
            //process.waitFor();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showFloatingWindow();
        return super.onStartCommand(intent, flags, startId);
    }

    private void showFloatingWindow() {
        windowManager.addView(floatView, layoutParams);
        floatView.setOnTouchListener(new FloatingOnTouchListener());
    }

    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;
                    layoutParams.x = layoutParams.x + movedX;
                    layoutParams.y = layoutParams.y + movedY;
                    Log.i(TAG, "onTouch: " + layoutParams.x + " " + layoutParams.y);
                    windowManager.updateViewLayout(view, layoutParams);
                    break;
                default:
                    break;
            }
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatView != null) {
            windowManager.removeViewImmediate(floatView);
        }
    }
}
