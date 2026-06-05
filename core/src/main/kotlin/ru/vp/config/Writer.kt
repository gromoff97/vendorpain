package ru.vp.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class Writer {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

    fun write(path: Path, config: Config) {
        try {
            path.parent?.let(Files::createDirectories)
            mapper.writeValue(path.toFile(), config)
        } catch (e: IOException) {
            throw VpException(ExitCode.CONFIG_WRITE_ERROR, "failed to write config file: $path", e)
        }
    }
}
