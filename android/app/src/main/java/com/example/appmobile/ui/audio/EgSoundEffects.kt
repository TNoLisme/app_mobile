package com.example.appmobile.ui.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.appmobile.ui.state.AppSettingsState
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class EgSoundEffect {
    Tap,
    Correct,
    Wrong,
    Success
}

object EgSoundEffects {
    private const val SAMPLE_RATE = 44_100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastTapAt = AtomicLong(0L)

    fun play(effect: EgSoundEffect) {
        if (!AppSettingsState.soundEffectsEnabled.value) return

        if (effect == EgSoundEffect.Tap) {
            val now = System.currentTimeMillis()
            val previous = lastTapAt.get()
            if (now - previous < 70L) return
            lastTapAt.set(now)
        }

        scope.launch {
            runCatching { playInternal(effect) }
        }
    }

    private fun playInternal(effect: EgSoundEffect) {
        val tones = when (effect) {
            EgSoundEffect.Tap -> listOf(Tone(880.0, 34, 0.10), Tone(0.0, 10), Tone(1180.0, 28, 0.08))
            EgSoundEffect.Correct -> listOf(Tone(660.0, 70), Tone(0.0, 18), Tone(880.0, 80), Tone(0.0, 18), Tone(1175.0, 95))
            EgSoundEffect.Wrong -> listOf(Tone(260.0, 105, 0.16), Tone(0.0, 25), Tone(190.0, 130, 0.14))
            EgSoundEffect.Success -> listOf(
                Tone(523.25, 70),
                Tone(0.0, 18),
                Tone(659.25, 75),
                Tone(0.0, 18),
                Tone(783.99, 85),
                Tone(0.0, 18),
                Tone(1046.5, 115, 0.14)
            )
        }
        val pcm = render(tones)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()

        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(tones.sumOf { it.durationMs }.toLong() + 80L)
        } finally {
            track.release()
        }
    }

    private fun render(tones: List<Tone>): ShortArray {
        val sampleCount = tones.sumOf { (SAMPLE_RATE * it.durationMs / 1000.0).roundToInt() }
        val pcm = ShortArray(sampleCount)
        var offset = 0
        tones.forEach { tone ->
            val count = (SAMPLE_RATE * tone.durationMs / 1000.0).roundToInt()
            val attack = minOf((SAMPLE_RATE * 0.006).roundToInt(), count / 2)
            val release = minOf((SAMPLE_RATE * 0.012).roundToInt(), count / 2)
            repeat(count) { i ->
                pcm[offset + i] = if (tone.frequencyHz <= 0.0) {
                    0
                } else {
                    val envelope = envelope(i, count, attack, release)
                    val wave = sin(2.0 * PI * tone.frequencyHz * i / SAMPLE_RATE)
                    (wave * envelope * tone.volume * Short.MAX_VALUE)
                        .roundToInt()
                        .coerceIn(-32768, 32767)
                        .toShort()
                }
            }
            offset += count
        }
        return pcm
    }

    private fun envelope(index: Int, count: Int, attack: Int, release: Int): Double {
        val attackGain = if (attack <= 0) 1.0 else (index.toDouble() / attack).coerceIn(0.0, 1.0)
        val releaseGain = if (release <= 0) 1.0 else ((count - index).toDouble() / release).coerceIn(0.0, 1.0)
        return minOf(attackGain, releaseGain)
    }

    private data class Tone(
        val frequencyHz: Double,
        val durationMs: Int,
        val volume: Double = 0.13
    )
}
