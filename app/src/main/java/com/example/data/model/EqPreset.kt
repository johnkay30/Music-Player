package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equalizer_presets")
data class EqPreset(
    @PrimaryKey val name: String,
    val band1: Float, // 60Hz
    val band2: Float, // 230Hz
    val band3: Float, // 910Hz
    val band4: Float, // 4kHz
    val band5: Float, // 14kHz
    val bassBoost: Float = 0f, // 0 to 100
    val virtualizer: Float = 0f, // 0 to 100
    val isCustom: Boolean = false
) {
    companion object {
        val PRESETS = listOf(
            EqPreset("Flat", 0f, 0f, 0f, 0f, 0f),
            EqPreset("Rock", 4f, 2f, -1f, 3f, 5f),
            EqPreset("Pop", -1f, 1f, 3f, 2f, -1f),
            EqPreset("Jazz", 3f, 2f, -2f, 2f, -1f),
            EqPreset("Classical", 4f, 3f, -1f, -1f, 3f),
            EqPreset("Hip Hop", 5f, 3f, 0f, 1f, 3f),
            EqPreset("Dance", 4f, 0f, 2f, 4f, 1f),
            EqPreset("Vocal", -2f, -3f, 3f, 4f, 2f),
            EqPreset("Bass Booster", 6f, 4f, 0f, 0f, 0f),
            EqPreset("Treble Booster", 0f, 0f, 0f, 4f, 6f),
            EqPreset("Electronic", 4f, 2f, -1f, 2f, 4f)
        )
    }
}
