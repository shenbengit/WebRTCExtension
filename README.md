# WebRTCExtension
Android端WebRTC一些扩展方法:

>1、获取音频输出数据；     
>2、支持自定义是否启用H264、VP8、VP9编码；    
>3、自定义SurfaceViewRenderer，支持画面角度旋转，支持设置垂直镜像；    

## 引入
### 将JitPack存储库添加到您的项目中(项目根目录下build.gradle文件)
```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
### 添加依赖
[![](https://jitpack.io/v/shenbengit/WebRTCExtension.svg)](https://jitpack.io/#shenbengit/WebRTCExtension)
> 在您引入项目的build.gradle中添加
```gradle
dependencies {
    implementation 'com.github.shenbengit:WebRTCExtension:Tag'
}
```
## 使用事例
>获取音频输出数据，[详见事例](https://github.com/shenbengit/WebRTCExtension/blob/7e4e63f3e64f0344fc35022051c410a3cb531ba7/app/src/main/java/com/shencoder/webrtcextensiondemo/WebRTCManager.kt#L51)    
>具体实现流程移步[博客](https://blog.csdn.net/csdn_shen0221/article/details/119846853)
```kotlin
//这里替换成你创建的JavaAudioDeviceModule
val audioDeviceModule : JavaAudioDeviceModule = JavaAudioDeviceModule.builder(applicationContext).createAudioDeviceModule()
//kotlin
audioDeviceModule.setAudioTrackSamplesReadyCallback {
    //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
    val audioFormat = it.audioFormat
    val channelCount = it.channelCount
    val sampleRate = it.sampleRate
    //pcm格式数据
    val data = it.data
}

//java
JavaAudioDeviceModuleExtKt.setAudioTrackSamplesReadyCallback(
    audioDeviceModule,
    audioSamples -> {
    //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
    int audioFormat = audioSamples.getAudioFormat();
    int channelCount = audioSamples.getChannelCount();
    int sampleRate = audioSamples.getSampleRate();
    //pcm格式数据
    byte[] data = audioSamples.getData();
});
```

>支持自定义是否启用H264、VP8、VP9编码，[详见事例](https://github.com/shenbengit/WebRTCExtension/blob/21bc32beb66cbd904810ee452fb0e8e1a34dbb33/app/src/main/java/com/shencoder/webrtcextensiondemo/WebRTCManager.kt#L84)    
>具体实现流程移步[博客](https://blog.csdn.net/csdn_shen0221/article/details/119982257)
```kotlin

//kotlin
//val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
val defaultVideoEncoderFactory =
    createCustomVideoEncoderFactory(eglBaseContext, enableIntelVp8Encoder = true,
        enableH264HighProfile = true,
        videoEncoderSupportedCallback = object : VideoEncoderSupportedCallback {
            override fun isSupportedH264(info: MediaCodecInfo): Boolean {
                //判断编码器是否支持
                return TextUtils.equals(
                    "OMX.rk.video_encoder.avc",
                    info.name
                ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            }

            override fun isSupportedVp8(info: MediaCodecInfo): Boolean {
                return true
            }

            override fun isSupportedVp9(info: MediaCodecInfo): Boolean {
                return true
            }
        })
val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
mPeerConnectionFactory = PeerConnectionFactory.builder()
.setOptions(options)
.setAudioDeviceModule(audioDeviceModule)
.setVideoEncoderFactory(defaultVideoEncoderFactory)
.setVideoDecoderFactory(defaultVideoDecoderFactory)
.createPeerConnectionFactory()

//java
DefaultVideoEncoderFactory encoderFactory = DefaultVideoEncoderFactoryExtKt.createCustomVideoEncoderFactory(eglBaseContext, true, , true, new VideoEncoderSupportedCallback() {
    @Override
    public boolean isSupportedH264(@NonNull MediaCodecInfo info) {
        return false;
    }

    @Override
    public boolean isSupportedVp8(@NonNull MediaCodecInfo info) {
        return false;
    }

    @Override
    public boolean isSupportedVp9(@NonNull MediaCodecInfo info) {
        return false;
    }
});
```


>自定义SurfaceViewRenderer，支持画面角度旋转，支持设置垂直镜像；    
>使用方法与**org.webrtc.SurfaceViewRenderer**一致，将**org.webrtc.SurfaceViewRenderer**替换成**com.shencoder.webrtcextension.CustomSurfaceViewRenderer**即可。   
  
布局中使用    
```xml
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <com.shencoder.webrtcextension.CustomSurfaceViewRenderer
        android:id="@+id/viewRenderer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

代码中使用    
```java
CustomSurfaceViewRenderer viewRenderer = findViewById(R.id.viewRenderer);
//是否垂直镜像
viewRenderer.setMirrorVertically(false);
//设置旋转角度：0°、90°、180°、270°
viewRenderer.setRotationAngle(RotationAngle.ANGLE_90);
```
# [License](https://github.com/shenbengit/WebRTCExtension/blob/master/LICENSE)
