package ru.vp.net

import ru.vp.config.Ssh
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JschTunnelTest {
    @Test
    fun `invalid known hosts path maps to ssh tunnel error`() {
        val error = assertFailsWith<VpException> {
            JschTunnel.open(
                Ssh(
                    host = "localhost",
                    port = 22,
                    user = "nobody",
                    password = "secret",
                    knownHostsPath = "/definitely/missing/known_hosts",
                    strictHostKeyChecking = true,
                ),
                timeoutSeconds = 1,
            )
        }

        assertEquals(ExitCode.SSH_TUNNEL_ERROR, error.exitCode)
    }
}
