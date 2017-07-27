package com.battleshippark.mediacodecsample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 23) {
            if (PackageManager.PERMISSION_DENIED == checkSelfPermission(Manifest.permission.CAMERA)) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                finish();
            }
        }
    }

    public void onCameraToMpeg(View view) {
        startActivity(new Intent(this, CameraToMpegActivity.class));
    }

    public void onDecoder(View view) {
        startActivity(new Intent(this, DecoderActivity.class));
    }

    public void onTranscoder(View view) {
        startActivity(new Intent(this, TranscoderActivity.class));
    }
}
