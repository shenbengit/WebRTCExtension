package com.shencoder.webrtcextension;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.MainThread;

import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceEglRenderer;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * Display the video stream on a SurfaceView.
 *
 * @author ShenBen
 * @date 2021/10/30 14:53
 * @email 714081644@qq.com
 */
public class CustomSurfaceViewRenderer extends SurfaceView implements SurfaceHolder.Callback, VideoSink, RendererCommon.RendererEvents {
    private static final String TAG = "CustomSurfaceViewRenderer";
    // Cached resource name.
    private final String resourceName = getResourceName();
    protected final RendererCommon.VideoLayoutMeasure videoLayoutMeasure = new RendererCommon.VideoLayoutMeasure();
    protected final SurfaceEglRenderer eglRenderer;
    // Callback for reporting renderer events. Read-only after initilization so no lock required.
    protected RendererCommon.RendererEvents rendererEvents;

    // Accessed only on the main thread.
    protected int rotatedFrameWidth;
    protected int rotatedFrameHeight;
    protected boolean enableFixedSize;
    private int surfaceWidth;
    private int surfaceHeight;
    private final Object rotationLock = new Object();
    /**
     * rotation angle
     */
    private RotationAngle mRotationAngle = RotationAngle.ANGLE_ORIGINAL;

    /**
     * Standard View constructor. In order to render something, you must first call init().
     */
    public CustomSurfaceViewRenderer(Context context) {
        this(context, null);
    }

    /**
     * Standard View constructor. In order to render something, you must first call init().
     */
    public CustomSurfaceViewRenderer(Context context, AttributeSet attrs) {
        super(context, attrs);
        eglRenderer = new SurfaceEglRenderer(resourceName);
        getHolder().addCallback(this);
        getHolder().addCallback(eglRenderer);
    }

    /**
     * Initialize this class, sharing resources with |sharedContext|. It is allowed to call init() to
     * reinitialize the renderer after a previous init()/release() cycle.
     *
     * @see #release()
     */
    @MainThread
    public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents) {
        init(sharedContext, rendererEvents, EglBase.CONFIG_PLAIN, new GlRectDrawer());
    }

    /**
     * Initialize this class, sharing resources with |sharedContext|. The custom |drawer| will be used
     * for drawing frames on the EGLSurface. This class is responsible for calling release() on
     * |drawer|. It is allowed to call init() to reinitialize the renderer after a previous
     * init()/release() cycle.
     *
     * @see #release()
     */
    @MainThread
    public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        ThreadUtils.checkIsOnMainThread();
        this.rendererEvents = rendererEvents;
        rotatedFrameWidth = 0;
        rotatedFrameHeight = 0;
        eglRenderer.init(sharedContext,  /* rendererEvents */this, configAttributes, drawer);
    }

    /**
     * Block until any pending frame is returned and all GL resources released, even if an interrupt
     * occurs. If an interrupt occurs during release(), the interrupt flag will be set. This function
     * should be called before the Activity is destroyed and the EGLContext is still valid. If you
     * don't call this function, the GL resources might leak.
     */
    public void release() {
        eglRenderer.release();
    }

    public RendererCommon.VideoLayoutMeasure getVideoLayoutMeasure() {
        return videoLayoutMeasure;
    }

    public SurfaceEglRenderer getEglRenderer() {
        return eglRenderer;
    }

    public RendererCommon.RendererEvents getRendererEvents() {
        return rendererEvents;
    }

    /**
     * Register a callback to be invoked when a new video frame has been received.
     *
     * @param listener    The callback to be invoked. The callback will be invoked on the render thread.
     *                    It should be lightweight and must not call removeFrameListener.
     * @param scale       The scale of the Bitmap passed to the callback, or 0 if no Bitmap is
     *                    required.
     * @param drawerParam Custom drawer to use for this frame listener.
     */
    public void addFrameListener(EglRenderer.FrameListener listener, float scale, RendererCommon.GlDrawer drawerParam) {
        eglRenderer.addFrameListener(listener, scale, drawerParam);
    }

    /**
     * Register a callback to be invoked when a new video frame has been received. This version uses
     * the drawer of the EglRenderer that was passed in init.
     *
     * @param listener The callback to be invoked. The callback will be invoked on the render thread.
     *                 It should be lightweight and must not call removeFrameListener.
     * @param scale    The scale of the Bitmap passed to the callback, or 0 if no Bitmap is
     *                 required.
     */
    public void addFrameListener(EglRenderer.FrameListener listener, float scale) {
        eglRenderer.addFrameListener(listener, scale);
    }


    public void removeFrameListener(EglRenderer.FrameListener listener) {
        eglRenderer.removeFrameListener(listener);
    }

    /**
     * Enables fixed size for the surface. This provides better performance but might be buggy on some
     * devices. By default this is turned off.
     */
    @MainThread
    public void setEnableHardwareScaler(boolean enabled) {
        ThreadUtils.checkIsOnMainThread();
        enableFixedSize = enabled;
        updateSurfaceSize();
    }

    /**
     * Set if the video stream should be mirrored horizontally or not.
     */
    public void setMirror(boolean mirror) {
        eglRenderer.setMirror(mirror);
    }

    /**
     * Set if the video stream should be mirrored vertically or not.
     *
     * @param mirrorVertically
     */
    public void setMirrorVertically(boolean mirrorVertically) {
        eglRenderer.setMirrorVertically(mirrorVertically);
    }

    public void setRotationAngle(RotationAngle rotationAngle) {
        synchronized (rotationLock) {
            mRotationAngle = rotationAngle;
        }
    }

    /**
     * Set how the video will fill the allowed layout area.
     */
    @MainThread
    public void setScalingType(RendererCommon.ScalingType scalingType) {
        ThreadUtils.checkIsOnMainThread();
        videoLayoutMeasure.setScalingType(scalingType);
        requestLayout();
    }

    @MainThread
    public void setScalingType(RendererCommon.ScalingType scalingTypeMatchOrientation, RendererCommon.ScalingType scalingTypeMismatchOrientation) {
        ThreadUtils.checkIsOnMainThread();
        videoLayoutMeasure.setScalingType(scalingTypeMatchOrientation, scalingTypeMismatchOrientation);
        requestLayout();
    }

    /**
     * Limit render framerate.
     *
     * @param fps Limit render framerate to this value, or use Float.POSITIVE_INFINITY to disable fps
     *            reduction.
     */
    public void setFpsReduction(float fps) {
        eglRenderer.setFpsReduction(fps);
    }

    public void disableFpsReduction() {
        eglRenderer.disableFpsReduction();
    }

    public void pauseVideo() {
        eglRenderer.pauseVideo();
    }

    /**
     * VideoSink interface.
     *
     * @param frame VideoFrame
     */
    public void onFrame(VideoFrame frame) {
        synchronized (rotationLock) {
            if (mRotationAngle != RotationAngle.ANGLE_ORIGINAL) {
                frame = new VideoFrame(frame.getBuffer(), mRotationAngle.getAngle(), frame.getTimestampNs());
            }
        }
        eglRenderer.onFrame(frame);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        Point size = videoLayoutMeasure.measure(widthSpec, heightSpec, rotatedFrameWidth, rotatedFrameHeight);
        setMeasuredDimension(size.x, size.y);
        logD("onMeasure(). New size: " + size.x + "x" + size.y);
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        eglRenderer.setLayoutAspectRatio((float) (right - left) / (float) (bottom - top));
        updateSurfaceSize();
    }

    @MainThread
    private void updateSurfaceSize() {
        ThreadUtils.checkIsOnMainThread();
        if (enableFixedSize && rotatedFrameWidth != 0 && rotatedFrameHeight != 0 && getWidth() != 0 && getHeight() != 0) {
            float layoutAspectRatio = (float) getWidth() / (float) getHeight();
            float frameAspectRatio = (float) rotatedFrameWidth / (float) rotatedFrameHeight;
            int drawnFrameWidth;
            int drawnFrameHeight;
            if (frameAspectRatio > layoutAspectRatio) {
                drawnFrameWidth = (int) ((float) rotatedFrameHeight * layoutAspectRatio);
                drawnFrameHeight = rotatedFrameHeight;
            } else {
                drawnFrameWidth = rotatedFrameWidth;
                drawnFrameHeight = (int) ((float) rotatedFrameWidth / layoutAspectRatio);
            }
            // Aspect ratio of the drawn frame and the view is the same.
            int width = Math.min(getWidth(), drawnFrameWidth);
            int height = Math.min(getHeight(), drawnFrameHeight);
            logD("updateSurfaceSize. Layout size: " + getWidth() + "x" + getHeight() + ", frame size: " + rotatedFrameWidth + "x" + rotatedFrameHeight + ", requested surface size: " + width + "x" + height + ", old surface size: " + surfaceWidth + "x" + surfaceHeight);
            if (width != surfaceWidth || height != surfaceHeight) {
                surfaceWidth = width;
                surfaceHeight = height;
                getHolder().setFixedSize(width, height);
            }
        } else {
            surfaceWidth = surfaceHeight = 0;
            getHolder().setSizeFromLayout();
        }

    }

    /**
     * SurfaceHolder.Callback interface.
     *
     * @param holder
     */
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceWidth = surfaceHeight = 0;
        updateSurfaceSize();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    private String getResourceName() {
        try {
            return getResources().getResourceEntryName(getId());
        } catch (Resources.NotFoundException var2) {
            return "";
        }
    }

    /**
     * Post a task to clear the SurfaceView to a transparent uniform color.
     */
    public void clearImage() {
        eglRenderer.clearImage();
    }

    public void onFirstFrameRendered() {
        if (rendererEvents != null) {
            rendererEvents.onFirstFrameRendered();
        }

    }

    public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
        if (rendererEvents != null) {
            rendererEvents.onFrameResolutionChanged(videoWidth, videoHeight, rotation);
        }

        int rotatedWidth = rotation != 0 && rotation != 180 ? videoHeight : videoWidth;
        int rotatedHeight = rotation != 0 && rotation != 180 ? videoWidth : videoHeight;
        // run immediately if possible for ui thread tests
        postOrRun(() -> {
            rotatedFrameWidth = rotatedWidth;
            rotatedFrameHeight = rotatedHeight;
            updateSurfaceSize();
            requestLayout();
        });
    }

    private void postOrRun(Runnable r) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            r.run();
        } else {
            post(r);
        }
    }

    private void logD(String string) {
        Logging.d(TAG, resourceName + ": " + string);
    }
}
