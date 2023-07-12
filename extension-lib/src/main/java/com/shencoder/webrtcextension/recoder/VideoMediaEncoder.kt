package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.view.Surface
import org.webrtc.*

/**
 * 视频编码器
 * @author  ShenBen
 * @date    2022/3/17 16:57
 * @email   714081644@qq.com
 */
class VideoMediaEncoder(private val sharedContext: EglBase.Context) : MediaEncoder("VideoEncoder"),
    VideoSink {

    private companion object {

        /**
         * fps
         */
        private const val FRAME_RATE = 30

        /**
         * I帧间隔
         */
        private const val IFRAME_INTERVAL = 5
    }

    /**
     * 使用可记录配置
     */
    private val eglBase = EglBase.create(sharedContext, EglBase.CONFIG_RECORDABLE)
    private val glDrawer: RendererCommon.GlDrawer = GlRectDrawer()
    private val frameDrawer = VideoFrameDrawer()

    /**
     * 请求Surface用作编码器的输入，而不是输入缓冲区。
     * 不用时调用[Surface.release]
     */
    private lateinit var surface: Surface

    @Volatile
    private var mSyncFrameFound = false

    @Volatile
    private var mFrameNumber = -1

    override fun onPrepare(controller: MediaEncoderEngine.Controller) {

    }

    override fun onStart() {
        mFrameNumber = 0
    }

    override fun onStop() {
        mFrameNumber = -1
        mMediaCodec?.signalEndOfInputStream()
        drainOutput(true)
    }

    override fun onStopped() {
        super.onStopped()
        if (this::surface.isInitialized) {
            surface.release()
        }
        eglBase.release()
        glDrawer.release()
        frameDrawer.release()
    }

    override fun onFrame(frame: VideoFrame) {
        frame.retain()
        mWorker.post {
            val timestampUs = frame.timestampNs / (1000 * 1000)
            if (shouldRenderFrame(timestampUs).not()) {
                frame.release()
                return@post
            }
            val videoWidth = frame.rotatedWidth
            val videoHeight = frame.rotatedHeight

            //通知我们得到第一帧及其绝对时间
            if (mFrameNumber == 1) {
                notifyFirstFrameMillis(timestampUs)
            }

            var codec = mMediaCodec
            if (codec == null) {
                codec = initVideoEncoder(videoWidth, videoHeight, frame.rotation)

                //请求Surface用作编码器的输入，而不是输入缓冲区。
                surface = codec.createInputSurface()
                eglBase.createSurface(surface)
                eglBase.makeCurrent()

                mMediaCodec = codec
                initMediaCodecBuffers()

                codec.start()
            }

            drainOutput(false)

            frameDrawer.drawFrame(frame, glDrawer, null, 0, 0, videoWidth, videoHeight)
            eglBase.swapBuffers()
            frame.release()
        }
    }

    override fun onWriteOutput(pool: OutputBufferPool, buffer: OutputBuffer) {
        if (!mSyncFrameFound) {
            val flag = MediaCodec.BUFFER_FLAG_KEY_FRAME
            val hasFlag = (buffer.info!!.flags and flag) == flag
            if (hasFlag) {
                mSyncFrameFound = true
                super.onWriteOutput(pool, buffer)
            } else {
                val params = Bundle()
                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                mMediaCodec?.setParameters(params)
                pool.recycle(buffer)
            }
        } else {
            super.onWriteOutput(pool, buffer)
        }
    }

    private fun initVideoEncoder(videoWidth: Int, videoHeight: Int, rotation: Int): MediaCodec {
        //h.264
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        format.setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        )
        format.setInteger(
            MediaFormat.KEY_COMPLEXITY,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        )
//        format.setInteger("rotation-degrees", rotation)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return encoder
    }

    private fun shouldRenderFrame(timestampUs: Long): Boolean {
        if (timestampUs == 0L) return false
        if (mFrameNumber < 0) return false
        mFrameNumber++
        return true
    }
}