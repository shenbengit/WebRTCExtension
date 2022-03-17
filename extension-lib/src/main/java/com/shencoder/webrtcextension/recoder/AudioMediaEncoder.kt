package com.shencoder.webrtcextension.recoder

import org.webrtc.audio.JavaAudioDeviceModule

/**
 * 音频编码器
 *
 * @author  ShenBen
 * @date    2022/3/17 16:56
 * @email   714081644@qq.com
 */
 class AudioMediaEncoder : MediaEncoder("AudioEncoder"),
    JavaAudioDeviceModule.SamplesReadyCallback {

    override fun onWebRtcAudioRecordSamplesReady(simples: JavaAudioDeviceModule.AudioSamples) {

    }

    override fun getEncodedBitRate(): Int {
       return 0
    }

   override fun onPrepare(controller: MediaEncoderEngine.Controller) {
      TODO("Not yet implemented")
   }

   override fun onStart() {
      TODO("Not yet implemented")
   }

   override fun onStop() {
      TODO("Not yet implemented")
   }

}