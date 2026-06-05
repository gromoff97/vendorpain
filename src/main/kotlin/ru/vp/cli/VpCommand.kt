package ru.vp.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import ru.vp.config.VpConfig
import ru.vp.error.ExitCode
import ru.vp.export.ExportResult
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.Callable

@Command(name = "vp")
internal class VpCommand(
    private val configLoader: (Path) -> VpConfig,
    private val exportAction: (VpConfig, PrintStream) -> ExportResult,
    private val stdout: PrintStream,
) : Callable<Int> {
    @Option(names = ["--conf"], required = true, description = ["Path to VP YAML config"])
    lateinit var conf: Path

    override fun call(): Int {
        val config = configLoader(conf)
        val result = exportAction(config, stdout)

        stdout.println("Export completed: ${result.filesWritten} CSV files written to ${result.outputDir}")
        if (result.archivePath != null) {
            stdout.println("Archive created: ${result.archivePath}")
        }

        return ExitCode.SUCCESS.code
    }
}
