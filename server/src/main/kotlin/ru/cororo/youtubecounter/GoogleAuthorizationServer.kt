package ru.cororo.youtubecounter

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.cororo.youtubecounter.storage.RefreshTokenStore

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

@Serializable
data class StorageConfig(
    @SerialName("database_path")
    val databasePath: String
)

@Serializable
data class GoogleAuthCodeRequest(val code: String, val redirectUri: String)

@Serializable
data class GoogleAccessToken(val accessToken: String)

fun Application.module() {
    val transport = NetHttpTransport()
    val jsonFactory = GsonFactory.getDefaultInstance()

    val (googleClientId, googleClientSecret) = property<GoogleAuthConfig>("google")
    val (databasePath) = property<StorageConfig>("storage")

    // Refresh tokens never reach the client; they live here and now outlive a restart.
    val refreshTokens = RefreshTokenStore(databasePath)
    refreshTokens.pruneStale()
    monitor.subscribe(ApplicationStopped) { refreshTokens.close() }

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respondText("Error! ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
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

            withContext(Dispatchers.IO) {
                refreshTokens.put(tokenResponse.accessToken, tokenResponse.refreshToken)
            }

            call.respond(GoogleAccessToken(tokenResponse.accessToken))
        }

        post("/oauth2/refresh") {
            val request = call.receive<GoogleAccessToken>()
            val refreshToken = withContext(Dispatchers.IO) { refreshTokens.find(request.accessToken) } ?: run {
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

            // Google only returns a refresh token on the first exchange; keep the old one otherwise.
            val newRefreshToken = tokenResponse.refreshToken ?: refreshToken
            withContext(Dispatchers.IO) {
                refreshTokens.rekey(request.accessToken, tokenResponse.accessToken, newRefreshToken)
            }

            call.respond(GoogleAccessToken(tokenResponse.accessToken))
        }

        staticResources("/static", "static", index = "index.html")
    }
}
