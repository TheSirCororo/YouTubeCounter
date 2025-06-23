package ru.cororo.youtubecounter.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder

private const val YOUTUBE_ENDPOINT_URL = "https://www.googleapis.com/youtube/v3"
private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
}

@Serializable
data class YouTubeVideoResponse(
    val items: List<VideoItem>
)

@Serializable
data class VideoStatistics(
    val likeCount: String? = null
)

@Serializable
data class VideoItem(
    val liveStreamingDetails: LiveStreamingDetails? = null,
    val statistics: VideoStatistics? = null
)

@Serializable
data class LiveStreamingDetails(
    val concurrentViewers: String? = null
)

suspend fun getYouTubeStreamViewersCount(
    accessToken: GoogleAccessToken,
    videoId: String,
    updateAccessToken: (GoogleAccessToken?) -> Unit
): Pair<Int?, Int?> {
    val response: HttpResponse = client.get("$YOUTUBE_ENDPOINT_URL/videos") {
        header("Authorization", "Bearer ${accessToken.accessToken}")
        parameter("part", "liveStreamingDetails,statistics")
        parameter("id", videoId)
    }

    if (!response.status.isSuccess()) {
        println("Got error status. Trying to refresh.")
        val newToken = try {
            refreshToken(accessToken)
        } catch (ex: Exception) {
            ex.printStackTrace()
            updateAccessToken(null)
            return null to null
        }

        updateAccessToken(newToken)
        return getYouTubeStreamViewersCount(newToken, videoId, updateAccessToken)
    }

    val body = response.body<YouTubeVideoResponse>()

    val viewers = body.items.firstOrNull()?.liveStreamingDetails?.concurrentViewers
    val likes = body.items.firstOrNull()?.statistics?.likeCount
    return viewers?.toIntOrNull() to likes?.toIntOrNull()
}

fun extractVideoId(url: String): String? {
    return try {
        val uri = URI(url)
        val host = uri.host
        val path = uri.path
        val query = uri.query

        when {
            host.contains("youtu.be") -> path.removePrefix("/")
            host.contains("youtube.com") -> {
                if (path.startsWith("/watch") && query != null) {
                    query.split("&")
                        .firstOrNull { it.startsWith("v=") }
                        ?.substringAfter("v=")
                        ?.let { URLDecoder.decode(it, "UTF-8") }
                } else if (path.startsWith("/live/")) {
                    path.substringAfterLast("/")
                } else null
            }

            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
