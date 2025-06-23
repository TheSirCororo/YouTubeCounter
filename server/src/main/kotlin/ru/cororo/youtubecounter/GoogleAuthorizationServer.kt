package ru.cororo.youtubecounter

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val refreshTokens = mutableMapOf<String, String>() // access - refresh

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Serializable
data class GoogleAuthConfig(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_secret")
    val clientSecret: String
)

fun Application.module() {
    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = GsonFactory.getDefaultInstance()

    val (googleClientId, googleClientSecret) = property<GoogleAuthConfig>("google")

    install(ContentNegotiation) {
        json()
    }

    routing {
        post("/oauth2/token") {
            val request = call.receive<GoogleAuthCodeRequest>()

            val tokenResponse = GoogleAuthorizationCodeTokenRequest(
                transport,
                jsonFactory,
                googleClientId,
                googleClientSecret,
                request.code,
                request.redirectUri
            ).execute()

            refreshTokens[tokenResponse.accessToken] = tokenResponse.refreshToken

            call.respond(GoogleAccessToken(tokenResponse.accessToken))
        }

        post("/oauth2/refresh") {
            val request = call.receive<GoogleAccessToken>()
            val refreshToken = refreshTokens[request.accessToken] ?: run {
                call.respondText("No refresh token found.", status = HttpStatusCode.Unauthorized)
                return@post
            }

            val tokenResponse = GoogleRefreshTokenRequest(
                transport,
                jsonFactory,
                refreshToken,
                googleClientId,
                googleClientSecret
            ).execute()

            refreshTokens.remove(request.accessToken)

            val newRefreshToken = tokenResponse.refreshToken ?: refreshToken
            refreshTokens[tokenResponse.accessToken] = newRefreshToken

            call.respond(GoogleAccessToken(tokenResponse.accessToken))
        }

        staticResources("/static", "static", index = "index.html")
    }
}


@Serializable
data class GoogleAuthCodeRequest(val code: String, val redirectUri: String)

@Serializable
data class GoogleAccessToken(val accessToken: String)
