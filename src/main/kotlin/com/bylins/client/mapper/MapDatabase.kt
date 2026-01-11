package com.bylins.client.mapper

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

/**
 * База данных для хранения карт в SQLite
 *
 * Схема:
 * - maps: список карт (id, name, description, created_at, updated_at)
 * - rooms: комнаты (id, map_id, room_id, name, x, y, z, visited, color, notes, terrain)
 * - exits: выходы между комнатами (id, room_id, direction, target_room_id)
 */
class MapDatabase {
    private var connection: Connection? = null
    private val dbPath: String

    init {
        // Создаем директорию для БД если её нет
        val mapsDir = Paths.get(System.getProperty("user.home"), ".bylins-client", "maps")
        if (!Files.exists(mapsDir)) {
            Files.createDirectories(mapsDir)
        }

        dbPath = mapsDir.resolve("maps.db").toString()
        connect()
        createTables()
    }

    /**
     * Подключение к базе данных
     */
    private fun connect() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            println("[MapDatabase] Connected to database: $dbPath")
        } catch (e: Exception) {
            println("[MapDatabase] Error connecting to database: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Создание таблиц если их нет
     */
    private fun createTables() {
        try {
            val statement = connection?.createStatement()

            // Таблица карт
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS maps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Таблица комнат
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    map_id INTEGER NOT NULL,
                    room_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    visited INTEGER NOT NULL DEFAULT 0,
                    color TEXT,
                    notes TEXT,
                    terrain TEXT,
                    FOREIGN KEY (map_id) REFERENCES maps(id) ON DELETE CASCADE,
                    UNIQUE(map_id, room_id)
                )
            """.trimIndent())

            // Таблица выходов
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS exits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    room_id INTEGER NOT NULL,
                    direction TEXT NOT NULL,
                    target_room_id TEXT NOT NULL,
                    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Индексы для быстрого поиска
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_rooms_map_id ON rooms(map_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_rooms_coords ON rooms(map_id, x, y, z)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_exits_room ON exits(room_id)")

            statement?.close()
            println("[MapDatabase] Tables created successfully")
        } catch (e: Exception) {
            println("[MapDatabase] Error creating tables: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Сохраняет карту в базу данных
     */
    fun saveMap(name: String, rooms: Map<String, Room>, description: String = ""): Boolean {
        return try {
            connection?.autoCommit = false

            // Удаляем старую карту если есть
            deleteMap(name)

            // Создаем новую запись карты
            val mapId = createMap(name, description)
            if (mapId == null) {
                connection?.rollback()
                return false
            }

            // Сохраняем комнаты
            val roomIdMapping = mutableMapOf<String, Long>()
            for ((roomKey, room) in rooms) {
                val dbRoomId = insertRoom(mapId, room)
                if (dbRoomId != null) {
                    roomIdMapping[roomKey] = dbRoomId
                }
            }

            // Сохраняем выходы
            for ((roomKey, room) in rooms) {
                val dbRoomId = roomIdMapping[roomKey] ?: continue
                for ((direction, exit) in room.exits) {
                    insertExit(dbRoomId, direction.name, exit.targetRoomId)
                }
            }

            connection?.commit()
            connection?.autoCommit = true

            println("[MapDatabase] Map '$name' saved successfully (${rooms.size} rooms)")
            true
        } catch (e: Exception) {
            connection?.rollback()
            connection?.autoCommit = true
            println("[MapDatabase] Error saving map: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Загружает карту из базы данных
     */
    fun loadMap(name: String): Map<String, Room>? {
        return try {
            // Получаем ID карты
            val mapId = getMapId(name) ?: return null

            // Загружаем комнаты
            val rooms = mutableMapOf<String, Room>()
            val roomIdMapping = mutableMapOf<Long, String>()

            val roomsStmt = connection?.prepareStatement(
                "SELECT * FROM rooms WHERE map_id = ?"
            )
            roomsStmt?.setLong(1, mapId)
            val roomsRs = roomsStmt?.executeQuery()

            while (roomsRs?.next() == true) {
                val room = parseRoom(roomsRs)
                rooms[room.id] = room
                roomIdMapping[roomsRs.getLong("id")] = room.id
            }

            roomsRs?.close()
            roomsStmt?.close()

            // Загружаем выходы
            for ((dbRoomId, roomKey) in roomIdMapping) {
                val exitsStmt = connection?.prepareStatement(
                    "SELECT direction, target_room_id FROM exits WHERE room_id = ?"
                )
                exitsStmt?.setLong(1, dbRoomId)
                val exitsRs = exitsStmt?.executeQuery()

                val room = rooms[roomKey]
                while (exitsRs?.next() == true) {
                    val directionName = exitsRs.getString("direction")
                    val targetRoomId = exitsRs.getString("target_room_id")
                    val direction = Direction.valueOf(directionName)
                    room?.addExit(direction, targetRoomId)
                }

                exitsRs?.close()
                exitsStmt?.close()
            }

            println("[MapDatabase] Map '$name' loaded successfully (${rooms.size} rooms)")
            rooms
        } catch (e: Exception) {
            println("[MapDatabase] Error loading map: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Возвращает список всех карт
     */
    fun listMaps(): List<MapInfo> {
        val maps = mutableListOf<MapInfo>()
        try {
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery("""
                SELECT m.name, m.description, m.created_at, m.updated_at,
                       COUNT(r.id) as room_count
                FROM maps m
                LEFT JOIN rooms r ON m.id = r.map_id
                GROUP BY m.id
                ORDER BY m.updated_at DESC
            """.trimIndent())

            while (rs?.next() == true) {
                maps.add(MapInfo(
                    name = rs.getString("name"),
                    description = rs.getString("description") ?: "",
                    roomCount = rs.getInt("room_count"),
                    createdAt = rs.getLong("created_at"),
                    updatedAt = rs.getLong("updated_at")
                ))
            }

            rs?.close()
            stmt?.close()
        } catch (e: Exception) {
            println("[MapDatabase] Error listing maps: ${e.message}")
            e.printStackTrace()
        }
        return maps
    }

    /**
     * Удаляет карту из базы данных
     */
    fun deleteMap(name: String): Boolean {
        return try {
            val stmt = connection?.prepareStatement("DELETE FROM maps WHERE name = ?")
            stmt?.setString(1, name)
            val deleted = stmt?.executeUpdate() ?: 0
            stmt?.close()

            if (deleted > 0) {
                println("[MapDatabase] Map '$name' deleted")
            }
            deleted > 0
        } catch (e: Exception) {
            println("[MapDatabase] Error deleting map: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // Вспомогательные методы

    private fun createMap(name: String, description: String): Long? {
        return try {
            val now = Instant.now().epochSecond
            val stmt = connection?.prepareStatement(
                "INSERT INTO maps (name, description, created_at, updated_at) VALUES (?, ?, ?, ?)"
            )
            stmt?.setString(1, name)
            stmt?.setString(2, description)
            stmt?.setLong(3, now)
            stmt?.setLong(4, now)
            stmt?.executeUpdate()
            stmt?.close()

            // Получаем ID созданной карты
            val idStmt = connection?.prepareStatement("SELECT last_insert_rowid()")
            val rs = idStmt?.executeQuery()
            val id = if (rs?.next() == true) rs.getLong(1) else null
            rs?.close()
            idStmt?.close()
            id
        } catch (e: Exception) {
            println("[MapDatabase] Error creating map: ${e.message}")
            null
        }
    }

    private fun getMapId(name: String): Long? {
        return try {
            val stmt = connection?.prepareStatement("SELECT id FROM maps WHERE name = ?")
            stmt?.setString(1, name)
            val rs = stmt?.executeQuery()
            val id = if (rs?.next() == true) rs.getLong("id") else null
            rs?.close()
            stmt?.close()
            id
        } catch (e: Exception) {
            null
        }
    }

    private fun insertRoom(mapId: Long, room: Room): Long? {
        return try {
            val stmt = connection?.prepareStatement("""
                INSERT INTO rooms (map_id, room_id, name, x, y, z, visited, color, notes, terrain)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            stmt?.setLong(1, mapId)
            stmt?.setString(2, room.id)
            stmt?.setString(3, room.name)
            stmt?.setInt(4, room.x)
            stmt?.setInt(5, room.y)
            stmt?.setInt(6, room.z)
            stmt?.setInt(7, if (room.visited) 1 else 0)
            stmt?.setString(8, room.color)
            stmt?.setString(9, room.notes)
            stmt?.setString(10, room.terrain)
            stmt?.executeUpdate()
            stmt?.close()

            // Получаем ID созданной комнаты
            val idStmt = connection?.prepareStatement("SELECT last_insert_rowid()")
            val rs = idStmt?.executeQuery()
            val id = if (rs?.next() == true) rs.getLong(1) else null
            rs?.close()
            idStmt?.close()
            id
        } catch (e: Exception) {
            println("[MapDatabase] Error inserting room: ${e.message}")
            null
        }
    }

    private fun insertExit(roomId: Long, direction: String, targetRoomId: String) {
        try {
            val stmt = connection?.prepareStatement(
                "INSERT INTO exits (room_id, direction, target_room_id) VALUES (?, ?, ?)"
            )
            stmt?.setLong(1, roomId)
            stmt?.setString(2, direction)
            stmt?.setString(3, targetRoomId)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            println("[MapDatabase] Error inserting exit: ${e.message}")
        }
    }

    private fun parseRoom(rs: ResultSet): Room {
        return Room(
            id = rs.getString("room_id"),
            name = rs.getString("name"),
            x = rs.getInt("x"),
            y = rs.getInt("y"),
            z = rs.getInt("z"),
            visited = rs.getInt("visited") == 1,
            color = rs.getString("color"),
            notes = rs.getString("notes") ?: "",
            terrain = rs.getString("terrain") ?: ""
        )
    }

    /**
     * Закрывает соединение с базой данных
     */
    fun close() {
        try {
            connection?.close()
            println("[MapDatabase] Database connection closed")
        } catch (e: Exception) {
            println("[MapDatabase] Error closing database: ${e.message}")
        }
    }
}

/**
 * Информация о карте
 */
data class MapInfo(
    val name: String,
    val description: String,
    val roomCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)
