@file:OptIn(ExperimentalEncodingApi::class)

package ru.cororo.youtubecounter.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import ru.cororo.youtubecounter.api.GoogleAccessToken
import ru.cororo.youtubecounter.api.authorizeGoogleOAuth
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
@Preview
fun GoogleAuthenticator(setToken: (GoogleAccessToken?) -> Unit) {
    var error by remember { mutableStateOf(false) }
    var pendingLogin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error) {
            Text("Произошла ошибка при входе в Google-аккаунт! Перезапустите приложение.")
        } else {
            Text("Войдите в Google-аккаунт.")
            if (!pendingLogin) {
                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            pendingLogin = true
                            val token = authorizeGoogleOAuth()

                            pendingLogin = false
                            setToken(token)
                        } catch (e: Exception) {
                            error = true
                            e.printStackTrace()
                            setToken(null)
                        }
                    }
                }) {
                    Text("Войти в Google")
                }
            } else {
                Text("Войдите в аккаунт Google в браузере.")
            }
        }
    }
}

