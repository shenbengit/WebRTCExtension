package com.shencoder.webrtcextension

import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * [VideoSink]的代理类，可用于[VideoFrame]的二次处理
 *
 *example:
 *<code>
 *     val svr = findViewById<SurfaceViewRenderer>(R.id.svr)
 *     val proxy = ProxyVideoSink(svr, object:VideoFrameProcessor{
 *         override fun onFrameProcessor(frame: VideoFrame): VideoFrame {
 *             //handle your video frame.
 *             val newFrame: VideoFrame = handleYourVideoFrame(frame)
 *             return newFrame;
 *         }
 *     })
 *     val videoTrack: VideoTrack = ...
 *     videoTrack.addSink(proxy)
 *</code>
 *
 * @author  ShenBen
 * @date    2021/12/6 14:04
 * @email   714081644@qq.com
 */
class ProxyVideoSink(private val sink: VideoSink, private val processor: VideoFrameProcessor) :
    VideoSink {

    override fun onFrame(frame: VideoFrame?) {
        if (frame == null) {
            return
        }
        val newFrame: VideoFrame
        synchronized(this) {
            newFrame = processor.onFrameProcessor(frame)
        }
        sink.onFrame(newFrame)
    }

    interface VideoFrameProcessor {
        fun onFrameProcessor(frame: VideoFrame): VideoFrame
    }
}