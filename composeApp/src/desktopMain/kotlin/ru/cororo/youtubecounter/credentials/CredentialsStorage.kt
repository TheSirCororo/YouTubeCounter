package ru.cororo.youtubecounter.credentials

import com.github.javakeyring.Keyring
import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.PasswordAccessException

private val keyring = Keyring.create(KeyringStorageType.KWALLET)

fun putAccessTokenToStorage(token: String?) {
    if (token != null) {
        keyring.setPassword("ru.cororo.youtubecounter", "access_token", token)
    } else {
        deleteAccessTokenFromStorage()
    }
}

fun deleteAccessTokenFromStorage() {
    try {
        keyring.deletePassword("ru.cororo.youtubecounter", "access_token")
    } catch (ex: PasswordAccessException) {
        if (ex.message?.isNotExistsMessage() == true) {
            return
        }

        throw ex
    }
}

fun getAccessTokenFromStorage(): String? =
    try {
        keyring.getPassword("ru.cororo.youtubecounter", "access_token")
    } catch (ex: PasswordAccessException) {
        if (ex.message?.isNotExistsMessage() == true) {
            null
        } else {
            error("Нет доступа к хранилищу ключей.")
        }
    }

private fun String.isNotExistsMessage() =
    contains("1168") || contains("not in wallet")