package com.shencoder.webrtcextension

import androidx.annotation.CallSuper
import io.github.crow_misia.libyuv.I420Buffer
import io.github.crow_misia.libyuv.Nv21Buffer
import io.github.crow_misia.libyuv.convertTo
import org.webrtc.*
import java.nio.ByteBuffer

/**
 *
 * [VideoFrame.getBuffer]->[VideoFrame.I420Buffer]->[I420Buffer]->[Nv21Buffer]->processing data->[VideoFrame]
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

    final override fun onFrameCaptured(frame: VideoFrame) {
        val buffer = frame.buffer
        val width = buffer.width
        val height = buffer.height
        //先转成VideoFrame.I420Buffer格式，这个转换可能比较耗时，5-8ms左右
        //如果由TextureBuffer转成I420Buffer，并不是一个标准的YUV420P格式，还需要二次转换
        val toI420 = buffer.toI420()

        val planes = arrayOf<ByteBuffer>(toI420.dataY, toI420.dataU, toI420.dataV)
        val strides = intArrayOf(toI420.strideY, toI420.strideU, toI420.strideV)

        val halfWidth = (width + 1).shr(1)
        val halfHeight = (height + 1).shr(1)

        val capacity = width * height
        val halfCapacity = (halfWidth + 1).shr(1) * height

        val planeWidths = intArrayOf(width, halfWidth, halfWidth)
        val planeHeights = intArrayOf(height, halfHeight, halfHeight)

        //使用Jni方法生成，后面释放，避免吃满内存
        val byteBuffer = JniCommon.nativeAllocateByteBuffer(capacity + halfCapacity + halfCapacity)

        //数量为3,分别对应Y、U、V
        for (i in 0..2) {
            if (strides[i] == planeWidths[i]) {
                //这里一般是Y分量
                byteBuffer.put(planes[i])
            } else {
                val sliceLengths = planeWidths[i] * planeHeights[i]

                val limit = byteBuffer.position() + sliceLengths
                byteBuffer.limit(limit)

                //这里一般是UV分量
                //使用byteBuffer.slice()生成的ByteBuffer，和源ByteBuffer数据相互影响
                val copyBuffer = byteBuffer.slice()

                YuvHelper.copyPlane(
                    planes[i],
                    strides[i],
                    copyBuffer,
                    planeWidths[i],
                    planeWidths[i],
                    planeHeights[i]
                )
                byteBuffer.position(limit)
            }
        }

        //标准的I420格式
        val newI420Buffer = I420Buffer.wrap(byteBuffer, width, height) {
            JniCommon.nativeFreeByteBuffer(byteBuffer)
            toI420.release()
        }

        //通过I420转成NV21
        val nv21Buffer = Nv21Buffer.allocate(width, height)
        newI420Buffer.convertTo(nv21Buffer)
        val nv21ByteArray = nv21Buffer.asByteArray()

        newI420Buffer.close()
        nv21Buffer.close()

        //处理nv21数据是否成功
        val success = handleNV21(nv21ByteArray, width, height, frame.rotation)
        if (success.not()) {
            //将处理好的VideoFrame发送出去
            mSink?.onFrame(frame)
            return
        }
        //将处理好的nv21数据转换为NV21Buffer，传给VideoFrame
        val videoFrame = VideoFrame(
            NV21Buffer(nv21ByteArray, width, height, null),
            frame.rotation,
            frame.timestampNs
        )

        videoFrame.retain()

        //将处理好的VideoFrame发送出去
        mSink?.onFrame(videoFrame)

        videoFrame.release()
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