package muesli1.cwm

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


const val MAX_USER_CODE_LENGTH = 5000
const val MAX_USER_CODE_ENTRIES = 10
const val NO_PASSWORD = "NO_PASSWORD"

@Serializable
data class Uga(val x: Int, val y: String) {

}

@Serializable
sealed class Packet

@Serializable
data class DeveloperInitPacket(
    val projectName: String,
    val code: MutableMap<String, String>
) : Packet()

@Serializable
data class DeveloperUpdatePacket(
    val path: String,
    val text: String
) : Packet()

@Serializable
data class UserCodeUpdatePacket(
    val path: String,
    val code: List<String>
) : Packet()

@Serializable
data class CompleteUserCodePacket(
    val code: MutableMap<String, Map<Int, List<String>>>
) : Packet()

fun main() {
    println(Json.encodeToString(Uga(5, "bitteWat")))
}