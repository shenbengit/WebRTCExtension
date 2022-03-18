package com.shencoder.webrtcextension.recoder

import com.shencoder.webrtcextension.Pool

/**
 * A simple [] implementation for output buffers.
 */
class OutputBufferPool @JvmOverloads constructor(maxPoolSize: Int = Int.MAX_VALUE) :
    Pool<OutputBuffer>(maxPoolSize, Factory {
        OutputBuffer()
    })