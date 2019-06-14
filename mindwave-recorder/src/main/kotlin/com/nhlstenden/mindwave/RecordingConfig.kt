package com.nhlstenden.mindwave

//configuration for the recorder app
//recording rate is rate at which data wil
data class RecordingConfig(
    val modeDuration: Double,
    val modes: List<String>
) {
    companion object {
        val DEFAULT = RecordingConfig(5.0, listOf("left", "none", "right"))
    }
}