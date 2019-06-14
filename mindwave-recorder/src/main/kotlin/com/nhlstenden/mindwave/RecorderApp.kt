package com.nhlstenden.mindwave

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import javafx.application.Application
import javafx.geometry.Pos
import javafx.scene.image.ImageView
import javafx.scene.layout.Border
import javafx.scene.layout.BorderStroke
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.layout.CornerRadii
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.text.Text
import javafx.util.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.*
import java.io.File

val mapper = jacksonObjectMapper()

fun main(args: Array<String>) {
    Application.launch(RecorderApp::class.java, *args)
}

class RecorderApp : App() {
    override val primaryView = RecorderView::class
}

//main window of the recorder
class RecorderView : View("Mindwave Recorder"), CoroutineScope by MainScope() {
    private val headsetReader = HeadsetReader(this, enableRawData = true)

    private val mediaPlayer = MediaPlayer(Media(resources.url("/sound.wav").toExternalForm()))

    //a handle for the currently running recording
    private var currentRecordingJob by property<Job?>(null)

    private fun currentJobProperty() = getProperty(RecorderView::currentRecordingJob)

    //try to read config and replace it with default if failed
    private val recordingConfig = try {
        mapper.readValue<RecordingConfig>(File("config.json"))
    } catch (_: Exception) {
        RecordingConfig.DEFAULT.also {
            mapper.writerWithDefaultPrettyPrinter().writeValue(File("config.json"), it)
        }
    }

    lateinit var label: Text
    lateinit var image: ImageView

    //layout of the recorder window
    override val root = vbox {
        alignment = Pos.CENTER

        label = text("Press start to start recording")

        spacer {
            style {
                borderWidth += box(1.px, 0.px, 0.px, 0.px)
                borderColor += box(Color.BLACK)
            }
        }

        image = imageview {
            image = resources.image("/none.png")
        }

        spacer {
            style {
                borderWidth += box(1.px, 0.px, 0.px, 0.px)
                borderColor += box(Color.BLACK)
            }
        }

        hbox {
            alignment = Pos.CENTER
            spacing = 40.0

            val startButton = button("start") {
                action {
                    currentRecordingJob = startRecording()
                }

                //only enabled when a task is running
                enableWhen(currentJobProperty().booleanBinding { it == null || !it.isActive })
            }

            button("cancel") {
                action {
                    currentRecordingJob?.cancel()
                    currentRecordingJob = null
                }

                enableWhen(startButton.disabledProperty())
            }
        }
    }

    //starts a recording session on button press
    private fun startRecording() = launch {
        updateUI {
            label.text = "Waiting for connection"
        }

        //wait for reader to stop returning status reports, which indicates it has connected to the headset
        var connected = false
        val connectionSub = headsetReader.subscribe {
            if (it !is HeadsetData.StatusReport) {
                connected = true
            }
        }

        //recording can be cancelled here, which causes yield() to throw an exception
        //finally makes sure we remove the loose subscriber if that happens
        try {
            while (!connected) {
                yield() //does nothing unless task is cancelled
            }
        } finally {
            headsetReader.unsubscribe(connectionSub)
        }

        //create a new file for this recording session
        createTempFile(prefix = "raw", suffix = ".csv", directory = File("."))
            .printWriter()
            .use { out ->
                out.println("timeMs,rawEeg,label")

                val recordingStart = System.currentTimeMillis()

                //iterate through all configured modes and record labelled data for each
                recordingConfig.modes.forEachIndexed { i, mode ->
                    updateUI {
                        image.image = resources.image("/$mode.png")
                        label.text = "Recording data: $mode (${i + 1} / ${recordingConfig.modes.size})"
                        mediaPlayer.stop()
                        mediaPlayer.seek(Duration.ZERO)
                        mediaPlayer.play()
                    }


                    //subscribe to receive and print raw data
                    val packetSub = headsetReader.subscribe { packet ->
                        val currentTime = System.currentTimeMillis()

                        when (packet) {
                            is HeadsetData.RawData ->
                                out.println(
                                    listOf(
                                        currentTime - recordingStart,
                                        packet.rawEeg,
                                        mode
                                    ).joinToString(",")
                                )
                            is HeadsetData.StatusReport ->
                                updateUI {
                                    label.text = "Connection lost"
                                }
                        }
                    }

                    //wait for the duration of the mode and stop listening
                    //also watch out for recording cancellation exception
                    try {
                        delay((1000 * recordingConfig.modeDuration).toLong())
                    } finally {
                        headsetReader.unsubscribe(packetSub)
                    }
                }

                //done
                updateUI {
                    image.image = resources.image("/none.png")
                    label.text = "Recording done"
                    currentRecordingJob = null
                }
            }
    }

    //posts a task to the UI thread
    private fun CoroutineScope.updateUI(block: suspend CoroutineScope.() -> Unit) =
        launch(Dispatchers.JavaFx, block = block)

    override fun onDelete() {
        cancel()
        super.onDelete()
    }
}