package com.shencoder.webrtcextension.util

import android.graphics.Bitmap
import io.github.crow_misia.libyuv.*
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer

/**
 *
 * @author  ShenBen
 * @date    2021/12/6 11:05
 * @email   714081644@qq.com
 */
object Nv21BufferUtil {

    /**
     * [Bitmap]To[Nv21Buffer]
     * Only use when [Bitmap.getConfig] is [Bitmap.Config.ARGB_8888]
     *
     * [Bitmap]->[AbgrBuffer]->[Nv21Buffer]
     *
     * @param bitmap
     * @param recycleBitmap is recycle [bitmap]
     */
    @JvmStatic
    @JvmOverloads
    fun argb8888BitmapToNv21Buffer(bitmap: Bitmap, recycleBitmap: Boolean = false): Nv21Buffer {
        val config = bitmap.config
        if (config != Bitmap.Config.ARGB_8888) {
            throw IllegalArgumentException("Unexpected bitmap config:$config")
        }
        val abgrBuffer = AbgrBuffer.allocate(bitmap.width, bitmap.height)
        bitmap.copyPixelsToBuffer(abgrBuffer.asBuffer())

        val nv21Buffer = Nv21Buffer.allocate(bitmap.width, bitmap.height)
        abgrBuffer.convertTo(nv21Buffer)

        abgrBuffer.close()

        if (recycleBitmap) {
            bitmap.recycle()
        }
        return nv21Buffer
    }

    /**
     * [Bitmap]To[Nv21Buffer]
     * Only use when [Bitmap.getConfig] is [Bitmap.Config.RGB_565]
     *
     * [Bitmap]->[Rgb565Buffer]->[I420Buffer]->[Nv21Buffer]
     *
     * @param bitmap
     * @param recycleBitmap is recycle [bitmap]
     */
    @JvmStatic
    @JvmOverloads
    fun rgb565BitmapToNv21Buffer(bitmap: Bitmap, recycleBitmap: Boolean = false): Nv21Buffer {
        val config = bitmap.config
        if (config != Bitmap.Config.RGB_565) {
            throw IllegalArgumentException("Unexpected bitmap config:$config")
        }
        val rgb565Buffer = Rgb565Buffer.allocate(bitmap.width, bitmap.height)
        bitmap.copyPixelsToBuffer(rgb565Buffer.asBuffer())

        val i420Buffer = I420Buffer.allocate(bitmap.width, bitmap.height)
        rgb565Buffer.convertTo(i420Buffer)

        val nv21Buffer = Nv21Buffer.allocate(bitmap.width, bitmap.height)
        i420Buffer.convertTo(nv21Buffer)

        rgb565Buffer.close()
        i420Buffer.close()

        if (recycleBitmap) {
            bitmap.recycle()
        }
        return nv21Buffer
    }

    /**
     * nv21 [ByteArray] to [Nv21Buffer]
     */
    @JvmStatic
    fun nv21ByteArrayToNv21Buffer(nv21: ByteArray, width: Int, height: Int): Nv21Buffer {
        val byteBuffer = ByteBuffer.allocateDirect(nv21.size)
        byteBuffer.put(nv21)
        return Nv21Buffer.wrap(byteBuffer, width, height)
    }
}