package com.nhlstenden.mindwave

//configuration for the recorder app
data class RecordingConfig(
    val iterations: Int = 3, //how many arrows to show
    val recordingPauseNs: Long = 100_000, //how often to record packets (in nanoseconds)
    val pauseBetweenModesMs: Long = 500, //how long to wait between arrow recordings (in milliseconds)
    val valuesBefore: Int = 200, //how many data points to record before each arrow
    val valuesAfter: Int = 200 //how many data points to record after each arrow
)