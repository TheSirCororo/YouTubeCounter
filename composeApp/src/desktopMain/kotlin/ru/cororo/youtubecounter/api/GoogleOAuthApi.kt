package ru.cororo.youtubecounter.api

import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import java.awt.Desktop
import java.net.URI

private const val GOOGLE_CLIENT_ID = "552436875611-lpn28228q2dpp6od64ukoqmi9ej18j77.apps.googleusercontent.com"
private const val YOUTUBE_API_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
private val backendServerUrl = System.getProperty("backend.server_url") ?: "https://youtubecounter.cororo.ru"
private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

suspend fun authorizeGoogleOAuth(): GoogleAccessToken {
    val jsonFactory = GsonFactory.getDefaultInstance()
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    val scopes = listOf(YOUTUBE_API_SCOPE)
    val flow = GoogleAuthorizationCodeFlow.Builder(
        httpTransport,
        jsonFactory,
        GOOGLE_CLIENT_ID,
        null,
        scopes
    )
        .setDataStoreFactory(MemoryDataStoreFactory())
        .setAccessType("offline")
        .setApprovalPrompt("force")
        .enablePKCE()
        .build()

    val receiver = LocalServerReceiver.Builder()
        .setPort(-1)
        .build()

    val redirectUri = receiver.redirectUri
    val authCodeUrl = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build()
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI.create(authCodeUrl))
    } else {
        error("Не поддерживается браузер.")
    }

    val code = receiver.waitForCode()
    val response = httpClient.post("$backendServerUrl/oauth2/token") {
        setBody(GoogleAuthCodeRequest(code, redirectUri))
        contentType(ContentType.Application.Json)
    }.body<GoogleAccessToken>()

    return response
}

suspend fun refreshToken(token: GoogleAccessToken): GoogleAccessToken {
    return httpClient.post("$backendServerUrl/oauth2/refresh") {
        setBody(token)
        contentType(ContentType.Application.Json)
    }.body<GoogleAccessToken>()
}

@Serializable
data class GoogleAuthCodeRequest(val code: String, val redirectUri: String)

@Serializable
data class GoogleAccessToken(val accessToken: String)

