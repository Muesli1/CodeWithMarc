package muesli1.cwm.plugins

import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable

@Serializable
class Customer(val uga: Int, val firstName: String, val secondName: String)

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        post("/upload") {
            val receive = call.receive(Customer::class)
            println("Received: $receive")
            call.respond(Customer(5, "Ada","Bda"))
        }
    }
}
