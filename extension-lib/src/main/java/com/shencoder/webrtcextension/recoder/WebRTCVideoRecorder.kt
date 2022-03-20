package com.shencoder.webrtcextension.recoder

import org.webrtc.EglBase
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 录制WebRTC视频
 *
 * 暂时不支持只录音频
 *
 * @author  ShenBen
 * @date    2022/3/17 17:02
 * @email   714081644@qq.com
 */
class WebRTCVideoRecorder(
    /**
     * 录音文件
     */
    private val file: File,
    sharedContext: EglBase.Context,
    /**
     * 是否录制音频
     * recording audio or not.
     */
    private val withAudio: Boolean
) : VideoSink, JavaAudioDeviceModule.SamplesReadyCallback, MediaEncoderEngine.Listener {

    private companion object {
        private const val STATE_IDLE = 0
        private const val STATE_RECORDING = 1
        private const val STATE_STOPPING = 2
        private const val STATE_END = 3
    }

    /**
     * Listens for video recorder events.
     */
    interface VideoResultListener {
        /**
         * The operation was completed, either with success or with an error.
         * @param result the result or null if error
         * @param exception the error or null if everything went fine
         */
        fun onVideoResult(result: File?, exception: Exception?)

        /**
         * The callback for the actual video recording starting.
         */
        fun onVideoRecordingStart()

        /**
         * Video recording has ended. We will finish processing the file
         * and soon [.onVideoResult] will be called.
         */
        fun onVideoRecordingEnd()
    }

    private var mEncoderEngine: MediaEncoderEngine = MediaEncoderEngine(
        file,
        VideoMediaEncoder(sharedContext),
        if (withAudio) {
            AudioMediaEncoder()
        } else {
            null
        },
        this
    )

    /**
     * 引擎是否已经启动
     */
    @Volatile
    private var mEncoderEngineStarted = false

    private val mEncoderEngineLock = Any()

    private val mState = AtomicInteger(STATE_IDLE)

    /**
     * 视频帧数据
     */
    override fun onFrame(frame: VideoFrame) {
        if (isRecording()) {
            mEncoderEngine.getVideoEncoder().onFrame(frame)
        }
    }

    /**
     * 音频帧数据-pcm
     */
    override fun onWebRtcAudioRecordSamplesReady(simples: JavaAudioDeviceModule.AudioSamples) {
        if (withAudio && isRecording()) {
            mEncoderEngine.getAudioEncoder()?.onWebRtcAudioRecordSamplesReady(simples)
        }
    }

    override fun onEncodingStart() {
        println("onEncodingStart--->")
        mEncoderEngineStarted = true
    }

    override fun onEncodingStop() {
        mEncoderEngineStarted = false
    }

    override fun onEncodingEnd(e: Throwable?) {
        mState.set(STATE_END)
    }

    fun start() {
        if (mState.get() == STATE_END) {
            return
        }
        if (mState.compareAndSet(STATE_IDLE, STATE_RECORDING)) {
            onStart()
        }
    }

    fun stop() {
        if (mState.get() == STATE_END) {
            return
        }

        if (isRecording()) {
            mState.set(STATE_STOPPING)
            onStop()
        }
    }

    /**
     * 是否正在录制
     */
    fun isRecording(): Boolean = mState.get() == STATE_RECORDING

    private fun onStart() {
        synchronized(mEncoderEngineLock) {
            mEncoderEngine.start()
        }
    }

    private fun onStop() {
        synchronized(mEncoderEngineLock) {
            mEncoderEngine.stop()
        }
    }

}