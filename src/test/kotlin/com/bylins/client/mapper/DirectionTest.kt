package com.bylins.client.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DirectionTest {

    @Test
    fun `getOpposite returns correct opposite direction`() {
        assertEquals(Direction.SOUTH, Direction.NORTH.getOpposite())
        assertEquals(Direction.NORTH, Direction.SOUTH.getOpposite())
        assertEquals(Direction.WEST, Direction.EAST.getOpposite())
        assertEquals(Direction.EAST, Direction.WEST.getOpposite())
        assertEquals(Direction.DOWN, Direction.UP.getOpposite())
        assertEquals(Direction.UP, Direction.DOWN.getOpposite())
    }

    @Test
    fun `fromCommand parses English commands`() {
        assertEquals(Direction.NORTH, Direction.fromCommand("north"))
        assertEquals(Direction.NORTH, Direction.fromCommand("NORTH"))
        assertEquals(Direction.NORTH, Direction.fromCommand("n"))
        assertEquals(Direction.SOUTH, Direction.fromCommand("south"))
        assertEquals(Direction.SOUTH, Direction.fromCommand("s"))
        assertEquals(Direction.EAST, Direction.fromCommand("east"))
        assertEquals(Direction.EAST, Direction.fromCommand("e"))
        assertEquals(Direction.WEST, Direction.fromCommand("west"))
        assertEquals(Direction.WEST, Direction.fromCommand("w"))
        assertEquals(Direction.UP, Direction.fromCommand("up"))
        assertEquals(Direction.UP, Direction.fromCommand("u"))
        assertEquals(Direction.DOWN, Direction.fromCommand("down"))
        assertEquals(Direction.DOWN, Direction.fromCommand("d"))
    }

    @Test
    fun `fromCommand parses Russian commands`() {
        assertEquals(Direction.NORTH, Direction.fromCommand("север"))
        assertEquals(Direction.NORTH, Direction.fromCommand("с"))
        assertEquals(Direction.SOUTH, Direction.fromCommand("юг"))
        assertEquals(Direction.SOUTH, Direction.fromCommand("ю"))
        assertEquals(Direction.EAST, Direction.fromCommand("восток"))
        assertEquals(Direction.EAST, Direction.fromCommand("в"))
        assertEquals(Direction.WEST, Direction.fromCommand("запад"))
        assertEquals(Direction.WEST, Direction.fromCommand("з"))
        assertEquals(Direction.UP, Direction.fromCommand("вверх"))
        assertEquals(Direction.DOWN, Direction.fromCommand("вниз"))
    }

    @Test
    fun `fromCommand returns null for invalid command`() {
        assertNull(Direction.fromCommand("invalid"))
        assertNull(Direction.fromCommand(""))
        assertNull(Direction.fromCommand("nowhere"))
    }

    @Test
    fun `direction has correct coordinate deltas`() {
        // North is negative Y (up on screen)
        assertEquals(0, Direction.NORTH.dx)
        assertEquals(-1, Direction.NORTH.dy)
        assertEquals(0, Direction.NORTH.dz)

        // South is positive Y (down on screen)
        assertEquals(0, Direction.SOUTH.dx)
        assertEquals(1, Direction.SOUTH.dy)
        assertEquals(0, Direction.SOUTH.dz)

        // East is positive X (right)
        assertEquals(1, Direction.EAST.dx)
        assertEquals(0, Direction.EAST.dy)
        assertEquals(0, Direction.EAST.dz)

        // West is negative X (left)
        assertEquals(-1, Direction.WEST.dx)
        assertEquals(0, Direction.WEST.dy)
        assertEquals(0, Direction.WEST.dz)

        // Up is positive Z
        assertEquals(0, Direction.UP.dx)
        assertEquals(0, Direction.UP.dy)
        assertEquals(1, Direction.UP.dz)

        // Down is negative Z
        assertEquals(0, Direction.DOWN.dx)
        assertEquals(0, Direction.DOWN.dy)
        assertEquals(-1, Direction.DOWN.dz)
    }
}
