package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import java.nio.ByteBuffer

/**
 *
 * @author  ShenBen
 * @date    2022/3/17 17:35
 * @email   714081644@qq.com
 */
data class OutputBuffer(
    val info: MediaCodec.BufferInfo,
    val trackIndex: Int,
    val data: ByteBuffer
)