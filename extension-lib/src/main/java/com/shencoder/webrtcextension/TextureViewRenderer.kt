package com.shencoder.webrtcextension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.util.AttributeSet
import android.view.TextureView
import androidx.annotation.MainThread
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink


/**
 *
 * @author Shenben
 * @date 2023/10/31 15:47
 * @description
 * @since
 */
open class TextureViewRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    TextureView(context, attrs, defStyleAttr), VideoSink {

    private val internalRendererEvents = object : RendererCommon.RendererEvents {
        override fun onFirstFrameRendered() {
            post { rendererEvents?.onFirstFrameRendered() }
        }

        override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
            val rotatedWidth = if (rotation % 180 == 0) videoWidth else videoHeight
            val rotatedHeight = if (rotation % 180 == 0) videoHeight else videoWidth
            post {
                rotatedFrameWidth = rotatedWidth
                rotatedFrameHeight = rotatedHeight
                requestLayout()

                rendererEvents?.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
            }
        }

    }
    private val resourceName: String = getResourceName()
    val videoLayoutMeasure = RendererCommon.VideoLayoutMeasure()
    val eglRenderer = TextureEglRenderer(resourceName, internalRendererEvents)
    var rendererEvents: RendererCommon.RendererEvents? = null
        private set

    protected var rotatedFrameWidth = 0
    protected var rotatedFrameHeight = 0

    protected val rotationLock = Any()
    protected var rotationAngle = RotationAngle.ANGLE_0
        private set

    init {
        surfaceTextureListener = eglRenderer
    }

    /**
     * Initialize this class, sharing resources with |sharedContext|. It is allowed to call init() to
     * reinitialize the renderer after a previous init()/release() cycle.
     *
     * [release]
     *
     * @param sharedContext
     * @param rendererEvents
     * @param configAttributes
     * @param drawer
     * @param usePresentationTimeStamp
     */
    @JvmOverloads
    @MainThread
    fun init(
        sharedContext: EglBase.Context? = null,
        rendererEvents: RendererCommon.RendererEvents? = null,
        configAttributes: IntArray = EglBase.CONFIG_PLAIN,
        drawer: RendererCommon.GlDrawer = GlRectDrawer(),
        usePresentationTimeStamp: Boolean = false
    ) {
        this.rendererEvents = rendererEvents
        eglRenderer.init(sharedContext, configAttributes, drawer, usePresentationTimeStamp)
    }

    fun setMirror(mirror: Boolean) {
        eglRenderer.setMirror(mirror)
    }

    fun setMirrorVertically(mirror: Boolean) {
        eglRenderer.setMirrorVertically(mirror)
    }

    fun setScalingType(scalingType: RendererCommon.ScalingType) {
        setScalingType(scalingType, scalingType)
    }

    fun setScalingType(
        scalingTypeMatchOrientation: RendererCommon.ScalingType,
        scalingTypeMismatchOrientation: RendererCommon.ScalingType,
    ) {
        ThreadUtils.checkIsOnMainThread()
        videoLayoutMeasure.setScalingType(
            scalingTypeMatchOrientation,
            scalingTypeMismatchOrientation
        )
        requestLayout()
    }

    fun pauseVideo() {
        eglRenderer.pauseVideo()
    }

    fun resumeVideo() {
        eglRenderer.disableFpsReduction()
    }

    /**
     * Set additional rotation angle
     * 设置额外的旋转角度
     * @param rotationAngle
     */
    fun setRotationAngle(rotationAngle: RotationAngle) {
        synchronized(rotationLock) { this.rotationAngle = rotationAngle }
    }

    /**
     * Post a task to clear the SurfaceView to a transparent uniform color.
     */
    fun clearImage() {
        eglRenderer.clearImage()
    }

    /**
     * Register a callback to be invoked when a new video frame has been received.
     *
     * @param listener    The callback to be invoked. The callback will be invoked on the render thread.
     * It should be lightweight and must not call removeFrameListener.
     * @param scale       The scale of the Bitmap passed to the callback, or 0 if no Bitmap is
     * required.
     * @param drawerParam Custom drawer to use for this frame listener.
     */
    @JvmOverloads
    fun addFrameListener(
        listener: EglRenderer.FrameListener,
        scale: Float = 1.0f,
        drawerParam: GlDrawer? = null
    ) {
        eglRenderer.addFrameListener(listener, scale, drawerParam)
    }

    fun removeFrameListener(listener: EglRenderer.FrameListener) {
        eglRenderer.removeFrameListener(listener)
    }

    @JvmOverloads
    fun takeSnapshot(
        scale: Float = 1.0f,
        drawerParam: GlDrawer? = null,
        callback: (bitmap: Bitmap?) -> Unit
    ) {
        addFrameListener(object : EglRenderer.FrameListener {
            private var enable = true
            override fun onFrame(frame: Bitmap?) {
                if (enable) {
                    enable = false
                    post {
                        removeFrameListener(this)
                        callback(frame)
                    }
                }
            }
        }, scale, drawerParam)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val size: Point =
            videoLayoutMeasure.measure(widthSpec, heightSpec, rotatedFrameWidth, rotatedFrameHeight)
        setMeasuredDimension(size.x, size.y)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        eglRenderer.setLayoutAspectRatio((right - left).toFloat() / (bottom - top).toFloat())
    }

    /**
     * Block until any pending frame is returned and all GL resources released, even if an interrupt
     * occurs. If an interrupt occurs during release(), the interrupt flag will be set. This function
     * should be called before the Activity is destroyed and the EGLContext is still valid. If you
     * don't call this function, the GL resources might leak.
     */
    fun release() {
        eglRenderer.release()
    }

    override fun onFrame(videoFrame: VideoFrame) {
        var frame = videoFrame
        synchronized(rotationLock) {
            if (rotationAngle != RotationAngle.ANGLE_0) {
                frame = VideoFrame(
                    frame.buffer,
                    frame.rotation + rotationAngle.angle,
                    frame.timestampNs
                )
            }
        }
        eglRenderer.onFrame(frame)
    }

    private fun getResourceName(): String {
        return kotlin.runCatching { resources.getResourceEntryName(id) + ": " }.getOrDefault("")
    }
}