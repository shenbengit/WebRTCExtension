package com.shencoder.webrtcextension;

/**
 * @author ShenBen
 * @date 2021/10/30 15:16
 * @email 714081644@qq.com
 */
public enum RotationAngle {
    /**
     * 0°
     */
    ANGLE_0(0),
    /**
     * 90°
     */
    ANGLE_90(90),
    /**
     * 180°
     */
    ANGLE_180(180),
    /**
     * 270°
     */
    ANGLE_270(270);

    private final int angle;
    RotationAngle(int angle) {
        this.angle = angle;
    }

    public int getAngle() {
        return angle;
    }
}
