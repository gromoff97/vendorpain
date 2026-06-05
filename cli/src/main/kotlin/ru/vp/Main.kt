package ru.vp

import ru.vp.cli.VpCli
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(VpCli().execute(args))
}
