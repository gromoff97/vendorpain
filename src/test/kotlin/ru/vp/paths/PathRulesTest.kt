package ru.vp.paths

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import ru.vp.error.ExitCode
import ru.vp.error.VpException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathRulesTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            " vendor",
            "vendor ",
            "vendor.",
            "ven/dor",
            "ven\\dor",
            "ven<dor",
            "ven>dor",
            "ven:dor",
            "ven\"dor",
            "ven|dor",
            "ven?dor",
            "ven*dor",
            "CON",
            "con",
            "COM1",
            "LPT9",
        ],
    )
    fun `invalid cross platform directory segment fails`(segment: String) {
        val error = assertFailsWith<VpException> {
            PathRules().check(segment)
        }

        assertEquals(ExitCode.INVALID_OUTPUT_PATH, error.exitCode)
    }

    @Test
    fun `control characters in directory segment fail`() {
        val error = assertFailsWith<VpException> {
            PathRules().check("vendor\u0000name")
        }

        assertEquals(ExitCode.INVALID_OUTPUT_PATH, error.exitCode)
    }

    @Test
    fun `unicode directory segment is accepted one to one`() {
        val segment = "ООО Ромашка"

        assertEquals(segment, PathRules().check(segment))
    }
}
