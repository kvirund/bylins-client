package com.bylins.client.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class MapManagerTest {

    private fun createTestMapManager(): MapManager {
        val manager = MapManager()
        // Clear any rooms loaded from autosave
        manager.clearMap()
        return manager
    }

    @Test
    fun `addRoom adds room to map`() {
        val mapManager = createTestMapManager()
        val room = Room(id = "100", name = "Test Room")

        mapManager.addRoom(room)

        val retrieved = mapManager.getRoom("100")
        assertNotNull(retrieved)
        assertEquals("Test Room", retrieved.name)
    }

    @Test
    fun `getRoom returns null for non-existent room`() {
        val mapManager = createTestMapManager()

        val room = mapManager.getRoom("non-existent")
        assertNull(room)
    }

    @Test
    fun `removeRoom removes room from map`() {
        val mapManager = createTestMapManager()
        val room = Room(id = "100", name = "Test Room")
        mapManager.addRoom(room)

        mapManager.removeRoom("100")

        assertNull(mapManager.getRoom("100"))
    }

    @Test
    fun `setCurrentRoom sets current room`() {
        val mapManager = createTestMapManager()
        val room = Room(id = "100", name = "Test Room")
        mapManager.addRoom(room)

        mapManager.setCurrentRoom("100")

        val current = mapManager.getCurrentRoom()
        assertNotNull(current)
        assertEquals("100", current.id)
    }

    @Test
    fun `setCurrentRoom marks room as visited`() {
        val mapManager = createTestMapManager()
        val room = Room(id = "100", name = "Test Room", visited = false)
        mapManager.addRoom(room)

        mapManager.setCurrentRoom("100")

        val current = mapManager.getCurrentRoom()
        assertNotNull(current)
        assertTrue(current.visited)
    }

    @Test
    fun `handleMovement creates new room with roomId`() {
        val mapManager = createTestMapManager()

        val room = mapManager.handleMovement(
            direction = Direction.NORTH,
            newRoomName = "New Room",
            exits = listOf(Direction.SOUTH, Direction.EAST),
            roomId = "100"
        )

        assertNotNull(room)
        assertEquals("100", room.id)
        assertEquals("New Room", room.name)
    }

    @Test
    fun `handleMovement returns null without roomId`() {
        val mapManager = createTestMapManager()

        val room = mapManager.handleMovement(
            direction = Direction.NORTH,
            newRoomName = "New Room",
            exits = listOf(Direction.SOUTH),
            roomId = null
        )

        // Should return null because roomId is required
        assertNull(room)
    }

    @Test
    fun `handleMovement creates link from current room`() {
        val mapManager = createTestMapManager()

        // Create starting room
        val startRoom = Room(id = "100", name = "Start Room", visited = true)
        mapManager.addRoom(startRoom)
        mapManager.setCurrentRoom("100")

        // Move north
        val newRoom = mapManager.handleMovement(
            direction = Direction.NORTH,
            newRoomName = "North Room",
            exits = listOf(Direction.SOUTH),
            roomId = "200"
        )

        assertNotNull(newRoom)
        assertEquals("200", newRoom.id)

        // Check forward link from start room
        val updatedStart = mapManager.getRoom("100")
        assertNotNull(updatedStart)
        assertTrue(updatedStart.hasExit(Direction.NORTH))
        assertEquals("200", updatedStart.getExit(Direction.NORTH)?.targetRoomId)

        // Check reverse link from new room
        val updatedNew = mapManager.getRoom("200")
        assertNotNull(updatedNew)
        assertTrue(updatedNew.hasExit(Direction.SOUTH))
        assertEquals("100", updatedNew.getExit(Direction.SOUTH)?.targetRoomId)
    }

    @Test
    fun `handleMovement adds unexplored exits`() {
        val mapManager = createTestMapManager()

        val room = mapManager.handleMovement(
            direction = Direction.NORTH,
            newRoomName = "New Room",
            exits = listOf(Direction.SOUTH, Direction.EAST, Direction.WEST),
            roomId = "100"
        )

        assertNotNull(room)
        val retrieved = mapManager.getRoom("100")
        assertNotNull(retrieved)

        // All exits should exist
        assertTrue(retrieved.hasExit(Direction.SOUTH))
        assertTrue(retrieved.hasExit(Direction.EAST))
        assertTrue(retrieved.hasExit(Direction.WEST))

        // All should be unexplored (no current room before)
        assertFalse(retrieved.isExitExplored(Direction.SOUTH))
        assertFalse(retrieved.isExitExplored(Direction.EAST))
        assertFalse(retrieved.isExitExplored(Direction.WEST))
    }

    @Test
    fun `handleMovement reuses existing room by ID`() {
        val mapManager = createTestMapManager()

        // Create existing room
        val existingRoom = Room(id = "100", name = "Old Name", visited = false)
        mapManager.addRoom(existingRoom)

        // Move to the same room ID
        val room = mapManager.handleMovement(
            direction = Direction.NORTH,
            newRoomName = "New Name",
            exits = listOf(Direction.SOUTH),
            roomId = "100"
        )

        assertNotNull(room)
        assertEquals("100", room.id)
        assertEquals("New Name", room.name) // Name should be updated

        // Should only have one room with this ID
        assertEquals(1, mapManager.rooms.value.size)
    }

    @Test
    fun `clearMap removes all rooms`() {
        val mapManager = createTestMapManager()
        mapManager.addRoom(Room(id = "100", name = "Room 1"))
        mapManager.addRoom(Room(id = "200", name = "Room 2"))
        mapManager.setCurrentRoom("100")

        mapManager.clearMap()

        assertTrue(mapManager.rooms.value.isEmpty())
        assertNull(mapManager.getCurrentRoom())
    }

    @Test
    fun `setMapEnabled toggles mapping`() {
        val mapManager = createTestMapManager()

        mapManager.setMapEnabled(false)
        assertFalse(mapManager.mapEnabled.value)

        mapManager.setMapEnabled(true)
        assertTrue(mapManager.mapEnabled.value)
    }

    @Test
    fun `handleMovement returns null when mapping disabled`() {
        val mapManager = createTestMapManager()
        mapManager.setMapEnabled(false)

        val room = mapManager.handleMovement(
            direction = Direction.NORTH,
            newRoomName = "New Room",
            exits = listOf(Direction.SOUTH),
            roomId = "100"
        )

        assertNull(room)
    }

    @Test
    fun `setRoomNote updates room note`() {
        val mapManager = createTestMapManager()
        mapManager.addRoom(Room(id = "100", name = "Room"))

        mapManager.setRoomNote("100", "Important location")

        val room = mapManager.getRoom("100")
        assertNotNull(room)
        assertEquals("Important location", room.notes)
    }

    @Test
    fun `setRoomZone updates room zone`() {
        val mapManager = createTestMapManager()
        mapManager.addRoom(Room(id = "100", name = "Room"))

        mapManager.setRoomZone("100", "zone_50")

        val room = mapManager.getRoom("100")
        assertNotNull(room)
        assertEquals("zone_50", room.zone)
    }

    @Test
    fun `addRoomTag adds tag to room`() {
        val mapManager = createTestMapManager()
        mapManager.addRoom(Room(id = "100", name = "Room"))

        mapManager.addRoomTag("100", "shop")
        mapManager.addRoomTag("100", "trainer")

        val room = mapManager.getRoom("100")
        assertNotNull(room)
        assertTrue(room.tags.contains("shop"))
        assertTrue(room.tags.contains("trainer"))
    }

    @Test
    fun `removeRoomTag removes tag from room`() {
        val mapManager = createTestMapManager()
        mapManager.addRoom(Room(id = "100", name = "Room", tags = setOf("shop", "trainer")))

        mapManager.removeRoomTag("100", "shop")

        val room = mapManager.getRoom("100")
        assertNotNull(room)
        assertFalse(room.tags.contains("shop"))
        assertTrue(room.tags.contains("trainer"))
    }

    @Test
    fun `getRoomsByTag returns rooms with specified tag`() {
        val mapManager = createTestMapManager()
        mapManager.addRoom(Room(id = "100", name = "Shop 1", tags = setOf("shop")))
        mapManager.addRoom(Room(id = "200", name = "Shop 2", tags = setOf("shop", "trainer")))
        mapManager.addRoom(Room(id = "300", name = "Trainer", tags = setOf("trainer")))

        val shops = mapManager.getRoomsByTag("shop")

        assertEquals(2, shops.size)
        assertTrue(shops.any { it.id == "100" })
        assertTrue(shops.any { it.id == "200" })
    }
}
