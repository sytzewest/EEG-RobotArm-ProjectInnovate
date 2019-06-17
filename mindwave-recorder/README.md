# MindWave raw data recorder

A simple visual program that displays a configurable series of arrows and records data from the headset
using TG_Connector. The data is then labeled by time from recording start (in ms) and arrow displayed at the time of recording
and saved to a .csv file 


## Running the recorder
To run the program, first do one of the following  
* build the project with `./gradlew build`, then run the created JAR file with `java -jar build/libs/mindwave-recorder-<version>-all.jar`
* run the project with `./gradlew run`

Make sure the headset is connected and TG_Connector is running. Press the start button to start recording.

## Using the recorder

On first launch, a config.json file will be created. It has several configuration options:
* iterations - how many random arrows will be shown
* recordingPauseNs - pause (in nanoseconds) before data points are read. **WARNING**: if this is set to less than 20,000,000 (20ms),
 there are likely to be duplicate values in the data output, as the headset does not provide new data fast enough.
* pauseBetweenModesMs - Pause between arrow being hidden and the start of recording for next arrow (in milliseconds)
* valuesBefore - data points to record before each arrow is shown
* valuesAfter - data points to record after each arrow is shown

Change the values to your preferred ones and restart the program. Then press Start to begin recording.
Focus on the arrows appearing on the screen. Press Stop to abort the recording and discard the data.

The data will be output into a file rawData.csv. When changing the settings, 
it is recommended to remove this file or rename it to avoid recording multiple different data types into it.

## Tools used
Build tools: Gradle  
Language: Kotlin  
GUI Libraries: JavaFX, TornadoFX  
Concurrency tools: Kotlin Coroutines  
Data serialization: Jackson