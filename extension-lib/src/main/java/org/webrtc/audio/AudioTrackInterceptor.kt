package org.webrtc.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/**
 * 由于[WebRtcAudioTrack]包不可见，切未暴露出音频输出数据，使用反射替换 [WebRtcAudioTrack.audioTrack]，并且回调音频数据；
 * [AudioTrackInterceptor]其实就是一个壳，要把[WebRtcAudioTrack.audioTrack]赋值给[AudioTrackInterceptor.originalTrack]，
 * 重写[WebRtcAudioTrack.audioTrack]调用的相关方法，然后使用[originalTrack]调用即可
 *
 * @author  ShenBen
 * @date    2021/08/21 13:47
 * @email   714081644@qq.com
 */
class AudioTrackInterceptor constructor(
    /**
     * 即：原[WebRtcAudioTrack.audioTrack]
     */
    private var originalTrack: AudioTrack,
    /**
     * 音频数据输出回调
     */
    private var samplesReadyCallback: JavaAudioDeviceModule.SamplesReadyCallback
) : AudioTrack(//不用关心这里传的参数，只是一个壳
    AudioManager.STREAM_VOICE_CALL,
    44100,
    AudioFormat.CHANNEL_OUT_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    8192,
    MODE_STREAM
) {

    override fun getState(): Int {
        return originalTrack.state
    }

    override fun play() {
        originalTrack.play()
    }

    override fun getPlayState(): Int {
        return originalTrack.playState
    }

    override fun stop() {
        return originalTrack.stop()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getUnderrunCount(): Int {
        return originalTrack.underrunCount
    }

    override fun getAudioSessionId(): Int {
        return originalTrack.audioSessionId
    }

    override fun getChannelCount(): Int {
        return originalTrack.channelCount
    }

    override fun getSampleRate(): Int {
        return originalTrack.sampleRate
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun getBufferCapacityInFrames(): Int {
        return originalTrack.bufferCapacityInFrames
    }

    override fun getAudioFormat(): Int {
        return originalTrack.audioFormat
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun getBufferSizeInFrames(): Int {
        return originalTrack.bufferSizeInFrames
    }

    override fun pause() {
        originalTrack.pause()
    }

    override fun release() {
        originalTrack.release()
    }

    override fun getPlaybackHeadPosition(): Int {
        return originalTrack.playbackHeadPosition
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun getFormat(): AudioFormat {
        return originalTrack.format
    }

    override fun getPlaybackRate(): Int {
        return originalTrack.playbackRate
    }

    override fun getStreamType(): Int {
        return originalTrack.streamType
    }

    override fun getChannelConfiguration(): Int {
        return originalTrack.channelConfiguration
    }

    override fun flush() {
        originalTrack.flush()
    }

    /**
     * [WebRtcAudioTrack.AudioTrackThread.writeBytes]
     * 写入音频数据，这里我们处理一下，回调即可
     */
    override fun write(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        val write = originalTrack.write(audioData, offsetInBytes, sizeInBytes)
        if (write == sizeInBytes) {
            val bytes = audioData.copyOfRange(offsetInBytes, offsetInBytes + sizeInBytes)
            samplesReadyCallback.onWebRtcAudioRecordSamplesReady(
                JavaAudioDeviceModule.AudioSamples(
                    originalTrack.audioFormat,
                    originalTrack.channelCount,
                    originalTrack.sampleRate,
                    bytes
                )
            )
        }
        return write
    }

    /**
     * [WebRtcAudioTrack.AudioTrackThread.writeBytes]
     * 写入音频数据，这里我们处理一下，回调即可
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun write(audioData: ByteBuffer, sizeInBytes: Int, writeMode: Int): Int {
        val position = audioData.position()
        val from = if (audioData.isDirect) position else audioData.arrayOffset() + position

        val write = originalTrack.write(audioData, sizeInBytes, writeMode)
        if (write == sizeInBytes) {
            val bytes = audioData.array().copyOfRange(from, from + sizeInBytes)
            samplesReadyCallback.onWebRtcAudioRecordSamplesReady(
                JavaAudioDeviceModule.AudioSamples(
                    originalTrack.audioFormat,
                    originalTrack.channelCount,
                    originalTrack.sampleRate,
                    bytes
                )
            )
        }
        return write
    }

    /**
     * 原[WebRtcAudioTrack.audioTrack] 并未调用该方法
     * 只是实现一下
     */
    override fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        return originalTrack.write(audioData, offsetInShorts, sizeInShorts)
    }

    /**
     * 原[WebRtcAudioTrack.audioTrack] 并未调用该方法
     * 只是实现一下
     */
    @RequiresApi(Build.VERSION_CODES.M)
    override fun write(
        audioData: ByteArray,
        offsetInBytes: Int,
        sizeInBytes: Int,
        writeMode: Int
    ): Int {
        return originalTrack.write(audioData, offsetInBytes, sizeInBytes, writeMode)
    }

    /**
     * 原[WebRtcAudioTrack.audioTrack] 并未调用该方法
     * 只是实现一下
     */
    @RequiresApi(Build.VERSION_CODES.M)
    override fun write(
        audioData: ByteBuffer,
        sizeInBytes: Int,
        writeMode: Int,
        timestamp: Long
    ): Int {
        return originalTrack.write(audioData, sizeInBytes, writeMode, timestamp)
    }

    /**
     * 原[WebRtcAudioTrack.audioTrack] 并未调用该方法
     * 只是实现一下
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun write(
        audioData: FloatArray,
        offsetInFloats: Int,
        sizeInFloats: Int,
        writeMode: Int
    ): Int {
        return originalTrack.write(audioData, offsetInFloats, sizeInFloats, writeMode)
    }

    /**
     * 原[WebRtcAudioTrack.audioTrack] 并未调用该方法
     * 只是实现一下
     */
    @RequiresApi(Build.VERSION_CODES.M)
    override fun write(
        audioData: ShortArray,
        offsetInShorts: Int,
        sizeInShorts: Int,
        writeMode: Int
    ): Int {
        return originalTrack.write(audioData, offsetInShorts, sizeInShorts, writeMode)
    }

}

