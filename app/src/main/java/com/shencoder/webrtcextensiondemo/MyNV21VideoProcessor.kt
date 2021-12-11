package com.shencoder.webrtcextensiondemo

import com.shencoder.webrtcextension.BaseNV21VideoProcessor

/**
 *
 * @author  ShenBen
 * @date    2021/12/11 18:24
 * @email   714081644@qq.com
 */
class MyNV21VideoProcessor : BaseNV21VideoProcessor() {
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
    override fun handleNV21(nv21: ByteArray, width: Int, height: Int, rotation: Int): Boolean {
        //handle nv21 data
        return true
    }
}