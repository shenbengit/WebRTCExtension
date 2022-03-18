package com.shencoder.webrtcextension.recoder

/**
 * Computes timestamps for audio frames.
 * Video frames do not need this since the timestamp comes from
 * the surface texture.
 *
 * This is independent from the channels count, as long as the read bytes include
 * all channels and the byte rate accounts for this as well.
 * If channels is 2, both values will be doubled and we behave the same.
 *
 * This class keeps track of gaps between frames.
 * This can be used, for example, to write zeros instead of nothing.
 */
class AudioTimestamp(private val mByteRate: Int) {

    companion object {
        @JvmStatic
        fun bytesToUs(bytes: Long, byteRate: Int): Long {
            return 1000000L * bytes / byteRate
        }

        @JvmStatic
        fun bytesToMillis(bytes: Long, byteRate: Int): Long {
            return 1000L * bytes / byteRate
        }
    }

    private var mBaseTimeUs: Long = 0
    private var mBytesSinceBaseTime: Long = 0
    private var mGapUs: Long = 0

    /**
     * This method accounts for the current time and proved to be the most reliable among
     * the ones tested.
     *
     * This creates regular timestamps unless we accumulate a lot of delay (greater than
     * twice the buffer duration), in which case it creates a gap and starts again trying
     * to be regular from the new point.
     *
     * Returns timestamps in the [System.nanoTime] reference.
     */
    fun increaseUs(readBytes: Int): Long {
        val bufferDurationUs = bytesToUs(
            readBytes.toLong(), mByteRate
        )
        val bufferEndTimeUs = System.nanoTime() / 1000 // now
        val bufferStartTimeUs = bufferEndTimeUs - bufferDurationUs

        // If this is the first time, the base time is the buffer start time.
        if (mBytesSinceBaseTime == 0L) mBaseTimeUs = bufferStartTimeUs

        // Recompute time assuming that we are respecting the sampling frequency.
        // This puts the time at the end of last read buffer, which means, where we
        // should be if we had no delay / missed buffers.
        val correctedTimeUs = mBaseTimeUs + bytesToUs(mBytesSinceBaseTime, mByteRate)
        val correctionUs = bufferStartTimeUs - correctedTimeUs
        return if (correctionUs >= 2L * bufferDurationUs) {
            // However, if the correction is too big (> 2*bufferDurationUs), reset to this point.
            // This is triggered if we lose buffers and are recording/encoding at a slower rate.
            mBaseTimeUs = bufferStartTimeUs
            mBytesSinceBaseTime = readBytes.toLong()
            mGapUs = correctionUs
            mBaseTimeUs
        } else {
            if (correctionUs < 0) {
                // This means that this method is being called too often, so that the expected start
                // time for this buffer is BEFORE the last buffer end. So, respect the last buffer
                // end instead.
            }
            mGapUs = 0
            mBytesSinceBaseTime += readBytes.toLong()
            correctedTimeUs
        }
    }

    /**
     * Returns the number of gaps (meaning, missing frames) assuming that each
     * frame has frameBytes size. Possibly 0.
     *
     * @param frameBytes size of standard frame
     * @return number of gaps
     */
    fun getGapCount(frameBytes: Int): Int {
        if (mGapUs == 0L) return 0
        val durationUs = bytesToUs(
            frameBytes.toLong(), mByteRate
        )
        return (mGapUs / durationUs).toInt()
    }

    /**
     * Returns the timestamp of the first missing frame.
     * Should be called only after [.getGapCount] returns something
     * greater than zero.
     *
     * @param lastTimeUs the last real frame timestamp
     * @return the first missing frame timestamp
     */
    fun getGapStartUs(lastTimeUs: Long): Long {
        return lastTimeUs - mGapUs
    }
}