package ru.vp.error

class VpException(
    val exitCode: ExitCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
