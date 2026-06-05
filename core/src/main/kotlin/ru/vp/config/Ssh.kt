package ru.vp.config

data class Ssh(
    val host: String,
    val port: Int,
    val user: String,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val passphrase: String? = null,
    val knownHostsPath: String? = null,
    val strictHostKeyChecking: Boolean = false,
)
