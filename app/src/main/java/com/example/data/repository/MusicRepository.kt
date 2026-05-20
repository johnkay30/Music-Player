package com.example.data.repository

import com.example.data.dao.MusicDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {
    val allTracks: Flow<List<Track>> = musicDao.getAllTracks()
    val favoriteTracks: Flow<List<Track>> = musicDao.getFavoriteTracks()
    val mostPlayedTracks: Flow<List<Track>> = musicDao.getMostPlayedTracks()
    val recentlyAddedTracks: Flow<List<Track>> = musicDao.getRecentlyAddedTracks()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val allPresets: Flow<List<EqPreset>> = musicDao.getAllPresets()

    // We can also have simple non-stale flows:
    val recentlyAdded: Flow<List<Track>> = musicDao.getRecentlyAddedTracks()

    suspend fun getTrackById(trackId: String): Track? = musicDao.getTrackById(trackId)

    suspend fun insertTracks(tracks: List<Track>) = musicDao.insertTracks(tracks)

    suspend fun updateFavoriteStatus(trackId: String, isFavorite: Boolean) = 
        musicDao.updateFavoriteStatus(trackId, isFavorite)

    suspend fun incrementPlayCount(trackId: String) = musicDao.incrementPlayCount(trackId)

    suspend fun createPlaylist(name: String, isSmart: Boolean = false, iconName: String = "playlist_play"): Long {
        return musicDao.createPlaylist(Playlist(name = name, isSmart = isSmart, iconName = iconName))
    }

    suspend fun deletePlaylist(playlistId: Long) = musicDao.deletePlaylist(playlistId)

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        musicDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        musicDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> = 
        musicDao.getTracksForPlaylist(playlistId)

    suspend fun savePreset(preset: EqPreset) = musicDao.savePreset(preset)

    suspend fun deletePreset(name: String) = musicDao.deletePreset(name)

    suspend fun clearAllTracks() = musicDao.clearAllTracks()
}
