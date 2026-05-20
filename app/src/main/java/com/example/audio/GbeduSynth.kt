package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.*
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class GbeduSynth(
    private val presetName: String,
    private val onSpectrumUpdated: (FloatArray) -> Unit
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var synthThread: Thread? = null
    private var volume = 1.0f

    // Synthesis configurations
    private val sampleRate = 22050 // Keep CPU overhead minimal
    private var time = 0.0

    // Sound generation states
    private var tick = 0
    private var chordProgression = listOf(
        listOf(220.0, 275.0, 330.0), // Am (sub-octave, relative frequencies)
        listOf(293.66, 349.23, 440.0), // Dm
        listOf(261.63, 329.63, 392.00), // C
        listOf(196.00, 246.94, 293.66)  // G
    )
    private var currentChords = chordProgression[0]
    private var melodyNotes = listOf(440.0, 493.88, 523.25, 587.33, 659.25, 783.99)

    init {
        // Adjust musical mood based on track preset
        when (presetName) {
            "Lagos Synthwave" -> {
                chordProgression = listOf(
                    listOf(110.0, 165.0, 220.0), // Low detached drone
                    listOf(130.81, 196.0, 261.63),
                    listOf(146.83, 220.0, 293.66),
                    listOf(164.81, 246.94, 329.63)
                )
                melodyNotes = listOf(220.0, 330.0, 440.0, 523.25, 659.25, 880.0)
            }
            "Cyber Grid Ambient" -> {
                chordProgression = listOf(
                    listOf(73.42, 110.0, 146.83), // D deep ambient
                    listOf(87.31, 130.81, 174.61),
                    listOf(98.00, 146.83, 196.00),
                    listOf(110.0, 165.0, 220.0)
                )
                melodyNotes = listOf(146.83, 220.0, 293.66, 392.00, 440.0, 587.33)
            }
            "Sunset Sahara Flute" -> {
                chordProgression = listOf(
                    listOf(146.83, 220.0, 293.66), // Desert minor tone
                    listOf(155.56, 233.08, 311.13),
                    listOf(196.00, 293.66, 392.00),
                    listOf(146.83, 220.0, 293.66)
                )
                melodyNotes = listOf(293.66, 349.23, 392.00, 440.0, 523.25, 587.33)
            }
            else -> { // "African Sunrise Beat"
                chordProgression = listOf(
                    listOf(164.81, 246.94, 329.63), // Em bouncy
                    listOf(220.00, 275.00, 330.00), // Am
                    listOf(196.00, 246.94, 293.66), // G
                    listOf(220.00, 275.00, 330.00)  // Am
                )
                melodyNotes = listOf(329.63, 392.00, 440.0, 493.88, 587.33, 659.25)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (isPlaying) return
        isPlaying = true

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBufferSize, 2048),
            AudioTrack.MODE_STREAM
        )

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        synthThread = Thread {
            val bufferSize = 1024
            val buffer = ShortArray(bufferSize)
            val random = Random()
            var melodyFreq = 0.0
            var melodyDuration = 0
            var melodyVolume = 0.0

            while (isPlaying) {
                // Change chord and rhythm tick
                tick++
                if (tick % 16 == 0) {
                    val chordIdx = (tick / 16) % chordProgression.size
                    currentChords = chordProgression[chordIdx]
                }

                // Melodic note trigger
                if (melodyDuration <= 0) {
                    if (random.nextFloat() > 0.45) { // 55% chance of triggering note
                        val noteIdx = random.nextInt(melodyNotes.size)
                        melodyFreq = melodyNotes[noteIdx]
                        melodyDuration = random.nextInt(4) + 1 // lasts 1 to 4 blocks of 1024 samples
                        melodyVolume = 0.4 + random.nextFloat() * 0.4
                    } else {
                        melodyFreq = 0.0
                        melodyDuration = 2
                        melodyVolume = 0.0
                    }
                }
                melodyDuration--

                val simulatedSpectrum = FloatArray(16) { 0f }

                // Generate audio buffer representing combined waveforms
                for (i in 0 until bufferSize) {
                    // Time elapsed per sample
                    time += 1.0 / sampleRate

                    // Synthesize Sub-Bass
                    val bassWave = sin(2.0 * java.lang.Math.PI * (currentChords[0] / 2.0) * time)
                    simulatedSpectrum[1] = max(simulatedSpectrum[1], (bassWave.toFloat() + 1f) / 2f * 0.4f)

                    // Synthesize Midrange Harmony (clover triad harmonics)
                    val padWave = 0.4 * (
                        sin(2.0 * java.lang.Math.PI * currentChords[0] * time) +
                        0.7 * sin(2.0 * java.lang.Math.PI * currentChords[1] * time) +
                        0.5 * sin(2.0 * java.lang.Math.PI * currentChords[2] * time)
                    )
                    simulatedSpectrum[4] = max(simulatedSpectrum[4], (padWave.toFloat() + 1f) / 2f * 0.3f)

                    // Synthesize Solo Melody notes
                    var melodyWave = 0.0
                    if (melodyFreq > 0.0) {
                        // Make melody wave softer ring sawtooth + sine blend
                        val rawSine = sin(2.0 * java.lang.Math.PI * melodyFreq * time)
                        val rawSaw = (time * melodyFreq % 1.0) * 2.0 - 1.0
                        melodyWave = melodyVolume * (0.7 * rawSine + 0.3 * rawSaw)

                        // add spatial delay feedback
                        melodyWave += 0.2 * sin(2.0 * java.lang.Math.PI * melodyFreq * (time - 0.2))

                        val index = (melodyFreq / 100.0).toInt().coerceIn(6, 15)
                        simulatedSpectrum[index] = max(simulatedSpectrum[index], (melodyWave.toFloat() + 1f) / 2f * 0.6f)
                    }

                    // Sound blend
                    var blendedVal = (bassWave * 0.35 + padWave * 0.25 + melodyWave * 0.4) * volume

                    // Percussive kick drum simulator (bouncy accent at start of ticks)
                    if (presetName == "African Sunrise Beat" && tick % 4 == 0) {
                        val decay = max(0f, 1f - (i.toFloat() / bufferSize) * 2f)
                        val kickWave = sin(2.0 * java.lang.Math.PI * 55.0 * time) * decay * 0.35
                        blendedVal += kickWave * volume
                        simulatedSpectrum[0] = max(simulatedSpectrum[0], decay * 0.7f)
                    }

                    if (presetName == "Lagos Synthwave" && tick % 8 == 0) {
                        val decay = max(0f, 1f - (i.toFloat() / bufferSize) * 3f)
                        val snareNoise = (random.nextFloat() * 2.0f - 1.0f) * decay * 0.15f
                        blendedVal += snareNoise * volume
                        simulatedSpectrum[10] = max(simulatedSpectrum[10], decay * 0.5f)
                    }

                    // Convert double representation to 16-bit PCM shorts
                    val finalShortVal = (blendedVal * 32767.0)
                    buffer[i] = max(-32768.0, min(32767.0, finalShortVal)).toInt().toShort()
                }

                // Push buffer to AudioTrack
                try {
                    audioTrack?.write(buffer, 0, bufferSize)
                } catch (e: Exception) {
                    break
                }

                // Smooth simulated analyzer FFT bands based on synthesized notes
                val smoothSpectrum = FloatArray(16) { idx ->
                    val rawVal = simulatedSpectrum[idx]
                    // fill empty gaps with ambient frequency ripples
                    val backgroundAmbient = if (isPlaying) (sin((time * 2.5) + idx).toFloat() + 1f) / 2f * 0.12f else 0f
                    val currentVal = max(rawVal, backgroundAmbient)
                    currentVal
                }
                onSpectrumUpdated(smoothSpectrum)
            }
        }
        synthThread?.start()
    }

    fun pause() {
        try {
            audioTrack?.pause()
        } catch (e: Exception) {}
    }

    fun resume() {
        try {
            audioTrack?.play()
        } catch (e: Exception) {}
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
        synthThread?.interrupt()
        synthThread = null
    }

    fun setVolume(vol: Float) {
        this.volume = max(0.0f, min(1.0f, vol))
        try {
            audioTrack?.setVolume(vol)
        } catch (e: Exception) {}
    }
}
