package com.shencoder.webrtcextensiondemo.http

import com.shencoder.webrtcextensiondemo.http.bean.SrsRequestBean
import com.shencoder.webrtcextensiondemo.http.bean.SrsResponseBean
import retrofit2.http.Body
import retrofit2.http.POST

/**
 *
 * @author  ShenBen
 * @date    2021/8/19 12:38
 * @email   714081644@qq.com
 */
interface ApiService {

    @POST("/rtc/v1/play/")
    suspend fun play(@Body body: SrsRequestBean): SrsResponseBean

    @POST("/rtc/v1/publish/")
    suspend fun publish(@Body body: SrsRequestBean): SrsResponseBean
}