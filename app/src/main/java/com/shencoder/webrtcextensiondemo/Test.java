package com.shencoder.webrtcextensiondemo;

import android.content.Context;

import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModuleExtKt;

/**
 * @author ShenBen
 * @date 2021/08/21 19:13
 * @email 714081644@qq.com
 */
public class Test {

    public void test(Context context){
        JavaAudioDeviceModule javaAudioDeviceModule =null;
        javaAudioDeviceModule=  JavaAudioDeviceModule.builder(context)
                .setAudioTrackStateCallback(new JavaAudioDeviceModule.AudioTrackStateCallback() {
                    @Override
                    public void onWebRtcAudioTrackStart() {

                    }

                    @Override
                    public void onWebRtcAudioTrackStop() {

                    }
                }).createAudioDeviceModule();
        JavaAudioDeviceModuleExtKt.getAudioTrackSamplesReadyCallback(javaAudioDeviceModule, new JavaAudioDeviceModule.SamplesReadyCallback() {
            @Override
            public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples audioSamples) {

            }
        });
    }
}
