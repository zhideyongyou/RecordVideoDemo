/************************************************************
 * * EaseMob CONFIDENTIAL
 * __________________
 * Copyright (C) 2013-2014 EaseMob Technologies. All rights reserved.
 * <p/>
 * NOTICE: All information contained herein is, and remains
 * the property of EaseMob Technologies.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from EaseMob Technologies.
 */
package io.github.zxjzerg.demo.media;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecordVideoActivity extends AppCompatActivity implements OnClickListener {

    private static final String TAG = RecordVideoActivity.class.getSimpleName();

    public static final String VIDEO_CACHE_DIR =
        Environment.getExternalStorageDirectory() + "/lib-media/" + "video cache/";

    /** 启动拍照界面 */
    public static final int REQUEST_PLAY_VIDEO = 1;

    public static final String EXTRA_VIDEO_PATH = "extra_video_path";

    /** 视频录制时间的最大值 */
    private static final int VIDEO_MAX_DURATION = 15 * 1000;
    /** 视频录制的默认帧率 */
    private static final int PREFERRED_FRAME_RATE = 15;
    /** 视频录制的默认宽度 */
    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    /** 视频录制的默认高度 */
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;

    /** 视频路径 */
    private String mVideoPath = "";
    /** 视频画面宽度 */
    private int mPreviewWidth;
    /** 视频画面高度 */
    private int mPreviewHeight;
    /** 使用的摄像头。0是后置摄像头，1是前置摄像头 */
    private int mUsingCamera = 0;
    /** 视频帧率 */
    private int mVideoFrameRate = -1;

    private PowerManager.WakeLock mWakeLock;
    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private SurfaceHolder mSurfaceHolder;
    private MySurfaceCallback mSurfaceCallback;

    private ImageView mBtnStartRecord;
    private ImageView mBtnStopRecord;
    private VideoView mVideoView;
    private Chronometer mChronometer;
    private Button mBtnSwitchCamera;
    private ProgressBar mPbTimeLeft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 选择支持半透明模式，在有surfaceview的activity中使用
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        setContentView(R.layout.activity_record_vedio);

        mSurfaceCallback = new MySurfaceCallback();
        initViews();

        MediaLoader.createCacheDir();
    }

    private void initViews() {
        mBtnSwitchCamera = (Button) findViewById(R.id.btn_switch_camera);
        mBtnSwitchCamera.setVisibility(View.VISIBLE);
        mVideoView = (VideoView) findViewById(R.id.video_preview);
        mBtnStartRecord = (ImageView) findViewById(R.id.iv_recorder_start);
        mBtnStopRecord = (ImageView) findViewById(R.id.iv_recorder_stop);
        mChronometer = (Chronometer) findViewById(R.id.chronometer_record_time);
        mPbTimeLeft = (ProgressBar) findViewById(R.id.pb_time_left);

        mBtnSwitchCamera.setOnClickListener(this);
        mBtnStartRecord.setOnClickListener(this);
        mBtnStopRecord.setOnClickListener(this);
        findViewById(R.id.btn_cancel).setOnClickListener(this);

        mSurfaceHolder = mVideoView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceCallback);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock == null) {
            // 获取唤醒锁,保持屏幕常亮
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }
        if (!initCamera()) {
            showToast("初始化相机失败");
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopVideoRecording();
        releaseCamera();
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mPbTimeLeft.setVisibility(View.INVISIBLE);
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.btn_switch_camera) {
            switchCamera();
        } else if (i == R.id.iv_recorder_start) {
            if (startRecording()) {
                mBtnSwitchCamera.setVisibility(View.INVISIBLE);
                mBtnStartRecord.setVisibility(View.INVISIBLE);
                mBtnStopRecord.setVisibility(View.VISIBLE);
                mChronometer.setBase(SystemClock.elapsedRealtime());
                mChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                    @Override
                    public void onChronometerTick(Chronometer chronometer) {
                        long elapsedMillis = SystemClock.elapsedRealtime() - chronometer.getBase();
                        if (elapsedMillis > 10 * 1000) {
                            mChronometer.setTextColor(
                                ContextCompat.getColor(RecordVideoActivity.this, android.R.color.holo_red_light));
                        } else {
                            mChronometer.setTextColor(
                                ContextCompat.getColor(RecordVideoActivity.this, android.R.color.white));
                        }

                        int progress = (int) ((1 - (float) elapsedMillis / (15 * 1000)) * 100);
                        mPbTimeLeft.setProgress(progress);
                    }
                });
                mChronometer.start();
                mPbTimeLeft.setVisibility(View.VISIBLE);
            } else {
                showToast("无法开启视频录制");
            }
        } else if (i == R.id.iv_recorder_stop) {
            stopVideoRecording();
            Intent playVideo = new Intent(this, PlayVideoActivity.class);
            playVideo.putExtra(PlayVideoActivity.EXTRA_VIDEO_PATH, mVideoPath);
            playVideo.putExtra(PlayVideoActivity.EXTRA_IS_RECORDING, true);
            startActivityForResult(playVideo, REQUEST_PLAY_VIDEO);
        } else if (i == R.id.btn_cancel) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PLAY_VIDEO) {
            if (resultCode == RESULT_OK) {
                Intent videoData = new Intent();
                videoData.putExtra(EXTRA_VIDEO_PATH, mVideoPath);
                setResult(RESULT_OK, videoData);
                finish();
            }
        }
    }

    /** 初始化摄像头 */
    private boolean initCamera() {
        try {
            if (mUsingCamera == 0) {
                mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
            } else {
                mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
            }
            mCamera.lock();
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(mVideoView.getHolder());
            mCamera.startPreview();
        } catch (Exception ex) {
            Log.e(TAG, "init Camera fail ", ex);
            return false;
        }
        return true;
    }

    /** 设置SurfaceView */
    private void handleSurfaceCreate() {
        if (mCamera == null) {
            finish();
            return;
        }
        setPreviewFrameRate();
        setPreviewSize();
    }

    /** 开始录制视频 */
    public boolean startRecording() {
        mVideoPath = null;
        if (initRecorder()) {
            mMediaRecorder.setOnInfoListener(new OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopVideoRecording();
                        if (mVideoPath != null) {
                            Intent playVideo = new Intent(RecordVideoActivity.this, PlayVideoActivity.class);
                            playVideo.putExtra(PlayVideoActivity.EXTRA_VIDEO_PATH, mVideoPath);
                            playVideo.putExtra(PlayVideoActivity.EXTRA_IS_RECORDING, true);
                            startActivityForResult(playVideo, REQUEST_PLAY_VIDEO);
                        }
                    }
                }
            });
            mMediaRecorder.setOnErrorListener(new OnErrorListener() {
                @Override
                public void onError(MediaRecorder mr, int what, int extra) {
                    stopVideoRecording();
                }
            });
            mMediaRecorder.start();
            return true;
        } else {
            return false;
        }
    }

    /** 初始化Recorder */
    private boolean initRecorder() {
        if (mCamera == null && !initCamera()) {
            return false;
        }
        mCamera.unlock();
        mCamera.stopPreview();

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        // 设置录制视频源为Camera（相机）
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if (mUsingCamera == 1) {
            mMediaRecorder.setOrientationHint(270);
        } else {
            mMediaRecorder.setOrientationHint(90);
        }
        // 设置录制完成后视频的封装格式THREE_GPP为3gp.MPEG_4为mp4
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        // 设置录制的视频编码h263 h264
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错
        mMediaRecorder.setVideoSize(mPreviewWidth, mPreviewHeight);
        // 设置视频的比特率
        mMediaRecorder.setVideoEncodingBitRate(720 * 1024);
        // // 设置录制的视频帧率。必须放在设置编码和格式的后面，否则报错
        mMediaRecorder.setVideoFrameRate(mVideoFrameRate);
        // 设置视频文件输出的路径
        mVideoPath = VIDEO_CACHE_DIR + System.currentTimeMillis() + ".mp4";
        mMediaRecorder.setOutputFile(mVideoPath);
        mMediaRecorder.setMaxDuration(VIDEO_MAX_DURATION);
        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.e(TAG, "MediaRecorder prepare error!", e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "MediaRecorder prepare error!", e);
            return false;
        }
        return true;
    }

    /** 停止录制视频 */
    public void stopVideoRecording() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            try {
                mMediaRecorder.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException", e);
            }
        }
        releaseRecorder();

        // 变更UI
        mBtnSwitchCamera.setVisibility(View.VISIBLE);
        mBtnStartRecord.setVisibility(View.VISIBLE);
        mBtnStopRecord.setVisibility(View.INVISIBLE);
        mChronometer.stop();
        mPbTimeLeft.setVisibility(View.INVISIBLE);
    }

    /** 释放Recorder */
    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /** 释放Camera */
    protected void releaseCamera() {
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Release camera failed!", e);
        }
    }

    /** 切换前后摄像头 */
    public void switchCamera() {
        if (mCamera == null) {
            return;
        }
        if (Camera.getNumberOfCameras() >= 2) {
            mBtnSwitchCamera.setEnabled(false);
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
            switch (mUsingCamera) {
                case 0:
                    mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
                    mUsingCamera = 1;
                    break;
                case 1:
                    mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
                    mUsingCamera = 0;
                    break;
                default:
                    mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
                    mUsingCamera = 0;
                    break;
            }
            try {
                mCamera.lock();
                mCamera.setDisplayOrientation(90);
                mCamera.setPreviewDisplay(mVideoView.getHolder());
                mCamera.startPreview();
            } catch (IOException e) {
                mCamera.release();
                mCamera = null;
            }
            mBtnSwitchCamera.setEnabled(true);
        }
    }

    /** 设置视频录制时预览画面的帧率 */
    private void setPreviewFrameRate() {
        List<Integer> frameRates = mCamera.getParameters().getSupportedPreviewFrameRates();
        if (frameRates != null && frameRates.size() > 0) {
            Collections.sort(frameRates);
            for (int i = 0; i < frameRates.size(); i++) {
                int supportRate = frameRates.get(i);
                if (supportRate == PREFERRED_FRAME_RATE) {
                    mVideoFrameRate = PREFERRED_FRAME_RATE;
                }
            }
            // 如果不支持默认的帧率，则设为支持的第一个帧率
            if (mVideoFrameRate == -1) {
                mVideoFrameRate = frameRates.get(0);
            }
        }
    }

    /** 设置视频录制时预览画面的宽高 */
    private void setPreviewSize() {
        List<Camera.Size> resolutionList = getResolutionList(mCamera);
        if (resolutionList != null && resolutionList.size() > 0) {
            Collections.sort(resolutionList, new ResolutionComparator());
            Camera.Size previewSize = null;
            // 如果摄像头支持640*480，那么强制设为640*480
            for (int i = 0; i < resolutionList.size(); i++) {
                Camera.Size size = resolutionList.get(i);
                if (size != null && size.width == PREFERRED_PREVIEW_WIDTH && size.height == PREFERRED_PREVIEW_HEIGHT) {
                    previewSize = size;
                    mPreviewWidth = previewSize.width;
                    mPreviewHeight = previewSize.height;
                    break;
                }
            }
            // 如果不支持设为中间的那个
            if (previewSize == null) {
                int mediumResolution = resolutionList.size() / 2;
                if (mediumResolution >= resolutionList.size()) {
                    mediumResolution = resolutionList.size() - 1;
                }
                previewSize = resolutionList.get(mediumResolution);
                mPreviewWidth = previewSize.width;
                mPreviewHeight = previewSize.height;
            }
            Log.d(TAG, "mPreviewWidth: " + mPreviewWidth);
            Log.d(TAG, "mPreviewHeight: " + mPreviewHeight);
        }
    }

    /** 获取摄像头支持的分辨率 */
    public static List<Camera.Size> getResolutionList(Camera camera) {
        Parameters parameters = camera.getParameters();
        return parameters.getSupportedPreviewSizes();
    }

    private class MySurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (mCamera == null) {
                return;
            }
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
                handleSurfaceCreate();

                int screenWidth = getWindowManager().getDefaultDisplay().getWidth();
                android.view.ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
                lp.width = screenWidth;
                lp.height = (int) (((float) mPreviewWidth / (float) mPreviewHeight) * (float) screenWidth);
                mVideoView.setLayoutParams(lp);
            } catch (Exception ex) {
                Log.e("video", "start preview fail ", ex);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mSurfaceHolder = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v("video", "surfaceDestroyed");
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class ResolutionComparator implements Comparator<Camera.Size> {

        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            if (lhs.height != rhs.height) {
                return lhs.height - rhs.height;
            } else {
                return lhs.width - rhs.width;
            }
        }
    }
}
