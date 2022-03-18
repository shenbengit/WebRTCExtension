package com.shencoder.webrtcextension.recoder

import android.media.MediaFormat
import android.media.MediaMuxer
import com.shencoder.webrtcextension.WorkerHandler
import java.io.File

/**
 * 编码引擎
 *
 * @author  ShenBen
 * @date    2022/3/17 16:55
 * @email   714081644@qq.com
 */
class MediaEncoderEngine @JvmOverloads constructor(
    /**
     * 录音文件
     */
    private val file: File,
    videoMediaEncoder: VideoMediaEncoder,
    audioMediaEncoder: AudioMediaEncoder? = null,
    private val mListener: Listener? = null
) {

    interface Listener {
        /**
         * 编码开始时调用
         */
        fun onEncodingStart()

        /**
         * 编码停止时调用。
         * 此时，复用器或编码器可能仍在处理数据，但我们已停止接收输入（录制视频和音频帧）。
         * 事实上，我们很快就会停下来。
         */
        fun onEncodingStop()

        /**
         * 由于某种原因编码结束时调用。
         * 如果有异常，则失败。
         * @param e 异常（如果存在）
         */
        fun onEncodingEnd(e: Throwable?)
    }

    companion object {
        const val END_BY_USER = 0
        const val END_BY_MAX_DURATION = 1
        const val END_BY_MAX_SIZE = 2
    }

    /**
     * 编码器集合
     */
    private val mEncoders = mutableListOf<MediaEncoder>()

    /**
     * 媒体混合器
     */
    private val mMediaMuxer: MediaMuxer = MediaMuxer(
        file.absolutePath,
        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )

    /**
     * 已经启动的编码器的数量
     */
    @Volatile
    private var mStartedEncodersCount = 0

    /**
     * 已经停止的编码器的数量
     */
    @Volatile
    private var mStoppedEncodersCount = 0

    /**
     * 媒体混合器是否已经启动
     */
    private var mMediaMuxerStarted = false

    private val mControllerLock = Any()
    private val mControllerThread: WorkerHandler = WorkerHandler.get("EncoderEngine")
    private val mController = Controller()

    init {
        mEncoders.add(videoMediaEncoder)
        audioMediaEncoder?.let { mEncoders.add(it) }

        mEncoders.forEach { it.prepare(mController) }
    }


    fun start() {
        mEncoders.forEach { it.start() }
    }


    /**
     *
     */
    fun stop() {
        mEncoders.forEach { it.stop() }
        mListener?.onEncodingStop()
    }

    /**
     * 在所有编码器调用[Controller.requestStop]请求释放后调用
     */
    private fun end() {
        var error: Throwable? = null

        kotlin.runCatching {
            mMediaMuxer.stop()
        }.onFailure {
            error = it
        }

        kotlin.runCatching {
            mMediaMuxer.release()
        }.onFailure {
            if (error == null) {
                error = it
            }
        }
        mListener?.onEncodingEnd(error)

        mStartedEncodersCount = 0
        mStoppedEncodersCount = 0
        mMediaMuxerStarted = false
        mControllerThread.destroy()
    }

    fun getVideoEncoder(): VideoMediaEncoder = mEncoders[0] as VideoMediaEncoder

    fun getAudioEncoder(): AudioMediaEncoder? {
        return if (mEncoders.size > 1) {
            mEncoders[1] as AudioMediaEncoder
        } else {
            null
        }
    }


    inner class Controller {
        /**
         * 请求多路复用器应该启动。这不能保证执行：
         * 我们等待所有编码器调用此方法，然后才启动混合器
         */
        fun notifyStarted(format: MediaFormat): Int {
            synchronized(mControllerLock) {
                check(!mMediaMuxerStarted) { "Trying to start but muxer started already" }
                val track = mMediaMuxer.addTrack(format)
                if (++mStartedEncodersCount == mEncoders.size) {
                    mControllerThread.run {
                        mMediaMuxer.start()
                        mMediaMuxerStarted = true
                        mListener?.onEncodingStart()
                    }
                }
                return track
            }
        }

        /**
         * 混合器是否已经启动
         */
        fun isStarted(): Boolean {
            synchronized(mControllerLock) { return mMediaMuxerStarted }
        }

        /**
         * 将给定数据写入多路复用器。
         * 在[isStarted]之后
         */
        fun write(buffer: OutputBuffer) {
            mMediaMuxer.writeSampleData(buffer.trackIndex, buffer.data!!, buffer.info!!)
        }

        /**
         * 请求引擎停止。直到所有的编码器都调用这个方法后才会执行，所以它是一种软请求。
         */
        fun requestStop(track: Int) {
            synchronized(mControllerLock) {
                if (--mStartedEncodersCount == 0) {
                    mControllerThread.run { stop() }
                }
            }
        }

        /**
         * 通知编码器已停止。在所有编码器调用它之后，我们将实际停止复用器
         *
         * @param track track
         */
        fun notifyStopped(track: Int) {
            synchronized(mControllerLock) {
                if (++mStoppedEncodersCount == mEncoders.size) {
                    mControllerThread.run { end() }
                }
            }
        }

    }
}