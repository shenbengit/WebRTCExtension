package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.LinkedBlockingDeque

/**
 * 音频编码器
 *
 * @author  ShenBen
 * @date    2022/3/17 16:56
 * @email   714081644@qq.com
 */
class AudioMediaEncoder : MediaEncoder("AudioEncoder"),
    JavaAudioDeviceModule.SamplesReadyCallback {
    private companion object {
        private const val BYTE_RATE = 64 * 1024
    }

    private val mEncodingThread = AudioEncodingThread()

    private val mInputBufferPool = InputBufferPool()

    private val mInputBufferDeque = LinkedBlockingDeque<InputBuffer>()

    @Volatile
    private var mHadStarted = false

    @Volatile
    private var mLastTimeUs: Long = 0

    @Volatile
    private var mFirstTimeUs = Long.MIN_VALUE
    private val mTimestamp: AudioTimestamp = AudioTimestamp(BYTE_RATE)

    override fun onPrepare(controller: MediaEncoderEngine.Controller) {

    }

    override fun onStart() {
        if (mHadStarted) {
            return
        }
        mHadStarted = true
        mEncodingThread.start()
    }

    override fun onStop() {
        mHadStarted = false
        if (mInputBufferDeque.isEmpty()) {
            val buffer = mInputBufferPool.get()
            buffer?.let {
                increaseTime(0)
                it.isEndOfStream = true
                it.timestamp = mLastTimeUs
                acquireInputBuffer(it)
                encodeInputBuffer(it)
                drainOutput(true)
            }
        } else {
            val inputBuffer = mInputBufferDeque.peekLast()
            inputBuffer?.isEndOfStream = true
        }
    }

    override fun onWebRtcAudioRecordSamplesReady(simples: JavaAudioDeviceModule.AudioSamples) {
        if (mHadStarted.not()) {
            return
        }

        mWorker.post {
            var codec = mMediaCodec
            if (codec == null) {
                codec = initAudioEncoder(simples)
                mMediaCodec = codec
                initMediaCodecBuffers()

                codec.start()
            }
            val buffer = mInputBufferPool.get()
            buffer?.let {
                val data = simples.data
                it.source = data
                it.dataLength = data.size
                it.isEndOfStream = false

                increaseTime(data.size)
                it.timestamp = mLastTimeUs
                mInputBufferDeque.add(it)
            }
        }
    }


    private fun initAudioEncoder(simples: JavaAudioDeviceModule.AudioSamples): MediaCodec {
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            simples.sampleRate,
            simples.channelCount
        )
        audioFormat.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
    }

    private fun increaseTime(readBytes: Int) {
        mLastTimeUs += readBytes * 125L / 12L
        if (mFirstTimeUs == Long.MIN_VALUE) {
            mFirstTimeUs = mLastTimeUs
            // Compute the first frame milliseconds as well.
            notifyFirstFrameMillis(mFirstTimeUs)
        }
    }

    /**
     * 编码线程
     */
    private inner class AudioEncodingThread : Thread("AudioEncodingThread") {

        override fun run() {
            super.run()

            while (true) {
                val inputBuffer = mInputBufferDeque.poll()
                if (inputBuffer == null) {
                    if (mHadStarted.not()) {
                        break
                    }
                    sleep(30)
                } else {
                    if (inputBuffer.isEndOfStream) {
                        acquireInputBuffer(inputBuffer)
                        encode(inputBuffer)
                        break
                    } else if (tryAcquireInputBuffer(inputBuffer)) {
                        encode(inputBuffer)
                    } else {
                        sleep(30)
                    }
                }
            }
            Log.w("AudioEncodingThread", "AudioEncodingThread - end ")
            mInputBufferPool.clear()
        }

        private fun encode(buffer: InputBuffer) {
            val data = buffer.data ?: return
            val source = buffer.source ?: return

            data.put(source)
            encodeInputBuffer(buffer)
            mInputBufferPool.recycle(buffer)

            val eos = buffer.isEndOfStream
            drainOutput(eos)
        }
    }
}