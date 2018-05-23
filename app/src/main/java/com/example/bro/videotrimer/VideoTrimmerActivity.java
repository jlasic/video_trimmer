package com.example.bro.videotrimer;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.example.bro.videotrimer.RangeSeekbar.CrystalRangeSeekbar;
import com.example.bro.videotrimer.RangeSeekbar.OnRangeSeekbarChangeListener;
import com.example.bro.videotrimer.RangeSeekbar.OnRangeSeekbarFinalValueListener;


public class VideoTrimmerActivity extends Activity {

    private static final String TAG = "VideoTrimmerActivity";

    private boolean mSurfaceActive = false;
    private boolean mTimelineSet = false;

    //time in milliseconds
    private int mMinLen;    //<0 -> mMinLen = 0;
    private int mMaxLen;    //>videoDuration || <0 ->mMaxLen = videoDuration


    //
    private static final int videoImagesBlockSize = 50;
    private static final int maxNumberOfBlocks = 4;

    //framesWidthDP same as video_container height
    private static final int framesWidthDP = 70;

    //mTimelinePadding change in activity_video_trimmer as well
    private static final int mTimelinePadding = 16;

    private Uri mVideoUri;
    private MediaPlayer mMediaPlayer;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceView mSurfaceView;

    private double PPI;
    private int mScreenWidth;

    //for calculating start and end times
    private int mTimelineWidthPX;
    private int mTimelinePositionPX = 0;
    private int mTimelineOffsetPX = 0;
    private int mTimelineEndOffsetPX = 0;
    private double msPerPixel;

    private boolean canChange = true;
    private TimelineFramesUpdater mTimelineFramesUpdater;

    private int FAILED_SYNCS;
    private int mPlayPosition;
    private boolean mPlay = true;

    private Handler mSyncVideoHandler;
    private Runnable mSyncVideo = new Runnable() {
        @Override
        public void run(){
            if (mMediaPlayer.getCurrentPosition() <= mPlayPosition) {
                mSyncVideoHandler.postDelayed(this, 20);
            }
            else if(mMediaPlayer.getCurrentPosition() > mPlayPosition+200){
                FAILED_SYNCS+=1;
                Log.d(TAG, "finding prev keyframe "+FAILED_SYNCS);
                mMediaPlayer.seekTo(mPlayPosition-FAILED_SYNCS*300);
            }
            else {
                mMediaPlayer.setVolume(1, 1);
                ProgressBar spinner = (ProgressBar) findViewById(R.id.progressSpinner);
                spinner.setVisibility(View.GONE);
                mProgressHandler = new Handler();
                new Thread(mProgressUpdate).start();
            }
        }
    };
    private Runnable mProgressUpdate;
    private Handler mProgressHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_trimmer);
        setResult(RESULT_CANCELED, new Intent());

        Bundle b = getIntent().getExtras();
        if(b != null){
            mMinLen = b.getInt("minLen");
            mMaxLen = b.getInt("maxLen");
            mVideoUri = Uri.parse(b.getString("URI"));
        }
        else
            finish();
        Log.d(TAG, mVideoUri.toString());

        requestPermission();

        //set aspect ratio 1:1
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        mScreenWidth = displayMetrics.widthPixels;
        RelativeLayout rl = (RelativeLayout) findViewById(R.id.video_container);
        rl.setLayoutParams(new LinearLayout.LayoutParams(mScreenWidth, mScreenWidth));


        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                Log.d(TAG, "SURFACE CREATED");
                mSurfaceHolder = surfaceHolder;

                prepareMediaPlayer();

                if (mTimelineSet == false) {
                    //resize SurfaceView if needed
                    resizeSurfaceView();

                    setupTimeline();
                    mTimelineSet = true;
                }
                mSurfaceActive = true;
                resumeVideo();
            }
            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
                Log.d(TAG, "SURFACE changed");
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                Log.d(TAG, "SURFACE destroyed");
                mSurfaceActive = false;
                if (mMediaPlayer != null) {
                    Log.d(TAG, "RELEASE");
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
            }
        });
    }

    private void prepareMediaPlayer(){
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(VideoTrimmerActivity.this, mVideoUri);
            mMediaPlayer.prepare();
            mMediaPlayer.setDisplay(mSurfaceHolder);
        }catch (Exception e){
            Log.e(TAG, "Video init: " + e.toString());
            finish();
            return;
        }
        mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                if (mPlay) {
                    pauseVideo();
                    mMediaPlayer.setVolume(0,0);
                    mMediaPlayer.start();
                    ProgressBar spinner = (ProgressBar) findViewById(R.id.progressSpinner);
                    spinner.setVisibility(View.VISIBLE);
                    mSyncVideoHandler = new Handler();
                    mSyncVideoHandler.postDelayed(mSyncVideo, 50);
                }
            }
        });
    }
    private void resizeSurfaceView(){
        int videoHeight = mMediaPlayer.getVideoHeight();
        int videoWidth = mMediaPlayer.getVideoWidth();
        float videoProportion = (float) videoWidth / (float) videoHeight;
        if (videoProportion != 1){
            android.view.ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
            if (videoProportion > 1) {
                lp.width = mScreenWidth;
                lp.height = (int) ((float) mScreenWidth / videoProportion);
            } else {
                lp.width = (int) (videoProportion * (float) mScreenWidth);
                lp.height = mScreenWidth;
            }
            // Commit the layout parameters
            mSurfaceView.setLayoutParams(lp);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //trigger surfaceHolder surfaceCreated
        mSurfaceView.setVisibility(View.VISIBLE);
    }
    @Override
    protected void onPause() {
        super.onPause();

        pauseVideo();
        //trigger surfaceHolder surfaceDestroyed
        mSurfaceView.setVisibility(View.GONE);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        pauseVideo();

        if (mTimelineFramesUpdater != null) {
            mTimelineFramesUpdater.cancel();
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void setupTimeline(){
        //set video_frames_container width
        Log.d(TAG, "SETUP");
        PPI = this.getResources().getDisplayMetrics().density;
        int pixels = (int) (mTimelinePadding * PPI);

        int duration = mMediaPlayer.getDuration();
        mTimelineWidthPX = mScreenWidth - 2*pixels;

        //testing min and max video length
        if (mMaxLen > duration || mMaxLen <= 0)
            mMaxLen = duration;
        if (mMinLen <0 || mMinLen>=mMaxLen)
            mMinLen = 0;

        double relWidth = (double) duration / (double) mMaxLen;
        RelativeLayout rl = (RelativeLayout) findViewById(R.id.video_frames_container);
        android.view.ViewGroup.LayoutParams lp = rl.getLayoutParams();
        lp.width = (int) (relWidth * (double)mTimelineWidthPX);
        rl.setLayoutParams(lp);

        msPerPixel =  (double)duration / (double)lp.width;

        fillFramesPlaceholders();

        setTimelineScrollListeners();
        setTimelineLimiterListeners();

        final TimelineProgressUpdater progressUpdater = (TimelineProgressUpdater) findViewById(R.id.progressUpdater);
        mProgressHandler = new Handler();
        mProgressUpdate = new Runnable() {
            @Override
            public void run() {
                if (mMediaPlayer.isPlaying()) {
                    progressUpdater.setProgress(
                            (float) ((int)((double)mMediaPlayer.getCurrentPosition() / msPerPixel) - mTimelinePositionPX) / (float) mTimelineWidthPX
                    );
                }
                if (!mMediaPlayer.isPlaying() ||
                        (mMediaPlayer.getCurrentPosition()-(int)((mTimelinePositionPX + mTimelineWidthPX - mTimelineEndOffsetPX)*msPerPixel))>=0)
                    resumeVideo();

                else if (mProgressHandler != null)
                    mProgressHandler.postDelayed(this, 20);
            }
        };
    }
    private void setTimelineScrollListeners(){
        //Timeline scroll listeners
        final MyScrollView scrollView = (MyScrollView) findViewById(R.id.scrollView);
        scrollView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    scrollView.startScrollerTask();
                }
                else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    pauseVideo();
                }
                return false;
            }
        });
        scrollView.setOnScrollStoppedListener(new MyScrollView.OnScrollStoppedListener() {
            public void onScrollStopped() {
                mTimelinePositionPX = scrollView.getScrollX();

                mTimelineFramesUpdater.update((int)((mTimelinePositionPX+mTimelineWidthPX)*msPerPixel));

                resumeVideo();
            }
        });
    }
    private void setTimelineLimiterListeners(){
        //timeline limiter listeners
        final CrystalRangeSeekbar rangeSeekbar = (CrystalRangeSeekbar) findViewById(R.id.rangeSeekbar);
        rangeSeekbar.setGap(100f * (float) mMinLen / (float) mMaxLen);
        rangeSeekbar.setOnRangeSeekbarChangeListener(new OnRangeSeekbarChangeListener() {
            @Override
            public void valueChanged(Number minValue, Number maxValue) {
                int thumb = rangeSeekbar.pressedThumb();  // Integer.MIN_VALUE, Integer.MAX_VALUE, 0-skip
                if (thumb == 0)
                    return;

                pauseVideo();

                if (!canChange)
                    return;

                int time;
                if (thumb == Integer.MIN_VALUE)
                    time =  (int) (((minValue.doubleValue() * (float) mTimelineWidthPX) + mTimelinePositionPX) * msPerPixel);

                else
                    time =  (int) (((maxValue.doubleValue() * (float) mTimelineWidthPX) + mTimelinePositionPX) * msPerPixel);

                mPlay = false;
                mMediaPlayer.seekTo(time);

                canChange = false;
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        canChange = true;
                    }
                };
                Handler handler = new Handler();
                handler.postDelayed(runnable, 100);
            }
        });
        rangeSeekbar.setOnRangeSeekbarFinalValueListener(new OnRangeSeekbarFinalValueListener() {
            @Override
            public void finalValue(Number minValue, Number maxValue) {
                mTimelineOffsetPX = (int) (minValue.doubleValue()* (double) mTimelineWidthPX);
                mTimelineEndOffsetPX = mTimelineWidthPX - (int) (maxValue.doubleValue() * (double) mTimelineWidthPX);
                resumeVideo();
            }
        });
    }

    private void pauseVideo(){
        if (mSyncVideoHandler != null){
            mSyncVideoHandler.removeCallbacks(mSyncVideo);
            mSyncVideoHandler = null;
        }
        if (mProgressHandler != null) {
            mProgressHandler.removeCallbacks(mProgressUpdate);
            mProgressHandler = null;
            TimelineProgressUpdater progressUpdater = (TimelineProgressUpdater) findViewById(R.id.progressUpdater);
            progressUpdater.cancle();
        }
        if (mMediaPlayer != null && mMediaPlayer.isPlaying())
            mMediaPlayer.pause();
    }
    private void resumeVideo(){
        if (!mSurfaceActive)
            return;

        Log.i(TAG, "resume: " + (mTimelinePositionPX + mTimelineOffsetPX) * msPerPixel + " length: " +
                ((mTimelineWidthPX - mTimelineOffsetPX - mTimelineEndOffsetPX)*msPerPixel));
        try {
            mPlay = true;
            FAILED_SYNCS = 0;
            mMediaPlayer.setVolume(0,0);
            mPlayPosition = (int) ((mTimelinePositionPX + mTimelineOffsetPX) * msPerPixel);
            mMediaPlayer.seekTo(mPlayPosition);
        }catch (Exception e){
            Log.e(TAG, "seek failed" + e.toString());
        }
    }

    private void fillFramesPlaceholders(){
        final LinearLayout linear = (LinearLayout) findViewById(R.id.video_frames);
        final RelativeLayout relative = (RelativeLayout) findViewById(R.id.video_frames_container);
        android.view.ViewGroup.LayoutParams relParam = relative.getLayoutParams();

        int pixelsDelta = (int) (framesWidthDP * PPI);
        int cout = (int)((double)relParam.width / ((double) pixelsDelta));
        for (int i = 0; i<cout; i++)
            createImageView(pixelsDelta, pixelsDelta, linear, i);

        createImageView(relParam.width-cout*pixelsDelta, pixelsDelta, linear, cout);

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(getApplicationContext(), mVideoUri);

        mTimelineFramesUpdater = new TimelineFramesUpdater(getApplicationContext(),linear,
                maxNumberOfBlocks,videoImagesBlockSize,linear.getChildCount(),(int)((double)pixelsDelta*msPerPixel),
                mMediaPlayer.getDuration(),retriever,pixelsDelta);

    }
    private ImageView createImageView(int width, int height, LinearLayout linearLayout, int index){
        ImageView iv = new ImageView(getApplicationContext());
        iv.setLayoutParams(new android.view.ViewGroup.LayoutParams(width,height));
//        iv.setBackgroundColor((0xFF000000));
        iv.setBackground(getResources().getDrawable(R.drawable.loading_loader));
        iv.setId(index);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        linearLayout.addView(iv);
        return iv;
    }

    private void requestPermission(){
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 100111);
    }

    public void sendResult(View view){
        Intent data = new Intent();

        //send result back to main activity
        data.putExtra("startTime", (int) ((mTimelinePositionPX + mTimelineOffsetPX) * msPerPixel));
        data.putExtra("endTime", (int)((mTimelinePositionPX + mTimelineWidthPX - mTimelineEndOffsetPX)*msPerPixel));
        setResult(RESULT_OK, data);
        finish();
    }
}
