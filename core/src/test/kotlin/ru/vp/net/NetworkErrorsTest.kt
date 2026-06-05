package ru.vp.net

import ru.vp.error.ExitCode
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkErrorsTest {
    @Test
    fun `classifies TLS failures separately from generic network errors`() {
        assertEquals(ExitCode.TLS_ERROR, NetworkErrors.classify(IOException(SSLHandshakeException("bad certificate"))))
        assertEquals(ExitCode.NETWORK_ERROR, NetworkErrors.classify(IOException("connection refused")))
    }
}
