package com.shencoder.webrtcextensiondemo

import android.app.Application
import android.content.Context
import android.util.Log
import com.shencoder.mvvmkit.ext.globalInit
import com.shencoder.webrtcextensiondemo.constant.Constant
import com.shencoder.webrtcextensiondemo.di.appModule
import org.koin.android.java.KoinAndroidApplication
import org.koin.core.logger.Level
import org.webrtc.PeerConnectionFactory
import xcrash.XCrash

/**
 *
 * @author  ShenBen
 * @date    2021/8/19 12:41
 * @email   714081644@qq.com
 */
class App : Application() {

    private companion object {
        private const val TAG = "App"
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        val parameters = XCrash.InitParameters()
        parameters.setAppVersion(BuildConfig.VERSION_NAME)
        parameters.setLogDir(getExternalFilesDir(Constant.CRASH_LOG)?.absolutePath)
        parameters.setJavaRethrow(false)
        parameters.setAnrRethrow(false)
        parameters.setNativeRethrow(false)
        val result = XCrash.init(this, parameters)
        Log.i(TAG, "XCrash.init: $result")
    }

    override fun onCreate() {
        super.onCreate()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(applicationContext).createInitializationOptions()
        )
        val koinApplication =
            KoinAndroidApplication
                .create(
                    this,
                    if (BuildConfig.DEBUG) Level.ERROR else Level.ERROR
                )
                .modules(appModule)
        globalInit(BuildConfig.DEBUG, "WebRTC-Extension", koinApplication)
    }
}