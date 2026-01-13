package com.bylins.client.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class RoomTest {

    @Test
    fun `addExit creates exit to target room`() {
        val room = Room(id = "100", name = "Test Room")
        room.addExit(Direction.NORTH, "200")

        assertTrue(room.hasExit(Direction.NORTH))
        assertEquals("200", room.getExit(Direction.NORTH)?.targetRoomId)
    }

    @Test
    fun `addExit ignores self-referencing exit`() {
        val room = Room(id = "100", name = "Test Room")
        room.addExit(Direction.NORTH, "100") // Self-reference

        // Exit should NOT be created
        assertFalse(room.hasExit(Direction.NORTH))
    }

    @Test
    fun `addUnexploredExit creates exit with empty targetRoomId`() {
        val room = Room(id = "100", name = "Test Room")
        room.addUnexploredExit(Direction.SOUTH)

        assertTrue(room.hasExit(Direction.SOUTH))
        assertEquals("", room.getExit(Direction.SOUTH)?.targetRoomId)
        assertFalse(room.isExitExplored(Direction.SOUTH))
    }

    @Test
    fun `addUnexploredExit does not overwrite explored exit`() {
        val room = Room(id = "100", name = "Test Room")
        room.addExit(Direction.NORTH, "200")
        room.addUnexploredExit(Direction.NORTH)

        // Should keep the explored exit
        assertEquals("200", room.getExit(Direction.NORTH)?.targetRoomId)
        assertTrue(room.isExitExplored(Direction.NORTH))
    }

    @Test
    fun `addUnexploredExit fixes self-referencing exit`() {
        val room = Room(id = "100", name = "Test Room")
        // Manually create a self-referencing exit (simulating bug)
        room.exits[Direction.NORTH] = Exit("100")

        // This should fix the self-reference
        room.addUnexploredExit(Direction.NORTH)

        assertEquals("", room.getExit(Direction.NORTH)?.targetRoomId)
        assertFalse(room.isExitExplored(Direction.NORTH))
    }

    @Test
    fun `isExitExplored returns true for explored exit`() {
        val room = Room(id = "100", name = "Test Room")
        room.addExit(Direction.NORTH, "200")

        assertTrue(room.isExitExplored(Direction.NORTH))
    }

    @Test
    fun `isExitExplored returns false for unexplored exit`() {
        val room = Room(id = "100", name = "Test Room")
        room.addUnexploredExit(Direction.NORTH)

        assertFalse(room.isExitExplored(Direction.NORTH))
    }

    @Test
    fun `isExitExplored returns false for non-existent exit`() {
        val room = Room(id = "100", name = "Test Room")

        assertFalse(room.isExitExplored(Direction.NORTH))
    }

    @Test
    fun `removeExit removes exit`() {
        val room = Room(id = "100", name = "Test Room")
        room.addExit(Direction.NORTH, "200")
        room.removeExit(Direction.NORTH)

        assertFalse(room.hasExit(Direction.NORTH))
        assertNull(room.getExit(Direction.NORTH))
    }

    @Test
    fun `getAvailableDirections returns all exit directions`() {
        val room = Room(id = "100", name = "Test Room")
        room.addExit(Direction.NORTH, "200")
        room.addUnexploredExit(Direction.SOUTH)
        room.addExit(Direction.EAST, "300")

        val directions = room.getAvailableDirections()
        assertEquals(3, directions.size)
        assertTrue(directions.contains(Direction.NORTH))
        assertTrue(directions.contains(Direction.SOUTH))
        assertTrue(directions.contains(Direction.EAST))
    }

    @Test
    fun `toMap converts room to map correctly`() {
        val room = Room(
            id = "100",
            name = "Test Room",
            description = "A test room",
            terrain = "city",
            zone = "test_zone",
            notes = "Some notes",
            visited = true
        )
        room.addExit(Direction.NORTH, "200")
        room.addUnexploredExit(Direction.SOUTH)

        val map = room.toMap()

        assertEquals("100", map["id"])
        assertEquals("Test Room", map["name"])
        assertEquals("A test room", map["description"])
        assertEquals("city", map["terrain"])
        assertEquals("test_zone", map["zone"])
        assertEquals("Some notes", map["notes"])
        assertEquals(true, map["visited"])

        @Suppress("UNCHECKED_CAST")
        val exits = map["exits"] as List<String>
        assertEquals(2, exits.size)
        assertTrue(exits.contains("NORTH"))
        assertTrue(exits.contains("SOUTH"))
    }
}
