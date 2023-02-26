package com.rouddy.twophonesupporter.util

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.reactivex.rxjava3.core.Observable

object AudioManagerFocusUtil {

    interface Request

    @RequiresApi(Build.VERSION_CODES.O)
    class RequestOver26 : Request {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .build()
    }

    class RequestBelow25 : Request {
        val listener = AudioManager.OnAudioFocusChangeListener {
            Log.e("!!!", "On Audio Focus Change:$it")
        }
    }

    fun request(audioManager: AudioManager): Observable<Request> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestOver26(audioManager)
        } else {
            requestBelow25(audioManager)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestOver26(audioManager: AudioManager): Observable<Request> {
        val request = RequestOver26()
        return Observable.create<Request?> { emitter ->
            val result = audioManager.requestAudioFocus(request.request)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                emitter.onNext(request)
            } else {
                emitter.onError(RuntimeException("Audio Focus Request error:$result"))
            }
        }
            .doFinally {
                audioManager.abandonAudioFocusRequest(request.request)
            }
    }

    @Suppress("DEPRECATION")
    private fun requestBelow25(audioManager: AudioManager): Observable<Request> {
        val request = RequestBelow25()
        return Observable.create<Request?> { emitter ->
            val result = audioManager.requestAudioFocus(request.listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                emitter.onNext(request)
            } else {
                emitter.onError(RuntimeException("Audio Focus Request error:$result"))
            }
        }
            .doFinally {
                audioManager.abandonAudioFocus(request.listener)
            }
    }
}