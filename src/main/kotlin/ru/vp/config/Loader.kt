package ru.vp.config

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class Loader(
    private val rules: Rules = Rules(),
) {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)

    fun load(path: Path): Config {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw VpException(ExitCode.CONFIG_NOT_READABLE, "config file is not readable: $path")
        }

        val tree = try {
            mapper.readTree(path.toFile())
        } catch (e: JsonProcessingException) {
            throw VpException(ExitCode.INVALID_YAML, "invalid YAML syntax in $path", e)
        } catch (e: IOException) {
            throw VpException(ExitCode.CONFIG_NOT_READABLE, "failed to read config file: $path", e)
        }

        val config = try {
            mapper.treeToValue(tree, Config::class.java)
        } catch (e: JsonParseException) {
            throw VpException(ExitCode.INVALID_YAML, "invalid YAML syntax in $path", e)
        } catch (e: JsonMappingException) {
            throw VpException(ExitCode.INVALID_CONFIG_SCHEMA, "invalid config schema in $path", e)
        } catch (e: IOException) {
            throw VpException(ExitCode.CONFIG_NOT_READABLE, "failed to read config file: $path", e)
        }

        return rules.validate(config)
    }
}
