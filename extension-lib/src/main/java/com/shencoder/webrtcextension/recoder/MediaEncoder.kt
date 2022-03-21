package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import android.util.Log
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
        private const val TAG = "MediaEncoder"
        private const val STATE_NONE = 0
        private const val STATE_PREPARING = 1
        private const val STATE_PREPARED = 2
        private const val STATE_STARTING = 3
        private const val STATE_STARTED = 4

        private const val STATE_LIMIT_REACHED = 5
        private const val STATE_STOPPING = 6
        private const val STATE_STOPPED = 7


        private const val INPUT_TIMEOUT_US = 100L
        private const val OUTPUT_TIMEOUT_US = 100L

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

    private var mTrackIndex = 0

    /**
     * 编码器
     */
    protected var mMediaCodec: MediaCodec? = null

    private val mOutputBufferPool = OutputBufferPool()

    private var mStartTimeMillis: Long = 0 // In System.currentTimeMillis()

    @Volatile
    private var mFirstTimeUs = Long.MIN_VALUE // In unknown reference

    @Volatile
    private var mLastTimeUs: Long = 0

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
            Log.d(TAG, "encoderName:${encoderName} - onStart: ")
            onStart()
        }
    }

    fun stop() {
        if (mState >= STATE_STOPPING) {
            return
        }
        setState(STATE_STOPPING)
        mWorker.post {
            Log.w(TAG, "encoderName:${encoderName} - onStop: ")
            onStop()
        }
    }

    /**
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
        mMediaCodec?.run {
            stop()
            release()
        }
        mOutputBufferPool.clear()
        mBuffers = null
        setState(STATE_STOPPED)
        mWorker.destroy()
        Log.w(TAG, "encoderName:${encoderName} - onStopped: ")
    }

    /**
     * 这个方法必须在初始化[mMediaCodec]之后
     */
    protected fun initMediaCodecBuffers() {
        mBuffers = MediaCodecBuffers(mMediaCodec!!)
    }

    protected fun tryAcquireInputBuffer(holder: InputBuffer): Boolean {
        val codec = mMediaCodec ?: return false
        val buffers = mBuffers ?: return false

        val inputBufferIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        return if (inputBufferIndex < 0) {
            false
        } else {
            holder.inputBufferIndex = inputBufferIndex
            holder.data = buffers.getInputBuffer(inputBufferIndex)
            true
        }
    }

    protected fun acquireInputBuffer(holder: InputBuffer) {
        while (!tryAcquireInputBuffer(holder)) {
        }
    }

    protected fun encodeInputBuffer(buffer: InputBuffer) {
        val codec = mMediaCodec ?: return
        if (buffer.isEndOfStream) { // send EOS
            codec.queueInputBuffer(
                buffer.inputBufferIndex, 0, 0,
                buffer.timestamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        } else {
            codec.queueInputBuffer(
                buffer.inputBufferIndex, 0, buffer.dataLength,
                buffer.timestamp, 0
            )
        }
    }

    /**
     * @param drainAll whether to drain all
     */
    protected fun drainOutput(drainAll: Boolean) {
        val codec = mMediaCodec ?: return
        val buffers = mBuffers ?: return

        while (true) {
            val encoderStatus = codec.dequeueOutputBuffer(
                mBufferInfo,
                OUTPUT_TIMEOUT_US
            )
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!drainAll) {
                    break
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                buffers.onOutputBuffersChanged()
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mController.isStarted()) {
                    throw RuntimeException("MediaFormat changed twice.")
                }
                val outputFormat = codec.outputFormat
                mTrackIndex = mController.notifyStarted(outputFormat)
                setState(STATE_STARTED)
            } else if (encoderStatus < 0) {
                //忽略
            } else {
                val encodedData = buffers.getOutputBuffer(encoderStatus)!!

                val isCodecConfig = (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (!isCodecConfig && mController.isStarted() && mBufferInfo.size != 0) {

                    encodedData.position(mBufferInfo.offset)
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size)


                    if (mFirstTimeUs == Long.MIN_VALUE) {
                        mFirstTimeUs = mBufferInfo.presentationTimeUs
                    }

//                    mLastTimeUs = mBufferInfo.presentationTimeUs
//
//                    mBufferInfo.presentationTimeUs =
//                        mStartTimeMillis * 1000 + mLastTimeUs - mFirstTimeUs
                    mBufferInfo.presentationTimeUs -= mFirstTimeUs

                    val buffer = mOutputBufferPool.get()!!
                    //noinspection ConstantConditions
                    buffer.info = mBufferInfo
                    buffer.trackIndex = mTrackIndex
                    buffer.data = encodedData

                    onWriteOutput(mOutputBufferPool, buffer)
                }
                codec.releaseOutputBuffer(encoderStatus, false)
                if (!drainAll) {
                    break
                }
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    //结束标识
                    onStopped()
                    break
                }
            }
        }
    }

    protected fun notifyFirstFrameMillis(firstFrameMillis: Long) {
        mStartTimeMillis = firstFrameMillis
    }

    @CallSuper
    protected open fun onWriteOutput(pool: OutputBufferPool, buffer: OutputBuffer) {
        mController.write(buffer)
        pool.recycle(buffer)
    }


    private fun setState(newState: Int) {
        mState = newState
    }
}