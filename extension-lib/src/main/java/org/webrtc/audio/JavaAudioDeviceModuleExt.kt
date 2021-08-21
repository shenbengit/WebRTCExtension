package org.webrtc.audio

import android.media.AudioTrack

/**
 *
 * @author  ShenBen
 * @date    2021/08/21 18:48
 * @email   714081644@qq.com
 */

/**
 * 回调音频输入数据
 * 反射，替换[WebRtcAudioTrack.audioTrack]，使用[AudioTrackInterceptor]
 * 其中要把[WebRtcAudioTrack.audioTrack]赋值给[AudioTrackInterceptor.originalTrack]，
 * [AudioTrackInterceptor]只是一个壳，具体实现是[AudioTrackInterceptor.originalTrack]
 *
 * @param samplesReadyCallback 回调接口 ，原始pcm数据
 */
fun JavaAudioDeviceModule.getAudioTrackSamplesReadyCallback(samplesReadyCallback: JavaAudioDeviceModule.SamplesReadyCallback) {
    val deviceModuleClass = this::class.java
    val audioOutputField = deviceModuleClass.getDeclaredField("audioOutput")
    audioOutputField.isAccessible = true
    val webRtcAudioTrack = audioOutputField.get(this) as WebRtcAudioTrack
    val audioTrackClass = webRtcAudioTrack::class.java
    val audioTrackFiled = audioTrackClass.getDeclaredField("audioTrack")
    audioTrackFiled.isAccessible = true
    val audioTrack = audioTrackFiled.get(webRtcAudioTrack)?.let {
        it as AudioTrack
    } ?: return

    val interceptor = AudioTrackInterceptor(audioTrack, samplesReadyCallback)
    audioTrackFiled.set(webRtcAudioTrack, interceptor)
}