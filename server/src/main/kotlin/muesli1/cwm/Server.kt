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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import muesli1.cwm.plugins.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate

const val REAL_PASSWORD: String = "okok"

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}


class ClientConnection(
    val session: DefaultWebSocketServerSession,
    val connectionId: Int = nextConnectionId.getAndIncrement()
) {

    var isDeveloper: Boolean = false

    suspend fun handshake(): Boolean {
        val receiveDeserialized = session.receiveDeserialized<String>()

        if (receiveDeserialized == NO_PASSWORD) {
            // User!

            val copy = synchronized(developerCodeLock) {
                developerCode.toMap()
            }
            copy.map { DeveloperUpdatePacket(it.key, it.value) }.forEach {
                send(it)
            }

            return true
        }

        if (receiveDeserialized == REAL_PASSWORD) {
            // DEVELOPER!

            val initPacket = session.receiveDeserialized<DeveloperInitPacket>()
            processDeveloperInit(initPacket)
            isDeveloper = true
            send(createCompleteUserCodePacket())


            return true;
        }


        session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid."))
        return false
    }

    fun kick() {
        runBlocking {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Kicked."))
        }
    }


    suspend fun send(packet: Packet) {
        session.sendSerialized<Packet>(packet)
    }

    init {

    }
}

val connections: MutableList<ClientConnection> = mutableListOf()
val packagesToProcess: MutableList<Pair<ClientConnection, Packet>> = mutableListOf()
val userCode: MutableMap<String, MutableMap<Int, List<String>>> = mutableMapOf()
val developerCode: MutableMap<String, String> = mutableMapOf()

val connectionsLock = Object()
val packagesLock = Object()
val userCodeLock = Object()
val developerCodeLock = Object()

val nextConnectionId: AtomicInteger = AtomicInteger(0)


fun processDeveloperInit(initPacket: DeveloperInitPacket) {
    println("Init Server ${initPacket.projectName} ${initPacket.code}")
}

suspend fun sendTo(predicate: (ClientConnection) -> Boolean, packet: Packet) {
    val toList = synchronized(connectionsLock) {
        connections.filter(predicate).toList()
    }
    toList.forEach {
        it.send(packet)
    }
}

fun printConnectionInfo(info: String) {
    val developerCount = connections.count { it.isDeveloper }
    val userCount = connections.size - developerCount

    println("$info Live connections: $userCount users and $developerCount developers")
}

fun openedConnection(connection: ClientConnection) {
    synchronized(connectionsLock) {
        connections.add(connection)
        printConnectionInfo("Opened connection.")
    }

}

fun closedConnection(connection: ClientConnection) {
    synchronized(connectionsLock) {
        connections.remove(connection)
        printConnectionInfo("Closed connection.")
    }
}

fun createCompleteUserCodePacket(): CompleteUserCodePacket {
    val userCodeCopy: MutableMap<String, Map<Int, List<String>>> = mutableMapOf()

    synchronized(userCodeLock) {
        userCode.forEach {
            userCodeCopy[it.key] = it.value.toMap()
        }
    }

    return CompleteUserCodePacket(userCodeCopy)
}

suspend fun processPacket(connection: ClientConnection, packet: Packet) {
    try {
        when (packet) {
            is DeveloperUpdatePacket -> {
                if (!connection.isDeveloper) {
                    connection.kick()
                    return
                }

                synchronized(developerCodeLock) {
                    developerCode[packet.path] = packet.text
                }
                sendTo({ !it.isDeveloper }, packet)
            }

            is UserCodeUpdatePacket -> {
                if (connection.isDeveloper) {
                    connection.kick()
                    return
                }

                val tooLong: Boolean = packet.code.size > MAX_USER_CODE_ENTRIES || packet.code.stream()
                    .anyMatch { s: String -> s.length > MAX_USER_CODE_LENGTH }

                if (tooLong) {
                    connection.kick()
                    return
                }
                synchronized(userCodeLock) {
                    val map = userCode.computeIfAbsent(packet.path) { mutableMapOf() }
                    map[connection.connectionId] = packet.code
                }

                sendTo({ it.isDeveloper }, createCompleteUserCodePacket())
            }

            else -> {
                connection.kick()
            }
        }
    } catch (e: Exception) {
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
        while (true) {
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

            try {
                if (handshake) {
                    openedConnection(connection)
                }

                launch {
                    for (frame in incoming) {
                        val packet = deserialized<Packet>(frame)

                        synchronized(packagesLock) {
                            packagesToProcess.add(Pair(connection, packet))
                        }
                    }
                }.join()
            } finally {
                closedConnection(connection)
            }
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
