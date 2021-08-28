# WebRTCExtension
Android端WebRTC一些扩展方法:

>1、获取网络传输中对方的音频数据     
>2、待补充...

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
>获取网络传输中对方的音频数据，[详见事例](https://github.com/shenbengit/WebRTCExtension/blob/7e4e63f3e64f0344fc35022051c410a3cb531ba7/app/src/main/java/com/shencoder/webrtcextensiondemo/WebRTCManager.kt#L51)    
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

# [License](https://github.com/shenbengit/WebRTCExtension/blob/master/LICENSE)
