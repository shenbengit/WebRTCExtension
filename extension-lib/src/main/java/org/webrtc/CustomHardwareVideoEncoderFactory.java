package org.webrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import androidx.annotation.Nullable;

import static org.webrtc.MediaCodecUtils.EXYNOS_PREFIX;
import static org.webrtc.MediaCodecUtils.INTEL_PREFIX;
import static org.webrtc.MediaCodecUtils.QCOM_PREFIX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 自定义硬解视频编码工厂
 * 用于解决WebRTC支持H264编码；
 * {@link CustomHardwareVideoEncoderFactory#isHardwareSupportedInCurrentSdkH264(MediaCodecInfo)}
 * 目前源码中仅支持部分大厂机型，导致即使我们的手机硬件支持，也可能导致无法使用H264
 * 用于解决sdp中无H264信息
 *
 * @author ShenBen
 * @date 2021/08/28 18:28
 * @email 714081644@qq.com
 */
@SuppressWarnings("deprecation") // API 16 requires the use of deprecated methods.
public class CustomHardwareVideoEncoderFactory implements VideoEncoderFactory {
    private static final String TAG = "CustomHardwareVideoEncoderFactory";

    // Forced key frame interval - used to reduce color distortions on Qualcomm platforms.
    private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000;
    private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000;
    private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000;
    /**
     * 支持对OMX.google 的匹配 ，如：OMX.google.h264.encoder
     * 主要是为了解决对华为手机的支持
     * 华为海思：OMX.hisi.video.encoder.avc 进行H264编码有问题
     */
    static final String GOOGLE_PREFIX = "OMX.google.";

    // List of devices with poor H.264 encoder quality.
    // HW H.264 encoder on below devices has poor bitrate control - actual
    // bitrates deviates a lot from the target value.
    private static final List<String> H264_HW_EXCEPTION_MODELS =
            Arrays.asList("SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4");

    @Nullable
    private final EglBase14.Context sharedContext;
    private final boolean enableIntelVp8Encoder;
    private final boolean enableH264HighProfile;
    @Nullable
    private final Predicate<MediaCodecInfo> codecAllowedPredicate;

    @Nullable
    private final VideoEncoderSupportedCallback videoEncoderSupportedCallback;

    /**
     * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
     *
     * @param sharedContext                 The textures generated will be accessible from this context. May be null,
     *                                      this disables texture support.
     * @param enableIntelVp8Encoder         true if Intel's VP8 encoder enabled.
     * @param enableH264HighProfile         true if H264 High Profile enabled.
     * @param videoEncoderSupportedCallback
     */
    public CustomHardwareVideoEncoderFactory(
            EglBase.Context sharedContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile, @Nullable VideoEncoderSupportedCallback videoEncoderSupportedCallback) {
        this(sharedContext, enableIntelVp8Encoder, enableH264HighProfile,
                /* codecAllowedPredicate= */ null, videoEncoderSupportedCallback);
    }

    /**
     * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
     *
     * @param sharedContext                 The textures generated will be accessible from this context. May be null,
     *                                      this disables texture support.
     * @param enableIntelVp8Encoder         true if Intel's VP8 encoder enabled.
     * @param enableH264HighProfile         true if H264 High Profile enabled.
     * @param codecAllowedPredicate         optional predicate to filter codecs. All codecs are allowed
     *                                      when predicate is not provided.
     * @param videoEncoderSupportedCallback
     */
    public CustomHardwareVideoEncoderFactory(EglBase.Context sharedContext, boolean enableIntelVp8Encoder,
                                             boolean enableH264HighProfile, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate,
                                             @Nullable VideoEncoderSupportedCallback videoEncoderSupportedCallback) {
        // Texture mode requires EglBase14.
        if (sharedContext instanceof EglBase14.Context) {
            this.sharedContext = (EglBase14.Context) sharedContext;
        } else {
            Logging.w(TAG, "No shared EglBase.Context.  Encoders will not use texture mode.");
            this.sharedContext = null;
        }
        this.enableIntelVp8Encoder = enableIntelVp8Encoder;
        this.enableH264HighProfile = enableH264HighProfile;
        this.codecAllowedPredicate = codecAllowedPredicate;
        this.videoEncoderSupportedCallback = videoEncoderSupportedCallback;
    }

    @Deprecated
    public CustomHardwareVideoEncoderFactory(boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
        this(null, enableIntelVp8Encoder, enableH264HighProfile, null);
    }

    @Nullable
    @Override
    public VideoEncoder createEncoder(VideoCodecInfo input) {
        // HW encoding is not supported below Android Kitkat.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null;
        }

        VideoCodecMimeType type = VideoCodecMimeType.valueOf(input.name);
        MediaCodecInfo info = findCodecForType(type);

        if (info == null) {
            return null;
        }

        String codecName = info.getName();
        String mime = type.mimeType();
        Integer surfaceColorFormat = MediaCodecUtils.selectColorFormat(
                MediaCodecUtils.TEXTURE_COLOR_FORMATS, info.getCapabilitiesForType(mime));
        Integer yuvColorFormat = MediaCodecUtils.selectColorFormat(
                MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(mime));

        if (type == VideoCodecMimeType.H264) {
            boolean isHighProfile = H264Utils.isSameH264Profile(
                    input.params, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ true));
            boolean isBaselineProfile = H264Utils.isSameH264Profile(
                    input.params, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ false));

            if (!isHighProfile && !isBaselineProfile) {
                return null;
            }
            if (isHighProfile && !isH264HighProfileSupported(info)) {
                return null;
            }
        }

        return new HardwareVideoEncoder(new MediaCodecWrapperFactoryImpl(), codecName, type,
                surfaceColorFormat, yuvColorFormat, input.params, getKeyFrameIntervalSec(type),
                getForcedKeyFrameIntervalMs(type, codecName), createBitrateAdjuster(type, codecName),
                sharedContext);
    }

    @Override
    public VideoCodecInfo[] getSupportedCodecs() {
        // HW encoding is not supported below Android Kitkat.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return new VideoCodecInfo[0];
        }

        List<VideoCodecInfo> supportedCodecInfos = new ArrayList<>();
        // Generate a list of supported codecs in order of preference:
        // VP8, VP9, H264 (high profile), H264 (baseline profile).
        for (VideoCodecMimeType type : new VideoCodecMimeType[]{VideoCodecMimeType.VP8,
                VideoCodecMimeType.VP9, VideoCodecMimeType.H264}) {
            MediaCodecInfo codec = findCodecForType(type);
            if (codec != null) {
                String name = type.name();
                // TODO(sakal): Always add H264 HP once WebRTC correctly removes codecs that are not
                // supported by the decoder.
                if (type == VideoCodecMimeType.H264 && isH264HighProfileSupported(codec)) {
                    supportedCodecInfos.add(new VideoCodecInfo(
                            name, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ true)));
                }

                supportedCodecInfos.add(new VideoCodecInfo(
                        name, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ false)));
            }
        }

        return supportedCodecInfos.toArray(new VideoCodecInfo[0]);
    }

    @Nullable
    private MediaCodecInfo findCodecForType(VideoCodecMimeType type) {
        int codecCount = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecCount; ++i) {
            MediaCodecInfo info = null;
            try {
                info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException e) {
                Logging.e(TAG, "Cannot retrieve encoder codec info", e);
            }

            if (info == null || !info.isEncoder()) {
                continue;
            }

            if (isSupportedCodec(info, type)) {
                return info;
            }
        }
        // No support for this type.
        return null;
    }

    // Returns true if the given MediaCodecInfo indicates a supported encoder for the given type.
    private boolean isSupportedCodec(MediaCodecInfo info, VideoCodecMimeType type) {
        if (!MediaCodecUtils.codecSupportsType(info, type)) {
            return false;
        }
        // Check for a supported color format.
        if (MediaCodecUtils.selectColorFormat(
                MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(type.mimeType()))
                == null) {
            return false;
        }
        return isHardwareSupportedInCurrentSdk(info, type) && isMediaCodecAllowed(info);
    }

    // Returns true if the given MediaCodecInfo indicates a hardware module that is supported on the
    // current SDK.
    private boolean isHardwareSupportedInCurrentSdk(MediaCodecInfo info, VideoCodecMimeType type) {
        switch (type) {
            case VP8:
                return isHardwareSupportedInCurrentSdkVp8(info);
            case VP9:
                return isHardwareSupportedInCurrentSdkVp9(info);
            case H264:
                return isHardwareSupportedInCurrentSdkH264(info);
        }
        return false;
    }

    private boolean isHardwareSupportedInCurrentSdkVp8(MediaCodecInfo info) {

        String name = info.getName();
        // QCOM Vp8 encoder is supported in KITKAT or later.
        boolean isSupported = (name.startsWith(QCOM_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                // Exynos VP8 encoder is supported in M or later.
                || (name.startsWith(EXYNOS_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                // Intel Vp8 encoder is supported in LOLLIPOP or later, with the intel encoder enabled.
                || (name.startsWith(INTEL_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && enableIntelVp8Encoder);
        if (isSupported) {
            return true;
        } else {
            //自行判断是否支持VP8
            return videoEncoderSupportedCallback != null && videoEncoderSupportedCallback.isSupportedVp8(info);
        }
    }

    private boolean isHardwareSupportedInCurrentSdkVp9(MediaCodecInfo info) {
        String name = info.getName();
        boolean isSupported = (name.startsWith(QCOM_PREFIX) || name.startsWith(EXYNOS_PREFIX))
                // Both QCOM and Exynos VP9 encoders are supported in N or later.
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
        if (isSupported) {
            return true;
        } else {
            //自行判断是否支持VP9
            return videoEncoderSupportedCallback != null && videoEncoderSupportedCallback.isSupportedVp9(info);
        }
    }

    private boolean isHardwareSupportedInCurrentSdkH264(MediaCodecInfo info) {
        // First, H264 hardware might perform poorly on this model.
        if (H264_HW_EXCEPTION_MODELS.contains(Build.MODEL)) {
            return false;
        }
        String name = info.getName();
        // QCOM H264 encoder is supported in KITKAT or later.
        boolean isSupported = (name.startsWith(QCOM_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) ||
                // Exynos H264 encoder is supported in LOLLIPOP or later.
                (name.startsWith(EXYNOS_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ||
                //解决华为手机使用海思（OMX.hisi.video.encoder.avc）无法正常编码的问题，使用OMX.google.h264.encoder
                (name.startsWith(GOOGLE_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        if (isSupported) {
            return true;
        } else {
            //自行判断是否支持H264
            return videoEncoderSupportedCallback != null && videoEncoderSupportedCallback.isSupportedH264(info);
        }
    }

    private boolean isMediaCodecAllowed(MediaCodecInfo info) {
        if (codecAllowedPredicate == null) {
            return true;
        }
        return codecAllowedPredicate.test(info);
    }

    private int getKeyFrameIntervalSec(VideoCodecMimeType type) {
        switch (type) {
            case VP8:// Fallthrough intended.
            case VP9:
                return 100;
            case H264:
                return 20;
        }
        throw new IllegalArgumentException("Unsupported VideoCodecMimeType " + type);
    }

    private int getForcedKeyFrameIntervalMs(VideoCodecMimeType type, String codecName) {
        if (type == VideoCodecMimeType.VP8 && codecName.startsWith(QCOM_PREFIX)) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP
                    || Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS;
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS;
            } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS;
            }
        }
        // Other codecs don't need key frame forcing.
        return 0;
    }

    private BitrateAdjuster createBitrateAdjuster(VideoCodecMimeType type, String codecName) {
        if (codecName.startsWith(EXYNOS_PREFIX)) {
            if (type == VideoCodecMimeType.VP8) {
                // Exynos VP8 encoders need dynamic bitrate adjustment.
                return new DynamicBitrateAdjuster();
            } else {
                // Exynos VP9 and H264 encoders need framerate-based bitrate adjustment.
                return new FramerateBitrateAdjuster();
            }
        }
        // Other codecs don't need bitrate adjustment.
        return new BaseBitrateAdjuster();
    }

    private boolean isH264HighProfileSupported(MediaCodecInfo info) {
        return enableH264HighProfile && Build.VERSION.SDK_INT > Build.VERSION_CODES.M
                && info.getName().startsWith(EXYNOS_PREFIX);
    }
}