package ru.vp.config

import okhttp3.Credentials

data class Auth(
    val method: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
) {
    fun authorizationHeader(): String =
        when (method) {
            BASIC -> Credentials.basic(username.orEmpty(), password.orEmpty(), Charsets.UTF_8)
            TOKEN -> "Bearer ${token.orEmpty()}"
            else -> ""
        }

    companion object {
        const val BASIC = "basic"
        const val TOKEN = "token"
    }
}
