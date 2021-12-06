package com.shencoder.webrtcextension

import androidx.annotation.CallSuper
import org.webrtc.*

/**
 * 仅处理[NV21Buffer]格式数据基类，会使用反射拿到[NV21Buffer.data]；
 * 如果想使用此类，则需要[Camera1Enumerator.captureToTexture]是false，
 * 即[Camera1Capturer.captureToTexture]为false
 *
 * you must be call [Camera1Enumerator(false)]
 *
 * @author  ShenBen
 * @date    2021/12/6 08:44
 * @email   714081644@qq.com
 */
abstract class BaseNV21VideoProcessor : VideoProcessor {

    protected var mSink: VideoSink? = null

    @CallSuper
    override fun setSink(sink: VideoSink?) {
        mSink = sink
    }

    @CallSuper
    override fun onCapturerStarted(success: Boolean) {

    }

    @CallSuper
    override fun onCapturerStopped() {

    }

    /**
     * 此方法会间接调用[NV21Buffer.cropAndScale]转为[VideoFrame.I420Buffer]，所以要在super之前处理
     */
    final override fun onFrameCaptured(
        frame: VideoFrame,
        parameters: VideoProcessor.FrameAdaptationParameters
    ) {
        if (parameters.drop) {
            //直接丢弃
            return
        }
        val buffer = frame.buffer
        if (buffer is NV21Buffer) {
            //进处理NV21Buffer
            //通过反射拿到[NV21Buffer.data]
            val nv21Class = buffer::class.java
            val declaredField = nv21Class.getDeclaredField("data")
            declaredField.isAccessible = true
            val bytes = declaredField.get(buffer) as ByteArray
            val nv21Bytes: ByteArray = checkNV21ByteArray(bytes, buffer.width, buffer.height)
            //处理nv21数据是否成功
            val success = handleNV21(nv21Bytes, buffer.width, buffer.height, frame.rotation)
            if (success.not()) {
                super.onFrameCaptured(frame, parameters)
                return
            }
            //将处理好的nv21数据转换为NV21Buffer，传给VideoFrame
            val nv21Buffer = NV21Buffer(nv21Bytes, buffer.width, buffer.height, null)
            val videoFrame = VideoFrame(nv21Buffer, frame.rotation, frame.timestampNs)
            //调用super方法，传入新生成的VideoFrame
            super.onFrameCaptured(videoFrame, parameters)
            videoFrame.release()
        } else {
            super.onFrameCaptured(frame, parameters)
        }
    }

    @CallSuper
    override fun onFrameCaptured(frame: VideoFrame) {
        //将处理好的VideoFrame发送出去
        mSink?.onFrame(frame)
    }

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
    abstract fun handleNV21(
        nv21: ByteArray,
        width: Int,
        height: Int,
        rotation: Int
    ): Boolean

    protected fun checkNV21ByteArray(nv21: ByteArray, width: Int, height: Int): ByteArray {
        //标准大小
        val size = width * height * 3 / 2
        return if (size == nv21.size) {
            nv21
        } else {
            //宽或高为奇数时，可能会出现比标准大小大的情况，要进行剪裁
            val byteArray = ByteArray(size)
            System.arraycopy(nv21, 0, byteArray, 0, size)
            byteArray
        }
    }
}