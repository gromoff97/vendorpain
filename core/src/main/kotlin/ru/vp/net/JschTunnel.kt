package ru.vp.net

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import ru.vp.config.Ssh
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.io.IOException

class JschTunnel private constructor(
    private val session: Session,
    private val socks: SocksServer,
) : Tunnel {
    override val port: Int = socks.port

    override fun close() {
        socks.close()
        session.disconnect()
    }

    companion object {
        fun open(ssh: Ssh, timeoutSeconds: Int): Tunnel =
            try {
                val jsch = JSch()
                ssh.knownHostsPath?.takeIf(String::isNotBlank)?.let(jsch::setKnownHosts)
                ssh.privateKeyPath?.takeIf(String::isNotBlank)?.let { key ->
                    if (ssh.passphrase.isNullOrBlank()) {
                        jsch.addIdentity(key)
                    } else {
                        jsch.addIdentity(key, ssh.passphrase)
                    }
                }

                val session = jsch.getSession(ssh.user, ssh.host, ssh.port)
                ssh.password?.takeIf(String::isNotBlank)?.let(session::setPassword)
                session.setConfig("StrictHostKeyChecking", if (ssh.strictHostKeyChecking) "yes" else "no")
                session.connect(timeoutSeconds * 1_000)

                JschTunnel(session, SocksServer(session, timeoutSeconds * 1_000))
            } catch (e: JSchException) {
                throw VpException(ExitCode.SSH_TUNNEL_ERROR, "failed to open SSH tunnel to ${ssh.user}@${ssh.host}:${ssh.port}", e)
            } catch (e: IOException) {
                throw VpException(ExitCode.SSH_TUNNEL_ERROR, "failed to open SSH tunnel to ${ssh.user}@${ssh.host}:${ssh.port}", e)
            }
    }
}
