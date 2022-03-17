package com.shencoder.webrtcextension.recoder

import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * 视频编码器
 * @author  ShenBen
 * @date    2022/3/17 16:57
 * @email   714081644@qq.com
 */
class VideoMediaEncoder : MediaEncoder("VideoEncoder"), VideoSink {

    override fun onFrame(frame: VideoFrame) {

    }

    override fun getEncodedBitRate(): Int {
        return 0
    }

    override fun onPrepare(controller: MediaEncoderEngine.Controller) {
        TODO("Not yet implemented")
    }

    override fun onStart() {
        TODO("Not yet implemented")
    }

    override fun onStop() {
        TODO("Not yet implemented")
    }

}