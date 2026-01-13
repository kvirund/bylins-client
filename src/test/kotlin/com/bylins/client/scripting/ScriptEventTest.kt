package com.bylins.client.scripting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptEventTest {

    @Test
    fun `ScriptEvent contains ON_MSDP_ENABLED event`() {
        val event = ScriptEvent.ON_MSDP_ENABLED
        assertEquals("ON_MSDP_ENABLED", event.name)
    }

    @Test
    fun `ScriptEvent contains ON_MSDP event`() {
        val event = ScriptEvent.ON_MSDP
        assertEquals("ON_MSDP", event.name)
    }

    @Test
    fun `ScriptEvent contains ON_CONNECT event`() {
        val event = ScriptEvent.ON_CONNECT
        assertEquals("ON_CONNECT", event.name)
    }

    @Test
    fun `ScriptEvent contains ON_DISCONNECT event`() {
        val event = ScriptEvent.ON_DISCONNECT
        assertEquals("ON_DISCONNECT", event.name)
    }

    @Test
    fun `all expected events are defined`() {
        val expectedEvents = listOf(
            "ON_LOAD",
            "ON_UNLOAD",
            "ON_COMMAND",
            "ON_LINE",
            "ON_CONNECT",
            "ON_DISCONNECT",
            "ON_MSDP",
            "ON_MSDP_ENABLED",
            "ON_GMCP",
            "ON_TRIGGER",
            "ON_ALIAS",
            "ON_ROOM_ENTER"
        )

        val actualEvents = ScriptEvent.entries.map { it.name }

        for (expected in expectedEvents) {
            assertTrue(actualEvents.contains(expected), "Missing event: $expected")
        }
    }
}
