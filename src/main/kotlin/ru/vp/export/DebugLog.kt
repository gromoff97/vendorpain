package ru.vp.export

import ru.vp.awesomegraphs.CsvResult
import ru.vp.archive.ArchiveResult
import ru.vp.config.Config
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

class DebugLog private constructor(
    private val log: Path,
    private val summary: Path,
) {
    fun record(file: OutputFilePlan, result: CsvResult, bytes: Long) {
        log.add(
            "request=${result.url} status=${result.status} contentType=${result.type ?: ""} " +
                "attempts=${result.tries} file=${file.file} bytes=$bytes\n",
        )
        summary.add(
            csv(file.path.joinToString("/"), file.slug, file.file.toString(), bytes.toString()),
        )
    }

    fun finish(clock: Clock) {
        log.add("finishedAt=${Instant.now(clock)}")
    }

    fun archiveCreated(result: ArchiveResult) {
        log.add("archiveCreated=${result.path} entries=${result.entries}")
    }

    companion object {
        fun create(dir: Path, config: Config, plan: ExportPlan, clock: Clock): DebugLog {
            val log = dir.resolve("vp-debug.log")
            val summary = dir.resolve("export-summary.csv")

            log.put(lines(config, plan, clock))
            summary.put("path,slug,file,bytes")

            return DebugLog(log, summary)
        }

        private fun lines(config: Config, plan: ExportPlan, clock: Clock): String =
            (
                listOf(
                    "startedAt=${Instant.now(clock)}",
                    "baseUrl=${config.options.baseUrl}",
                    "sinceDate=${config.options.sinceDate}",
                    "untilDate=${config.options.untilDate}",
                    "merges=${config.options.merges}",
                    "order=${config.options.order}",
                    "timeoutSeconds=${config.options.timeoutSeconds}",
                    "retries=${config.options.retries}",
                    "archive=${config.options.archive}",
                    "plannedFiles=${plan.files.size}",
                ) + plan.files.map { "planned ${it.slug} -> ${it.file}" }
                ).joinToString("\n")

        private fun Path.put(text: String) {
            try {
                Files.writeString(this, "$text\n", StandardOpenOption.CREATE_NEW)
            } catch (e: IOException) {
                throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to write debug artifact: $this", e)
            }
        }

        private fun Path.add(text: String) {
            try {
                Files.writeString(this, "$text\n", StandardOpenOption.APPEND)
            } catch (e: IOException) {
                throw VpException(ExitCode.FILESYSTEM_ERROR, "failed to append debug artifact: $this", e)
            }
        }

        private fun csv(vararg cells: String): String = cells.joinToString(",") { it.escape() }

        private fun String.escape(): String =
            if (any { it in ",\"\n\r/\\" }) "\"" + replace("\"", "\"\"") + "\"" else this
    }
}
