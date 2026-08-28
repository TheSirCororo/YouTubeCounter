package ru.cororo.youtubecounter.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import ru.cororo.youtubecounter.api.GoogleAccessToken
import ru.cororo.youtubecounter.api.getYouTubeStreamViewersCount
import youtubecounter.composeapp.generated.resources.Res
import youtubecounter.composeapp.generated.resources.icon
import kotlin.time.Duration.Companion.seconds

@Composable
fun ViewersPopupWindow(
    videoId: String,
    getAccessToken: () -> GoogleAccessToken,
    setAccessToken: (GoogleAccessToken?) -> Unit,
    onCloseRequest: () -> Unit
) {
    var viewersCount by remember { mutableStateOf(0) }
    var likesCount by remember { mutableStateOf(0) }

    LaunchedEffect(videoId) {
        while (true) {
            val stats = getYouTubeStreamViewersCount(
                accessToken = getAccessToken(),
                videoId = videoId,
                updateAccessToken = setAccessToken
            )
            // Keep the last known numbers on a failed poll rather than flashing zeroes.
            stats.viewers?.let { viewersCount = it }
            stats.likes?.let { likesCount = it }

            delay(3.seconds)
        }
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Счётчик",
        state = rememberWindowState(width = 200.dp, height = 100.dp),
        alwaysOnTop = true,
        resizable = false,
        icon = painterResource(Res.drawable.icon)
    ) {
        MaterialTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Viewers")
                    Text(text = "$viewersCount", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ThumbUp, contentDescription = "Likes")
                    Text(text = "$likesCount", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}