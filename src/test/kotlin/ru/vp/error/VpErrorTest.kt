package ru.vp.error

import kotlin.test.Test
import kotlin.test.assertEquals

class VpErrorTest {
    @Test
    fun `exit codes match specification`() {
        assertEquals(0, ExitCode.SUCCESS.code)
        assertEquals(10, ExitCode.INVALID_CLI_ARGS.code)
        assertEquals(11, ExitCode.CONFIG_NOT_READABLE.code)
        assertEquals(12, ExitCode.INVALID_YAML.code)
        assertEquals(13, ExitCode.INVALID_CONFIG_SCHEMA.code)
        assertEquals(14, ExitCode.INVALID_DATE_RANGE.code)
        assertEquals(15, ExitCode.INVALID_OPTION_VALUE.code)
        assertEquals(20, ExitCode.OUTPUT_DIR_EXISTS.code)
        assertEquals(21, ExitCode.FILESYSTEM_ERROR.code)
        assertEquals(22, ExitCode.INVALID_OUTPUT_PATH.code)
        assertEquals(30, ExitCode.AUTHENTICATION_FAILED.code)
        assertEquals(31, ExitCode.PERMISSION_DENIED.code)
        assertEquals(32, ExitCode.ENDPOINT_NOT_FOUND.code)
        assertEquals(33, ExitCode.USER_NOT_FOUND.code)
        assertEquals(34, ExitCode.NON_CSV_RESPONSE.code)
        assertEquals(40, ExitCode.NETWORK_ERROR.code)
        assertEquals(41, ExitCode.SERVER_ERROR.code)
        assertEquals(50, ExitCode.ARCHIVE_EXISTS.code)
        assertEquals(51, ExitCode.ARCHIVE_ERROR.code)
        assertEquals(99, ExitCode.INTERNAL_ERROR.code)
    }

    @Test
    fun `vp exception exposes exit code and message`() {
        val error = VpException(ExitCode.INVALID_CONFIG_SCHEMA, "exports must not be empty")

        assertEquals(13, error.exitCode.code)
        assertEquals("exports must not be empty", error.message)
    }
}
