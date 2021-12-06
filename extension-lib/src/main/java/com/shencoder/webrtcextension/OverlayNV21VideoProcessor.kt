package com.shencoder.webrtcextension

import android.util.Log
import androidx.annotation.IntRange
import com.shencoder.webrtcextension.util.NV21Util
import io.github.crow_misia.libyuv.Nv21Buffer
import io.github.crow_misia.libyuv.RotateMode
import io.github.crow_misia.libyuv.rotate
import kotlin.math.abs
import kotlin.math.max
import com.shencoder.webrtcextension.util.Nv21BufferUtil
import org.webrtc.VideoFrame

/**
 * 在nv21数据上进行叠图操作，已经处理不同[VideoFrame.rotation]操作，始终以左上角为起始点；
 * overlay data on nv21 bytes, different of [VideoFrame.rotation] operations have been processed, always starting from the upper left corner;
 *
 *
 * 如果[Nv21Buffer.width]+[left]>=[videoFrameWidth] 或者
 * [Nv21Buffer.height]+[top]>=[videoFrameHeight]则会进行相应的剪裁。
 *
 * if [Nv21Buffer.width]+[left]>=[videoFrameWidth] or [Nv21Buffer.height]+[top]>=[videoFrameHeight], and it will be clipped.
 *
 * @author  ShenBen
 * @date    2021/12/6 09:36
 * @email   714081644@qq.com
 */
class OverlayNV21VideoProcessor @JvmOverloads constructor(
    /**
     * 叠图的[Nv21Buffer]
     * {@see [Nv21BufferUtil]}
     */
    private val overlayNv21Buffer: Nv21Buffer,
    /**
     * 叠图起始左边位置，尽量确保为偶数
     * left position, try to ensure that it is even.
     */
    @IntRange(from = 0) private val left: Int,
    /**
     * 叠图起始上边位置，尽量确保为偶数
     * top position, try to ensure that it is even.
     */
    @IntRange(from = 0) private val top: Int,
    /**
     * [overlayNv21Buffer]中是否存在透明部分的数据；
     * 尽量使用不带透明数据的，透明数据处理比较耗时。
     *
     * if true, it will take more time.
     */
    private val hasTransparent: Boolean = false
) : BaseNV21VideoProcessor() {

    private companion object {
        private const val TAG = "OverlayNV21Processor"
    }

    private lateinit var realNV21ByteArray: ByteArray

    private val lock = Object()

    private var videoFrameWidth = 0
    private var videoFrameHeight = 0

    /**
     * 当前VideoFrame方向
     */
    private var videoFrameRotation = 0

    /**
     * 在camera NV21数据中叠图的left位置
     */
    private var startLeft = left

    /**
     * 在camera NV21数据中叠图的top位置
     */
    private var startTop = top

    private var overlayWidth: Int = overlayNv21Buffer.width

    private var overlayHeight: Int = overlayNv21Buffer.height

    override fun handleNV21(nv21: ByteArray, width: Int, height: Int, rotation: Int): Boolean {
        synchronized(lock) {
            if (videoFrameWidth != width || videoFrameHeight != height || videoFrameRotation != rotation) {
                val adaptOverlayNv21 = adaptOverlayNv21(rotation, width, height)
                if (adaptOverlayNv21.not()) {
                    return false
                }
            }
        }
        //叠图
        NV21Util.overlayNV21(
            nv21,
            width,
            height,
            startLeft,
            startTop,
            realNV21ByteArray,
            overlayWidth,
            overlayHeight,
            hasTransparent
        )
        return true
    }

    /**
     * 根据旋转角度对叠图nv21数据进行转换
     *
     * @param rotation    角度：0°、90°、180°、270°
     * @param frameWidth  原始帧数据的宽度
     * @param frameHeight 原始帧数据的宽度
     *
     * @return true：转换成功，false:转换失败
     */
    private fun adaptOverlayNv21(rotation: Int, frameWidth: Int, frameHeight: Int): Boolean {
        when (rotation) {
            0 -> {//不用旋转，直接处理即可
                //先判断是否合法
                if (left >= frameWidth) {
                    return false
                }
                if (top >= frameHeight) {
                    return false
                }
                overlayWidth = overlayNv21Buffer.width
                overlayHeight = overlayNv21Buffer.height

                val tempByteArray = overlayNv21Buffer.asByteArray()
                realNV21ByteArray =
                    checkNV21ByteArray(tempByteArray, overlayWidth, overlayHeight)

                startLeft = left
                startTop = top
            }
            90 -> {//需要将overlayNV21Buffer旋转270°，然后判断可显示的大小是否需要剪裁，再计算在camera nv21数据中开始叠图的位置
                //判断位置是否合法
                if (top >= frameWidth) {
                    return false
                }
                if (left >= frameHeight) {
                    return false
                }
                //宽高交换
                overlayWidth = overlayNv21Buffer.height
                overlayHeight = overlayNv21Buffer.width
                //将overlayNV21Buffer进行旋转270°
                val rotateNv21Buffer = Nv21Buffer.allocate(overlayWidth, overlayHeight)
                overlayNv21Buffer.rotate(rotateNv21Buffer, RotateMode.ROTATE_270)
                val tempByteArray = rotateNv21Buffer.asByteArray()
                realNV21ByteArray =
                    checkNV21ByteArray(tempByteArray, overlayWidth, overlayHeight)
                rotateNv21Buffer.close()

                startLeft = top

                val tempTop = frameHeight - left - overlayHeight
                if (tempTop < 0) {
                    //超出边界，需要进行剪裁
                    //先计算需要剪裁的高
                    val height = overlayHeight - abs(tempTop)
                    if (height <= 0) {
                        //完全超出边界，不需要进行叠图
                        return false
                    }
                    //二次剪裁
                    val cropNV21 = NV21Util.cropNV21(
                        realNV21ByteArray,
                        overlayWidth,
                        overlayHeight,
                        overlayWidth,
                        height,
                        0,
                        abs(tempTop)
                    ) ?: return false
                    realNV21ByteArray = cropNV21
                    overlayHeight = height
                }
                startTop = max(tempTop, 0)
            }
            180 -> {//需要将overlayNV21Buffer旋转180°，然后判断可显示的大小是否需要剪裁，再计算在camera nv21数据中开始叠图的位置
                //先判断是否合法
                if (left >= frameWidth) {
                    return false
                }
                if (top >= frameHeight) {
                    return false
                }

                overlayWidth = overlayNv21Buffer.width
                overlayHeight = overlayNv21Buffer.height
                //将overlayNV21Buffer数据进行旋转180°
                val rotateNv21Buffer = Nv21Buffer.allocate(overlayWidth, overlayHeight)
                overlayNv21Buffer.rotate(rotateNv21Buffer, RotateMode.ROTATE_180)
                val tempByteArray = rotateNv21Buffer.asByteArray()
                realNV21ByteArray =
                    checkNV21ByteArray(tempByteArray, overlayWidth, overlayHeight)
                rotateNv21Buffer.close()

                val tempLeft = frameWidth - left - overlayWidth
                val tempTop = frameHeight - top - overlayHeight
                if (tempLeft < 0 || tempTop < 0) {
                    //超出边界，需要进行剪裁
                    val width: Int
                    if (tempLeft < 0) {
                        //先计算需要剪裁的宽
                        width = overlayHeight - abs(tempLeft)
                        if (width <= 0) {
                            //完全超出边界，不需要进行叠图
                            return false
                        }
                    } else {
                        width = overlayWidth
                    }
                    val height: Int
                    if (tempTop < 0) {
                        //先计算需要剪裁的高
                        height = overlayHeight - abs(tempTop)
                        if (height <= 0) {
                            //完全超出边界，不需要进行叠图
                            return false
                        }
                    } else {
                        height = overlayHeight
                    }
                    //二次剪裁
                    val cropNV21 = NV21Util.cropNV21(
                        realNV21ByteArray,
                        overlayWidth,
                        overlayHeight,
                        width,
                        height,
                        0,
                        if (tempTop > 0) 0 else abs(tempTop)
                    ) ?: return false
                    realNV21ByteArray = cropNV21
                    overlayWidth = width
                    overlayHeight = height
                }
                startLeft = max(tempLeft, 0)
                startTop = max(tempTop, 0)
            }
            270 -> {//需要将overlayNV21Buffer旋转90°，然后判断可显示的大小是否需要剪裁，再计算在camera nv21数据中开始叠图的位置
                //判断位置是否合法
                if (top >= frameWidth) {
                    return false
                }
                if (left >= frameHeight) {
                    return false
                }
                //宽高交换
                overlayWidth = overlayNv21Buffer.height
                overlayHeight = overlayNv21Buffer.width
                //将overlayNV21Buffer数据进行旋转270°
                val rotateNv21Buffer = Nv21Buffer.allocate(overlayWidth, overlayHeight)
                overlayNv21Buffer.rotate(rotateNv21Buffer, RotateMode.ROTATE_90)
                val tempByteArray = rotateNv21Buffer.asByteArray()
                realNV21ByteArray =
                    checkNV21ByteArray(tempByteArray, overlayWidth, overlayHeight)

                rotateNv21Buffer.close()

                val tempLeft = frameWidth - top - overlayWidth
                if (tempLeft < 0) {
                    //超出边界，需要进行剪裁
                    //先计算需要剪裁的宽
                    val width = overlayHeight - abs(tempLeft)
                    if (width <= 0) {
                        //完全超出边界，不需要进行叠图
                        return false
                    }
                    //二次剪裁
                    val cropNV21 = NV21Util.cropNV21(
                        realNV21ByteArray,
                        overlayWidth,
                        overlayHeight,
                        width,
                        overlayHeight,
                        abs(tempLeft),
                        0
                    ) ?: return false
                    realNV21ByteArray = cropNV21
                    overlayWidth = width
                }
                startLeft = max(tempLeft, 0)
                startTop = left
            }
            else -> {
                Log.e(TAG, "adaptOverlayNv21: unknown rotation: $rotation")
                return false
            }
        }
        videoFrameWidth = frameWidth
        videoFrameHeight = frameHeight
        videoFrameRotation = rotation
        return true
    }
}