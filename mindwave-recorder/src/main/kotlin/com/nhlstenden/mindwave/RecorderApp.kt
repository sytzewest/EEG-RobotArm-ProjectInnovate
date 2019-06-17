package com.nhlstenden.mindwave

import com.fasterxml.jackson.module.kotlin.readValue
import javafx.application.Application
import javafx.geometry.Pos
import javafx.scene.image.ImageView
import javafx.scene.paint.Color
import javafx.scene.text.Text
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.*
import java.io.File

fun main(args: Array<String>) {
    Application.launch(RecorderApp::class.java, *args)
}

class RecorderApp : App() {
    override val primaryView = RecorderView::class
}

//main window of the recorder
class RecorderView : View("Mindwave Recorder"), CoroutineScope by MainScope() {
    private val headsetReader = HeadsetReader(enableRawData = true)

    //a handle for the currently running recording
    private var currentRecordingJob by property<Job?>(null)

    //a reactive property for the handle, used to update the buttons
    private fun currentJobProperty() = getProperty(RecorderView::currentRecordingJob)

    //try to read config and replace it with default if failed
    private val recordingConfig = try {
        mapper.readValue<RecordingConfig>(File("config.json"))
    } catch (_: Exception) {
        RecordingConfig().also {
            mapper.writerWithDefaultPrettyPrinter().writeValue(File("config.json"), it)
        }
    }

    private lateinit var label: Text
    private lateinit var image: ImageView

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
                    //cancel and discard the recording task
                    currentRecordingJob?.cancel()
                    currentRecordingJob = null
                }

                enableWhen(startButton.disabledProperty())
            }
        }
    }

    //starts a recording session on button press
    private fun startRecording() = launch(Dispatchers.Default) {
        updateUI {
            label.text = "Waiting for connection"
        }

        //wait for raw data to become available
        while (headsetReader.getLatestPacket<HeadsetData.RawData>() == null) {
            yield()
        }

        val outFile = File("rawData.csv")
        //if file doesn't exist, create it and write the table heading in the background
        val createTask = async(Dispatchers.IO) {
            if (!outFile.exists()) {
                outFile.createNewFile()
                val valueLabels = (1..(recordingConfig.valuesBefore + recordingConfig.valuesAfter)).joinToString(
                    separator = ",",
                    transform = { "v$it" }
                )
                outFile.appendText("$valueLabels,label\n")
            }
        }

        //generate a series of random modes to record
        val modes = (1..recordingConfig.iterations).map {
            listOf("left", "right").random()
        }

        //write new data to a string
        val newData = StringBuilder()
        //iterate through all configured modes and record labelled data for each
        modes.forEachIndexed { i, mode ->
            //hide arrow, record before
            updateUI {
                image.image = resources.image("/none.png")
                label.text = "Recording data (${i + 1} / ${recordingConfig.iterations})"
            }.join()

            val before = recordDataset(recordingConfig.valuesBefore, recordingConfig.recordingPauseNs)

            //show arrow, record after
            updateUI {
                image.image = resources.image("/$mode.png")
            }.join()

            val after = recordDataset(recordingConfig.valuesAfter, recordingConfig.recordingPauseNs)
            newData.append((before + after + mode).joinToString(",", postfix = "\n"))

            require(before.size == recordingConfig.valuesBefore)
            require(after.size == recordingConfig.valuesAfter)

            if (recordingConfig.pauseBetweenModesMs > 0) {
                //hide arrow, wait before next recording
                updateUI {
                    image.image = resources.image("/none.png")
                }.join()

                //delay(..) seems bugged, breaks system timer?
                //using Thread.sleep instead
                Thread.sleep(recordingConfig.pauseBetweenModesMs)
            }
        }

        //show completion
        updateUI {
            image.image = resources.image("/none.png")
            label.text = "Recording done"
            currentRecordingJob = null
        }

        //wait for the file creation and writing to finish (it likely)
        //then append the data to the file
        createTask.await()
        outFile.appendText(newData.toString())
    }

    //posts a task to the UI thread
    private fun CoroutineScope.updateUI(block: suspend CoroutineScope.() -> Unit) =
        launch(Dispatchers.JavaFx, block = block)

    //records values from the HeadsetReader
    private fun recordDataset(
        valueCount: Int,
        recordingPauseNs: Long
    ): List<String> {

        //record the specified amount of packets, waiting for the specified period between each recording
        return List(valueCount) {
            Thread.sleep(recordingPauseNs / 1000000, (recordingPauseNs % 1000000).toInt())
            val packet = headsetReader.getLatestPacket<HeadsetData.RawData>()!!

            packet.packet.rawEeg.toString()
        }
    }

    //cancel all background tasks if the application is closed
    override fun onDelete() {
        cancel()
        super.onDelete()
    }
}