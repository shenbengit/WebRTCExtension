package com.shencoder.webrtcextensiondemo

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodecInfo
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.elvishew.xlog.XLog
import com.shencoder.mvvmkit.base.view.BaseSupportActivity
import com.shencoder.mvvmkit.base.viewmodel.DefaultViewModel
import com.shencoder.mvvmkit.util.MoshiUtil
import com.shencoder.mvvmkit.util.toastError
import com.shencoder.mvvmkit.util.toastWarning
import com.shencoder.webrtcextension.OverlayNV21VideoProcessor
import com.shencoder.webrtcextension.ProxyVideoSink
import com.shencoder.webrtcextension.util.Nv21BufferUtil
import com.shencoder.webrtcextensiondemo.constant.Constant
import com.shencoder.webrtcextensiondemo.databinding.ActivityMainBinding
import com.shencoder.webrtcextensiondemo.http.RetrofitClient
import com.shencoder.webrtcextensiondemo.http.bean.SrsRequestBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.setAudioTrackSamplesReadyCallback

/**
 * 事例demo
 * 危险权限自行处理
 */
class MainActivity : BaseSupportActivity<DefaultViewModel, ActivityMainBinding>() {

    private companion object {
        private const val URL =
            "webrtc://${Constant.SRS_SERVER_IP}/live/camera"
    }

    private val retrofitClient by inject<RetrofitClient>()

    private val eglBaseContext = EglBase.create().eglBaseContext
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var videoTrack: VideoTrack? = null
    private var cameraVideoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private lateinit var audioDeviceModule: JavaAudioDeviceModule
    private lateinit var proxyVideoSink: ProxyVideoSink
    override fun getLayoutId(): Int {
        return R.layout.activity_main
    }

    override fun injectViewModel(): Lazy<DefaultViewModel> {
        return inject()
    }

    override fun getViewModelId(): Int {
        return 0
    }

    override fun initView() {
        mBinding.etUrl.setText(URL)
        mBinding.svr.run {
            init(eglBaseContext, null)
            //垂直镜像
//            setMirrorVertically(false)
            //旋转方向
//            setRotationAngle(RotationAngle.ANGLE_0)
        }

        proxyVideoSink = ProxyVideoSink(mBinding.svr, object : ProxyVideoSink.VideoFrameProcessor {
            override fun onFrameProcessor(frame: VideoFrame): VideoFrame {
                //自行处理帧数据
                return frame
            }
        })

        mBinding.btnPush.setOnClickListener {
            val url = mBinding.etUrl.text.toString().trim()
            if (url.isBlank()) {
                toastWarning("请输入拉流地址")
                return@setOnClickListener
            }
            initPushRTC(url)
        }
    }

    override fun initData(savedInstanceState: Bundle?) {
        val options = PeerConnectionFactory.Options()
        val encoderFactory = createCustomVideoEncoderFactory(eglBaseContext, true, true,
            object : VideoEncoderSupportedCallback {
                override fun isSupportedVp8(info: MediaCodecInfo): Boolean {
                    return true
                }

                override fun isSupportedVp9(info: MediaCodecInfo): Boolean {
                    return true
                }

                override fun isSupportedH264(info: MediaCodecInfo): Boolean {
                    //自行判断是否支持H264编码
                    return true
                }
            })
        audioDeviceModule = JavaAudioDeviceModule.builder(this)
            .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                override fun onWebRtcAudioTrackStart() {
                    audioDeviceModule.setAudioTrackSamplesReadyCallback {
                        //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
                        val audioFormat = it.audioFormat
                        val channelCount = it.channelCount
                        val sampleRate = it.sampleRate
                        //pcm格式数据
                        val data = it.data
                    }
                    //如果使用Java
//                    JavaAudioDeviceModuleExtKt.setAudioTrackSamplesReadyCallback(
//                        audioDeviceModule,
//                        audioSamples -> {
//                        //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
//                        int audioFormat = audioSamples.getAudioFormat();
//                        int channelCount = audioSamples.getChannelCount();
//                        int sampleRate = audioSamples.getSampleRate();
//                        //pcm格式数据
//                        byte[] data = audioSamples.getData ();
//                    });
                }

                override fun onWebRtcAudioTrackStop() {
                }

            })
            .setSamplesReadyCallback {

            }.createAudioDeviceModule()
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }


    private fun initPushRTC(url: String) {
        val createAudioSource = peerConnectionFactory.createAudioSource(createAudioConstraints())
        val audioTrack =
            peerConnectionFactory.createAudioTrack("local_audio_track", createAudioSource)

        cameraVideoCapturer = createVideoCapture(this)
        cameraVideoCapturer?.let { capture ->
            val videoSource = peerConnectionFactory.createVideoSource(capture.isScreencast)
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.aaa)
            val nv12Buffer = Nv21BufferUtil.argb8888BitmapToNv21Buffer(bitmap, true)
            //二次处理视频帧数据，叠图
            videoSource.setVideoProcessor(
                OverlayNV21VideoProcessor(
                    overlayNv21Buffer = nv12Buffer,
                    left = 50,
                    top = 50,
                    hasTransparent = true
                )
            )
            videoTrack =
                peerConnectionFactory.createVideoTrack("local_video_track", videoSource).apply {
                    addSink(proxyVideoSink)
                }
            surfaceTextureHelper =
                SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext)
            capture.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
            capture.startCapture(1920, 1080, 30)
        }

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            PeerConnectionObserver()
        )?.apply {
            videoTrack?.let {
                addTransceiver(
                    it,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                )
            }
            addTransceiver(
                audioTrack,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
            )
        }

        peerConnection?.let { connection ->
            connection.createOffer(object : SdpAdapter("createOffer") {
                override fun onCreateSuccess(description: SessionDescription?) {
                    super.onCreateSuccess(description)
                    description?.let {
                        if (it.type == SessionDescription.Type.OFFER) {
                            val offerSdp = it.description
                            connection.setLocalDescription(SdpAdapter("setLocalDescription"), it)

                            val srsBean = SrsRequestBean(
                                it.description,
                                url
                            )

                            val toJson = MoshiUtil.toJson(srsBean)
                            println("push-json:${toJson}")
                            //请求srs
                            lifecycleScope.launch {
                                val result = try {
                                    withContext(Dispatchers.IO) {
                                        retrofitClient.apiService.publish(srsBean)
                                    }
                                } catch (e: Exception) {
                                    println("push网络请求出错：${e.printStackTrace()}")
                                    toastError("push网络请求出错：${e.printStackTrace()}")
                                    null
                                }

                                result?.let { bean ->
                                    if (bean.code == 0) {
                                        XLog.i("push网络成功，code：${bean.code}")
                                        val remoteSdp = SessionDescription(
                                            SessionDescription.Type.ANSWER,
                                            convertAnswerSdp(offerSdp, bean.sdp)
                                        )
                                        connection.setRemoteDescription(
                                            SdpAdapter("setRemoteDescription"),
                                            remoteSdp
                                        )
                                    } else {
                                        XLog.w("push网络请求失败，code：${bean.code}")
                                        toastWarning("push网络请求失败，code：${bean.code}")
                                    }
                                }
                            }
                        }
                    }
                }
            }, MediaConstraints())
        }
    }

    private fun createAudioConstraints(): MediaConstraints {
        val audioConstraints = MediaConstraints()
        //回声消除
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation",
                "true"
            )
        )
        //自动增益
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        //高音过滤
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        //噪音处理
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression",
                "true"
            )
        )
        return audioConstraints
    }

    private fun createVideoCapture(context: Context): CameraVideoCapturer? {
        val enumerator: CameraEnumerator =
            if (Camera2Enumerator.isSupported(context)) {
                Camera2Enumerator(context)
            } else {
                Camera1Enumerator()
            }
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        for (name in enumerator.deviceNames) {
            if (enumerator.isBackFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    /**
     * 转换AnswerSdp
     * @param offerSdp offerSdp：创建offer时生成的sdp
     * @param answerSdp answerSdp：网络请求srs服务器返回的sdp
     * @return 转换后的AnswerSdp
     */
    private fun convertAnswerSdp(offerSdp: String, answerSdp: String?): String {
        if (answerSdp.isNullOrBlank()) {
            return ""
        }
        val indexOfOfferVideo = offerSdp.indexOf("m=video")
        val indexOfOfferAudio = offerSdp.indexOf("m=audio")
        if (indexOfOfferVideo == -1 || indexOfOfferAudio == -1) {
            return answerSdp
        }
        val indexOfAnswerVideo = answerSdp.indexOf("m=video")
        val indexOfAnswerAudio = answerSdp.indexOf("m=audio")
        if (indexOfAnswerVideo == -1 || indexOfAnswerAudio == -1) {
            return answerSdp
        }

        val isFirstOfferVideo = indexOfOfferVideo < indexOfOfferAudio
        val isFirstAnswerVideo = indexOfAnswerVideo < indexOfAnswerAudio
        return if (isFirstOfferVideo == isFirstAnswerVideo) {
            //顺序一致
            answerSdp
        } else {
            //需要调换顺序
            buildString {
                append(answerSdp.substring(0, indexOfAnswerVideo.coerceAtMost(indexOfAnswerAudio)))
                append(
                    answerSdp.substring(
                        indexOfAnswerVideo.coerceAtLeast(indexOfOfferVideo),
                        answerSdp.length
                    )
                )
                append(
                    answerSdp.substring(
                        indexOfAnswerVideo.coerceAtMost(indexOfAnswerAudio),
                        indexOfAnswerVideo.coerceAtLeast(indexOfOfferVideo)
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mBinding.svr.release()
        audioDeviceModule.release()
        cameraVideoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        videoTrack?.dispose()
        peerConnection?.dispose()
        peerConnectionFactory.dispose()
    }

}