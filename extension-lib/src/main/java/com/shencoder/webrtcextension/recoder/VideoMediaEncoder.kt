package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
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

    override fun onPrepare(controller: MediaEncoderEngine.Controller) {

    }

    override fun onStart() {

    }

    override fun onStop() {
        mMediaCodec?.signalEndOfInputStream()
        drainOutput(true)
    }

    override fun onFrame(frame: VideoFrame) {
        mWorker.post {
            frame.retain()
            val videoWidth = frame.rotatedWidth
            val videoHeight = frame.rotatedHeight

            var codec = mMediaCodec
            if (codec == null) {
                codec = initVideoEncoder(videoWidth, videoHeight, frame.rotation)

                //请求Surface用作编码器的输入，而不是输入缓冲区。
                surface = codec.createInputSurface()

                eglBase.createSurface(surface)
                codec.start()

                eglBase.makeCurrent()
                initMediaCodecBuffers()
            }

            frameDrawer.drawFrame(frame, glDrawer, null, 0, 0, videoWidth, videoHeight)
            frame.release()

//            drainVideoEncoder()
            eglBase.swapBuffers()

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
        format.setInteger("rotation-degrees", rotation)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return encoder
    }

}