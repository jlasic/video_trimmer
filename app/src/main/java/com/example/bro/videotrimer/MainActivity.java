package com.example.bro.videotrimer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;


public class MainActivity extends Activity {

    private static final int PICK_VIDEO_REQUEST = 1001;
    private static final int TRIM_VIDEO_REQUEST = 1002;
    private static final String TAG = "MainActivity";
    private Uri mVideoUri;

    private EditText minLength;
    private EditText maxLength;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //if resuming app with launcher icon
        if (!isTaskRoot()
                && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)
                && getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_MAIN)) {

            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        minLength = (EditText) findViewById(R.id.minLen);
        maxLength = (EditText) findViewById(R.id.maxLen);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == RESULT_OK) {
            Log.d(TAG, "Got video " + data.getData());
            mVideoUri = data.getData();
        }
        if (requestCode == TRIM_VIDEO_REQUEST) {
            int startTime = data.getIntExtra("startTime",-1);
            int endTime = data.getIntExtra("endTime", -1);
            TextView tv = (TextView)findViewById(R.id.startTime);
            tv.setText(""+startTime);
            tv = (TextView)findViewById(R.id.endTime);
            tv.setText(""+endTime);
        }
    }

    public void selectVideo(View view) {
        Intent pickVideo = new Intent(Intent.ACTION_PICK);
        pickVideo.setTypeAndNormalize("video/*");
        startActivityForResult(pickVideo, PICK_VIDEO_REQUEST);
    }

    public void trimVideo(View view){
        int min, max;       //milliseconds
        try{
            min = Integer.parseInt(minLength.getText().toString());
            max = Integer.parseInt(maxLength.getText().toString());
        }catch (Exception e){
            return;
        }
        if (mVideoUri != null) {
            VideoTrimmerActivity vt = new VideoTrimmerActivity();
            Intent a = new Intent(this, vt.getClass());
            Intent intent = new Intent(this, VideoTrimmerActivity.class);
            intent.putExtra("minLen", min * 1000);
            intent.putExtra("maxLen", max * 1000);
            intent.putExtra("URI", mVideoUri.toString());
            startActivityForResult(intent, TRIM_VIDEO_REQUEST);
        }
    }

}