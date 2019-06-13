package com.nhlstenden.mindwave

import javafx.application.Application
import javafx.geometry.Pos
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import tornadofx.*
import java.io.File

fun main() {
    Application.launch(RecorderApp::class.java)
}

class RecorderApp : App() {
    override val primaryView = RecorderView::class

    override fun stop() {
        headsetReader.close()
        super.stop()
    }
}

val headsetReader = HeadsetReader(enableRawData = true)

class RecorderView : View("Mindwave Recorder") {
    private val mediaPlayer = MediaPlayer(Media(resources.url("/sound.wav").toExternalForm()))

    override val root = vbox {
        alignment = Pos.CENTER
        padding = insets(0, 10)

        val label = text("Press start to start recording")

        val image = imageview {
            image = resources.image("/none.png")
        }

        button("start") {
            action {
                //TODO forbid more than 1 action at once
                GlobalScope.launch {
                    launch(Dispatchers.JavaFx) {
                        label.text = "Waiting for connection"
                    }

                    do {
                        val data = headsetReader.read()
                        println(data)
                    } while (data is HeadsetData.StatusReport)

                    val modes = listOf("left", "right", "left", "none", "right", "none", "right", "left")

                    createTempFile(suffix = ".csv", directory = File(".")).printWriter().use { out ->

                        out.println("timeMs,rawEeg,label")

                        val recordingStart = System.currentTimeMillis()

                        modes.forEachIndexed { i, mode ->
                            val modeStart = System.currentTimeMillis()

                            launch(Dispatchers.JavaFx) {
                                image.image = resources.image("/$mode.png")
                                label.text = "Recording data: $mode (${i + 1} / ${modes.size})"
                                mediaPlayer.stop()
                                mediaPlayer.seek(Duration.ZERO)
                                mediaPlayer.play()
                            }

                            do {
                                val currentTime = System.currentTimeMillis()

                                when (val data = headsetReader.read()) {
                                    //TODO filter low signal based on GeneralData
                                    is HeadsetData.RawData ->
                                        out.println(
                                            listOf(
                                                currentTime - recordingStart,
                                                data.rawEeg,
                                                mode
                                            ).joinToString(",").also(::println)
                                        )
                                    is HeadsetData.StatusReport ->
                                        launch(Dispatchers.JavaFx) {
                                            label.text = "Connection lost"
                                        }
                                }
                            } while (currentTime - modeStart < 5000)

                            launch(Dispatchers.JavaFx) {
                                image.image = resources.image("/none.png")
                                label.text = "Recording done"
                            }
                        }
                    }
                }
            }
        }
    }
}