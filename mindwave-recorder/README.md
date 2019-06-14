# MindWave raw data recorder

A simple visual program that displays a configurable series of arrows and records data from the headset
using TG_Connector. The data is then labeled by time from recording start (in ms) and arrow displayed at the time of recording
and saved to a .csv file 


## Running the recorder
To run the program, first do one of the following  
* build the project with `./gradlew build`, then run the created JAR file with `java -jar build/libs/mindwave-recorder-<version>-all.jar`
* run the project with `./gradlew run`

Make sure the headset is connected and TG_Connector is running. Press the start button to start recording.

## Tools used
Build tools: Gradle  
Language: Kotlin  
GUI Libraries: JavaFX, TornadoFX  
Concurrency tools: Kotlin Coroutines  
Data serialization: Jackson