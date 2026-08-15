package com.example.bluetooth

/**
 * Single source of truth for ASCII character -> USB HID key press mapping.
 *
 * Returns a Pair of (modifierBits, HID scan code) or null for unsupported
 * characters. Modifier bits follow the keyboard report layout: 0x01 = Ctrl,
 * 0x02 = Shift, 0x04 = Alt, 0x08 = GUI (Win/Cmd).
 *
 * Previously this mapping was duplicated in [com.example.viewmodel.AirMouseViewModel.sendText]
 * and the Keyboard screen's transmitCharacter(), and it silently dropped
 * characters like backtick/tilde. Both call sites now use this mapper.
 */
object HidKeyMapper {

    /**
     * Map a single character to (modifierBits, scanCode).
     * Returns null when the character has no HID representation.
     */
    fun map(char: Char): Pair<Byte, Byte>? {
        return when (char) {
            in 'a'..'z' -> 0.toByte() to (0x04 + (char - 'a')).toByte()
            in 'A'..'Z' -> 0x02.toByte() to (0x04 + (char - 'A')).toByte() // Shift
            in '1'..'9' -> 0.toByte() to (0x1E + (char - '1')).toByte()
            '0' -> 0.toByte() to 0x27.toByte()
            ' ' -> 0.toByte() to 0x2C.toByte()
            '\n' -> 0.toByte() to 0x28.toByte()
            '\t' -> 0.toByte() to 0x2B.toByte()
            '`' -> 0.toByte() to 0x35.toByte()
            '~' -> 0x02.toByte() to 0x35.toByte()
            '!' -> 0x02.toByte() to 0x1E.toByte()
            '@' -> 0x02.toByte() to 0x1F.toByte()
            '#' -> 0x02.toByte() to 0x20.toByte()
            '$' -> 0x02.toByte() to 0x21.toByte()
            '%' -> 0x02.toByte() to 0x22.toByte()
            '^' -> 0x02.toByte() to 0x23.toByte()
            '&' -> 0x02.toByte() to 0x24.toByte()
            '*' -> 0x02.toByte() to 0x25.toByte()
            '(' -> 0x02.toByte() to 0x26.toByte()
            ')' -> 0x02.toByte() to 0x27.toByte()
            '-' -> 0.toByte() to 0x2D.toByte()
            '_' -> 0x02.toByte() to 0x2D.toByte()
            '=' -> 0.toByte() to 0x2E.toByte()
            '+' -> 0x02.toByte() to 0x2E.toByte()
            '[' -> 0.toByte() to 0x2F.toByte()
            '{' -> 0x02.toByte() to 0x2F.toByte()
            ']' -> 0.toByte() to 0x30.toByte()
            '}' -> 0x02.toByte() to 0x30.toByte()
            '\\' -> 0.toByte() to 0x31.toByte()
            '|' -> 0x02.toByte() to 0x31.toByte()
            ';' -> 0.toByte() to 0x33.toByte()
            ':' -> 0x02.toByte() to 0x33.toByte()
            '\'' -> 0.toByte() to 0x34.toByte()
            '"' -> 0x02.toByte() to 0x34.toByte()
            ',' -> 0.toByte() to 0x36.toByte()
            '<' -> 0x02.toByte() to 0x36.toByte()
            '.' -> 0.toByte() to 0x37.toByte()
            '>' -> 0x02.toByte() to 0x37.toByte()
            '/' -> 0.toByte() to 0x38.toByte()
            '?' -> 0x02.toByte() to 0x38.toByte()
            else -> null
        }
    }
}
