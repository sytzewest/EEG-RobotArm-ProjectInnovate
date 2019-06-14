package com.nhlstenden.mindwave

import com.fasterxml.jackson.core.JsonParseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Socket
import java.util.*

class HeadsetReader(scope: CoroutineScope, enableRawData: Boolean) {

    private val socket: Socket
    private val subscribers = Collections.synchronizedMap(mutableMapOf<Any, (HeadsetData) -> Unit>())

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
        //if packet can be parsed as a proper json, the configuration has been updated
        val reader = socket.getInputStream().bufferedReader()
        while (true) {
            try {
                HeadsetData.from(reader.readLine())
                break
            } catch (_: JsonParseException) {

            }
        }

        //launch a parallel task reading packets as they come in and sending them to current subscribers
        //will keep reading until the application closes
        scope.launch(Dispatchers.IO) {
            //make sure to close sockets if the task is cancelled
            socket.use {
                reader.useLines { lines ->
                    lines.map(HeadsetData.Companion::from).forEach { packet ->
                        //send data to subscribers asynchronously so they can't block the reading
                        launch {
                            subscribers.values.forEach { it(packet) }
                        }
                    }
                }
            }
        }
    }

    //adds a subscriber and returns a token that can be used to remove that subscriber later
    fun subscribe(subscriber: (HeadsetData) -> Unit) = Any().also {
        subscribers += it to subscriber
    }

    //removes a subscriber
    fun unsubscribe(token: Any) {
        subscribers -= token
    }
}