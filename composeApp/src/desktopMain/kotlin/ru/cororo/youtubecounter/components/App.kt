package ru.cororo.youtubecounter.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import ru.cororo.youtubecounter.api.extractVideoId

@Composable
@Preview
fun App(onShowPopup: () -> Unit, setVideoId: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Введите ссылку на стрим:")
        TextField(url, onValueChange = { url = it })

        Button(onClick = {
            val extracted = extractVideoId(url)
            if (extracted == null) {
                error = true
            } else {
                error = false
                setVideoId(extracted)
                onShowPopup()
            }
        }) {
            Text("Показать счётчик")
        }

        if (error) {
            Text("Ошибка: не удалось извлечь videoId", color = MaterialTheme.colorScheme.error)
        }
    }
}