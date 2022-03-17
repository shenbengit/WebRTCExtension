package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import androidx.annotation.CallSuper
import com.shencoder.webrtcextension.WorkerHandler

/**
 * 媒体编码器基类
 *
 * @author  ShenBen
 * @date    2022/3/17 16:56
 * @email   714081644@qq.com
 */
abstract class MediaEncoder(protected val encoderName: String) {

    private companion object {
        private const val STATE_NONE = 0
        private const val STATE_PREPARING = 1
        private const val STATE_PREPARED = 2
        private const val STATE_STARTING = 3
        private const val STATE_STARTED = 4

        private const val STATE_LIMIT_REACHED = 5
        private const val STATE_STOPPING = 6
        private const val STATE_STOPPED = 7


        private const val INPUT_TIMEOUT_US = 0L
        private const val OUTPUT_TIMEOUT_US = 0L

    }

    private val mBufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()

    private var mBuffers: MediaCodecBuffers? = null

    /**
     * 当前状态
     */
    @Volatile
    private var mState = STATE_NONE

    private lateinit var mController: MediaEncoderEngine.Controller
    protected lateinit var mWorker: WorkerHandler

    private val mTrackIndex = 0

    protected lateinit var mMediaCodec: MediaCodec

    protected abstract fun getEncodedBitRate(): Int

    fun prepare(controller: MediaEncoderEngine.Controller) {
        mController = controller
        mWorker = WorkerHandler.get(encoderName)
        mWorker.thread.priority = Thread.MAX_PRIORITY
        mWorker.post {
            setState(STATE_PREPARING)
            onPrepare(controller)
            setState(STATE_PREPARED)
        }
    }

    fun start() {
        mWorker.post {
            if (mState < STATE_PREPARED || mState >= STATE_STARTING) {
                return@post
            }
            setState(STATE_STARTING)
            onStart()
        }
    }

    fun stop() {
        if (mState >= STATE_STOPPING) {
            return
        }
        setState(STATE_STOPPING)
        mWorker.post {
            onStop()
        }
    }

    /**
     * 调用以在开始之前准备此编码器。任何初始化都应该在这里完成，因为它不会干扰原始线程
     *
     * 此时子类必须创建[mMediaCodec]对象
     *
     * @param controller the muxer controller
     */
    @EncoderThread
    protected abstract fun onPrepare(controller: MediaEncoderEngine.Controller)

    /**
     * 开始录制
     */
    @EncoderThread
    protected abstract fun onStart()

    /**
     * The caller notifying of a certain event occurring.
     * Should analyze the string and see if the event is important.
     * @param event what happened
     * @param data object
     */
    @EncoderThread
    protected open fun onEvent(event: String, data: Any?) {

    }

    /**
     * 停止录制
     */
    @EncoderThread
    protected abstract fun onStop()

    @CallSuper
    protected open fun onStopped() {
        mController.notifyStopped(mTrackIndex)
        mMediaCodec.stop()
        mMediaCodec.release()
//        mOutputBufferPool.clear()
//        mOutputBufferPool = null
        mBuffers = null
        setState(STATE_STOPPED)
        mWorker.destroy()
    }


    private fun setState(newState: Int) {
        mState = newState
    }
}