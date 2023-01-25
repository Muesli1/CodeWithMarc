package muesli1.cwm

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

abstract class ClientReceiver {
    abstract fun closed(reason: String?, exceptionReason: String?, connecting: Boolean)
    abstract fun received(packet: Packet)
    abstract fun createDeveloperData(): DeveloperInitPacket
    abstract fun connectedSession()
}

//TODO: SET TO REAL SERVER
/*private const val SERVER_LOCATION = "codewithmarc.tutorium.tudalgo.org"
private const val SERVER_PORT = 80
private const val SERVER_PATH = "/connection"*/

private const val SERVER_LOCATION = "0.0.0.0"
private const val SERVER_PORT = 8080
private const val SERVER_PATH = "/connection"

class ClientApplication {

    private var connected: Boolean = false
    private var password: String? = null


    private val outgoingBlocks: List<ClientApplication.() -> Unit> = mutableListOf()
    var currentSession: DefaultClientWebSocketSession? = null

    val currentSessionLock = Object()
    val outgoingBlocksLock = Object()


    val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                allowStructuredMapKeys = true
            })
        }
    }


    fun sendUnsafe(body: Packet): Boolean {
        val session: DefaultClientWebSocketSession?
        synchronized(currentSessionLock) {
            session = currentSession
        }

        if (session == null) {
            return false;
        }
        session.launch {
            session.sendSerialized(body)
        }
        return true;
    }

    /*
    suspend inline fun <reified T, reified Q> sendHttp(commandName: String, body: T): Q {
        return client.post("http://0.0.0.0:8080/$commandName") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<Q>()
    }
    */


    fun close() {
        assert(connected)

        val session: DefaultClientWebSocketSession?
        synchronized(currentSessionLock) {
            session = currentSession
        }


        if (session != null) {
            runBlocking {
                session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "End of session"))
            }
        }

        client.close()
        connected = false
    }

    fun connect(password: String?, clientReceiver: ClientReceiver) {
        assert(!connected)

        this.password = password
        this.connected = true



        GlobalScope.launch {
            while (connected) {
                var reason: CloseReason? = null
                var exceptionReason: Exception? = null

                try {


                    val session = client.webSocket(host = SERVER_LOCATION, port = SERVER_PORT, path = SERVER_PATH) {


                        synchronized(currentSessionLock) {
                            currentSession = this
                        }

                        launch {

                            try {
                                sendSerialized(password ?: NO_PASSWORD)
                                if (password != null) {
                                    sendSerialized(clientReceiver.createDeveloperData())
                                }

                                clientReceiver.connectedSession()

                                for (frame in incoming) {
                                    val packet = deserialize<Packet>(frame)
                                    // println(packet)
                                    clientReceiver.received(packet)
                                }

                                reason = closeReason.await()
                            } catch (e: Exception) {
                                exceptionReason = e
                            }
                            //println("Was closed because of: $reason")

                            /*session.receiveDeserialized<>()

                        session.sendSerialized(Customer(5, "uga", "buga"))
                        session.close(CloseReason(40, "Wasabi"))*/
                        }.join()

                        synchronized(currentSessionLock) {
                            currentSession = null
                        }
                    }
                } catch (e: Exception) {
                    // e.printStackTrace()
                    currentSession?.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.toString()))
                    exceptionReason = e
                } finally {
                    clientReceiver.closed(
                        if (reason == null) null else reason.toString(),
                        if (exceptionReason == null) null else exceptionReason.toString(),
                        connected
                    )
                    //
                    //Thread.sleep(5000)
                    delay(5000)
                }
            }
        }
    }
}

fun createClient(): ClientApplication {
    return ClientApplication()
}


suspend fun main() {
    val app = ClientApplication()


    app.connect("okok", object : ClientReceiver() {
        override fun closed(reason: String?, exceptionReason: String?, connecting: Boolean) {
            println("Closed because $reason and $exceptionReason. Reconnect in 5 seconds...")
        }

        override fun received(packet: Packet) {
            println("Received: $packet")
        }

        override fun createDeveloperData(): DeveloperInitPacket {
            return DeveloperInitPacket("DEINE MUDDA", HashMap())
        }

        override fun connectedSession() {
            TODO("Not yet implemented")
        }
    })

    delay(2000)
    println("Try send!")
    app.sendUnsafe(DeveloperUpdatePacket("WASABI/dir/file.txt", "ok"))

    Thread.sleep(50000)
    //println(app.send<Customer, Customer>("upload", Customer(5, "Uga","Buga")))
}


public suspend inline fun <reified T> DefaultClientWebSocketSession.deserialize(frame: Frame): T {
    val converter = converter
        ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    return deserializedBase<T>(
        converter,
        call.request.headers.suitableCharset(),
        frame
    ) as T
}

public suspend inline fun <reified T> WebSocketSession.deserializedBase(
    converter: WebsocketContentConverter,
    charset: Charset,
    frame: Frame
): Any? {

    if (!converter.isApplicable(frame)) {
        throw WebsocketDeserializeException(
            "Converter doesn't support frame type ${frame.frameType.name}",
            frame = frame
        )
    }

    val typeInfo = typeInfo<T>()
    val result = converter.deserialize(
        charset = charset,
        typeInfo = typeInfo,
        content = frame
    )

    if (result is T) return result
    if (result == null) {
        if (typeInfo.kotlinType?.isMarkedNullable == true) return null
        throw WebsocketDeserializeException("Frame has null content", frame = frame)
    }

    throw WebsocketDeserializeException(
        "Can't deserialize value : expected value of type ${T::class.simpleName}," +
                " got ${result::class.simpleName}",
        frame = frame
    )
}