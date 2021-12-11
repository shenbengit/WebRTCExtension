package com.shencoder.webrtcextensiondemo

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodecInfo
import android.os.Build
import android.text.TextUtils
import com.shencoder.webrtcextension.OverlayNV21VideoProcessor
import com.shencoder.webrtcextension.ProxyVideoSink
import com.shencoder.webrtcextension.util.Nv21BufferUtil
import com.shencoder.webrtcextensiondemo.R.drawable.ding
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.setAudioTrackSamplesReadyCallback

/**
 *
 * @author  ShenBen
 * @date    2021/08/21 19:24
 * @email   714081644@qq.com
 */
class WebRTCManager private constructor() {


    private object SingleHolder {
        val INSTANCE = WebRTCManager()
    }

    companion object {
        @JvmStatic
        fun getInstance() = SingleHolder.INSTANCE
    }

    private lateinit var audioDeviceModule: JavaAudioDeviceModule
    private lateinit var mPeerConnectionFactory: PeerConnectionFactory

    fun init(applicationContext: Context) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(applicationContext)
                .createInitializationOptions()
        )

        val eglBaseContext = EglBase.create().eglBaseContext


        audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
            .setSamplesReadyCallback {
                //音频输入数据，麦克风数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
                val audioFormat = it.audioFormat
                val channelCount = it.channelCount
                val sampleRate = it.sampleRate
                //pcm格式数据
                val data = it.data
            }
            .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                override fun onWebRtcAudioTrackStart() {
                    audioDeviceModule.setAudioTrackSamplesReadyCallback {
                        //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
                        val audioFormat = it.audioFormat
                        val channelCount = it.channelCount
                        val sampleRate = it.sampleRate
                        //pcm格式数据
                        val data = it.data
                    }


                }

                override fun onWebRtcAudioTrackStop() {

                }
            })
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options()

//        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val defaultVideoEncoderFactory =
            createCustomVideoEncoderFactory(eglBaseContext, enableIntelVp8Encoder = true,
                enableH264HighProfile = true,
                videoEncoderSupportedCallback = object : VideoEncoderSupportedCallback {
                    override fun isSupportedH264(info: MediaCodecInfo): Boolean {
                        //判断编码器是否支持
                        return TextUtils.equals(
                            "OMX.rk.video_encoder.avc",
                            info.name
                        )
                    }

                    override fun isSupportedVp8(info: MediaCodecInfo): Boolean {
                        return true
                    }

                    override fun isSupportedVp9(info: MediaCodecInfo): Boolean {
                        return true
                    }
                })
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        mPeerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        //目前仅支持Camera1，且captureToTexture 必须要传false
        val camera1Enumerator = Camera1Enumerator(false)
        val videoCapturer = camera1Enumerator.createCapturer("front", null)
        val videoSource = mPeerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
        videoCapturer.initialize(
            SurfaceTextureHelper.create("SurfaceTextureHelper", eglBaseContext),
            applicationContext,
            videoSource.capturerObserver
        )

        val bitmap = BitmapFactory.decodeResource(applicationContext.resources, ding)

        videoSource.setVideoProcessor(
            OverlayNV21VideoProcessor(
                overlayNv21Buffer = Nv21BufferUtil.argb8888BitmapToNv21Buffer(
                    bitmap,
                    true
                ),
                left = 50,
                top = 50,
                hasTransparent = true
            )
        )

        val videoTrack = mPeerConnectionFactory.createVideoTrack(
            "video_track",
            videoSource
        )

        val svr = SurfaceViewRenderer(applicationContext)
        videoTrack.addSink(ProxyVideoSink(svr, object : ProxyVideoSink.VideoFrameProcessor {
            override fun onFrameProcessor(frame: VideoFrame): VideoFrame {
                return frame
            }
        }))

    }

    fun release() {
        mPeerConnectionFactory.dispose()
        audioDeviceModule.release()
    }
}