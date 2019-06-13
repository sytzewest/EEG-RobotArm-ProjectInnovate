package com.nhlstenden.mindwave

import java.io.BufferedReader
import java.net.Socket
import kotlin.system.exitProcess

class HeadsetReader(enableRawData: Boolean = false) : AutoCloseable {
    private val socket: Socket
    private val reader: BufferedReader

    init {
        try {
            socket = Socket("localhost", 13854)
        } catch (e: Exception) {
            System.err.println("Could not connect to ThinkGear Connector")
            exitProcess(-1)
        }

        reader = socket.getInputStream().bufferedReader()

        while (!socket.isConnected) {}

        //by default raw data is hidden and data is received in binary
        //this switches format to JSON with raw enabled
        socket.getOutputStream().write(
                """
                    {"enableRawOutput": $enableRawData, "format": "Json"}
                """.trim().toByteArray(charset("ASCII")
                )
        )
        socket.getOutputStream().flush()

        //wait for config change to take effect since there's no response to confirm it
        Thread.sleep(500)
    }

    //data comes in one packet per line, all data packets in one stream
    fun read() = reader.readLine().let(HeadsetData.Companion::from)

    override fun close() {
        reader.close()
        socket.close()
    }
}