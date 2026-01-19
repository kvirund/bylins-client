package com.bylins.client.mapper

import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * База данных для хранения карт в SQLite
 *
 * Схема:
 * - zones: зоны (id, name, notes, updated_at)
 * - rooms: комнаты (id, name, description, zone_id, terrain, color, notes, tags, visited, updated_at)
 * - exits: выходы между комнатами (room_id, direction, target_room_id, door)
 */
private val logger = KotlinLogging.logger("MapDatabase")

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
        migrateIfNeeded()
        createTables()
    }

    /**
     * Подключение к базе данных
     */
    private fun connect() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            logger.info { "Connected to database: $dbPath" }
        } catch (e: Exception) {
            logger.error { "Error connecting to database: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Миграция данных из старых таблиц
     */
    private fun migrateIfNeeded() {
        try {
            val statement = connection?.createStatement()

            // Проверяем наличие старых таблиц
            val hasOldTables = tableExists("autosave_rooms") || tableExists("zone_notes") || tableExists("zone_names")

            if (hasOldTables) {
                logger.info { "Starting migration from old schema..." }
                connection?.autoCommit = false

                // 1. Создаём новую таблицу zones если её нет
                statement?.execute("""
                    CREATE TABLE IF NOT EXISTS zones (
                        id TEXT PRIMARY KEY,
                        name TEXT,
                        notes TEXT DEFAULT '',
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // 2. Мигрируем данные из zone_notes и zone_names
                if (tableExists("zone_names")) {
                    statement?.execute("""
                        INSERT OR IGNORE INTO zones (id, name, notes, updated_at)
                        SELECT zone_id, area_name, '', updated_at FROM zone_names
                    """.trimIndent())
                }
                if (tableExists("zone_notes")) {
                    // Обновляем notes для существующих зон
                    statement?.execute("""
                        UPDATE zones SET notes = (
                            SELECT notes FROM zone_notes WHERE zone_notes.zone_name = zones.id
                        ) WHERE EXISTS (
                            SELECT 1 FROM zone_notes WHERE zone_notes.zone_name = zones.id
                        )
                    """.trimIndent())
                    // Вставляем зоны которых ещё нет
                    statement?.execute("""
                        INSERT OR IGNORE INTO zones (id, name, notes, updated_at)
                        SELECT zone_name, NULL, notes, updated_at FROM zone_notes
                    """.trimIndent())
                }

                // 3. Создаём новые таблицы rooms и exits
                statement?.execute("""
                    CREATE TABLE IF NOT EXISTS rooms_new (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT DEFAULT '',
                        zone_id TEXT,
                        terrain TEXT,
                        color TEXT,
                        notes TEXT DEFAULT '',
                        tags TEXT DEFAULT '',
                        visited INTEGER DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (zone_id) REFERENCES zones(id)
                    )
                """.trimIndent())

                statement?.execute("""
                    CREATE TABLE IF NOT EXISTS exits_new (
                        room_id TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        target_room_id TEXT NOT NULL,
                        door TEXT,
                        PRIMARY KEY (room_id, direction),
                        FOREIGN KEY (room_id) REFERENCES rooms_new(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 4. Мигрируем данные из autosave_rooms
                if (tableExists("autosave_rooms")) {
                    statement?.execute("""
                        INSERT OR REPLACE INTO rooms_new (id, name, description, zone_id, terrain, color, notes, tags, visited, updated_at)
                        SELECT room_id, name, COALESCE(description, ''), zone, terrain, color, COALESCE(notes, ''), COALESCE(tags, ''), visited, updated_at
                        FROM autosave_rooms
                    """.trimIndent())
                }

                // 5. Мигрируем данные из autosave_exits
                if (tableExists("autosave_exits")) {
                    statement?.execute("""
                        INSERT OR REPLACE INTO exits_new (room_id, direction, target_room_id, door)
                        SELECT room_id, direction, COALESCE(target_room_id, ''), door
                        FROM autosave_exits
                    """.trimIndent())
                }

                // 6. Удаляем старые таблицы
                statement?.execute("DROP TABLE IF EXISTS autosave_exits")
                statement?.execute("DROP TABLE IF EXISTS autosave_rooms")
                statement?.execute("DROP TABLE IF EXISTS zone_notes")
                statement?.execute("DROP TABLE IF EXISTS zone_names")
                statement?.execute("DROP TABLE IF EXISTS exits")
                statement?.execute("DROP TABLE IF EXISTS rooms")
                statement?.execute("DROP TABLE IF EXISTS maps")

                // 7. Переименовываем новые таблицы
                statement?.execute("ALTER TABLE rooms_new RENAME TO rooms")
                statement?.execute("ALTER TABLE exits_new RENAME TO exits")

                connection?.commit()
                connection?.autoCommit = true

                logger.info { "Migration completed successfully" }
            }

            statement?.close()
        } catch (e: Exception) {
            connection?.rollback()
            connection?.autoCommit = true
            logger.error { "Error during migration: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Проверяет существование таблицы
     */
    private fun tableExists(tableName: String): Boolean {
        return try {
            val stmt = connection?.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
            )
            stmt?.setString(1, tableName)
            val rs = stmt?.executeQuery()
            val exists = rs?.next() == true
            rs?.close()
            stmt?.close()
            exists
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Создание таблиц если их нет
     */
    private fun createTables() {
        try {
            val statement = connection?.createStatement()

            // Таблица зон
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS zones (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    notes TEXT DEFAULT '',
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Таблица комнат
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT DEFAULT '',
                    zone_id TEXT,
                    terrain TEXT,
                    color TEXT,
                    notes TEXT DEFAULT '',
                    tags TEXT DEFAULT '',
                    visited INTEGER DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (zone_id) REFERENCES zones(id)
                )
            """.trimIndent())

            // Таблица выходов
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS exits (
                    room_id TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    target_room_id TEXT NOT NULL,
                    door TEXT,
                    PRIMARY KEY (room_id, direction),
                    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Индексы
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_rooms_zone ON rooms(zone_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_exits_target ON exits(target_room_id)")

            statement?.close()
            logger.info { "Tables created/verified successfully" }
        } catch (e: Exception) {
            logger.error { "Error creating tables: ${e.message}" }
            e.printStackTrace()
        }
    }

    // ============================================
    // Операции с зонами
    // ============================================

    /**
     * Сохраняет или обновляет зону
     */
    fun saveZone(zoneId: String, name: String? = null, notes: String? = null) {
        if (zoneId.isBlank()) return
        try {
            val now = Instant.now().epochSecond

            // Проверяем существует ли зона
            val existingStmt = connection?.prepareStatement("SELECT name, notes FROM zones WHERE id = ?")
            existingStmt?.setString(1, zoneId)
            val rs = existingStmt?.executeQuery()

            if (rs?.next() == true) {
                // Обновляем существующую зону
                val currentName = rs.getString("name")
                val currentNotes = rs.getString("notes")
                rs.close()
                existingStmt?.close()

                val newName = name ?: currentName
                val newNotes = notes ?: currentNotes

                val updateStmt = connection?.prepareStatement("""
                    UPDATE zones SET name = ?, notes = ?, updated_at = ? WHERE id = ?
                """.trimIndent())
                updateStmt?.setString(1, newName)
                updateStmt?.setString(2, newNotes)
                updateStmt?.setLong(3, now)
                updateStmt?.setString(4, zoneId)
                updateStmt?.executeUpdate()
                updateStmt?.close()
            } else {
                rs?.close()
                existingStmt?.close()

                // Вставляем новую зону
                val insertStmt = connection?.prepareStatement("""
                    INSERT INTO zones (id, name, notes, updated_at) VALUES (?, ?, ?, ?)
                """.trimIndent())
                insertStmt?.setString(1, zoneId)
                insertStmt?.setString(2, name ?: "")
                insertStmt?.setString(3, notes ?: "")
                insertStmt?.setLong(4, now)
                insertStmt?.executeUpdate()
                insertStmt?.close()
            }
        } catch (e: Exception) {
            logger.error { "Error saving zone: ${e.message}" }
        }
    }

    /**
     * Загружает все зоны: Map<zoneId, Pair<name, notes>>
     */
    fun loadAllZones(): Map<String, Pair<String?, String>> {
        return try {
            val result = mutableMapOf<String, Pair<String?, String>>()
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery("SELECT id, name, notes FROM zones")
            while (rs?.next() == true) {
                val id = rs.getString("id")
                val name = rs.getString("name")
                val notes = rs.getString("notes") ?: ""
                if (id.isNotBlank()) {
                    result[id] = Pair(name, notes)
                }
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error loading zones: ${e.message}" }
            emptyMap()
        }
    }

    // ============================================
    // Операции с комнатами
    // ============================================

    /**
     * Сохраняет одну комнату (upsert)
     */
    @Synchronized
    fun saveRoom(room: Room) {
        try {
            connection?.autoCommit = false

            val now = Instant.now().epochSecond
            val tagsJson = room.tags.joinToString(",")

            // UPSERT комнаты
            val roomStmt = connection?.prepareStatement("""
                INSERT OR REPLACE INTO rooms
                (id, name, description, zone_id, terrain, color, notes, tags, visited, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            roomStmt?.setString(1, room.id)
            roomStmt?.setString(2, room.name)
            roomStmt?.setString(3, room.description)
            roomStmt?.setString(4, room.zone)
            roomStmt?.setString(5, room.terrain)
            roomStmt?.setString(6, room.color)
            roomStmt?.setString(7, room.notes)
            roomStmt?.setString(8, tagsJson)
            roomStmt?.setInt(9, if (room.visited) 1 else 0)
            roomStmt?.setLong(10, now)
            roomStmt?.executeUpdate()
            roomStmt?.close()

            // Удаляем старые выходы
            val deleteExitsStmt = connection?.prepareStatement(
                "DELETE FROM exits WHERE room_id = ?"
            )
            deleteExitsStmt?.setString(1, room.id)
            deleteExitsStmt?.executeUpdate()
            deleteExitsStmt?.close()

            // Добавляем новые выходы
            for ((direction, exit) in room.exits) {
                val exitStmt = connection?.prepareStatement("""
                    INSERT INTO exits (room_id, direction, target_room_id, door)
                    VALUES (?, ?, ?, ?)
                """.trimIndent())
                exitStmt?.setString(1, room.id)
                exitStmt?.setString(2, direction.name)
                exitStmt?.setString(3, exit.targetRoomId)
                exitStmt?.setString(4, exit.door)
                exitStmt?.executeUpdate()
                exitStmt?.close()
            }

            connection?.commit()
            connection?.autoCommit = true
        } catch (e: Exception) {
            connection?.rollback()
            connection?.autoCommit = true
            logger.error { "Error saving room: ${e.message}" }
        }
    }

    /**
     * Удаляет комнату
     */
    fun deleteRoom(roomId: String) {
        try {
            val stmt = connection?.prepareStatement("DELETE FROM rooms WHERE id = ?")
            stmt?.setString(1, roomId)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error deleting room: ${e.message}" }
        }
    }

    /**
     * Загружает все комнаты
     */
    fun loadAllRooms(): Map<String, Room>? {
        return try {
            val rooms = mutableMapOf<String, Room>()

            // Загружаем комнаты
            val roomsStmt = connection?.createStatement()
            val roomsRs = roomsStmt?.executeQuery("SELECT * FROM rooms")

            while (roomsRs?.next() == true) {
                val tagsStr = roomsRs.getString("tags") ?: ""
                val tags = if (tagsStr.isNotEmpty()) tagsStr.split(",").toSet() else emptySet()

                val room = Room(
                    id = roomsRs.getString("id"),
                    name = roomsRs.getString("name"),
                    description = roomsRs.getString("description") ?: "",
                    visited = roomsRs.getInt("visited") == 1,
                    color = roomsRs.getString("color"),
                    notes = roomsRs.getString("notes") ?: "",
                    terrain = roomsRs.getString("terrain") ?: "",
                    zone = roomsRs.getString("zone_id") ?: "",
                    tags = tags
                )
                rooms[room.id] = room
            }
            roomsRs?.close()
            roomsStmt?.close()

            // Загружаем выходы
            val exitsStmt = connection?.createStatement()
            val exitsRs = exitsStmt?.executeQuery("SELECT * FROM exits")

            while (exitsRs?.next() == true) {
                val roomId = exitsRs.getString("room_id")
                val room = rooms[roomId] ?: continue

                val directionName = exitsRs.getString("direction")
                val direction = try {
                    Direction.valueOf(directionName)
                } catch (e: Exception) {
                    continue
                }

                val exit = Exit(
                    targetRoomId = exitsRs.getString("target_room_id") ?: "",
                    door = exitsRs.getString("door")
                )
                room.exits[direction] = exit
            }
            exitsRs?.close()
            exitsStmt?.close()

            if (rooms.isNotEmpty()) {
                logger.info { "Loaded ${rooms.size} rooms from database" }
            }
            rooms.ifEmpty { null }
        } catch (e: Exception) {
            logger.error { "Error loading rooms: ${e.message}" }
            e.printStackTrace()
            null
        }
    }

    /**
     * Очищает все комнаты
     */
    fun clearAllRooms() {
        try {
            val stmt = connection?.createStatement()
            stmt?.execute("DELETE FROM rooms")
            stmt?.close()
            logger.info { "All rooms cleared" }
        } catch (e: Exception) {
            logger.error { "Error clearing rooms: ${e.message}" }
        }
    }

    /**
     * Возвращает количество комнат
     */
    fun getRoomCount(): Int {
        return try {
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery("SELECT COUNT(*) FROM rooms")
            val count = if (rs?.next() == true) rs.getInt(1) else 0
            rs?.close()
            stmt?.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Закрывает соединение с базой данных
     */
    fun close() {
        try {
            connection?.close()
            logger.info { "Database connection closed" }
        } catch (e: Exception) {
            logger.error { "Error closing database: ${e.message}" }
        }
    }
}
