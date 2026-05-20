package com.example.scanner

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.Track
import java.io.File

class LocalMediaScanner(private val context: Context) {

    fun scanLocalAudio(): List<Track> {
        val tracksList = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val trackCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol).toString()
                    val path = it.getString(dataCol) ?: ""
                    val musicUri = "content://media/external/audio/media/$id"

                    // On modern Android (API 29+ / Scoped Storage), java.io.File(path).exists() returns false for shared media.
                    // By removing that check, we can fully query and play all device music via the Media Content URI.
                    val title = it.getString(titleCol) ?: "Unknown Song"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val duration = it.getLong(durationCol)
                    val trackNum = it.getInt(trackCol)
                    val year = it.getInt(yearCol)
                    val dateAdded = it.getLong(dateAddedCol) * 1000L // Convert sec to ms

                    // Determine standard genre fallback based on folder or metadata
                    var genre = "Unknown"
                    if (path.contains("pop", ignoreCase = true)) genre = "Pop"
                    else if (path.contains("rock", ignoreCase = true)) genre = "Rock"
                    else if (path.contains("jazz", ignoreCase = true)) genre = "Jazz"
                    else if (path.contains("afro", ignoreCase = true)) genre = "Afrobeats"

                    tracksList.add(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = if (duration > 0) duration else 180000L,
                            path = musicUri,
                            trackNumber = trackNum,
                            year = year,
                            dateAdded = dateAdded,
                            genre = genre,
                            isDemo = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("LocalMediaScanner", "Failed to query media store", e)
        }

        return tracksList
    }

    companion object {
        fun getDemoTracks(): List<Track> {
            return listOf(
                Track(
                    id = "demo_1",
                    title = "African Sunrise Beat",
                    artist = "Gbedu Synth Engine",
                    album = "Organic Roots",
                    duration = 194000L, // 3:14
                    path = "synth:african_sunrise",
                    trackNumber = 1,
                    year = 2026,
                    genre = "Afrobeats",
                    isDemo = true
                ),
                Track(
                    id = "demo_2",
                    title = "Lagos Synthwave",
                    artist = "Neon Skyline",
                    album = "Midnight Transit",
                    duration = 240000L, // 4:00
                    path = "synth:lagos_synthwave",
                    trackNumber = 2,
                    year = 2026,
                    genre = "Synthwave",
                    isDemo = true
                ),
                Track(
                    id = "demo_3",
                    title = "Cyber Grid Ambient",
                    artist = "Altered Frequencies",
                    album = "Matrix Drift",
                    duration = 300000L, // 5:00
                    path = "synth:cyber_grid",
                    trackNumber = 3,
                    year = 2026,
                    genre = "Ambient",
                    isDemo = true
                ),
                Track(
                    id = "demo_4",
                    title = "Sunset Sahara Flute",
                    artist = "Nomad Chords",
                    album = "Dune Echoes",
                    duration = 186000L, // 3:06
                    path = "synth:sahara_flute",
                    trackNumber = 4,
                    year = 2026,
                    genre = "Ethnic Ambient",
                    isDemo = true
                )
            )
        }
    }
}
