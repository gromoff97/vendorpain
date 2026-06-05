package ru.vp.net

interface Tunnel : AutoCloseable {
    val port: Int
}
