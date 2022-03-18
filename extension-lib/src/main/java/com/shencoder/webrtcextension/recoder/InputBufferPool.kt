package com.shencoder.webrtcextension.recoder

import com.shencoder.webrtcextension.Pool

internal class InputBufferPool @JvmOverloads constructor(maxPoolSize: Int = Int.MAX_VALUE) :
    Pool<InputBuffer>(maxPoolSize, Factory { InputBuffer() })