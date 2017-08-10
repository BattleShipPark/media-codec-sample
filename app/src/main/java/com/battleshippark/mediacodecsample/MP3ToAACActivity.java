package com.battleshippark.mediacodecsample;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

public class MP3ToAACActivity extends Activity {
    private static final String TAG = MP3ToAACActivity.class.getSimpleName();
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private MediaExtractor extractor;
    private MediaFormat audioFormat;
    private MediaCodec mAudioEncoder;
    private MediaMuxer mMuxer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mp3_to_aac);

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    prepare();
                    encode();
                    release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void prepare() throws IOException {
        extractor = new MediaExtractor();
        AssetFileDescriptor srcFd = getResources().openRawResourceFd(R.raw.bg);
        extractor.setDataSource(srcFd.getFileDescriptor(), srcFd.getStartOffset(), srcFd.getLength());
        MediaFormat inputFormat = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            inputFormat = extractor.getTrackFormat(i);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                extractor.selectTrack(i);
            }
        }

        audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);//inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);//inputFormat.getInteger("bit-rate"));
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);//MAX_SAMPLE_SIZE);

        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();

        try {
            String path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "transcoded.m4a").getAbsolutePath();
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }

    private void encode() {
        boolean extractDone = false, muxDone = false;
        int trackIndex = -1;

        while (!muxDone) {
            if (!extractDone) {
                int inputIndex = mAudioEncoder.dequeueInputBuffer(-1);
                if (inputIndex >= 0) {
                    ByteBuffer dstBuf = mAudioEncoder.getInputBuffer(inputIndex);
                    dstBuf.clear();
                    int sampleSize = extractor.readSampleData(dstBuf, 0);

                    if (extractor.advance() && sampleSize > 0) {
                        mAudioEncoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        Log.d(TAG, "InputBuffer " + sampleSize + " in " + extractor.getSampleTime());
                    } else {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        mAudioEncoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        extractDone = true;
                    }
                }
            }

            if (!muxDone) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputIndex = mAudioEncoder.dequeueOutputBuffer(info, 1000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                    trackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                } else if (outputIndex < 0) {
                    Log.d(TAG, "OutputBuffer error " + outputIndex);
                } else {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                        ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(outputIndex);
                        mMuxer.writeSampleData(trackIndex, encodedData, info);
                        mAudioEncoder.releaseOutputBuffer(outputIndex, false);
                        Log.d(TAG, "OutputBuffer release " + encodedData.capacity());
                    } else {
                        muxDone = true;
                    }
                }
            }
        }
    }

    private void release() {
        mAudioEncoder.stop();
        mAudioEncoder.release();

        mMuxer.stop();
        mMuxer.release();
    }
}
