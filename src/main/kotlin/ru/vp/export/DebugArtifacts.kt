package ru.vp.export

import ru.vp.awesomegraphs.CsvDownloadResult
import ru.vp.archive.ArchiveResult
import ru.vp.config.VpConfig
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import ru.vp.paths.ExportPlan
import ru.vp.paths.OutputFilePlan
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant

class DebugArtifacts private constructor(
    private val logPath: Path,
    private val summaryPath: Path,
) {
    fun record(file: OutputFilePlan, result: CsvDownloadResult, bytesWritten: Long) {
        append(
            logPath,
            "request=${result.requestUrl} status=${result.statusCode} contentType=${result.contentType ?: ""} " +
                "attempts=${result.attempts} file=${file.csvPath} bytes=$bytesWritten\n",
        )
        append(
            summaryPath,
            listOf(
                file.exportPath.joinToString("/"),
                file.slug,
                file.csvPath.toString(),
                bytesWritten.toString(),
            ).joinToCsvLine() + "\n",
        )
    }

    fun finish(clock: Clock) {
        append(logPath, "finishedAt=${Instant.now(clock)}\n")
    }

    fun archiveCreated(result: ArchiveResult) {
        append(logPath, "archiveCreated=${result.archivePath} entries=${result.entriesWritten}\n")
    }

    companion object {
        fun create(outputDir: Path, config: VpConfig, plan: ExportPlan, clock: Clock): DebugArtifacts {
            val logPath = outputDir.resolve("vp-debug.log")
            val summaryPath = outputDir.resolve("export-summary.csv")

            write(
                logPath,
                buildString {
                    appendLine("startedAt=${Instant.now(clock)}")
                    appendLine("baseUrl=${config.options.baseUrl}")
                    appendLine("sinceDate=${config.options.sinceDate}")
                    appendLine("untilDate=${config.options.untilDate}")
                    appendLine("merges=${config.options.merges}")
                    appendLine("order=${config.options.order}")
                    appendLine("timeoutSeconds=${config.options.timeoutSeconds}")
                    appendLine("retries=${config.options.retries}")
                    appendLine("archive=${config.options.archive}")
                    appendLine("plannedFiles=${plan.files.size}")
                    plan.files.forEach { file ->
                        appendLine("planned ${file.slug} -> ${file.csvPath}")
                    }
                },
            )
            write(summaryPath, "path,slug,file,bytes\n")

            return DebugArtifacts(logPath, summaryPath)
        }

        private fun write(path: Path, text: String) {
            try {
                Files.writeString(path, text, StandardOpenOption.CREATE_NEW)
            } catch (e: IOException) {
                throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to write debug artifact: $path", e)
            }
        }

        private fun append(path: Path, text: String) {
            try {
                Files.writeString(path, text, StandardOpenOption.APPEND)
            } catch (e: IOException) {
                throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to append debug artifact: $path", e)
            }
        }

        private fun List<String>.joinToCsvLine(): String = joinToString(",") { it.csvEscape() }

        private fun String.csvEscape(): String {
            val needsQuotes = any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == '/' || it == '\\' }
            if (!needsQuotes) {
                return this
            }
            return "\"" + replace("\"", "\"\"") + "\""
        }
    }
}
