package com.shencoder.webrtcextension

import android.graphics.SurfaceTexture
import android.view.TextureView.SurfaceTextureListener
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.Logging
import org.webrtc.RendererCommon
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import java.util.concurrent.CountDownLatch


/**
 *
 * @author Shenben
 * @date 2023/10/31 16:01
 * @description
 * @since
 */
open class TextureEglRenderer @JvmOverloads constructor(
    name: String,
    private val rendererEvents: RendererCommon.RendererEvents? = null
) : EglRenderer(name), SurfaceTextureListener {

    private companion object {
        private const val TAG = "TextureEglRenderer"
    }

    private val layoutLock = Any()

    private var isRenderingPaused = false

    private var isFirstFrameRendered = false

    private var rotatedFrameWidth = 0

    private var rotatedFrameHeight = 0

    private var frameRotation = 0

    override fun init(
        sharedContext: EglBase.Context?,
        configAttributes: IntArray,
        drawer: RendererCommon.GlDrawer
    ) {
        init(sharedContext, configAttributes, drawer, false)
    }

    override fun init(
        sharedContext: EglBase.Context?,
        configAttributes: IntArray,
        drawer: RendererCommon.GlDrawer,
        usePresentationTimeStamp: Boolean
    ) {
        synchronized(layoutLock) {
            isFirstFrameRendered = false
            rotatedFrameWidth = 0
            rotatedFrameHeight = 0
            frameRotation = 0
        }
        super.init(sharedContext, configAttributes, drawer, usePresentationTimeStamp)
    }

    override fun setFpsReduction(fps: Float) {
        synchronized(layoutLock) { isRenderingPaused = fps == 0.0f }
        super.setFpsReduction(fps)
    }

    override fun disableFpsReduction() {
        synchronized(layoutLock) { isRenderingPaused = false }
        super.disableFpsReduction()
    }

    override fun pauseVideo() {
        synchronized(layoutLock) { isRenderingPaused = true }
        super.pauseVideo()
    }

    override fun onFrame(frame: VideoFrame) {
        updateFrameDimensionsAndReportEvents(frame)
        super.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        createEglSurface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        logD("onSurfaceTextureSizeChanged: size: $width x $height")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        ThreadUtils.checkIsOnMainThread()
        val completionLatch = CountDownLatch(1)
        releaseEglSurface { completionLatch.countDown() }
        ThreadUtils.awaitUninterruptibly(completionLatch)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        ThreadUtils.checkIsOnMainThread()
    }

    private fun updateFrameDimensionsAndReportEvents(frame: VideoFrame) {
        synchronized(layoutLock) {
            if (!isRenderingPaused) {
                if (!isFirstFrameRendered) {
                    logD("Reporting first rendered frame.")
                    rendererEvents?.onFirstFrameRendered()
                    isFirstFrameRendered = true
                }
                if (rotatedFrameWidth != frame.rotatedWidth ||
                    rotatedFrameHeight != frame.rotatedHeight ||
                    frameRotation != frame.rotation
                ) {
                    logD("Reporting frame resolution changed to ${frame.buffer.width} x  ${frame.buffer.height} with rotation ${frame.rotation}")

                    rendererEvents?.onFrameResolutionChanged(
                        frame.buffer.width,
                        frame.buffer.height,
                        frame.rotation
                    )
                    rotatedFrameWidth = frame.rotatedWidth
                    rotatedFrameHeight = frame.rotatedHeight
                    frameRotation = frame.rotation
                }
            }
        }
    }

    private fun logD(string: String) {
        Logging.d(TAG, "$name: $string")
    }
}