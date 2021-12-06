package com.shencoder.webrtcextension.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author ShenBen
 * @date 2021/11/21 14:01
 * @email 714081644@qq.com
 */
public class NV21Util {
    /**
     * 透明Y值
     */
    public static final byte TRANSPARENT_Y = 0x10;
    /**
     * 透明UV值
     */
    public static final byte TRANSPARENT_UV = (byte) 0x80;

    /**
     * nv21数据剪裁
     *
     * @param src        原始nv21数据
     * @param srcWidth   原始nv21数据的宽
     * @param srcHeight  原始nv21数据的高
     * @param clipWidth  剪裁的宽度
     * @param clipHeight 剪裁的高度
     * @param left       剪裁的开始的左边位置，坐标相对于nv21原始数据
     * @param top        剪裁的开始的上边位置，坐标相对于nv21原始数据
     * @return 剪裁异常时返回null，成功时返回剪裁的nv21数据
     */
    @Nullable
    public static byte[] cropNV21(@NonNull byte[] src, int srcWidth, int srcHeight, int clipWidth, int clipHeight, int left, int top) {
        if (src.length != srcWidth * srcHeight * 3 / 2) {
            return null;
        }
        if (clipWidth + left > srcWidth || clipHeight + top > srcHeight) {
            return null;
        }
        if (clipWidth == srcWidth && clipHeight == srcHeight && left == 0 && top == 0) {
            return src;
        }
        //确保为偶数
        clipWidth &= ~1;
        clipHeight &= ~1;
        left &= ~1;
        top &= ~1;
        byte[] cropBytes = new byte[clipWidth * clipHeight * 3 / 2];
        int bottom = top + clipHeight;
        //先复制Y数据
        for (int i = top; i < bottom; i++) {
            System.arraycopy(src, left + i * srcWidth, cropBytes, (i - top) * clipWidth, clipWidth);
        }
        //复制UV数据
        int startH = srcHeight + top / 2;
        int endH = srcHeight + bottom / 2;
        for (int i = startH; i < endH; i++) {
            System.arraycopy(src,
                    left + i * srcWidth,
                    cropBytes,
                    (i - startH + clipHeight) * clipWidth,
                    clipWidth);
        }
        return cropBytes;
    }

    /**
     * 叠图
     * 调用完成后改方法后，直接使用传入的nv21 数据即可。
     *
     * @param nv21          叠图最下面的图的nv21数据，大小要比被叠图的nv21数据大
     * @param width         最下面叠图的nv21数据的宽
     * @param height        最下面叠图的nv21数据的高
     * @param left          叠图起始左边位置
     * @param top           叠图起始的上边位置
     * @param overlayNv21   小图的nv21数据
     * @param overlayWidth  小图的宽
     * @param overlayHeight 小图的高
     */
    public static void overlayNV21(@NonNull byte[] nv21, int width, int height, int left, int top, @NonNull byte[] overlayNv21, int overlayWidth, int overlayHeight) {
        overlayNV21(nv21, width, height, left, top, overlayNv21, overlayWidth, overlayHeight, false);
    }

    /**
     * 叠图，先判断是否超出范围，超出进行剪裁。
     * 调用完成后改方法后，直接使用传入的nv21 数据即可。
     *
     * @param nv21          叠图最下面的图的nv21数据，大小要比被叠图的nv21数据大
     * @param width         最下面叠图的nv21数据的宽
     * @param height        最下面叠图的nv21数据的高
     * @param left          叠图起始左边位置
     * @param top           叠图起始的上边位置
     * @param overlayNv21   小图的nv21数据
     * @param overlayWidth  小图的宽
     * @param overlayHeight 小图的高
     * @param transparent   叠图中是否有透明数据；如果有，但传参false的话，会以黑色填充；如果是true的话，会比较耗时
     */
    public static void overlayNV21(@NonNull byte[] nv21, int width, int height, int left, int top, @NonNull byte[] overlayNv21, int overlayWidth, int overlayHeight, boolean transparent) {
        if (nv21.length != width * height * 3 / 2) {
            return;
        }
        if (overlayNv21.length != overlayWidth * overlayHeight * 3 / 2) {
            return;
        }
        int originalOverlayWidth = overlayWidth;
        int originalOverlayHeight = overlayHeight;
        if (overlayWidth + left > width) {
            //不符合要求，进行二次剪裁
            overlayWidth = width - left;
        }
        if (overlayHeight + top > height) {
            //不符合要求，进行二次剪裁
            overlayHeight = height - top;
        }
        //确保为偶数
        left &= ~1;
        top &= ~1;
        overlayWidth &= ~1;
        overlayHeight &= ~1;

        //裁剪
        overlayNv21 = cropNV21(overlayNv21, originalOverlayWidth, originalOverlayHeight, overlayWidth, overlayHeight, 0, 0);
        if (overlayNv21 == null) {
            return;
        }
        if (transparent) {
            //途中有透明部分
            //先剪裁出nv21中覆盖部分的相同位置的数据
            byte[] cropNV21 = cropNV21(nv21, width, height, overlayWidth, overlayHeight, left, top);
            if (cropNV21 == null) {
                return;
            }
            int size = overlayWidth * overlayHeight * 3 / 2;
            //然后合并
            if (cropNV21.length != size || overlayNv21.length != size) {
                return;
            }

            int splitY = overlayWidth * overlayHeight;
            for (int i = 0; i < size; i++) {
                if (i < splitY) {
                    //Y数据
                    if (overlayNv21[i] != TRANSPARENT_Y) {
                        cropNV21[i] = overlayNv21[i];
                    }
                } else {
                    //UV数据
                    if (overlayNv21[i] != TRANSPARENT_UV) {
                        cropNV21[i] = overlayNv21[i];
                    }
                }
            }
            overlayNv21 = cropNV21;
        }

        //先复制Y数据
        for (int i = 0; i < overlayHeight; i++) {
            System.arraycopy(overlayNv21, i * overlayWidth, nv21, left + (top + i) * width, overlayWidth);
        }
        //复制UV数据
        int startH = overlayHeight;
        int endH = overlayHeight + (overlayWidth * overlayHeight / 2) / overlayWidth;

        int basic = height + top / 2;
        for (int i = startH; i < endH; i++) {
            System.arraycopy(overlayNv21,
                    i * overlayWidth,
                    nv21,
                    left + (basic + (i - startH)) * width,
                    overlayWidth);
        }
    }

}
