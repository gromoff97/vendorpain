package ru.vp.export

import ru.vp.archive.Zip
import ru.vp.awesomegraphs.CsvSource
import ru.vp.awesomegraphs.GraphsClient
import ru.vp.config.Config
import ru.vp.config.Options
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.paths.Layout
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock

class Exporter(
    private val layout: Layout = Layout(),
    private val clients: (Options) -> CsvSource = { options -> GraphsClient(options) },
    private val zip: Zip = Zip(),
    private val stdout: PrintStream = System.out,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun export(config: Config): ExportResult {
        val plan = layout.plan(config)
        val client = clients(config.options)

        mkdir(plan.dir)
        val debug = config.options.debug.takeIf { it }?.let { DebugLog.create(plan.dir, config, plan, clock) }

        plan.files.forEachIndexed { index, file ->
            stdout.println("[${index + 1}/${plan.files.size}] exporting ${file.slug} -> ${file.file}")
            dirs(file.dir)
            val result = client.downloadCsv(file.slug)
            write(file.file, result.bytes)
            debug?.record(file, result, Files.size(file.file))
        }

        plan.zip?.let { debug?.archiveCreated(zip.archive(plan.dir, it)) }
        debug?.finish(clock)

        return ExportResult(
            dir = plan.dir,
            zip = plan.zip,
            files = plan.files.size,
        )
    }

    private fun mkdir(dir: Path) {
        try {
            dir.parent?.let(Files::createDirectories)
            Files.createDirectory(dir)
        } catch (e: IOException) {
            throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to create outputDir: $dir", e)
        }
    }

    private fun dirs(dir: Path) {
        try {
            Files.createDirectories(dir)
        } catch (e: IOException) {
            throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to create output directory: $dir", e)
        }
    }

    private fun write(path: Path, bytes: ByteArray) {
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW)
        } catch (e: IOException) {
            throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to write CSV file: $path", e)
        }
    }
}
