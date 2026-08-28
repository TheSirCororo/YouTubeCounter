package ru.cororo.youtubecounter.credentials

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException

private const val SERVICE = "ru.cororo.youtubecounter"
private const val ACCOUNT = "access_token"

private val keyring = Keyring.create()

fun putAccessTokenToStorage(token: String?) {
    if (token != null) {
        keyring.setPassword(SERVICE, ACCOUNT, token)
    } else {
        deleteAccessTokenFromStorage()
    }
}

fun deleteAccessTokenFromStorage() {
    try {
        keyring.deletePassword(SERVICE, ACCOUNT)
    } catch (ex: PasswordAccessException) {
        if (ex.message?.isNotExistsMessage() == true) {
            return
        }

        throw ex
    }
}

fun getAccessTokenFromStorage(): String? =
    try {
        keyring.getPassword(SERVICE, ACCOUNT)
    } catch (ex: PasswordAccessException) {
        if (ex.message?.isNotExistsMessage() == true) {
            null
        } else {
            error("Нет доступа к хранилищу ключей.")
        }
    }

// The keyring backends report a missing entry as a failure, each in their own words:
// 1168 is the Windows credential store, "not in wallet" is KDE/GNOME.
private fun String.isNotExistsMessage() =
    contains("1168") || contains("not in wallet")