package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.util.Log
import com.example.data.model.EqPreset
import com.example.data.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.*
import kotlin.math.max
import kotlin.math.min

class AudioPlaybackManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Player instances
    private var currentPlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null // For gapless and crossfade

    // Audio Effects bound to current session
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    // Synth Player for Demo Tracks
    private var synthPlayer: GbeduSynth? = null

    // Queue & Playback State
    private var playlist: List<Track> = emptyList()
    private var currentTrackIndex = -1
    private var isShuffle = false
    private var isRepeatAll = false
    private var isRepeatOne = false

    // State flows
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0.0f to 1.0f
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L) // in ms
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    // Config parameters
    private var crossfadeDurationSec = 0 // 0 to 5 seconds
    private var isSleepTimerActive = false
    private var sleepTimerJob: Job? = null
    private val _sleepMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepMinutesLeft: StateFlow<Int?> = _sleepMinutesLeft.asStateFlow()

    // FX State
    private var currentPreset: EqPreset = EqPreset.PRESETS.first()
    private var enabledFX = true

    // Visualizer simulation flow (provides dynamic FFT-like bands for visualizer animations)
    private val _visualizerBands = MutableStateFlow(FloatArray(16) { 0f })
    val visualizerBands: StateFlow<FloatArray> = _visualizerBands.asStateFlow()

    private var progressTrackerJob: Job? = null
    private var crossfadeJob: Job? = null

    // Headphone disconnect receiver
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    init {
        // Register receiver for headphone disconnection
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(noisyReceiver, filter)
        } catch (e: Exception) {
            Log.e("AudioManager", "Failed to register headphone disconnect receiver", e)
        }
        startVisualizerSimulation()
    }

    fun setQueue(tracks: List<Track>, startIndex: Int) {
        this.playlist = tracks
        _queue.value = tracks
        if (startIndex in tracks.indices) {
            currentTrackIndex = startIndex
            playTrack(tracks[startIndex])
        }
    }

    fun getQueue(): List<Track> = playlist

    fun playTrack(track: Track) {
        // Stop current reproduction
        stopAllPlayers()

        _currentTrack.value = track
        _isPlaying.value = true

        if (track.isDemo) {
            // Use Gbedu Synth Player
            currentPlayer = null
            synthPlayer?.stop()
            synthPlayer = GbeduSynth(track.title) { visualizerValues ->
                // Feed real synth frequency changes directly to visualizer!
                _visualizerBands.value = visualizerValues
            }
            synthPlayer?.start()
            startPositionTrackerForSynth(track.duration)
        } else {
            // Play real music file
            synthPlayer?.stop()
            synthPlayer = null
            try {
                val player = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(context, Uri.parse(track.path))
                    prepare()
                    start()
                }
                currentPlayer = player
                setupAudioEffects(player.audioSessionId)

                player.setOnCompletionListener {
                    handleTrackCompletion()
                }

                startPositionTrackerForRealTrack(player, track.duration)
            } catch (e: IOException) {
                Log.e("AudioPlaybackManager", "Error setting data source/preparing", e)
                // Fallback: If local file is unplayable or corrupted, simulate as demo synth
                val demoFallback = track.copy(isDemo = true)
                _currentTrack.value = demoFallback
                _isPlaying.value = true
                synthPlayer = GbeduSynth(demoFallback.title) { _visualizerBands.value = it }
                synthPlayer?.start()
                startPositionTrackerForSynth(demoFallback.duration)
            }
        }
    }

    fun togglePlayPause() {
        val currTrack = _currentTrack.value ?: return
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        _isPlaying.value = false
        if (_currentTrack.value?.isDemo == true) {
            synthPlayer?.pause()
        } else {
            currentPlayer?.let {
                if (it.isPlaying) {
                     it.pause()
                }
            }
        }
    }

    fun resume() {
        val currTrack = _currentTrack.value ?: return
        _isPlaying.value = true
        if (currTrack.isDemo) {
            synthPlayer?.resume()
        } else {
            currentPlayer?.let {
                try {
                    it.start()
                } catch (e: Exception) {
                    Log.e("AudioPlaybackManager", "Error resuming MediaPlayer", e)
                }
            }
        }
    }

    fun stop() {
        pause()
        stopAllPlayers()
        _currentTrack.value = null
        _currentPosition.value = 0L
        _playbackProgress.value = 0f
    }

    fun seekTo(positionMs: Long) {
        val currTrack = _currentTrack.value ?: return
        if (currTrack.isDemo) {
            _currentPosition.value = clamp(positionMs, 0, currTrack.duration)
            _playbackProgress.value = _currentPosition.value.toFloat() / currTrack.duration
        } else {
            currentPlayer?.let {
                try {
                    val progressSeek = clamp(positionMs.toInt(), 0, it.duration)
                    it.seekTo(progressSeek)
                    _currentPosition.value = progressSeek.toLong()
                    _playbackProgress.value = progressSeek.toFloat() / it.duration
                } catch (e: Exception) {
                    Log.e("AudioPlaybackManager", "Seek failed", e)
                }
            }
        }
    }

    fun skipToNext() {
        if (playlist.isEmpty()) return

        if (isRepeatOne) {
            // Repeat same track
            _currentTrack.value?.let { playTrack(it) }
            return
        }

        if (isShuffle) {
            val nextIndex = Random().nextInt(playlist.size)
            currentTrackIndex = nextIndex
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size
            if (!isRepeatAll && currentTrackIndex == 0) {
                // Stopped at the end of playlist
                stop()
                return
            }
        }

        if (currentTrackIndex in playlist.indices) {
            playTrack(playlist[currentTrackIndex])
        }
    }

    fun skipToPrevious() {
        if (playlist.isEmpty()) return

        if (_currentPosition.value > 3000) {
            // Seek to beginning if playing for more than 3 seconds
            seekTo(0)
            return
        }

        if (isShuffle) {
            val prevIndex = Random().nextInt(playlist.size)
            currentTrackIndex = prevIndex
        } else {
            currentTrackIndex = if (currentTrackIndex - 1 < 0) {
                playlist.size - 1
            } else {
                currentTrackIndex - 1
            }
        }

        if (currentTrackIndex in playlist.indices) {
            playTrack(playlist[currentTrackIndex])
        }
    }

    private fun handleTrackCompletion() {
        if (crossfadeDurationSec > 0 && _currentTrack.value?.isDemo == false) {
             // crossfade already triggered or we chain to next
        }
        skipToNext()
    }

    private fun stopAllPlayers() {
        progressTrackerJob?.cancel()
        crossfadeJob?.cancel()
        synthPlayer?.stop()
        synthPlayer = null

        currentPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        currentPlayer = null

        nextPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        nextPlayer = null

        releaseAudioEffects()
    }

    // Positions tracker
    private fun startPositionTrackerForRealTrack(player: MediaPlayer, duration: Long) {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (_isPlaying.value) {
                    try {
                        val pos = player.currentPosition.toLong()
                        _currentPosition.value = pos
                        _playbackProgress.value = if (duration > 0) pos.toFloat() / duration else 0f

                        // Crossfade check
                        if (crossfadeDurationSec > 0 && duration - pos <= crossfadeDurationSec * 1000L && nextPlayer == null) {
                            startCrossfadeTransition()
                        }
                    } catch (e: Exception) {
                        // ignore if player released
                    }
                }
                delay(200)
            }
        }
    }

    private fun startPositionTrackerForSynth(duration: Long) {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch(Dispatchers.Main) {
            var cachedPos = _currentPosition.value
            while (isActive) {
                if (_isPlaying.value) {
                    cachedPos += 200
                    if (cachedPos >= duration) {
                        cachedPos = 0
                        _currentPosition.value = 0
                        _playbackProgress.value = 0f
                        skipToNext()
                        break
                    }
                    _currentPosition.value = cachedPos
                    _playbackProgress.value = cachedPos.toFloat() / duration
                }
                delay(200)
            }
        }
    }

    private fun startCrossfadeTransition() {
        val nextIndex = if (isShuffle) {
            Random().nextInt(playlist.size)
        } else {
            (currentTrackIndex + 1) % playlist.size
        }
        if (nextIndex == 0 && !isRepeatAll) return // Do not loop if not repeat all
        val nextTrack = playlist.getOrNull(nextIndex) ?: return

        if (nextTrack.isDemo) return // Synth doesn't support crossfade easily

        crossfadeJob = scope.launch(Dispatchers.IO) {
            try {
                val nextMedia = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(context, Uri.parse(nextTrack.path))
                    setVolume(0f, 0f)
                    prepare()
                    start()
                }
                nextPlayer = nextMedia

                val steps = 10
                val delayTime = (crossfadeDurationSec * 1000) / steps
                for (i in 1..steps) {
                    val progress = i.toFloat() / steps
                    withContext(Dispatchers.Main) {
                        currentPlayer?.setVolume(1f - progress, 1f - progress)
                        nextPlayer?.setVolume(progress, progress)
                    }
                    delay(delayTime.toLong())
                }

                withContext(Dispatchers.Main) {
                    currentPlayer?.let {
                        try {
                            if (it.isPlaying) it.stop()
                            it.release()
                        } catch (e: Exception) {}
                    }
                    currentPlayer = nextPlayer
                    nextPlayer = null
                    currentTrackIndex = nextIndex
                    _currentTrack.value = nextTrack
                    currentPlayer?.setVolume(1f, 1f)
                    currentPlayer?.setOnCompletionListener {
                        handleTrackCompletion()
                    }
                    currentPlayer?.audioSessionId?.let { setupAudioEffects(it) }
                    startPositionTrackerForRealTrack(currentPlayer!!, nextTrack.duration)
                }
            } catch (e: Exception) {
                Log.e("AudioPlaybackManager", "Crossfade failed", e)
            }
        }
    }

    // Dynamic FFT Waveform simulation for modern visualizers
    private fun startVisualizerSimulation() {
        scope.launch(Dispatchers.Default) {
             val r = Random()
             while (isActive) {
                 if (_isPlaying.value) {
                     // If demo is playing, GbeduSynth provides its exact synthesized frequencies.
                     // Otherwise, we gracefully simulate elegant waves
                     if (_currentTrack.value?.isDemo == false) {
                         val simulated = FloatArray(16) { i ->
                             // elegant wave mathematical combination
                             val wave = Math.sin((System.currentTimeMillis() / 400.0) + (i * 0.4)).toFloat()
                             val offset = r.nextFloat() * 0.2f
                             max(0.05f, min(1.0f, (wave + 1f) / 2f * 0.7f + offset))
                         }
                         _visualizerBands.value = simulated
                     }
                 } else {
                     // fade bands to zero
                     val current = _visualizerBands.value
                     val faded = FloatArray(16) { i -> max(0f, current[i] - 0.1f) }
                     _visualizerBands.value = faded
                 }
                 delay(80)
             }
        }
    }

    // Equalizer, Bass Boost, Virtualizer configurations
    private fun setupAudioEffects(audioSessionId: Int) {
        if (!enabledFX) return
        try {
            releaseAudioEffects()

            val eq = Equalizer(0, audioSessionId)
            eq.enabled = true
            applyPresetToHardware(eq, currentPreset)
            equalizer = eq

            // Bass boost
            val bb = BassBoost(0, audioSessionId)
            bb.enabled = true
            if (bb.strengthSupported) {
                bb.setStrength((currentPreset.bassBoost * 10).toInt().toShort()) // scale 0-100 to 0-1000
            }
            bassBoost = bb

            // Virtualizer
            val virt = Virtualizer(0, audioSessionId)
            virt.enabled = true
            if (virt.strengthSupported) {
                virt.setStrength((currentPreset.virtualizer * 10).toInt().toShort())
            }
            virtualizer = virt

        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed to setup system Audio Effects; falling back to UI-simulation mode", e)
        }
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
            virtualizer?.release()
            virtualizer = null
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun applyPresetToHardware(eq: Equalizer, preset: EqPreset) {
        try {
            val numBands = eq.numberOfBands
            val bandsToSet = min(numBands.toInt(), 5)
            val presetBands = listOf(preset.band1, preset.band2, preset.band3, preset.band4, preset.band5)

            for (i in 0 until bandsToSet) {
                val dbVal = presetBands[i]
                // Convert db back to millibels (-1500 to +1500 millibels)
                val mB = (dbVal * 100).toInt().toShort()
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val clamped = max(minLevel.toInt(), min(maxLevel.toInt(), mB.toInt())).toShort()
                eq.setBandLevel(i.toShort(), clamped)
            }
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed to set bands", e)
        }
    }

    fun applyEqPreset(preset: EqPreset) {
        currentPreset = preset
        equalizer?.let {
            applyPresetToHardware(it, preset)
        }
        bassBoost?.let {
            if (it.strengthSupported) {
                try {
                    it.setStrength((preset.bassBoost * 10).toInt().toShort())
                } catch (e: Exception) {}
            }
        }
        virtualizer?.let {
            if (it.strengthSupported) {
                try {
                    it.setStrength((preset.virtualizer * 10).toInt().toShort())
                } catch (e: Exception) {}
            }
        }
    }

    fun toggleEqualizer(enabled: Boolean) {
        enabledFX = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        if (enabled) {
            currentPlayer?.audioSessionId?.let { setupAudioEffects(it) }
        } else {
            releaseAudioEffects()
        }
    }

    // Setters for Repeat and Shuffle
    fun setShuffleEnabled(enabled: Boolean) {
        isShuffle = enabled
    }

    fun isShuffleEnabled() = isShuffle

    fun setRepeatMode(one: Boolean, all: Boolean) {
        isRepeatOne = one
        isRepeatAll = all
    }

    fun isRepeatOneEnabled() = isRepeatOne
    fun isRepeatAllEnabled() = isRepeatAll

    fun setCrossfade(seconds: Int) {
        this.crossfadeDurationSec = clamp(seconds, 0, 5)
    }

    fun getCrossfade() = crossfadeDurationSec

    // Sleep Timer
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            isSleepTimerActive = false
            _sleepMinutesLeft.value = null
            return
        }

        isSleepTimerActive = true
        _sleepMinutesLeft.value = minutes

        sleepTimerJob = scope.launch(Dispatchers.Main) {
            var minRemaining = minutes
            while (minRemaining > 0) {
                delay(60 * 1000L) // wait 1 minute
                minRemaining--
                _sleepMinutesLeft.value = if (minRemaining > 0) minRemaining else null
            }
            // Fade out volume and pause
            fadeOutAndPause()
            isSleepTimerActive = false
            _sleepMinutesLeft.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepMinutesLeft.value = null
        isSleepTimerActive = false
    }

    private suspend fun fadeOutAndPause() {
        if (_isPlaying.value) {
            val steps = 10
            for (i in steps downTo 0) {
                val volume = i.toFloat() / steps
                withContext(Dispatchers.Main) {
                    if (_currentTrack.value?.isDemo == true) {
                        synthPlayer?.setVolume(volume)
                    } else {
                        currentPlayer?.setVolume(volume, volume)
                    }
                }
                delay(150)
            }
            pause()
            // Reset volumes
            withContext(Dispatchers.Main) {
                currentPlayer?.setVolume(1f, 1f)
                synthPlayer?.setVolume(1f)
            }
        }
    }

    fun onDestroy() {
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {}
        stopAllPlayers()
        scope.cancel()
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        return max(min, min(max, value))
    }

    private fun clamp(value: Long, min: Long, max: Long): Long {
        return max(min, min(max, value))
    }
}
