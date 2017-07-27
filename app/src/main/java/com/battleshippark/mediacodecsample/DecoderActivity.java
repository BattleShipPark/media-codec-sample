package com.battleshippark.mediacodecsample;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class DecoderActivity extends Activity implements SurfaceHolder.Callback {
    private static final String FILE_PATH = "dizzy.mp4";
    private VideoDecoderThread mVideoDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decoder);

        ((SurfaceView) findViewById(R.id.surface_view)).getHolder().addCallback(this);

        mVideoDecoder = new VideoDecoderThread();
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        if (mVideoDecoder != null) {
            try {
                AssetFileDescriptor afd = getResources().getAssets().openFd(FILE_PATH);
                if (mVideoDecoder.init(surfaceHolder.getSurface(), afd)) {
                    mVideoDecoder.start();
                } else {
                    mVideoDecoder = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (mVideoDecoder != null) {
            mVideoDecoder.close();
        }
    }
}
