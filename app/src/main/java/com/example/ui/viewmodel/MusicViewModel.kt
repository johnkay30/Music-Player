package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlaybackManager
import com.example.data.database.MusicDatabase
import com.example.data.model.*
import com.example.data.repository.MusicRepository
import com.example.scanner.LocalMediaScanner
import com.example.ui.theme.GbeduThemeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = MusicDatabase.getDatabase(context)
    private val repository = MusicRepository(database.musicDao())

    // Audio Playback Engine
    val playbackManager = AudioPlaybackManager(context)

    // UI States
    val allTracks: StateFlow<List<Track>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedTracks: StateFlow<List<Track>> = repository.mostPlayedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAdded: StateFlow<List<Track>> = repository.recentlyAdded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presets: StateFlow<List<EqPreset>> = repository.allPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EqPreset.PRESETS)

    // Current screen search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Playlist detail selection
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _playlistTracks = MutableStateFlow<List<Track>>(emptyList())
    val playlistTracks: StateFlow<List<Track>> = _playlistTracks.asStateFlow()

    // Interactive Theme selection
    private val _currentTheme = MutableStateFlow(GbeduThemeType.AURA_OBSIDIAN)
    val currentTheme: StateFlow<GbeduThemeType> = _currentTheme.asStateFlow()

    // Scanning visual progress indicator state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Playback integration states
    val currentTrack = playbackManager.currentTrack
    val isPlaying = playbackManager.isPlaying
    val playbackProgress = playbackManager.playbackProgress
    val currentPosition = playbackManager.currentPosition
    val visualizerBands = playbackManager.visualizerBands
    val sleepMinutesLeft = playbackManager.sleepMinutesLeft
    val currentQueue = playbackManager.queue

    // Repeat & Shuffle state copies for Composable binding
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow("NONE") // "NONE", "ONE", "ALL"
    val repeatMode: StateFlow<String> = _repeatMode.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(true)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _currentPresetName = MutableStateFlow("Flat")
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()

    private val _crossfadeSec = MutableStateFlow(0)
    val crossfadeSec: StateFlow<Int> = _crossfadeSec.asStateFlow()

    init {
        // Run database initialization and fallback tracks configuration
        viewModelScope.launch(Dispatchers.IO) {
            // Setup default database presets
            for (p in EqPreset.PRESETS) {
                repository.savePreset(p)
            }
            // Auto trigger scan on initial launch. If db is empty, populate demo synth tracks
            setupInitialTracksIfEmpty()
        }
    }

    private suspend fun setupInitialTracksIfEmpty() {
        val count = database.musicDao().getTrackById("demo_1")
        if (count == null) {
            // First run: pre-populate Room with local synthesized Gbedu demo tracks so Gbedu Player has immediate offline playability
            val demos = LocalMediaScanner.getDemoTracks()
            repository.insertTracks(demos)
        }
    }

    fun scanMedia() {
        if (_isScanning.value) return
        _isScanning.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear demo tracks if the user initiates a manual scan? Or keep them side-by-side.
                // Keeping demo tracks as fallback is always awesome. Let's scan from the device now
                val scanner = LocalMediaScanner(context)
                val foundTracks = scanner.scanLocalAudio()

                if (foundTracks.isNotEmpty()) {
                    // Update database
                    repository.insertTracks(foundTracks)
                } else {
                    // Ensure demos exist
                    val demos = LocalMediaScanner.getDemoTracks()
                    repository.insertTracks(demos)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Playback control wrappers
    fun playTrack(track: Track, fromList: List<Track>) {
        viewModelScope.launch(Dispatchers.Main) {
            playbackManager.setQueue(fromList, fromList.indexOf(track))
            repository.incrementPlayCount(track.id)
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun skipToNext() {
        playbackManager.skipToNext()
    }

    fun skipToPrevious() {
        playbackManager.skipToPrevious()
    }

    fun seekTo(progress: Float) {
        val duration = currentTrack.value?.duration ?: return
        playbackManager.seekTo((progress * duration).toLong())
    }

    fun toggleShuffle() {
        val newVal = !_isShuffle.value
        _isShuffle.value = newVal
        playbackManager.setShuffleEnabled(newVal)
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            "NONE" -> "ALL"
            "ALL" -> "ONE"
            else -> "NONE"
        }
        _repeatMode.value = nextMode
        when (nextMode) {
            "NONE" -> playbackManager.setRepeatMode(one = false, all = false)
            "ALL" -> playbackManager.setRepeatMode(one = false, all = true)
            "ONE" -> playbackManager.setRepeatMode(one = true, all = false)
        }
    }

    // Favorites
    fun toggleFavorite(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateFavoriteStatus(track.id, !track.isFavorite)
            // also update currently playing cache if it corresponds to this track
            val playing = currentTrack.value
            if (playing != null && playing.id == track.id) {
                // Update currentTrack. This naturally triggers UI updates.
                playbackManager.playTrack(playing.copy(isFavorite = !track.isFavorite))
                // Keep playing state
                if (!isPlaying.value) {
                    playbackManager.pause()
                }
            }
        }
    }

    // Themes
    fun setTheme(theme: GbeduThemeType) {
        _currentTheme.value = theme
    }

    // Custom Playlists
    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlist.id)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrackToPlaylist(playlist.id, track.id)
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTrackFromPlaylist(playlist.id, track.id)
            // Refresh visible playlist songs
            loadPlaylistTracks(playlist)
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        if (playlist != null) {
            loadPlaylistTracks(playlist)
        } else {
            _playlistTracks.value = emptyList()
        }
    }

    private fun loadPlaylistTracks(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getTracksForPlaylist(playlist.id).collectLatest { songs ->
                _playlistTracks.value = songs
            }
        }
    }

    // Equalizer Adjustments
    fun setEqPreset(preset: EqPreset) {
        _currentPresetName.value = preset.name
        playbackManager.applyEqPreset(preset)
    }

    fun toggleEqualizer() {
        val nextVal = !_equalizerEnabled.value
        _equalizerEnabled.value = nextVal
        playbackManager.toggleEqualizer(nextVal)
    }

    fun updateEqSlider(bandIndex: Int, value: Float) {
        // Create custom preset and save/apply
        val basePreset = presets.value.find { it.name == _currentPresetName.value } ?: presets.value.first()
        val customName = "Custom"
        
        val newPreset = when (bandIndex) {
            0 -> basePreset.copy(name = customName, band1 = value, isCustom = true)
            1 -> basePreset.copy(name = customName, band2 = value, isCustom = true)
            2 -> basePreset.copy(name = customName, band3 = value, isCustom = true)
            3 -> basePreset.copy(name = customName, band4 = value, isCustom = true)
            4 -> basePreset.copy(name = customName, band5 = value, isCustom = true)
            else -> basePreset
        }
        _currentPresetName.value = customName
        playbackManager.applyEqPreset(newPreset)
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePreset(newPreset)
        }
    }

    fun updateBassBoost(boostVal: Float) {
        val basePreset = presets.value.find { it.name == _currentPresetName.value } ?: presets.value.first()
        val customName = "Custom"
        val newPreset = basePreset.copy(name = customName, bassBoost = boostVal, isCustom = true)
        _currentPresetName.value = customName
        playbackManager.applyEqPreset(newPreset)
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePreset(newPreset)
        }
    }

    fun updateVirtualizer(virtualizerVal: Float) {
        val basePreset = presets.value.find { it.name == _currentPresetName.value } ?: presets.value.first()
        val customName = "Custom"
        val newPreset = basePreset.copy(name = customName, virtualizer = virtualizerVal, isCustom = true)
        _currentPresetName.value = customName
        playbackManager.applyEqPreset(newPreset)
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePreset(newPreset)
        }
    }

    // Crossfade control
    fun setCrossfade(seconds: Int) {
        _crossfadeSec.value = seconds
        playbackManager.setCrossfade(seconds)
    }

    // Sleep timer control
    fun startSleepTimer(minutes: Int) {
        playbackManager.startSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        playbackManager.cancelSleepTimer()
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.onDestroy()
    }
}
