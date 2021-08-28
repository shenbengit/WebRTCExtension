package org.webrtc

import android.media.MediaCodecInfo

/**
 *
 * @author  ShenBen
 * @date    2021/08/28 18:58
 * @email   714081644@qq.com
 */


fun createVideoEncoderFactory(
    eglContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean,
    enableH264HighProfile: Boolean,
    codecAllowedPredicate: Predicate<MediaCodecInfo>?
) = DefaultVideoEncoderFactory(
    HardwareVideoEncoderFactory(
        eglContext,
        enableIntelVp8Encoder,
        enableH264HighProfile,
        codecAllowedPredicate
    )
)

fun createCustomVideoEncoderFactory(
    eglContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean,
    enableH264HighProfile: Boolean,
    codecAllowedPredicate: Predicate<MediaCodecInfo>? = null,
    videoEncoderSupportedCallback: VideoEncoderSupportedCallback? = null
) = DefaultVideoEncoderFactory(
    CustomHardwareVideoEncoderFactory(
        eglContext,
        enableIntelVp8Encoder,
        enableH264HighProfile,
        codecAllowedPredicate,
        videoEncoderSupportedCallback
    )
)

fun createCustomVideoEncoderFactory(
    eglContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean,
    enableH264HighProfile: Boolean,
    videoEncoderSupportedCallback: VideoEncoderSupportedCallback?
) = createCustomVideoEncoderFactory(
    eglContext,
    enableIntelVp8Encoder,
    enableH264HighProfile,
    null,
    videoEncoderSupportedCallback
)