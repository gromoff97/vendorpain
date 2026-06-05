package ru.vp.paths

import ru.vp.config.Config
import ru.vp.config.Group
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Layout(
    private val paths: PathRules = PathRules(),
) {
    fun plan(config: Config): ExportPlan {
        val dir = Paths.get(config.options.outputDir)
        if (Files.exists(dir)) {
            throw VpException(ExitCode.OUTPUT_DIR_EXISTS, "outputDir already exists: $dir")
        }

        val zip = if (config.options.archive) zipFor(dir) else null
        if (zip != null && Files.exists(zip)) {
            throw VpException(ExitCode.ARCHIVE_EXISTS, "archive already exists: $zip")
        }

        val files = config.exports.flatMap { files(config, dir, it) }

        return ExportPlan(
            dir = dir,
            zip = zip,
            files = files,
        )
    }

    private fun files(config: Config, root: Path, export: Group): List<OutputFilePlan> {
        val path = export.path.map(paths::check)
        val dir = path.fold(root, Path::resolve)

        return export.slugs.map { slug ->
            val name = "${slug}-${config.options.sinceDate}_${config.options.untilDate}-commits.csv"
            OutputFilePlan(
                path = path,
                slug = slug,
                dir = dir,
                file = dir.resolve(name),
            )
        }
    }

    private fun zipFor(dir: Path): Path {
        val name = dir.fileName
            ?: throw VpException(ExitCode.INVALID_OUTPUT_PATH, "outputDir must have a directory name")
        return dir.parent?.resolve("$name.zip") ?: Paths.get("$name.zip")
    }
}
