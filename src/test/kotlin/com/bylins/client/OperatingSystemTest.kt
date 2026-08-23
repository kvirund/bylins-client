package com.bylins.client

import kotlin.test.Test
import kotlin.test.assertEquals

class OperatingSystemTest {
    @Test
    fun recognizesSupportedOperatingSystems() {
        assertEquals(OperatingSystem.MacOS, OperatingSystem.from("Mac OS X"))
        assertEquals(OperatingSystem.Windows, OperatingSystem.from("Windows 11"))
        assertEquals(OperatingSystem.Linux, OperatingSystem.from("Linux"))
    }

    @Test
    fun treatsUnknownOrMissingNamesAsOther() {
        assertEquals(OperatingSystem.Other, OperatingSystem.from("FreeBSD"))
        assertEquals(OperatingSystem.Other, OperatingSystem.from(null))
    }
}