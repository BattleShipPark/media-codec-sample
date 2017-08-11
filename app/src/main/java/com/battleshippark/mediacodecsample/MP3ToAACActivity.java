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
    private static final String INPUT_AUDIO_MIME_TYPE = "audio/mpeg";
    private static final String OUTPUT_AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int MAX_SAMPLE_SIZE = 16 * 1024;
    private MediaExtractor extractor;
    private MediaCodec decoder, encoder;
    private MediaMuxer muxer;
    private int trackIndex;
    private long lastPresentationTimeUs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mp3_to_aac);

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    prepare();
                    boolean decodeDone = false;
                    boolean encodeDone = false, muxDone = false;
                    while (!muxDone) {
                        if (!decodeDone) {
                            decodeDone = decode();
                        }
                        if (!encodeDone) {
                            encodeDone = encode();
                        }
                        muxDone = mux();
                    }
                    release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void prepare() throws IOException {
        extractor = new MediaExtractor();
        AssetFileDescriptor srcFd = getResources().openRawResourceFd(R.raw.mp3_medium);
        extractor.setDataSource(srcFd.getFileDescriptor(), srcFd.getStartOffset(), srcFd.getLength());
        int sampleRate = -1, channelCount = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat inputFormat = extractor.getTrackFormat(i);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                extractor.selectTrack(i);
                sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                break;
            }
        }

        MediaFormat decoderFormat = MediaFormat.createAudioFormat(INPUT_AUDIO_MIME_TYPE, sampleRate, channelCount);
        decoder = MediaCodec.createDecoderByType(INPUT_AUDIO_MIME_TYPE);
        decoder.configure(decoderFormat, null, null, 0);
        decoder.start();

        MediaFormat encoderFormat = MediaFormat.createAudioFormat(OUTPUT_AUDIO_MIME_TYPE, sampleRate, channelCount);
        encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        encoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_SAMPLE_SIZE);

        encoder = MediaCodec.createEncoderByType(OUTPUT_AUDIO_MIME_TYPE);
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        try {
            String path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "transcoded.m4a").getAbsolutePath();
            muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }

    int decodeCount, encodeCount, muxCount = -1;

    private boolean decode() {
        int inputIndex = decoder.dequeueInputBuffer(1000);
        if (inputIndex >= 0) {
            ByteBuffer dstBuf = decoder.getInputBuffer(inputIndex);
            int sampleSize = extractor.readSampleData(dstBuf, 0);

            if (extractor.advance() && sampleSize > 0) {
                decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                dstBuf.clear();
                Log.d(TAG, String.format("Decode InputBuffer: size=%d, count=%d in %d", sampleSize, decodeCount++, extractor.getSampleTime()));
            } else {
                Log.d(TAG, "Decode InputBuffer BUFFER_FLAG_END_OF_STREAM");
                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return true;
            }
        }
        return false;
    }

    private boolean encode() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputIndex = decoder.dequeueOutputBuffer(info, 100);
        if (outputIndex < 0) {
            Log.d(TAG, "Decode OutputBuffer error " + outputIndex);
        } else {
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                ByteBuffer decodedData = decoder.getOutputBuffer(outputIndex);
                encode(decodedData, info.presentationTimeUs);
                decodedData.clear();
                decoder.releaseOutputBuffer(outputIndex, false);
                Log.d(TAG, "Decode OutputBuffer release " + decodedData.limit() + " in " + info.presentationTimeUs);
            } else {
                encode(null, 0);
                return true;
            }
        }
        return false;
    }

    private void encode(ByteBuffer decodedData, long presentationTimeUs) {
        int inputIndex = encoder.dequeueInputBuffer(100);
        if (inputIndex >= 0) {
            if (decodedData == null) {
                Log.d(TAG, "Encode InputBuffer BUFFER_FLAG_END_OF_STREAM");
                encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                ByteBuffer dstBuf = encoder.getInputBuffer(inputIndex);
                dstBuf.clear();
                dstBuf.put(decodedData);

                encoder.queueInputBuffer(inputIndex, 0, dstBuf.position(), presentationTimeUs, 0);
                Log.d(TAG, String.format("Encode InputBuffer: size=%d, count=%d in %d", dstBuf.position(), encodeCount++, presentationTimeUs));
            }
        }
    }

    private boolean mux() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputIndex = encoder.dequeueOutputBuffer(info, 100);
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = encoder.getOutputFormat();
            trackIndex = muxer.addTrack(newFormat);
            muxer.start();
        } else if (outputIndex < 0) {
            Log.d(TAG, "Encode OutputBuffer error " + outputIndex);
        } else {
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                ByteBuffer encodedData = encoder.getOutputBuffer(outputIndex);
                if (lastPresentationTimeUs > info.presentationTimeUs) {
                    Log.d(TAG, "Encode OutputBuffer release " + info.presentationTimeUs + " after " + lastPresentationTimeUs);
                } else {
                    muxer.writeSampleData(trackIndex, encodedData, info);
                    Log.d(TAG, String.format("Encode OutputBuffer: size=%d, count=%d in %d", encodedData.limit(), muxCount++, info.presentationTimeUs));
                    lastPresentationTimeUs = info.presentationTimeUs;
                }
                encoder.releaseOutputBuffer(outputIndex, false);
            } else {
                return true;
            }
        }
        return false;
    }

    private void release() {
        decoder.stop();
        decoder.release();

        encoder.stop();
        encoder.release();

        muxer.stop();
        muxer.release();
    }
}
