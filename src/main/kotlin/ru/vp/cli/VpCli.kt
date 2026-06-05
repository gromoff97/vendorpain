package ru.vp.cli

import picocli.CommandLine
import ru.vp.config.ConfigLoader
import ru.vp.config.VpConfig
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.export.ExportResult
import ru.vp.export.Exporter
import java.io.PrintStream
import java.io.PrintWriter
import java.nio.file.Path

class VpCli(
    private val configLoader: (Path) -> VpConfig = ConfigLoader()::load,
    private val exportAction: (VpConfig, PrintStream) -> ExportResult = { config, stdout ->
        Exporter(stdout = stdout).export(config)
    },
) {
    fun execute(
        args: Array<String>,
        stdout: PrintStream = System.out,
        stderr: PrintStream = System.err,
    ): Int {
        val command = VpCommand(configLoader, exportAction, stdout)
        return CommandLine(command)
            .setOut(PrintWriter(stdout, true))
            .setErr(PrintWriter(stderr, true))
            .setParameterExceptionHandler { exception, _ ->
                stderr.println("Error ${ExitCode.INVALID_CLI_ARGS.code}: ${exception.message}")
                ExitCode.INVALID_CLI_ARGS.code
            }
            .setExecutionExceptionHandler { exception, _, _ ->
                handleExecutionException(exception, stderr)
            }
            .execute(*args)
    }

    private fun handleExecutionException(exception: Exception, stderr: PrintStream): Int {
        val domainError = exception as? VpException
        if (domainError != null) {
            stderr.println("Error ${domainError.exitCode.code}: ${domainError.message}")
            return domainError.exitCode.code
        }

        stderr.println("Error ${ExitCode.INTERNAL_ERROR.code}: unexpected internal error")
        return ExitCode.INTERNAL_ERROR.code
    }
}
