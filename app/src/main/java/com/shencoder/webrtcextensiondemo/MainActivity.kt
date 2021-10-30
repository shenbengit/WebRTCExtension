package com.shencoder.webrtcextensiondemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.shencoder.webrtcextension.CustomSurfaceViewRenderer
import com.shencoder.webrtcextension.RotationAngle
import org.webrtc.audio.JavaAudioDeviceModule

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val viewRenderer: CustomSurfaceViewRenderer = findViewById(R.id.viewRenderer)
        //是否垂直镜像
        viewRenderer.setMirrorVertically(false)
        /**
         * 旋转角度
         */
        viewRenderer.setRotationAngle(RotationAngle.ANGLE_0)

        WebRTCManager.getInstance().init(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        WebRTCManager.getInstance().release()
    }
}