package com.shencoder.webrtcextensiondemo.http.bean


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SrsResponseBean(
    @Json(name = "code")
    val code: Int,
    @Json(name = "sdp")
    val sdp: String?,
    @Json(name = "server")
    val server: String?,
    @Json(name = "sessionid")
    val sessionId: String?
)