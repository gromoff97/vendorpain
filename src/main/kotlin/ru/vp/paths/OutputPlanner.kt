package ru.vp.paths

import ru.vp.config.ExportConfig
import ru.vp.config.VpConfig
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class OutputPlanner(
    private val pathValidator: PathValidator = PathValidator(),
) {
    fun plan(config: VpConfig): ExportPlan {
        val outputDir = Paths.get(config.options.outputDir)
        if (Files.exists(outputDir)) {
            throw VpException(ExitCode.OUTPUT_DIR_EXISTS, "outputDir already exists: $outputDir")
        }

        val archivePath = if (config.options.archive) archivePathFor(outputDir) else null
        if (archivePath != null && Files.exists(archivePath)) {
            throw VpException(ExitCode.ARCHIVE_EXISTS, "archive already exists: $archivePath")
        }

        val files = config.exports.flatMap { export ->
            outputFiles(config, outputDir, export)
        }

        return ExportPlan(
            outputDir = outputDir,
            archivePath = archivePath,
            files = files,
        )
    }

    private fun outputFiles(config: VpConfig, outputDir: Path, export: ExportConfig): List<OutputFilePlan> {
        val validatedPath = export.path.map(pathValidator::validateSegment)
        val directory = validatedPath.fold(outputDir) { current, segment -> current.resolve(segment) }

        return export.slugs.map { slug ->
            val fileName = "${slug}-${config.options.sinceDate}_${config.options.untilDate}-commits.csv"
            OutputFilePlan(
                exportPath = validatedPath,
                slug = slug,
                directory = directory,
                csvPath = directory.resolve(fileName),
            )
        }
    }

    private fun archivePathFor(outputDir: Path): Path {
        val outputName = outputDir.fileName
            ?: throw VpException(ExitCode.INVALID_OUTPUT_PATH, "outputDir must have a directory name")
        val archiveName = "${outputName}.zip"
        return outputDir.parent?.resolve(archiveName) ?: Paths.get(archiveName)
    }
}
