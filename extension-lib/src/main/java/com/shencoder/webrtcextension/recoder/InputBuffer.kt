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


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InputBuffer

        if (source != null) {
            if (other.source == null) return false
            if (!source.contentEquals(other.source)) return false
        } else if (other.source != null) return false
        if (data != other.data) return false
        if (isEndOfStream != other.isEndOfStream) return false

        return true
    }

    override fun hashCode(): Int {
        var result = source?.contentHashCode() ?: 0
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + isEndOfStream.hashCode()
        return result
    }


}