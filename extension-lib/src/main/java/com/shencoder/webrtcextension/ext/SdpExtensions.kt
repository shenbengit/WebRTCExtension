package com.shencoder.webrtcextension.ext

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/**
 *
 * @author Shenben
 * @date 2023/11/3 10:32
 * @description
 * @since
 */

suspend inline fun createSessionDescription(crossinline call: (SdpObserver) -> Unit): Result<SessionDescription> =
    suspendCoroutine {
        val observer = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    it.resume(Result.success(sdp))
                } else {
                    it.resume(Result.failure(RuntimeException("SessionDescription is null!")))
                }
            }

            override fun onCreateFailure(error: String?) =
                it.resume(Result.failure(RuntimeException(error)))

            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }

        call(observer)
    }

suspend inline fun suspendSdpObserver(crossinline call: (SdpObserver) -> Unit): Result<Unit> =
    suspendCoroutine {
        val observer = object : SdpObserver {

            override fun onCreateFailure(error: String?) = Unit
            override fun onCreateSuccess(sdp: SessionDescription?) = Unit

            override fun onSetSuccess() = it.resume(Result.success(Unit))
            override fun onSetFailure(error: String?) =
                it.resume(Result.failure(RuntimeException(error)))
        }
        call(observer)
    }