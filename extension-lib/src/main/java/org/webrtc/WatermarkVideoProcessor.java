/*
 *  Copyright 2026 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source tree.
 */

package org.webrtc;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 给视频帧绘制一组或多组 Bitmap 水印的 {@link VideoProcessor}。
 *
 * <p>水印坐标按“视觉正方向”的画面理解：{@code (0, 0)} 表示左上角，x 向右增长，
 * y 向下增长，画面尺寸使用 {@link VideoFrame#getRotatedWidth()} x
 * {@link VideoFrame#getRotatedHeight()}。处理后的输出帧是 RGB
 * {@link VideoFrame.TextureBuffer}，并且 rotation 固定为 0。
 *
 * <p>也就是说，调用方只需要按用户看到的正常方向配置水印位置，本类内部负责处理
 * WebRTC frame rotation、纹理 transform、FBO 离屏渲染和 OpenGL 坐标系差异。
 */
public class WatermarkVideoProcessor implements VideoProcessor {
    private static final String TAG = "WatermarkVideoProcessor";

    // 水印片元 shader：从水印纹理中采样当前像素，并乘上外部传入的整体透明度 alpha。
    private static final String WATERMARK_FRAGMENT_SHADER =
            "uniform float alpha;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = sample(tc) * alpha;\n"
                    + "}\n";

    // Bitmap 像素数据的原点在左上角，OpenGL 纹理坐标的原点在左下角；这里预先做一次 Y 翻转。
    private static final float[] BITMAP_TEXTURE_MATRIX = createBitmapTextureMatrix();

    // 水印锚点。offset 的方向按视觉坐标理解：左/上是正向偏移，右/下是距离边缘的内边距。
    public enum Anchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    private enum SizeMode {PIXELS, FRAME_WIDTH_FRACTION, FRAME_HEIGHT_FRACTION}

    public static final class Watermark {
        // 原始水印图。为了性能，实际绘制时会缓存成 GL_TEXTURE_2D。
        public final Bitmap bitmap;
        public final Anchor anchor;
        public final int offsetXPx;
        public final int offsetYPx;
        public final float alpha;

        private final SizeMode sizeMode;
        private final float widthValue;
        private final float heightValue;

        private Watermark(Bitmap bitmap, SizeMode sizeMode, float widthValue, float heightValue,
                          Anchor anchor, int offsetXPx, int offsetYPx, float alpha) {
            if (bitmap == null) {
                throw new IllegalArgumentException("bitmap must not be null.");
            }
            if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                throw new IllegalArgumentException("bitmap must have a positive size.");
            }
            if (anchor == null) {
                throw new IllegalArgumentException("anchor must not be null.");
            }
            this.bitmap = bitmap;
            this.sizeMode = sizeMode;
            this.widthValue = widthValue;
            this.heightValue = heightValue;
            this.anchor = anchor;
            this.offsetXPx = offsetXPx;
            this.offsetYPx = offsetYPx;
            this.alpha = clamp(alpha, 0f, 1f);
        }

        /**
         * 使用固定像素尺寸创建水印。widthPx 或 heightPx 可以传 0，表示按另一边和 Bitmap
         * 原始宽高比自动计算。
         */
        public static Watermark withPixelSize(Bitmap bitmap, Anchor anchor, int offsetXPx, int offsetYPx) {
            return withPixelSize(bitmap, bitmap.getWidth(), bitmap.getHeight(), anchor, offsetXPx, offsetYPx);
        }

        /**
         * 使用固定像素尺寸创建水印。widthPx 或 heightPx 可以传 0，表示按另一边和 Bitmap
         * 原始宽高比自动计算。
         */
        public static Watermark withPixelSize(Bitmap bitmap, int widthPx, int heightPx, Anchor anchor, int offsetXPx, int offsetYPx) {
            if (widthPx <= 0 && heightPx <= 0) {
                throw new IllegalArgumentException("widthPx or heightPx must be positive.");
            }
            return new Watermark(bitmap, SizeMode.PIXELS, widthPx, heightPx, anchor, offsetXPx,
                    offsetYPx, 1f);
        }

        /**
         * 使用视频画面宽度比例创建水印。例如 0.14f 表示水印宽度占最终视频视觉宽度的 14%。
         */
        public static Watermark withWidthFraction(Bitmap bitmap, float widthFraction, Anchor anchor, int offsetXPx, int offsetYPx) {
            if (widthFraction <= 0f) {
                throw new IllegalArgumentException("widthFraction must be positive.");
            }
            return new Watermark(bitmap, SizeMode.FRAME_WIDTH_FRACTION, widthFraction, 0f, anchor,
                    offsetXPx, offsetYPx, 1f);
        }

        /**
         * 使用视频画面高度比例创建水印。例如 0.10f 表示水印高度占最终视频视觉高度的 10%。
         */
        public static Watermark withHeightFraction(Bitmap bitmap, float heightFraction, Anchor anchor, int offsetXPx, int offsetYPx) {
            if (heightFraction <= 0f) {
                throw new IllegalArgumentException("heightFraction must be positive.");
            }
            return new Watermark(bitmap, SizeMode.FRAME_HEIGHT_FRACTION, 0f, heightFraction, anchor,
                    offsetXPx, offsetYPx, 1f);
        }

        /**
         * 返回一个只修改整体透明度的新水印配置，原对象保持不变。
         */
        public Watermark withAlpha(float alpha) {
            return new Watermark(bitmap, sizeMode, widthValue, heightValue, anchor, offsetXPx, offsetYPx, alpha);
        }

        /**
         * 返回一个只修改偏移量的新水印配置，偏移量仍按视觉坐标理解。
         */
        public Watermark withOffset(int offsetXPx, int offsetYPx) {
            return new Watermark(bitmap, sizeMode, widthValue, heightValue, anchor, offsetXPx, offsetYPx, alpha);
        }

        /**
         * 返回一个只修改锚点的新水印配置。
         */
        public Watermark withAnchor(Anchor anchor) {
            return new Watermark(bitmap, sizeMode, widthValue, heightValue, anchor, offsetXPx, offsetYPx, alpha);
        }

        /**
         * 把水印配置解析成最终绘制区域。
         *
         * <p>这里的 frameWidth/frameHeight 已经是旋转后的视觉尺寸，所以计算出来的 x/y
         * 也是视觉坐标系里的左上角坐标。
         */
        private ResolvedWatermark resolve(int frameWidth, int frameHeight) {
            final float bitmapAspect = bitmap.getWidth() / (float) bitmap.getHeight();
            int width;
            int height;
            // 先根据配置计算水印在最终视频画面上的显示尺寸；比例模式会自动保持 Bitmap 宽高比。
            switch (sizeMode) {
                case PIXELS:
                    width = Math.round(widthValue);
                    height = Math.round(heightValue);
                    if (width <= 0) {
                        width = Math.round(height * bitmapAspect);
                    }
                    if (height <= 0) {
                        height = Math.round(width / bitmapAspect);
                    }
                    break;
                case FRAME_WIDTH_FRACTION:
                    width = Math.round(frameWidth * widthValue);
                    height = Math.round(width / bitmapAspect);
                    break;
                case FRAME_HEIGHT_FRACTION:
                    height = Math.round(frameHeight * heightValue);
                    width = Math.round(height * bitmapAspect);
                    break;
                default:
                    throw new IllegalStateException("Unknown size mode.");
            }

            if (width <= 0 || height <= 0) {
                return ResolvedWatermark.empty();
            }

            int x;
            int y;
            // 再根据锚点把 offset 转换成视觉坐标系中的左上角坐标。
            switch (anchor) {
                case TOP_LEFT:
                    x = offsetXPx;
                    y = offsetYPx;
                    break;
                case TOP_CENTER:
                    x = (frameWidth - width) / 2 + offsetXPx;
                    y = offsetYPx;
                    break;
                case TOP_RIGHT:
                    x = frameWidth - width - offsetXPx;
                    y = offsetYPx;
                    break;
                case CENTER_LEFT:
                    x = offsetXPx;
                    y = (frameHeight - height) / 2 + offsetYPx;
                    break;
                case CENTER:
                    x = (frameWidth - width) / 2 + offsetXPx;
                    y = (frameHeight - height) / 2 + offsetYPx;
                    break;
                case CENTER_RIGHT:
                    x = frameWidth - width - offsetXPx;
                    y = (frameHeight - height) / 2 + offsetYPx;
                    break;
                case BOTTOM_LEFT:
                    x = offsetXPx;
                    y = frameHeight - height - offsetYPx;
                    break;
                case BOTTOM_CENTER:
                    x = (frameWidth - width) / 2 + offsetXPx;
                    y = frameHeight - height - offsetYPx;
                    break;
                case BOTTOM_RIGHT:
                    x = frameWidth - width - offsetXPx;
                    y = frameHeight - height - offsetYPx;
                    break;
                default:
                    throw new IllegalStateException("Unknown anchor.");
            }

            // 不让水印跑出画面外。后续如果需要允许半透明水印被裁切，可以把这里改成不 clamp。
            x = clamp(x, 0, Math.max(0, frameWidth - width));
            y = clamp(y, 0, Math.max(0, frameHeight - height));
            return new ResolvedWatermark(x, y, width, height);
        }
    }

    private static final class ResolvedWatermark {
        final int visualX;
        final int visualY;
        final int width;
        final int height;

        ResolvedWatermark(int visualX, int visualY, int width, int height) {
            this.visualX = visualX;
            this.visualY = visualY;
            this.width = width;
            this.height = height;
        }

        static ResolvedWatermark empty() {
            return new ResolvedWatermark(0, 0, 0, 0);
        }

        boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }

    private static final class WatermarkTexture {
        final int textureId;
        final int width;
        final int height;
        // Bitmap 内容变化时 generationId 会变化，用它判断缓存的 GL 纹理是否需要重新上传。
        final int generationId;

        WatermarkTexture(int textureId, int width, int height, int generationId) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.generationId = generationId;
        }
    }

    private static final class WatermarkShaderCallbacks implements GlGenericDrawer.ShaderCallbacks {
        private int alphaLocation;
        private float alpha = 1f;

        void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        @Override
        public void onNewShader(GlShader shader) {
            // shader 第一次创建或切换输入类型时拿 uniform 位置，避免每帧重复查找。
            alphaLocation = shader.getUniformLocation("alpha");
        }

        @Override
        public void onPrepareShader(GlShader shader, float[] texMatrix, int frameWidth,
                                    int frameHeight, int viewportWidth, int viewportHeight) {
            GLES20.glUniform1f(alphaLocation, alpha);
        }
    }

    // 所有 OpenGL 调用都必须在同一个有 current EGLContext 的线程上执行。
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    // 用 Bitmap 对象身份作为 key；同内容但不同 Bitmap 实例会各自上传纹理。
    private final IdentityHashMap<Bitmap, WatermarkTexture> watermarkTextures = new IdentityHashMap<>();

    @Nullable
    private volatile VideoSink sink;
    private volatile List<Watermark> watermarks = Collections.emptyList();
    private volatile boolean enabled = true;

    @Nullable
    private EglBase eglBase;
    @Nullable
    private VideoFrameDrawer frameDrawer;
    @Nullable
    private GlRectDrawer frameGlDrawer;
    @Nullable
    private WatermarkShaderCallbacks watermarkShaderCallbacks;
    @Nullable
    private GlGenericDrawer watermarkDrawer;
    @Nullable
    private YuvConverter yuvConverter;

    private int pendingOutputTextures;
    // dispose() 以后不能立刻释放 EGL：下游可能还持有本类输出的纹理帧。
    private boolean releaseRequested;
    private boolean glReleased;

    public WatermarkVideoProcessor(EglBase.Context sharedContext) {
        this(sharedContext, "WatermarkVideoProcessor");
    }

    public WatermarkVideoProcessor(EglBase.Context sharedContext, String threadName) {
        if (sharedContext == null) {
            throw new IllegalArgumentException("sharedContext must not be null.");
        }
        renderThread = new HandlerThread(threadName);
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        try {
            ThreadUtils.invokeAtFrontUninterruptibly(renderHandler, () -> initGl(sharedContext));
        } catch (RuntimeException e) {
            renderThread.quit();
            throw e;
        }
    }

    /**
     * 开关总入口，关闭后会直接透传帧，不再做水印合成。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 批量设置水印列表。
     *
     * <p>这里不会立刻重建所有纹理，只会在 GL 线程上清理不再使用的旧纹理，保留仍在使用的
     * Bitmap 对应缓存。
     */
    public void setWatermarks(List<Watermark> watermarks) {
        if (watermarks == null || watermarks.isEmpty()) {
            this.watermarks = Collections.emptyList();
        } else {
            this.watermarks = Collections.unmodifiableList(watermarks);
        }
        if (!disposed.get()) {
            // 配置变更后，异步清理不再使用的水印纹理，避免长期占用显存。
            renderHandler.post(() -> releaseUnusedWatermarkTextures(this.watermarks));
        }
    }

    public void setWatermarks(Watermark... watermarks) {
        if (watermarks == null || watermarks.length == 0) {
            setWatermarks(Collections.emptyList());
            return;
        }
        final List<Watermark> watermarkList = new ArrayList<>(watermarks.length);
        Collections.addAll(watermarkList, watermarks);
        setWatermarks(watermarkList);
    }

    /**
     * 释放处理器持有的 GL/EGL 资源。
     *
     * <p>调用后不会立刻销毁输出纹理，因为下游可能还在持有刚刚送出去的帧；等这些输出帧
     * 都被 release 之后，才会真正销毁 EGL 上下文和 shader/texture 资源。
     */
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        sink = null;
        watermarks = Collections.emptyList();
        renderHandler.post(() -> {
            releaseRequested = true;
            maybeReleaseGlResources();
        });
    }

    @Override
    public void onCapturerStarted(boolean success) {
    }

    @Override
    public void onCapturerStopped() {
    }

    /**
     * 入口方法：收到一帧后先判断是否需要直接透传，再决定是否在 GL 线程里做水印合成。
     */
    @Override
    public void onFrameCaptured(VideoFrame frame) {
        if (disposed.get()) {
            return;
        }
        if (!enabled || watermarks.isEmpty()) {
            // 第一版只处理 TextureBuffer。I420/NV21 等 CPU buffer 先透传，后续可扩展上传到 GL 后复用本流程。
            forwardFrame(frame);
            return;
        }

        // 后续会切到 GL 线程异步处理，所以这里先 retain，避免调用方释放后 buffer 提前失效。
        frame.retain();
        renderHandler.post(() -> {
            try {
                processTextureFrame(frame);
            } finally {
                frame.release();
            }
        });
    }

    /**
     * 设置输出端 sink，也就是处理完成后帧要送往哪里。
     */
    @Override
    public void setSink(@Nullable VideoSink sink) {
        this.sink = sink;
    }

    /**
     * 初始化本类自己的 GL 环境。
     *
     * <p>这里会创建一个和外部共享 context 的离屏 pbuffer surface。这样既能访问输入纹理，
     * 又不会直接画到屏幕上。
     */
    private void initGl(EglBase.Context sharedContext) {
        // 创建一个共享 EGLContext 的离屏 pbuffer surface；本类不直接画到屏幕，只画到 FBO 纹理。
        eglBase = EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER);
        eglBase.createDummyPbufferSurface();
        eglBase.makeCurrent();
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);

        frameDrawer = new VideoFrameDrawer();
        frameGlDrawer = new GlRectDrawer();
        watermarkShaderCallbacks = new WatermarkShaderCallbacks();
        watermarkDrawer = new GlGenericDrawer(WATERMARK_FRAGMENT_SHADER, watermarkShaderCallbacks);
        yuvConverter = new YuvConverter();
    }

    /**
     * 把输入帧画到离屏 FBO 里，再叠加水印，最后包装成新的输出纹理帧。
     */
    private void processTextureFrame(VideoFrame frame) {
        if (disposed.get() || releaseRequested || glReleased) {
            return;
        }
        final VideoSink currentSink = sink;
        final List<Watermark> currentWatermarks = watermarks;
        if (currentSink == null) {
            return;
        }
        if (currentWatermarks.isEmpty()) {
            currentSink.onFrame(frame);
            return;
        }

        // 输出尺寸用“旋转后的视觉尺寸”。例如竖屏 720x1280，即使原始 buffer 是 1280x720。
        final int outputWidth = frame.getRotatedWidth();
        final int outputHeight = frame.getRotatedHeight();
        if (outputWidth <= 0 || outputHeight <= 0) {
            return;
        }

        int outputTextureId = 0;
        int frameBufferId = 0;
        try {
            // 1. 创建一张新的 2D RGB/RGBA 纹理，作为最终带水印的视频帧。
            outputTextureId = createOutputTexture(outputWidth, outputHeight);
            // 2. 把这张纹理挂到 FBO 上，后续所有 draw 都会写入这张纹理，而不是屏幕。
            frameBufferId = createFrameBuffer(outputTextureId);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId);
            GLES20.glViewport(0, 0, outputWidth, outputHeight);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 3. 先把原视频帧画进 FBO。VideoFrameDrawer 会处理 texture transform 和 frame rotation。
            if (frameDrawer != null) {
                frameDrawer.drawFrame(frame, frameGlDrawer, null, 0, 0, outputWidth, outputHeight);
            }
            // 4. 再按视觉坐标把所有水印叠加到同一张输出纹理上。
            drawWatermarks(currentWatermarks, outputWidth, outputHeight);

            // FBO 只负责“写入输出纹理”，写完就可以删；输出纹理本身还要交给下游使用。
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteFramebuffers(1, new int[]{frameBufferId}, 0);
            frameBufferId = 0;
            GlUtil.checkNoGLES2Error("WatermarkVideoProcessor.processTextureFrame");

            final TextureBufferImpl outputBuffer = wrapOutputTexture(outputTextureId, outputWidth, outputHeight);
            outputTextureId = 0;
            // 画入 FBO 后，视频内容已经是视觉正方向，所以 rotation 置 0。
            final VideoFrame outputFrame = new VideoFrame(outputBuffer, 0 /* rotation */, frame.getTimestampNs());
            try {
                currentSink.onFrame(outputFrame);
            } finally {
                outputFrame.release();
            }
        } catch (RuntimeException e) {
            Logging.e(TAG, "Failed to draw watermark frame. Forwarding original frame.", e);
            forwardFrame(frame);
        } finally {
            if (frameBufferId != 0) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glDeleteFramebuffers(1, new int[]{frameBufferId}, 0);
            }
            if (outputTextureId != 0) {
                GLES20.glDeleteTextures(1, new int[]{outputTextureId}, 0);
            }
        }
    }

    /**
     * 在已经画好原始视频内容的 FBO 上，继续逐个叠加水印。
     *
     * <p>这里使用的是视觉坐标：左上角为原点，因此在传给 glViewport 前要把 Y 翻转成
     * OpenGL 的底部原点坐标。
     */
    private void drawWatermarks(List<Watermark> currentWatermarks, int outputWidth, int outputHeight) {
        // 水印一般带 alpha 通道，需要开启混合；Bitmap 默认多为 premultiplied alpha。
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        for (Watermark watermark : currentWatermarks) {
            if (watermark == null || watermark.bitmap.isRecycled()) {
                continue;
            }
            final ResolvedWatermark resolved = watermark.resolve(outputWidth, outputHeight);
            if (resolved.isEmpty()) {
                continue;
            }
            final WatermarkTexture texture = getOrCreateWatermarkTexture(watermark.bitmap);
            if (texture == null) {
                continue;
            }
            final int viewportX = resolved.visualX;
            // 视觉坐标 y=0 在上方，而 glViewport 的 y=0 在下方，所以这里要翻转 Y。
            final int viewportY = outputHeight - resolved.visualY - resolved.height;
            if (watermarkShaderCallbacks != null) {
                watermarkShaderCallbacks.setAlpha(watermark.alpha);
            }
            if (watermarkDrawer != null) {
                watermarkDrawer.drawRgb(texture.textureId, BITMAP_TEXTURE_MATRIX, texture.width,
                        texture.height, viewportX, viewportY, resolved.width, resolved.height);
            }
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    @Nullable
    private WatermarkTexture getOrCreateWatermarkTexture(Bitmap bitmap) {
        if (bitmap.isRecycled()) {
            releaseWatermarkTexture(bitmap);
            return null;
        }
        final WatermarkTexture cachedTexture = watermarkTextures.get(bitmap);
        final int generationId = bitmap.getGenerationId();
        // Bitmap 没变就复用已上传的 GL 纹理，避免每帧 texImage2D 造成卡顿。
        if (cachedTexture != null && cachedTexture.generationId == generationId
                && cachedTexture.width == bitmap.getWidth() && cachedTexture.height == bitmap.getHeight()) {
            return cachedTexture;
        }
        if (cachedTexture != null) {
            GLES20.glDeleteTextures(1, new int[]{cachedTexture.textureId}, 0);
            watermarkTextures.remove(bitmap);
        }

        // Bitmap 新建或内容已变化：上传到 GL_TEXTURE_2D，之后绘制水印只绑定纹理即可。
        final int textureId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GlUtil.checkNoGLES2Error("WatermarkVideoProcessor.uploadWatermarkTexture");

        final WatermarkTexture texture = new WatermarkTexture(textureId, bitmap.getWidth(), bitmap.getHeight(), generationId);
        watermarkTextures.put(bitmap, texture);
        return texture;
    }

    private int createOutputTexture(int width, int height) {
        // 每帧创建独立输出纹理，避免下游还没用完时下一帧覆盖同一张纹理。
        final int textureId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GlUtil.checkNoGLES2Error("WatermarkVideoProcessor.createOutputTexture");
        return textureId;
    }

    private int createFrameBuffer(int textureId) {
        final int[] frameBuffers = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        final int frameBufferId = frameBuffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, textureId, 0);
        final int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer not complete, status: " + status);
        }
        return frameBufferId;
    }

    private TextureBufferImpl wrapOutputTexture(int textureId, int width, int height) {
        pendingOutputTextures++;
        final int textureIdForRelease = textureId;
        // TextureBufferImpl 的 release callback 会在最后一个持有者释放帧时触发；
        // 这里回到 GL 线程删除 texture，保证 OpenGL 资源在正确线程释放。
        return new TextureBufferImpl(width, height, VideoFrame.TextureBuffer.Type.RGB, textureId,
                new Matrix(), renderHandler, yuvConverter, () -> {
            renderHandler.post(() -> releaseOutputTexture(textureIdForRelease));
        });
    }

    private void releaseOutputTexture(int textureId) {
        if (!glReleased) {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
        }
        pendingOutputTextures--;
        maybeReleaseGlResources();
    }

    private void releaseUnusedWatermarkTextures(List<Watermark> activeWatermarks) {
        if (glReleased) {
            return;
        }
        final IdentityHashMap<Bitmap, Boolean> activeBitmaps = new IdentityHashMap<>();
        for (Watermark watermark : activeWatermarks) {
            if (watermark != null) {
                activeBitmaps.put(watermark.bitmap, Boolean.TRUE);
            }
        }

        final Iterator<Map.Entry<Bitmap, WatermarkTexture>> iterator = watermarkTextures.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Bitmap, WatermarkTexture> entry = iterator.next();
            if (!activeBitmaps.containsKey(entry.getKey()) || entry.getKey().isRecycled()) {
                GLES20.glDeleteTextures(1, new int[]{entry.getValue().textureId}, 0);
                iterator.remove();
            }
        }
    }

    private void releaseWatermarkTexture(Bitmap bitmap) {
        final WatermarkTexture texture = watermarkTextures.remove(bitmap);
        if (texture != null && !glReleased) {
            GLES20.glDeleteTextures(1, new int[]{texture.textureId}, 0);
        }
    }

    private void releaseWatermarkTextures() {
        for (WatermarkTexture texture : watermarkTextures.values()) {
            GLES20.glDeleteTextures(1, new int[]{texture.textureId}, 0);
        }
        watermarkTextures.clear();
    }

    private void maybeReleaseGlResources() {
        // 只有 dispose 已请求，并且所有输出纹理都被下游释放后，才能真正销毁 EGL/GL 资源。
        if (!releaseRequested || pendingOutputTextures != 0 || glReleased) {
            return;
        }
        glReleased = true;
        // 水印输入纹理只被本类持有，可以立即释放；输出纹理要等下游 release 后再删。
        releaseWatermarkTextures();
        if (watermarkDrawer != null) {
            watermarkDrawer.release();
            watermarkDrawer = null;
        }
        if (frameGlDrawer != null) {
            frameGlDrawer.release();
            frameGlDrawer = null;
        }
        if (frameDrawer != null) {
            frameDrawer.release();
            frameDrawer = null;
        }
        if (yuvConverter != null) {
            yuvConverter.release();
            yuvConverter = null;
        }
        if (eglBase != null) {
            eglBase.detachCurrent();
            eglBase.release();
            eglBase = null;
        }
        renderThread.quit();
    }

    private void forwardFrame(VideoFrame frame) {
        final VideoSink currentSink = sink;
        if (currentSink != null) {
            currentSink.onFrame(frame);
        }
    }

    private static float[] createBitmapTextureMatrix() {
        // 把 Bitmap 的左上角原点转换成 OpenGL 采样使用的左下角原点。
        final Matrix matrix = new Matrix();
        matrix.preTranslate(0.5f, 0.5f);
        matrix.preScale(1f, -1f);
        matrix.preTranslate(-0.5f, -0.5f);
        return RendererCommon.convertMatrixFromAndroidGraphicsMatrix(matrix);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
