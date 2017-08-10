# media-codec-sample

CameraToMpegActivity: http://www.bigflake.com/mediacodec/CameraToMpegTest.java.txt
<br>Encode file with surface coming from camera. There is no preview.

VideoToSurfaceActivity: https://github.com/taehwandev/MediaCodecExample/tree/master/src/net/thdev/mediacodecexample/decoder
<br>Decode file to surface view

TranscoderActivity: https://android.googlesource.com/platform/cts/+/kitkat-release/tests/tests/media/src/android/media/cts/MediaMuxerTest.java
<br>Extractor and muxer

ConcatActivity
<br>Concat video-file2 after video-file1

ImageToVideoActivity: https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/EncodeDecodeTest.java
<br>Encode file with image(actually generated buffer)

ImageToSurfaceToVideoActivity: https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/DecodeEditEncodeTest.java
<br>Encode file with image to surface1, and decode surface1 to surface2, and encode surface2 to file

MP3ToAACActivity: https://github.com/OnlyInAmerica/HWEncoderExperiments/blob/audioonly/HWEncoderExperiments/src/main/java/net/openwatch/hwencoderexperiments/AudioEncoder.java
<br>Transcode MP3 to AAC. Decode MP3 and encode to AAC
