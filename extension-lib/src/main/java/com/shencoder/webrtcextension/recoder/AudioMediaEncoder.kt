package com.shencoder.webrtcextension.recoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.LinkedBlockingQueue

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
        private const val BYTE_RATE = 44100 * 2
    }

    private val mEncodingThread = AudioEncodingThread()

    private val mInputBufferPool = InputBufferPool()

    private val mInputBufferQueue = LinkedBlockingQueue<InputBuffer>()

    private var mRequestStop = false

    @Volatile
    private var mLastTimeUs: Long = 0

    @Volatile
    private var mFirstTimeUs = Long.MIN_VALUE
    private val mTimestamp: AudioTimestamp = AudioTimestamp(BYTE_RATE)

    override fun onPrepare(controller: MediaEncoderEngine.Controller) {

    }

    override fun onStart() {
        mRequestStop = false
        mEncodingThread.start()
    }

    override fun onStop() {
        mRequestStop = true
    }

    override fun onStopped() {
        super.onStopped()
        mRequestStop = false
    }

    override fun onWebRtcAudioRecordSamplesReady(simples: JavaAudioDeviceModule.AudioSamples) {
        var codec = mMediaCodec
        if (codec == null) {
            codec = initAudioEncoder(simples)
            codec.start()

            mMediaCodec = codec

            initMediaCodecBuffers()
        }

        mWorker.post {
            val buffer = mInputBufferPool.get()
            buffer?.let {
                val data = simples.data
                buffer.source = data
                buffer.dataLength = data.size
                increaseTime(data.size)
                buffer.timestamp = mLastTimeUs
                mInputBufferQueue.add(buffer)
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
        mLastTimeUs = mTimestamp.increaseUs(readBytes)
        if (mFirstTimeUs == Long.MIN_VALUE) {
            mFirstTimeUs = mLastTimeUs
            // Compute the first frame milliseconds as well.
            notifyFirstFrameMillis(
                System.currentTimeMillis()
                        - AudioTimestamp.bytesToMillis(readBytes.toLong(), BYTE_RATE)
            )
        }
    }

    /**
     * 编码线程
     */
    private inner class AudioEncodingThread : Thread("AudioEncodingThread") {

        override fun run() {
            super.run()

            while (true) {
                val inputBuffer = mInputBufferQueue.peek()
                if (inputBuffer == null) {
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