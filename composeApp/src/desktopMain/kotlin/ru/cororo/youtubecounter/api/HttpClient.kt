package ru.cororo.youtubecounter.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/** Shared by the backend and YouTube calls, so the app keeps a single connection pool. */
internal val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        // The YouTube responses carry far more fields than the DTOs below declare.
        json(Json { ignoreUnknownKeys = true })
    }
}
