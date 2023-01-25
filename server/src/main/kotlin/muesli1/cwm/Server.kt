package muesli1.cwm

import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import io.ktor.websocket.serialization.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import muesli1.cwm.plugins.*

const val REAL_PASSWORD: String = "okok"

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}


@Serializable
data class Customer(val uga: Int, val firstName: String, val secondName: String)

class ClientConnection(val session: DefaultWebSocketServerSession) {

    var isDeveloper: Boolean = false

    suspend fun handshake(): Boolean {
        val receiveDeserialized = session.receiveDeserialized<String>()

        if(receiveDeserialized == NO_PASSWORD) {
            // Client!
            return true
        }

        if(receiveDeserialized == REAL_PASSWORD) {
            // DEVELOPER!

            val initPacket = session.receiveDeserialized<DeveloperInitPacket>()
            processDeveloperInit(initPacket)
            isDeveloper = true

            return true;
        }


        session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid."))
        return false
    }



    suspend fun send(packet: Packet) {
        session.sendSerialized<Packet>(packet)
    }

    init {

    }
}

val connections: MutableList<ClientConnection> = mutableListOf()
val packagesToProcess: MutableList<Pair<ClientConnection, Packet>> = mutableListOf()

val connectionsLock = Object()
val packagesLock = Object()


fun processDeveloperInit(initPacket: DeveloperInitPacket) {
    println("Init Server ${initPacket.projectName}")
}

suspend fun processPacket(connection: ClientConnection, packet: Packet) {
    try {
        connection.send(NoodlePacket("Confirm!"))
    }
    catch (e: Exception) {
        // Do not crash!
        e.printStackTrace()
    }
}


fun Application.module() {
    configureSecurity()
    configureRouting()
    configureSockets()

    install(ContentNegotiation) {
        json()
    }

    GlobalScope.launch {
        while(true) {
            val queue: List<Pair<ClientConnection, Packet>>
            synchronized(packagesLock) {
                queue = ArrayList(packagesToProcess)
                packagesToProcess.clear()
            }

            queue.forEach { processPacket(it.first, it.second) }

            delay(10)
        }
    }

    routing {
        webSocket("/connection") {
            // send("You are connected!")
            val connection = ClientConnection(this)
            val handshake = connection.handshake()

            if(handshake) {
                synchronized(connectionsLock) {
                    connections.add(connection)
                }
            }

            launch {
                for(frame in incoming) {
                    val packet = deserialized<Packet>(frame)

                    synchronized(packagesLock) {
                        packagesToProcess.add(Pair(connection, packet))
                    }
                }
            }.join()

        }
    }
}


public suspend inline fun <reified T> WebSocketServerSession.deserialized(frame: Frame): T {
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
