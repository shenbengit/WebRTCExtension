package com.shencoder.webrtcextensiondemo

import android.content.Context
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

        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
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

                    //如果使用Java
//                    JavaAudioDeviceModuleExtKt.setAudioTrackSamplesReadyCallback(
//                        audioDeviceModule,
//                        audioSamples -> {
//                        //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
//                        int audioFormat = audioSamples.getAudioFormat ();
//                        int channelCount = audioSamples.getChannelCount ();
//                        int sampleRate = audioSamples.getSampleRate ();
//                        //pcm格式数据
//                        byte[] data = audioSamples.getData ();
//                    });
                }

                override fun onWebRtcAudioTrackStop() {

                }
            })
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options()

        mPeerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
    }

    fun release() {
        mPeerConnectionFactory.dispose()
        audioDeviceModule.release()
    }
}