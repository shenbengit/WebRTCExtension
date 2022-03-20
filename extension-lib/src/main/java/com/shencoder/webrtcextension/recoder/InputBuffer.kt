package com.shencoder.webrtcextension.recoder

import java.nio.ByteBuffer

/**
 *
 * @author  ShenBen
 * @date    2022/3/17 17:35
 * @email   714081644@qq.com
 */
class InputBuffer {

    var source: ByteArray? = null

    var data: ByteBuffer? = null

    var inputBufferIndex = 0

    var timestamp: Long = 0L

    var dataLength = 0

    /**
     * 是否是最后一个流
     */
    var isEndOfStream: Boolean = false
}