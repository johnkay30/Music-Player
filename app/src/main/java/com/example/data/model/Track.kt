package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val genre: String = "Unknown",
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val isDemo: Boolean = false
) : Serializable

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val trackCount: Int,
    val year: Int = 0,
    val isDemo: Boolean = false
)

data class Artist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val isDemo: Boolean = false
)

data class Genre(
    val name: String,
    val trackCount: Int
)
