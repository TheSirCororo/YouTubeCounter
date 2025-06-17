package ru.cororo.youtubecounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import ru.cororo.youtubecounter.api.GoogleAccessToken
import ru.cororo.youtubecounter.components.App
import ru.cororo.youtubecounter.components.GoogleAuthenticator
import ru.cororo.youtubecounter.components.ViewersPopupWindow
import ru.cororo.youtubecounter.credentials.getAccessTokenFromStorage
import ru.cororo.youtubecounter.credentials.putAccessTokenToStorage
import youtubecounter.composeapp.generated.resources.Res
import youtubecounter.composeapp.generated.resources.icon

fun main() = application {
    val mainWindowState = rememberWindowState(height = 225.dp, width = 400.dp)
    var showPopup by remember { mutableStateOf(false) }
    var videoId by remember { mutableStateOf("") }
    var accessToken by remember {
        mutableStateOf(getAccessTokenFromStorage()?.let {
            GoogleAccessToken(it)
        })
    }

    if (accessToken == null) {
        Window(
            onCloseRequest = ::exitApplication,
            state = mainWindowState,
            title = "Вход в аккаунт",
            icon = painterResource(Res.drawable.icon)
        ) {
            GoogleAuthenticator {
                accessToken = it
                putAccessTokenToStorage(it?.accessToken)
            }
        }
    } else {
        if (showPopup) {
            ViewersPopupWindow(
                getAccessToken = { accessToken!! },
                videoId = videoId,
                onCloseRequest = { showPopup = false },
                setAccessToken = {
                    accessToken = it
                    putAccessTokenToStorage(it?.accessToken)
                }
            )
        } else {
            Window(
                onCloseRequest = ::exitApplication,
                state = mainWindowState,
                title = "Счётчик",
                icon = painterResource(Res.drawable.icon)
            ) {
                App(
                    onShowPopup = { showPopup = true },
                    setVideoId = { videoId = it },
                    setAccessToken = {
                        accessToken = it
                        putAccessTokenToStorage(it?.accessToken)
                    }
                )
            }
        }
    }
}