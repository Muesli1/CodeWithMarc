package muesli1.cwm

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


const val NO_PASSWORD = "NO_PASSWORD"

@Serializable
data class Uga(val x: Int, val y: String) {

}

@Serializable
sealed class Packet

@Serializable
data class NoodlePacket(val uga: String) : Packet() {

}

@Serializable
data class DeveloperInitPacket(val projectName: String) : Packet() {

}


fun main() {
    println(Json.encodeToString(Uga(5, "bitteWat")))
}