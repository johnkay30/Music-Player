package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Tracks
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): Track?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavoriteStatus(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String)

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY playCount DESC LIMIT 30")
    fun getMostPlayedTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT 30")
    fun getRecentlyAddedTracks(): Flow<List<Track>>

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Transaction
    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_track_cross_ref r ON t.id = r.trackId 
        WHERE r.playlistId = :playlistId 
        ORDER BY t.title ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>>

    // Equalizer Presets
    @Query("SELECT * FROM equalizer_presets")
    fun getAllPresets(): Flow<List<EqPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreset(preset: EqPreset)

    @Query("DELETE FROM equalizer_presets WHERE name = :name")
    suspend fun deletePreset(name: String)

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()
}
