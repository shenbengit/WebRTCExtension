package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import java.nio.ByteBuffer

/**
 *
 * @author  ShenBen
 * @date    2022/3/17 17:35
 * @email   714081644@qq.com
 */
class OutputBuffer {
    var info: MediaCodec.BufferInfo? = null
    var trackIndex: Int = -1
    var data: ByteBuffer? = null
}