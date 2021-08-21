package com.shencoder.webrtcextensiondemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import org.webrtc.audio.JavaAudioDeviceModule

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WebRTCManager.getInstance().init(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        WebRTCManager.getInstance().release()
    }
}