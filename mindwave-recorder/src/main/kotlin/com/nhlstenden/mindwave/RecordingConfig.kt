package com.nhlstenden.mindwave

//configuration for the recorder app
//recording rate is rate at which data wil
data class RecordingConfig(
    val recordingRate: Double,
    val modeDuration: Double,
    val modes: List<String>
) {
    companion object {
        val DEFAULT = RecordingConfig(100.0, 5.0, listOf("left", "none", "right"))
    }
}