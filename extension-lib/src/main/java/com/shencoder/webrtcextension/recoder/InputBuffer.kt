package com.shencoder.webrtcextension.recoder

import java.nio.ByteBuffer

/**
 *
 * @author  ShenBen
 * @date    2022/3/17 17:35
 * @email   714081644@qq.com
 */
data class InputBuffer(
    val trackIndex: ByteBuffer,
    val data: ByteBuffer
)