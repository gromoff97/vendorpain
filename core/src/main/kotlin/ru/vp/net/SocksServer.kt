package ru.vp.net

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SocksServer(
    private val session: Session,
    private val timeoutMillis: Int,
) : Closeable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    @Volatile
    private var active = true

    val port: Int = server.localPort

    init {
        executor.execute(::accept)
    }

    override fun close() {
        active = false
        runCatching { server.close() }
        executor.shutdownNow()
    }

    private fun accept() {
        while (active) {
            try {
                val socket = server.accept()
                executor.execute { socket.use(::handle) }
            } catch (e: IOException) {
                if (active) throw e
            }
        }
    }

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val target = try {
            greeting(input, output)
            request(input)
        } catch (e: IOException) {
            return
        }

        val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(target.host)
        channel.setPort(target.port)
        channel.setOrgIPAddress(socket.inetAddress.hostAddress)
        channel.setOrgPort(socket.port)

        try {
            channel.connect(timeoutMillis)
            ok(output)
            pipe(input, channel.getOutputStream())
            copy(channel.getInputStream(), output)
        } catch (e: IOException) {
            return
        } finally {
            channel.disconnect()
        }
    }

    private fun pipe(input: BufferedInputStream, output: OutputStream) {
        executor.execute {
            runCatching {
                copy(input, output)
            }
            runCatching { output.close() }
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            output.write(buffer, 0, read)
            output.flush()
        }
    }

    private fun greeting(input: BufferedInputStream, output: BufferedOutputStream) {
        require(read(input) == 5)
        repeat(read(input)) { read(input) }
        output.write(byteArrayOf(5, 0))
        output.flush()
    }

    private fun request(input: BufferedInputStream): Target {
        require(read(input) == 5)
        require(read(input) == 1)
        read(input)
        val host = when (read(input)) {
            1 -> (1..4).joinToString(".") { read(input).toString() }
            3 -> ByteArray(read(input)) { read(input).toByte() }.toString(Charsets.UTF_8)
            else -> throw IOException("unsupported SOCKS address type")
        }
        val port = read(input) * 256 + read(input)
        return Target(host, port)
    }

    private fun ok(output: BufferedOutputStream) {
        output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun read(input: BufferedInputStream): Int =
        input.read().takeIf { it >= 0 } ?: throw EOFException()

    private data class Target(
        val host: String,
        val port: Int,
    )
}
