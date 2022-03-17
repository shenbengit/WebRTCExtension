# WebRTCExtension
Android端WebRTC一些扩展方法:

- 1、获取音频输出数据；    
- 2、支持自定义是否启用H264、VP8、VP9编码；    
- 3、自定义SurfaceViewRenderer，支持画面角度旋转，支持设置垂直镜像；  
- 4、添加VideoSink代理类(ProxyVideoSink)；    
- 5、支持VideoProcessor针对视频数据进行二次处理，如叠图功能，添加基础类；

示例中的[demo](https://github.com/shenbengit/WebRTCExtension/tree/master/app)需要使用[SRS视频服务器](https://github.com/ossrs/srs)，具体搭建过程详见SRS官方文档。    
> 其他Android端WebRTC结合SRS使用示例，详见[WebRTC-SRS](https://github.com/shenbengit/WebRTC-SRS)，完整示例：私聊、群聊、聊天室功能详见[SrsRtcAndroidClient](https://github.com/shenbengit/SrsRtcAndroidClient)。

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
### 获取音频输出数据    

具体实现流程移步[博客](https://blog.csdn.net/csdn_shen0221/article/details/119846853)

```kotlin
val audioDeviceModule = JavaAudioDeviceModule.builder(this)
    .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
        override fun onWebRtcAudioTrackStart() {
            //添加音频输出数据监听
            //kotlin
            audioDeviceModule.setAudioTrackSamplesReadyCallback {
                //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
                val audioFormat = it.audioFormat
                val channelCount = it.channelCount
                val sampleRate = it.sampleRate
                //pcm格式数据
                val data = it.data
            }
            
            //use java
//            JavaAudioDeviceModuleExtKt.setAudioTrackSamplesReadyCallback(audioDeviceModule, audioSamples -> {
//                //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
//                int audioFormat = audioSamples.getAudioFormat();
//                int channelCount = audioSamples.getChannelCount();
//                int sampleRate = audioSamples.getSampleRate();
//                //pcm格式数据
//                byte[] data = audioSamples.getData ();
//             });
        }

        override fun onWebRtcAudioTrackStop() {
        }

}) 
.setSamplesReadyCallback {

}.createAudioDeviceModule()
```

### 支持自定义是否启用H264、VP8、VP9编码        
具体实现流程移步[博客](https://blog.csdn.net/csdn_shen0221/article/details/119982257)
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

//use java
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


### 自定义SurfaceViewRenderer，支持画面角度旋转，支持设置垂直镜像；    
使用方法与**org.webrtc.SurfaceViewRenderer**一致，将**org.webrtc.SurfaceViewRenderer**替换成**com.shencoder.webrtcextension.CustomSurfaceViewRenderer**即可。其他使用方法不变。   
  
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
```kotlin
val viewRenderer = findViewById<CustomSurfaceViewRenderer>()
//是否垂直镜像
viewRenderer.setMirrorVertically(false)
//设置旋转角度：0°、90°、180°、270°
viewRenderer.setRotationAngle(RotationAngle.ANGLE_90)
```

### VideoSink代理类(ProxyVideoSink)

```kotlin
val svr = findViewById<SurfaceViewRenderer>(R.id.svr)
val proxy = ProxyVideoSink(svr, object:VideoFrameProcessor{
    override fun onFrameProcessor(frame: VideoFrame): VideoFrame {
        //handle your video frame.
        val newFrame: VideoFrame = handleYourVideoFrame(frame)
        return newFrame;
    }
})
val videoTrack: VideoTrack = ...
videoTrack.addSink(proxy)
```
### 支持VideoProcessor针对NV21格式数据进行叠图功能，添加基础类

效果展示：左上角有个![](https://github.com/shenbengit/WebRTCExtension/blob/master/app/src/main/res/drawable/aaa.png)图片。   

![](https://github.com/shenbengit/WebRTCExtension/blob/master/screenshots/overlay.gif)

- [Android端WebRTC本地音视频采集流程源码分析](https://www.jianshu.com/p/7dc1a6a9d9fd)    
- [NV21数据处理——实现剪裁，叠图](https://www.jianshu.com/p/9ef94aff13d9)

快速实现叠图功能(**OverlayNV21VideoProcessor**)    
> 在nv21数据上进行叠图操作，已经处理不同方向[VideoFrame.rotation]的操作，始终以左上角为起始点；
```kotlin
val videoSource = peerConnectionFactory.createVideoSource(capture.isScreencast)
val bitmap = BitmapFactory.decodeResource(resources, R.drawable.aaa)
val nv12Buffer = Nv21BufferUtil.argb8888BitmapToNv21Buffer(bitmap, true)
//二次处理视频帧数据，叠图
videoSource.setVideoProcessor(
    OverlayNV21VideoProcessor(
        overlayNv21Buffer = nv12Buffer,
        left = 50,
        top = 50,
        //是否存在透明部分的数据，尽量使用不带透明数据的，透明数据处理比较耗时。
        hasTransparent = true
    )
 )
```

基类**BaseNV21VideoProcessor**      
若您想自行处理，可以继承BaseNV21VideoProcessor，从而快速实现。
```kotlin 
class MyNV21VideoProcessor : BaseNV21VideoProcessor() {
    /**
     * 处理NV21数据
     *
     * @param nv21      原始nv21数据，请直接修改此数组的数据，会二次使用
     * @param width     原始nv21数据的宽
     * @param height    原始nv21数据的高
     * @param rotation  原始nv21数据的方向
     *
     * @return 是否处理完成；ture:发送[nv21]，false:则按照原有的流程处理
     */
    override fun handleNV21(nv21: ByteArray, width: Int, height: Int, rotation: Int): Boolean {
        //handle nv21 data
        return true
    }
}

...
val videoSource = peerConnectionFactory.createVideoSource(capture.isScreencast)
videoSource.setVideoProcessor(MyNV21VideoProcessor())
```
**NV21Util**  
NV21数据操作相关方法
```java
//nv21数据剪裁
byte[] cropNv21 = NV21Util.cropNV21(@NonNull byte[] src, int srcWidth, int srcHeight, int clipWidth, int clipHeight, int left, int top);
//叠图，会否超出范围，超出进行剪裁
NV21Util.overlayNV21(@NonNull byte[] nv21, int width, int height, int left, int top, @NonNull byte[] overlayNv21, int overlayWidth, int overlayHeight, boolean transparent);
```

**Nv21BufferUtil**   
转换Nv21Buffer相关方法，使用了库[libyuv-android](https://github.com/crow-misia/libyuv-android)    
```kotlin
//Bitmap.Config.ARGB_8888 格式bitmap转Nv21Buffer
val nv21Buffer: Nv21Buffer = Nv21BufferUtil.argb8888BitmapToNv21Buffer(bitmap: Bitmap, recycleBitmap: Boolean = false)
//Bitmap.Config.RGB_565 格式bitmap转Nv21Buffer
val nv21Buffer: Nv21Buffer = Nv21BufferUtil.rgb565BitmapToNv21Buffer(bitmap: Bitmap, recycleBitmap: Boolean = false)
//nv21 ByteArray转Nv21Buffer
val nv21Buffer: Nv21Buffer = Nv21BufferUtil.nv21ByteArrayToNv21Buffer(nv21: ByteArray, width: Int, height: Int)
```
都看到这儿了，还不给个**star**！
## 作者其他的开源项目
- 基于RecyclerView实现网格分页布局：[PagerGridLayoutManager](https://github.com/shenbengit/PagerGridLayoutManager)
- 基于Netty封装UDP收发工具：[UdpNetty](https://github.com/shenbengit/UdpNetty)
- Android端基于JavaCV实现人脸检测功能：[JavaCV-FaceDetect](https://github.com/shenbengit/JavaCV-FaceDetect)
- 使用Kotlin搭建Android MVVM快速开发框架：[MVVMKit](https://github.com/shenbengit/MVVMKit)

# [License](https://github.com/shenbengit/WebRTCExtension/blob/master/LICENSE)
