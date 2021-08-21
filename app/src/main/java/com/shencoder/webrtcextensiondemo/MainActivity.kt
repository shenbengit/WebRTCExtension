package com.shencoder.webrtcextensiondemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import org.webrtc.audio.JavaAudioDeviceModule

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val javaAudioDeviceModule = JavaAudioDeviceModule.builder(this)
            .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                override fun onWebRtcAudioRecordStart() {

                }

                override fun onWebRtcAudioRecordStop() {

                }

            }).setSamplesReadyCallback {

            }
            .createAudioDeviceModule()

    }
}