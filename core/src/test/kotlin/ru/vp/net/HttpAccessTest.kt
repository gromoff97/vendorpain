package ru.vp.net

import okhttp3.OkHttpClient
import ru.vp.config.Ssh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpAccessTest {
    @Test
    fun `direct access has no tunnel`() {
        val access = HttpAccess.open(ssh = null, timeoutSeconds = 60)

        assertEquals(null, access.client.proxy)
    }

    @Test
    fun `secure access keeps strict hostname verifier`() {
        val access = HttpAccess.open(ssh = null, timeoutSeconds = 60, insecure = false)

        assertSame(OkHttpClient().hostnameVerifier, access.client.hostnameVerifier)
    }

    @Test
    fun `insecure access accepts any hostname`() {
        val access = HttpAccess.open(ssh = null, timeoutSeconds = 60, insecure = true)

        assertTrue(access.client.hostnameVerifier.verify("wrong.host", null))
    }

    @Test
    fun `ssh access opens socks tunnel and closes it`() {
        val tunnel = FakeTunnel(5544)
        var opened: Ssh? = null

        val access = HttpAccess.open(
            ssh = ssh(),
            timeoutSeconds = 60,
            tunnels = { config, _ ->
                opened = config
                tunnel
            },
        )

        assertEquals(ssh(), opened)
        assertEquals(java.net.Proxy.Type.SOCKS, access.client.proxy?.type())
        assertSame(tunnel, access.tunnel)

        access.close()

        assertTrue(tunnel.closed)
    }

    private fun ssh(): Ssh = Ssh(
        host = "jump.example",
        port = 22,
        user = "anton",
        password = "secret",
        privateKeyPath = null,
        passphrase = null,
        knownHostsPath = null,
        strictHostKeyChecking = false,
    )

    private class FakeTunnel(
        override val port: Int,
    ) : Tunnel {
        var closed = false

        override fun close() {
            closed = true
        }
    }
}
