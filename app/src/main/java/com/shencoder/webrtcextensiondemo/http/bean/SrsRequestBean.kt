package com.shencoder.webrtcextensiondemo.http.bean


import com.squareup.moshi.Json

data class SrsRequestBean(
    @Json(name = "sdp")
    val sdp: String?,
    @Json(name = "streamurl")
    val streamUrl: String?
)