package com.example.bro.videotrimer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

import static android.R.attr.data;
import static android.R.attr.width;
import static android.icu.lang.UCharacter.GraphemeClusterBreak.L;


/**
 * Created by Bro on 04/03/2017.
 */

public class TimelineFramesUpdater {
    private final static String TAG = "TimelineFramesUpdater";

    private Context mContext;
    private LinearLayout mTimelineLinearLayout;

    private int mMaxNumberOfBlocks;
    private int mMaxBlockSize;
    private int mMSperFrame;
    private int mNumberOfFrames;
    private int mVideoDuration;
    private int mFrameWidthPX;

    private MediaMetadataRetriever mRetriever;
    private LinkedList<LinkedList<AsyncUpdate>> mAsyncUpdates;
    private LinkedList<Integer> mUpdatedBlocksStartTimes;

    public TimelineFramesUpdater(Context context, LinearLayout rootViewGroup, int maxNumberOfBlocks, int videoImagesBlockSize,
                                 int totalNumberOfFrames, int stepMS, int videoDuration,
                                 MediaMetadataRetriever retriever, int frameWidthPX){

        mContext=context;
        mTimelineLinearLayout=rootViewGroup;
        mFrameWidthPX = frameWidthPX;
        mMaxNumberOfBlocks = maxNumberOfBlocks;
        mMaxBlockSize = videoImagesBlockSize;
        mNumberOfFrames = totalNumberOfFrames;
        mMSperFrame = stepMS;
        mVideoDuration = videoDuration;
        mRetriever = retriever;
        mAsyncUpdates = new LinkedList<>();
        mUpdatedBlocksStartTimes = new LinkedList<>();
        update(0);

    }
    public void update (int timeMS){
        int normalizedTime = (((int) (((double)timeMS /((double) mMaxBlockSize * mMSperFrame))))*mMaxBlockSize*mMSperFrame);
        if (normalizedTime>mVideoDuration)
            return;

        for (int i = 0; i<mUpdatedBlocksStartTimes.size(); i++){
            if (mUpdatedBlocksStartTimes.get(i) == normalizedTime) {  //already loaded (or loading)
                return;
            }
        }
        if (mUpdatedBlocksStartTimes.size() < mMaxNumberOfBlocks) {
            startAsync(normalizedTime);
        }
        else {
            removeAsync();
            startAsync(normalizedTime);
        }
    }
    private void startAsync(int normalizedTime){
        mUpdatedBlocksStartTimes.add(normalizedTime);
        int time = normalizedTime;
        LinkedList<AsyncUpdate> asyncBits = new LinkedList<>();

        for(int i = 0; i<mMaxBlockSize; i++){
            if(time>mVideoDuration)
                break;
            AsyncUpdate bit = new AsyncUpdate();
            asyncBits.add(bit);
            bit.execute(time);
            time += mMSperFrame;
        }

        mAsyncUpdates.add(asyncBits);
    }
    private void removeAsync(){
        int normalizedTime = mUpdatedBlocksStartTimes.pollFirst();

        int asyncBitsSize = mAsyncUpdates.get(0).size();
        for (int i = 0; i<asyncBitsSize; i++){
            if (normalizedTime>mVideoDuration)
                break;
            AsyncUpdate a = mAsyncUpdates.get(0).pollFirst();
            try {
                a.cancel(true);
            }catch (Exception e){}
            int index = getFrameIndex(normalizedTime);
            ImageView iv = (ImageView) mTimelineLinearLayout.findViewById(index);
            if (iv != null)
                iv.setImageDrawable(null);
            normalizedTime += mMSperFrame;
        }
        mAsyncUpdates.pollFirst();
    }

    public void cancel(){
        while (mAsyncUpdates.size() > 0)
            removeAsync();

    }

    private int getFrameIndex(int normalizedTime){
        return (int) ((double)normalizedTime/(double) mMSperFrame);
    }

    private class ReturnData{
        public int frameIndex;
        public Bitmap bitmap;
        ReturnData(int index){
            frameIndex = index;
        }
    }
    private class AsyncUpdate extends AsyncTask<Object, Void, ReturnData>{
        @Override
        protected ReturnData doInBackground(Object... params) {
            int time = (int) params[0];
            int index = (int) ((double)time/(double) mMSperFrame);

            ReturnData data = new ReturnData(index);

            Bitmap b = mRetriever.getFrameAtTime(1000* (long)time, MediaMetadataRetriever.OPTION_CLOSEST);

            if (b == null){
                Log.d("Timeline", "NEMA");
                return data;
            }

            int width = b.getWidth();
            int height = b.getHeight();
            double scale;

            if (height > width){
                scale = (double) height / (double) width;
                width = mFrameWidthPX;
                height = (int)(width*scale);
            }
            else if (width > height){
                scale = (double) width / (double) height;
                height = mFrameWidthPX;
                width = (int)(height*scale);
            }
            else{
                width = height = mFrameWidthPX;
            }
            data.bitmap = android.graphics.Bitmap.createScaledBitmap(b, width,height, true);

            return data;
        }

        @Override
        protected void onPostExecute(ReturnData data) {
            ImageView iv = (ImageView) mTimelineLinearLayout.findViewById(data.frameIndex);
            if(iv!=null && data.bitmap!=null)
                iv.setImageBitmap(data.bitmap);
        }
    }
}

