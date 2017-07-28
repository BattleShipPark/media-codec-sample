package com.battleshippark.mediacodecsample;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Executors;

import static junit.framework.Assert.fail;

public class ImageToVideoActivity extends Activity {

    private static final String TAG = ImageToVideoActivity.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 15;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int NUM_FRAMES = FRAME_RATE * 5;   // total frames of video
    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private int mColorFormat;
    private int mTrackIndex;
    private int width, height;
    private TextView textview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_to_video);

        textview = (TextView) findViewById(R.id.textView);
        setText("prepare");

        width = 540;
        height = 960;

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                encode();
            }
        });
    }

    private void encode() {
        try {
            prepare();

            setText("encode");
            encodeFrames();

            setText("release");
            release();
        } catch (Exception e) {
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

    private void release() {
        mEncoder.stop();
        mEncoder.release();

        mMuxer.stop();
        mMuxer.release();
    }

    private void encodeFrames() {
        int frameIndex = 0;
        byte[] frameData = new byte[width * height * 3 / 2];
        boolean inputDone = false;
        boolean outputDone = false;

        while (!outputDone) {
            if (!inputDone) {
                int inputIndex = mEncoder.dequeueInputBuffer(-1);
                if (inputIndex >= 0) {
                    long ptsUsec = computePresentationTime(frameIndex);
                    if (frameIndex >= NUM_FRAMES) {
                        mEncoder.queueInputBuffer(inputIndex, 0, 0, ptsUsec, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        generateFrame(frameIndex, mColorFormat, frameData);

                        ByteBuffer buffer = mEncoder.getInputBuffer(inputIndex);
                        buffer.put(frameData);
                        // tell the decoder to process the frame
                        mEncoder.queueInputBuffer(inputIndex, 0, frameData.length, ptsUsec, 0);

                        frameIndex++;
                    }
                }
            }

            if (!outputDone) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputIndex = mEncoder.dequeueOutputBuffer(info, -1);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mEncoder.getOutputFormat();
                    mTrackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                } else {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                        ByteBuffer encodedData = mEncoder.getOutputBuffer(outputIndex);
                        mMuxer.writeSampleData(mTrackIndex, encodedData, info);
                        mEncoder.releaseOutputBuffer(outputIndex, false);
                    } else {
                        outputDone = true;
                    }
                }
            }
        }
    }

    private long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }

    /**
     * Generates data for frame N into the supplied buffer.  We have an 8-frame animation
     * sequence that wraps around.  It looks like this:
     * <pre>
     *   0 1 2 3
     *   7 6 5 4
     * </pre>
     * We draw one of the eight rectangles and leave the rest set to the zero-fill color.
     */
    private void generateFrame(int frameIndex, int colorFormat, byte[] frameData) {
        final int HALF_WIDTH = width / 2;
        boolean semiPlanar = isSemiPlanarYUV(colorFormat);

        // Set to zero.  In YUV this is a dull green.
        Arrays.fill(frameData, (byte) 0);

        int startX, startY, countX, countY;

        frameIndex %= 8;
        //frameIndex = (frameIndex / 8) % 8;    // use this instead for debug -- easier to see
        if (frameIndex < 4) {
            startX = frameIndex * (width / 4);
            startY = 0;
        } else {
            startX = (7 - frameIndex) * (width / 4);
            startY = width / 2;
        }

        for (int y = startY + (width / 2) - 1; y >= startY; --y) {
            for (int x = startX + (width / 4) - 1; x >= startX; --x) {
                if (semiPlanar) {
                    // full-size Y, followed by UV pairs at half resolution
                    // e.g. Nexus 4 OMX.qcom.video.encoder.avc COLOR_FormatYUV420SemiPlanar
                    // e.g. Galaxy Nexus OMX.TI.DUCATI1.VIDEO.H264E
                    //        OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
                    frameData[y * width + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[width * width + y * HALF_WIDTH + x] = (byte) TEST_U;
                        frameData[width * width + y * HALF_WIDTH + x + 1] = (byte) TEST_V;
                    }
                } else {
                    // full-size Y, followed by quarter-size U and quarter-size V
                    // e.g. Nexus 10 OMX.Exynos.AVC.Encoder COLOR_FormatYUV420Planar
                    // e.g. Nexus 7 OMX.Nvidia.h264.encoder COLOR_FormatYUV420Planar
                    frameData[y * width + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[width * width + (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_U;
                        frameData[width * width + HALF_WIDTH * (width / 2) +
                                (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_V;
                    }
                }
            }
        }
    }

    private boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

    private void prepare() throws IOException {
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            fail("no codec");
            return;
        }

        mColorFormat = selectColorFormat(codecInfo, MIME_TYPE);

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        // Create a MediaCodec for the desired codec, then configure it as an encoder with
        // our desired properties.
        mEncoder = MediaCodec.createByCodecName(codecInfo.getName());
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();

        mMuxer = new MediaMuxer(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "toVideo.mp4").getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        fail("couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }
}
