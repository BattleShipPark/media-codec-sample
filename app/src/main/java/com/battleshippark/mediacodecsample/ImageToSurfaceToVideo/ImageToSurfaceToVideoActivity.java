package com.battleshippark.mediacodecsample.ImageToSurfaceToVideo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.battleshippark.mediacodecsample.R;

import java.io.IOException;
import java.util.concurrent.Executors;

public class ImageToSurfaceToVideoActivity extends Activity {

    private TextView textview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_to_surface_to_video);

        textview = (TextView) findViewById(R.id.textView);
        setText("start");

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                DecodeEditEncodeTest test = new DecodeEditEncodeTest();
                test.setParameters(1280, 720, 6000000);
                try {
                    test.videoEditTest();
                    setText("end");
                } catch (IOException e) {
                    setText("fail");
                    e.printStackTrace();
                }
            }
        });
    }

    private void setText(final String msg) {
        textview.post(new Runnable() {
            @Override
            public void run() {
                textview.setText(textview.getText() + "\n" + msg);
            }
        });
    }
}
