package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerVersionTest {

    @Test
    fun `newer version returns 1`() {
        assertEquals(1, UpdateChecker.compareVersions("1.9.9", "1.9.8"))
        assertEquals(1, UpdateChecker.compareVersions("2.0.0", "1.9.9"))
        assertEquals(1, UpdateChecker.compareVersions("1.10.0", "1.9.9"))
        assertEquals(1, UpdateChecker.compareVersions("10.0.0", "9.9.9"))
    }

    @Test
    fun `older or equal version returns -1 or 0`() {
        assertEquals(-1, UpdateChecker.compareVersions("1.9.8", "1.9.9"))
        assertEquals(0, UpdateChecker.compareVersions("1.9.9", "1.9.9"))
        assertEquals(-1, UpdateChecker.compareVersions("1.9.9", "1.9.10"))
    }

    @Test
    fun `pre-release tags compare as their base version`() {
        assertEquals(0, UpdateChecker.compareVersions("1.9.9-rc1", "1.9.9"))
        assertEquals(0, UpdateChecker.compareVersions("1.9.9-beta.2", "1.9.9"))
        assertEquals(1, UpdateChecker.compareVersions("1.9.10-rc1", "1.9.9"))
    }
}
