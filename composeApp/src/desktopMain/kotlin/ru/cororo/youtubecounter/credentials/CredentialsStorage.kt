package ru.cororo.youtubecounter.credentials

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException

private val keyring = Keyring.create()

fun putAccessTokenToStorage(token: String?) {
    if (token != null) {
        keyring.setPassword("ru.cororo.youtubecounter", "access_token", token)
    } else {
        deleteAccessTokenFromStorage()
    }
}

fun deleteAccessTokenFromStorage() {
    keyring.deletePassword("ru.cororo.youtubecounter", "access_token")
}

fun getAccessTokenFromStorage(): String? =
    try {
        keyring.getPassword("ru.cororo.youtubecounter", "access_token")
    } catch (ex: PasswordAccessException) {
        if (ex.message?.contains("1168") == true) {
            null
        } else {
            error("Нет доступа к хранилищу ключей.")
        }
    }