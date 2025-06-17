package ru.cororo.youtubecounter.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import ru.cororo.youtubecounter.api.GoogleAccessToken
import ru.cororo.youtubecounter.api.getYouTubeStreamViewersCount
import youtubecounter.composeapp.generated.resources.Res
import youtubecounter.composeapp.generated.resources.icon

@Composable
fun ViewersPopupWindow(
    videoId: String,
    getAccessToken: () -> GoogleAccessToken,
    setAccessToken: (GoogleAccessToken) -> Unit,
    onCloseRequest: () -> Unit
) {
    var viewersCount by remember { mutableStateOf(0) }
    var likesCount by remember { mutableStateOf(0) }

    LaunchedEffect(videoId) {
        while (true) {
            val (viewersCounter, likesCounter) = getYouTubeStreamViewersCount(accessToken = getAccessToken(), videoId = videoId, updateAccessToken = setAccessToken)
            if (viewersCounter != null) {
                viewersCount = viewersCounter
            }

            if (likesCounter != null) {
                likesCount = likesCounter
            }

            delay(3000)
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