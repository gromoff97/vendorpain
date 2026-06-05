package ru.vp.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import ru.vp.config.Config
import ru.vp.error.ExitCode
import ru.vp.export.ExportProgress
import ru.vp.export.ExportResult
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.Callable

@Command(name = "vp")
internal class VpCommand(
    private val configLoader: (Path) -> Config,
    private val exportAction: (Config, (ExportProgress) -> Unit) -> ExportResult,
    private val stdout: PrintStream,
) : Callable<Int> {
    @Option(names = ["--conf"], required = true, description = ["Path to vendorpain YAML config"])
    lateinit var conf: Path

    override fun call(): Int {
        val config = configLoader(conf)
        val result = exportAction(config, ::progress)

        stdout.println("Export completed: ${result.files} CSV files written to ${result.dir}")
        if (result.zip != null) {
            stdout.println("Archive created: ${result.zip}")
        }

        return ExitCode.SUCCESS.code
    }

    private fun progress(progress: ExportProgress) {
        val slug = progress.slug ?: return
        stdout.println("[${progress.done + 1}/${progress.total}] exporting $slug -> ${progress.file}")
    }
}
