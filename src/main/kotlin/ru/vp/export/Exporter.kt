package ru.vp.export

import ru.vp.archive.ZipArchiver
import ru.vp.awesomegraphs.AwesomeGraphsClient
import ru.vp.awesomegraphs.CsvDownloader
import ru.vp.config.OptionsConfig
import ru.vp.config.VpConfig
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.paths.OutputPlanner
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock

class Exporter(
    private val planner: OutputPlanner = OutputPlanner(),
    private val downloaderFactory: (OptionsConfig) -> CsvDownloader = { options -> AwesomeGraphsClient(options) },
    private val zipArchiver: ZipArchiver = ZipArchiver(),
    private val stdout: PrintStream = System.out,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun export(config: VpConfig): ExportResult {
        val plan = planner.plan(config)
        val downloader = downloaderFactory(config.options)

        createOutputDir(plan.outputDir)
        val debugArtifacts = if (config.options.debug) {
            DebugArtifacts.create(plan.outputDir, config, plan, clock)
        } else {
            null
        }

        plan.files.forEachIndexed { index, file ->
            stdout.println("[${index + 1}/${plan.files.size}] exporting ${file.slug} -> ${file.csvPath}")
            createDirectories(file.directory)
            val result = downloader.downloadCsv(file.slug)
            writeCsv(file.csvPath, result.bytes)
            debugArtifacts?.record(file, result, Files.size(file.csvPath))
        }

        if (plan.archivePath != null) {
            val archiveResult = zipArchiver.archive(plan.outputDir, plan.archivePath)
            debugArtifacts?.archiveCreated(archiveResult)
        }
        debugArtifacts?.finish(clock)

        return ExportResult(
            outputDir = plan.outputDir,
            archivePath = plan.archivePath,
            filesWritten = plan.files.size,
        )
    }

    private fun createOutputDir(outputDir: Path) {
        try {
            outputDir.parent?.let(Files::createDirectories)
            Files.createDirectory(outputDir)
        } catch (e: IOException) {
            throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to create outputDir: $outputDir", e)
        }
    }

    private fun createDirectories(directory: Path) {
        try {
            Files.createDirectories(directory)
        } catch (e: IOException) {
            throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to create output directory: $directory", e)
        }
    }

    private fun writeCsv(path: Path, bytes: ByteArray) {
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW)
        } catch (e: IOException) {
            throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to write CSV file: $path", e)
        }
    }
}
