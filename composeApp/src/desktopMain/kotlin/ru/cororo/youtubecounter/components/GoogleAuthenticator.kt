package ru.cororo.youtubecounter.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.cororo.youtubecounter.api.GoogleAccessToken
import ru.cororo.youtubecounter.api.authorizeGoogleOAuth

@Composable
fun GoogleAuthenticator(setToken: (GoogleAccessToken?) -> Unit) {
    var error by remember { mutableStateOf(false) }
    var pendingLogin by remember { mutableStateOf(false) }
    // Tied to this window's composition instead of an unmanaged scope that outlives it.
    val scope = rememberCoroutineScope()

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
                    // Set before launching, so the button cannot be clicked twice.
                    pendingLogin = true
                    // authorizeGoogleOAuth blocks on the local callback server, so it must
                    // not run on the UI dispatcher.
                    scope.launch(Dispatchers.IO) {
                        try {
                            val token = authorizeGoogleOAuth()
                            setToken(token)
                        } catch (e: Exception) {
                            error = true
                            e.printStackTrace()
                            setToken(null)
                        } finally {
                            pendingLogin = false
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

