package com.nhlstenden.mindwave

import java.net.Socket
import java.util.*
import kotlin.concurrent.thread

//each packet will have a unique ID attached to it
data class DataPacketWithId<T : HeadsetData>(val packet: T, val id: Long)

fun <T : HeadsetData> T.withId(id: Long) = DataPacketWithId(this, id)

class HeadsetReader(enableRawData: Boolean) : AutoCloseable {
    private val socket: Socket
    private val latestPackets = Collections.synchronizedMap(mutableMapOf<String, DataPacketWithId<*>>())

    //packet ID counter
    private var nextId = 0L

    init {
        try {
            socket = Socket("localhost", 13854)
        } catch (e: Exception) {
            error("Could not connect to ThinkGear Connector")
        }

        //wait for connection
        while (!socket.isConnected) {
        }

        //by default raw data is hidden and data is received in binary
        //this switches format to JSON with raw enabled
        socket.getOutputStream().write(
            """
                {"enableRawOutput": $enableRawData, "format": "Json"}
            """.trim().toByteArray(
                charset("ASCII")
            )
        )
        socket.getOutputStream().flush()

        //wait for config change to take effect since there's no response to confirm it
        //this will wait for { to appear in the input and then discard the line it appears on
        //as it is likely malformed from the switch
        val reader = socket.getInputStream().reader()
        while (reader.read().toChar() != '{') {
        }
        while (reader.read().toChar() != '\r') {
        }

        //launch a parallel thread reading packets as they come in and sending them to current subscribers
        //will keep reading until the application or reader instance is closed
        thread {
            socket.use {
                reader.forEachLine {
                    val data = HeadsetData.from(it)

                    latestPackets[data::class.java.simpleName] = data.withId(nextId++)
                }
            }
        }
    }

    //returns latest packet of the specified type
    @Suppress("UNCHECKED_CAST")
    fun <T : HeadsetData> getLatestPacketOfType(type: String): DataPacketWithId<T>? {
        return latestPackets[type] as DataPacketWithId<T>?
    }

    override fun close() {
        socket.close()
    }
}

//returns latest packet of the type as specified by the type parameter
inline fun <reified T : HeadsetData> HeadsetReader.getLatestPacket() =
    getLatestPacketOfType<T>(T::class.java.simpleName)