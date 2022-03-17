package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import java.nio.ByteBuffer

/**
 * @author ShenBen
 * @date 2022/3/17 18:16
 * @email 714081644@qq.com
 */
internal class MediaCodecBuffers(private val mMediaCodec: MediaCodec) {

    fun getInputBuffer(index: Int): ByteBuffer? {
        return mMediaCodec.getInputBuffer(index)
    }

    fun getOutputBuffer(index: Int): ByteBuffer? {
        return mMediaCodec.getOutputBuffer(index)
    }

    fun onOutputBuffersChanged() {

    }

}