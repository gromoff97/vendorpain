package ru.vp

import kotlin.test.Test
import kotlin.test.assertEquals

class SanityTest {
    @Test
    fun `application name is vp`() {
        assertEquals("vp", AppInfo.name)
    }
}
