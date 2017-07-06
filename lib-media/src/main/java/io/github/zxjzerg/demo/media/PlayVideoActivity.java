package io.github.zxjzerg.demo.media;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.VideoView;

import com.github.lzyzsd.circleprogress.DonutProgress;

/**
 * 播放视屏的界面。需要通过Intent传递EXTRA_VIDEO_PATH作为视频的本地文件位置。
 */
public class PlayVideoActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String EXTRA_VIDEO_PATH = "extra_video_path";
    public static final String EXTRA_IS_RECORDING = "extra_is_recording";

    private VideoView mVideoView;
    private ImageButton mBtnPlay;
    private ImageButton mBtnPause;
    private Button mBtnCancel;
    private Button mBtnConfirm;
    private Chronometer mChronometer;
    private DonutProgress mViewProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        boolean isRecording = getIntent().getBooleanExtra(EXTRA_IS_RECORDING, false);
        if (TextUtils.isEmpty(videoPath)) {
            finish();
        }
        setContentView(R.layout.activity_play_video);
        initViews();

        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mBtnPlay.setVisibility(View.VISIBLE);
                mBtnPause.setVisibility(View.INVISIBLE);
                mChronometer.stop();
            }
        });
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                showToast("播放失败");
                mBtnPlay.setVisibility(View.VISIBLE);
                mBtnPause.setVisibility(View.INVISIBLE);
                mChronometer.stop();
                return false;
            }
        });

        if (isRecording) {
            mBtnCancel.setVisibility(View.VISIBLE);
            mBtnCancel.setText(R.string.re_record);
            mBtnConfirm.setVisibility(View.VISIBLE);
        } else {
            mBtnCancel.setVisibility(View.VISIBLE);
            mBtnCancel.setText(R.string.back);
            mBtnConfirm.setVisibility(View.GONE);
        }

        // 判断是否
        if (videoPath.contains("http")) {
            mViewProgress.setVisibility(View.VISIBLE);
            MediaLoader.loadVideo(videoPath, new MediaLoader.OnResponseListener() {
                @Override
                public void onSuccess(String path) {
                    mViewProgress.setVisibility(View.INVISIBLE);
                    mVideoView.setVideoPath(path);
                    mVideoView.start();
                    mChronometer.setBase(SystemClock.elapsedRealtime());
                    mChronometer.start();
                    mBtnPlay.setVisibility(View.INVISIBLE);
                    mBtnPause.setVisibility(View.VISIBLE);
                }

                @Override
                public void onError() {
                    mViewProgress.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onProgress(int progress) {
                    mViewProgress.setProgress(progress);
                }
            });
        } else {
            mVideoView.setVideoPath(videoPath);
            mVideoView.start();
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.start();
            mBtnPlay.setVisibility(View.INVISIBLE);
            mBtnPause.setVisibility(View.VISIBLE);
        }
    }

    private void initViews() {
        mVideoView = (VideoView) findViewById(R.id.video_preview);
        mBtnPlay = (ImageButton) findViewById(R.id.btn_play);
        mBtnPause = (ImageButton) findViewById(R.id.btn_pause);
        mBtnCancel = (Button) findViewById(R.id.btn_cancel);
        mBtnConfirm = (Button) findViewById(R.id.btn_select);
        mViewProgress = (DonutProgress) findViewById(R.id.view_progress);
        mChronometer = (Chronometer) findViewById(R.id.chronometer_record_time);

        mBtnPlay.setOnClickListener(this);
        mBtnPause.setOnClickListener(this);
        mBtnCancel.setOnClickListener(this);
        mBtnConfirm.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_play) {
            mBtnPlay.setVisibility(View.INVISIBLE);
            mBtnPause.setVisibility(View.VISIBLE);
            mVideoView.start();
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.start();
        } else if (i == R.id.btn_pause) {
            mBtnPlay.setVisibility(View.VISIBLE);
            mBtnPause.setVisibility(View.INVISIBLE);
            if (mVideoView.isPlaying()) {
                mVideoView.pause();
            }
        } else if (i == R.id.btn_cancel) {
            setResult(RESULT_CANCELED);
            finish();
        } else if (i == R.id.btn_select) {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        mVideoView.stopPlayback();
        super.onDestroy();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
