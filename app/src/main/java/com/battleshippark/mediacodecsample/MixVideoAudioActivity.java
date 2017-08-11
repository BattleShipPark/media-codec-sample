package com.battleshippark.mediacodecsample;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

public class MixVideoAudioActivity extends Activity {
    private static final String VIDEO = "video/";
    private static final String AUDIO = "audio/";
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;

    private TextView textview;
    private AssetFileDescriptor videoFd, audioFd;
    private MediaExtractor videoExtractor, audioExtractor;
    private MediaMuxer muxer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mix_video_audio);

        textview = (TextView) findViewById(R.id.textView);
        setText("prepare");

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                mix();
            }
        });
    }

    private void mix() {
        try {
            prepare();

            mixVideoAudio();

            setText("release");
            release();

            setText("end");
        } catch (IOException e) {
            setText("fail");
            e.printStackTrace();
        }
    }

    private void setText(final String msg) {
        textview.post(new Runnable() {
            @Override
            public void run() {
                textview.setText(textview.getText() + "\n" + msg);
            }
        });
    }

    private void prepare() throws IOException {
        videoFd = getResources().openRawResourceFd(R.raw.dizzy);
        videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoFd.getFileDescriptor(), videoFd.getStartOffset(), videoFd.getLength());

        audioFd = getResources().openRawResourceFd(R.raw.mp3_short);
        audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(audioFd.getFileDescriptor(), audioFd.getStartOffset(), audioFd.getLength());

        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/mix.mp4");
        muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private void mixVideoAudio() {
        int videoTrack = -1, audioTrack = -1;
        for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(VIDEO)) {
                videoExtractor.selectTrack(i);
                videoTrack = muxer.addTrack(format);
            }
        }

        for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
            MediaFormat format = audioExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(AUDIO)) {
                audioExtractor.selectTrack(i);
                audioTrack = muxer.addTrack(format);
            }
        }

        muxer.start();

        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int offset = 0;

        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = videoExtractor.readSampleData(dstBuf, offset);

            if (bufferInfo.size < 0) {
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                bufferInfo.flags = videoExtractor.getSampleFlags();

                muxer.writeSampleData(videoTrack, dstBuf, bufferInfo);
                videoExtractor.advance();
            }
        }

        boolean sawEOS2 = false;
        while (!sawEOS2) {
            bufferInfo.offset = offset;
            bufferInfo.size = audioExtractor.readSampleData(dstBuf, offset);

            if (bufferInfo.size < 0) {
                sawEOS2 = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                bufferInfo.flags = audioExtractor.getSampleFlags();
                muxer.writeSampleData(audioTrack, dstBuf, bufferInfo);
                audioExtractor.advance();
            }
        }
    }

    private void release() throws IOException {
        videoFd.close();
        audioFd.close();

        videoExtractor.release();
        audioExtractor.release();

        muxer.stop();
        muxer.release();
    }
}
