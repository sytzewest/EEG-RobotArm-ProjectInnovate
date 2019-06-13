package com.nhlstenden.mindwave

fun main() {
    val reader = HeadsetReader(true)

    while (true) {

        when (val data = reader.read()) {
            is HeadsetData.StatusReport -> println("Awaiting connection")
            is HeadsetData.GeneralData ->
                if (data.poorSignalLevel > 50) {
                    println("Poor signal, check fitting")
                } else {
                    println(data)
                }
        }
    }
}