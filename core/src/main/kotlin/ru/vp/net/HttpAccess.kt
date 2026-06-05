package ru.vp.net

import okhttp3.OkHttpClient
import ru.vp.config.Ssh
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpAccess(
    val client: OkHttpClient,
    val tunnel: Tunnel? = null,
) : AutoCloseable {
    override fun close() {
        tunnel?.close()
    }

    companion object {
        fun open(
            ssh: Ssh?,
            timeoutSeconds: Int,
            insecure: Boolean = false,
            tunnels: (Ssh, Int) -> Tunnel = JschTunnel::open,
        ): HttpAccess {
            val tunnel = ssh?.let { tunnels(it, timeoutSeconds) }
            return HttpAccess(client(timeoutSeconds, tunnel, insecure), tunnel)
        }

        private fun client(timeoutSeconds: Int, tunnel: Tunnel?, insecure: Boolean): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)

            if (insecure) {
                val trustManager = trustAll()
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                builder.sslSocketFactory(context.socketFactory, trustManager)
                builder.hostnameVerifier { _, _ -> true }
            }

            if (tunnel != null) {
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", tunnel.port)))
            }

            return builder.build()
        }

        private fun trustAll(): X509TrustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
    }
}
