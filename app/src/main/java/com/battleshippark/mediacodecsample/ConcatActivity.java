package com.battleshippark.mediacodecsample;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

public class ConcatActivity extends Activity {
    private static final String TAG = "ConcatActivity";
    private static final boolean VERBOSE = true;
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private TextView textview;
    private MediaExtractor extractor1, extractor2;
    private Map<Integer, Integer> indexMap;
    private long prevPresentationTimestampUs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_concat);
        textview = (TextView) findViewById(R.id.textview);
    }

    public void onVideoConcat(View view) {
        textview.setText(textview.getText() + "\nStart to concat video");

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    concatVideo(R.raw.short1, R.raw.short2,
                            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "concat.mp4").getAbsolutePath()
                    );
                    textview.post(new Runnable() {
                        @Override
                        public void run() {
                            textview.setText(textview.getText() + "\nSucceed to concat video");
                        }
                    });
                } catch (final IOException e) {
                    textview.post(new Runnable() {
                        @Override
                        public void run() {
                            textview.setText(textview.getText() + "\nFail to concat video");
                            e.printStackTrace();
                        }
                    });
                }
            }
        });
    }

    private void concatVideo(int srcMedia1, int srcMedia2, String dstMediaPath) throws IOException {
        Log.d(TAG, dstMediaPath);

        MediaMuxer muxer = new MediaMuxer(dstMediaPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        extractor1 = new MediaExtractor();
        extractor2 = new MediaExtractor();

        setupTrack(muxer, extractor1, srcMedia1, extractor2, srcMedia2);

        ByteBuffer dstBuf = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        muxer.start();

        copyToMuxer(muxer, dstBuf, bufferInfo, extractor1);
        copyToMuxer(muxer, dstBuf, bufferInfo, extractor2);

        muxer.stop();
        muxer.release();
    }

    private void setupTrack(MediaMuxer muxer, MediaExtractor extractor1, int srcMedia1, MediaExtractor extractor2, int srcMedia2) throws IOException {
        AssetFileDescriptor srcFd1 = getResources().openRawResourceFd(srcMedia1);
        extractor1.setDataSource(srcFd1.getFileDescriptor(), srcFd1.getStartOffset(),
                srcFd1.getLength());
        srcFd1.close();

        AssetFileDescriptor srcFd2 = getResources().openRawResourceFd(srcMedia2);
        extractor2.setDataSource(srcFd2.getFileDescriptor(), srcFd2.getStartOffset(),
                srcFd2.getLength());
        srcFd2.close();

        indexMap = new HashMap<>(extractor1.getTrackCount());
        for (int i = 0; i < extractor1.getTrackCount(); i++) {
            extractor1.selectTrack(i);
            MediaFormat format = extractor1.getTrackFormat(i);
            int dstIndex = muxer.addTrack(format);
            indexMap.put(i, dstIndex);
        }
        for (int i = 0; i < extractor2.getTrackCount(); i++) {
            extractor2.selectTrack(i);
            // I assume two videos have the same tracks.
//            MediaFormat format = extractor2.getTrackFormat(i);
//            int dstIndex = muxer.addTrack(format);
//            indexMap.put(i, dstIndex);
        }
    }

    private void copyToMuxer(MediaMuxer muxer, ByteBuffer dstBuf, MediaCodec.BufferInfo bufferInfo, MediaExtractor extractor) throws IOException {

        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int frameCount = 0;
        int offset = 0;

        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);

            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = prevPresentationTimestampUs + extractor.getSampleTime();
                bufferInfo.flags = extractor.getSampleFlags();
                int trackIndex = extractor.getSampleTrackIndex();

                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf,
                        bufferInfo);
                extractor.advance();

                frameCount++;
                if (VERBOSE) {
                    Log.d(TAG, "Frame (" + frameCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(KB) " + bufferInfo.size / 1024);
                }
            }
        }
        prevPresentationTimestampUs = bufferInfo.presentationTimeUs;
    }
}
