package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object HeartSoundPlayer {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var lastChimeTimestamp: Long = 0L

    /**
     * Plays a harmonious romantic chime chord (528Hz + 660Hz) with smooth decay.
     */
    fun playLoveChime() {
        val now = System.currentTimeMillis()
        if (now - lastChimeTimestamp < 350L) return
        lastChimeTimestamp = now

        scope.launch {
            try {
                val sampleRate = 44100
                val durationSec = 0.8
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                val freq1 = 528.0 // Love frequency
                val freq2 = 660.0 // Major third
                val freq3 = 792.0 // Perfect fifth

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    // Smooth envelope with soft attack and exponential decay
                    val envelope = (1.0 - time / durationSec) * (1.0 - Math.exp(-time * 40.0))

                    val sampleVal = (
                        0.5 * sin(2.0 * Math.PI * freq1 * time) +
                        0.3 * sin(2.0 * Math.PI * freq2 * time) +
                        0.2 * sin(2.0 * Math.PI * freq3 * time)
                    ) * envelope

                    buffer[i] = (sampleVal * Short.MAX_VALUE * 0.7).toInt().coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    ).toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Plays a deep realistic double heartbeat thump-thump tone.
     */
    fun playHeartThump() {
        scope.launch {
            try {
                val sampleRate = 44100
                val durationSec = 0.5
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    // First thump at 0.0s, second thump at 0.18s
                    val thump1 = if (time < 0.15) {
                        val localT = time
                        sin(2.0 * Math.PI * 75.0 * localT) * Math.exp(-localT * 28.0)
                    } else 0.0

                    val thump2 = if (time in 0.16..0.35) {
                        val localT = time - 0.16
                        sin(2.0 * Math.PI * 85.0 * localT) * Math.exp(-localT * 26.0) * 0.85
                    } else 0.0

                    val combined = (thump1 + thump2)
                    buffer[i] = (combined * Short.MAX_VALUE * 0.8).toInt().coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    ).toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (_: Exception) {
            }
        }
    }

    private fun playBuffer(buffer: ShortArray, sampleRate: Int) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes((buffer.size * 2).coerceAtLeast(minBufferSize))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        track.setNotificationMarkerPosition(buffer.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                try {
                    t?.stop()
                    t?.release()
                } catch (_: Exception) {}
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
    }
}
