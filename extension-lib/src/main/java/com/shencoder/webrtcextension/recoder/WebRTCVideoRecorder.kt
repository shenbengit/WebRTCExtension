package com.shencoder.webrtcextension.recoder

import org.webrtc.EglBase
import java.io.File

/**
 *
 * @author  ShenBen
 * @date    2022/3/17 17:02
 * @email   714081644@qq.com
 */
class WebRTCVideoRecorder(
    /**
     * 录音文件
     */
    private val file: File,
    private val sharedContext: EglBase.Context,
    /**
     * 是否录制音频
     * recording audio or not.
     */
    private val withAudio: Boolean
) {

}