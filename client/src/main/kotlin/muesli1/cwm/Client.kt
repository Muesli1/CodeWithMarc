package muesli1.cwm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

abstract class ClientReceiver {
    abstract fun closed(reason: String?, exceptionReason: String?)
    abstract fun received(packet: Packet)
    abstract fun createDeveloperData(): DeveloperInitPacket
}

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
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }


    fun sendUnsafe(body: Packet): Boolean {
        val session: DefaultClientWebSocketSession?
        synchronized(currentSessionLock) {
            session = currentSession
        }

        if(session == null) {
            return false;
        }
        session.launch {
            session.sendSerialized(body)
        }
        return true;
    }

    suspend inline fun <reified T, reified Q> sendHttp(commandName: String, body: T): Q {
        return client.post("http://0.0.0.0:8080/$commandName") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<Q>()
    }

    fun close() {
        client.close()
    }

    fun connect(password: String?, clientReceiver: ClientReceiver) {
        assert(!connected)

        this.password = password
        this.connected = true


        var oldThread: Thread = Thread {
            runBlocking {
                while(true) {
                    var reason: CloseReason? = null
                    var exceptionReason: Exception? = null

                    try {
                        println("EHH??")

                        val session = client.webSocket(host = "0.0.0.0", port = 8080, path = "/connection") {

                            println("OK!")

                            synchronized(currentSessionLock) {
                                currentSession = this
                            }

                            launch {
                                println("New session!")

                                try {
                                    sendSerialized(password ?: NO_PASSWORD)
                                    if(password != null) {
                                        sendSerialized(clientReceiver.createDeveloperData())
                                    }

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
                        println("CATCH!")
                        e.printStackTrace()
                        exceptionReason = e
                    }
                    finally {
                        println("CLOSED")
                        println("WHAT $reason and $exceptionReason")
                        clientReceiver.closed(if (reason == null) null else reason.toString(), if (exceptionReason == null) null else exceptionReason.toString())
                        //
                        Thread.sleep(5000)
                    }
                }
            }
        }
        oldThread.start()
    }
}

fun createClient(): ClientApplication {
    return ClientApplication()
}

@Serializable
data class Customer(val uga: Int, val firstName: String, val secondName: String)


suspend fun main() {
    val app = ClientApplication()


    app.connect("okok", object: ClientReceiver() {
        override fun closed(reason: String?, exceptionReason: String?) {
            println("Closed because $reason and $exceptionReason. Reconnect in 5 seconds...")
        }

        override fun received(packet: Packet) {
            println("Received: $packet")
        }

        override fun createDeveloperData(): DeveloperInitPacket {
            return DeveloperInitPacket("YAY")
        }
    })

    delay(2000)
    println("Try send!")
    app.sendUnsafe(NoodlePacket("WASABI"))

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