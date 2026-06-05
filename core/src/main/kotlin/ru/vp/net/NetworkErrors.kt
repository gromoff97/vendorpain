package ru.vp.net

import ru.vp.error.ExitCode
import javax.net.ssl.SSLException

object NetworkErrors {
    fun classify(error: Throwable): ExitCode =
        if (error.causes().any { it is SSLException }) ExitCode.TLS_ERROR else ExitCode.NETWORK_ERROR

    private fun Throwable.causes(): Sequence<Throwable> =
        generateSequence(this) { it.cause }
}
