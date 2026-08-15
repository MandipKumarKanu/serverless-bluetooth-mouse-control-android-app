package com.example.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HidKeyMapperTest {

    @Test
    fun `lowercase letters map to scan codes with no modifier`() {
        assertEquals(0.toByte(), HidKeyMapper.map('a')?.first)
        assertEquals(0x04.toByte(), HidKeyMapper.map('a')?.second)
        assertEquals(0.toByte(), HidKeyMapper.map('z')?.first)
        assertEquals(0x1D.toByte(), HidKeyMapper.map('z')?.second)
    }

    @Test
    fun `uppercase letters add the shift modifier`() {
        assertEquals(0x02.toByte(), HidKeyMapper.map('A')?.first)
        assertEquals(0x04.toByte(), HidKeyMapper.map('A')?.second)
        assertEquals(0x02.toByte(), HidKeyMapper.map('Z')?.first)
        assertEquals(0x1D.toByte(), HidKeyMapper.map('Z')?.second)
    }

    @Test
    fun `digits and space map correctly`() {
        assertEquals(0.toByte(), HidKeyMapper.map('1')?.first)
        assertEquals(0x1E.toByte(), HidKeyMapper.map('1')?.second)
        assertEquals(0x27.toByte(), HidKeyMapper.map('0')?.second)
        assertEquals(0x2C.toByte(), HidKeyMapper.map(' ')?.second)
    }

    @Test
    fun `backtick and tilde are supported`() {
        assertEquals(0.toByte(), HidKeyMapper.map('`')?.first)
        assertEquals(0x35.toByte(), HidKeyMapper.map('`')?.second)
        assertEquals(0x02.toByte(), HidKeyMapper.map('~')?.first)
        assertEquals(0x35.toByte(), HidKeyMapper.map('~')?.second)
    }

    @Test
    fun `unsupported characters return null`() {
        assertNull(HidKeyMapper.map('€'))
        assertNull(HidKeyMapper.map('é'))
        assertNull(HidKeyMapper.map('漢'))
    }
}
