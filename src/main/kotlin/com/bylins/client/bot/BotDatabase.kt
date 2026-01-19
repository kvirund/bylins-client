package com.bylins.client.bot

import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * База данных бота для хранения информации о мобах, статистике и данных для обучения
 */
private val logger = KotlinLogging.logger("BotDatabase")

class BotDatabase {
    private var connection: Connection? = null
    private val dbPath: String

    init {
        // Создаем директорию для БД если её нет
        val botDir = Paths.get(System.getProperty("user.home"), ".bylins-client", "bot")
        if (!Files.exists(botDir)) {
            Files.createDirectories(botDir)
        }

        dbPath = botDir.resolve("bot.db").toString()
        connect()
        createTables()
    }

    private fun connect() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            logger.info { "Connected to bot database: $dbPath" }
        } catch (e: Exception) {
            logger.error { "Error connecting to bot database: ${e.message}" }
            e.printStackTrace()
        }
    }

    private fun createTables() {
        try {
            val statement = connection?.createStatement()

            // Таблица мобов
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS mobs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    short_name TEXT,
                    level INTEGER,
                    avg_hp INTEGER,
                    exp_reward INTEGER,
                    gold_reward INTEGER,
                    zone_id TEXT,
                    aggressive INTEGER DEFAULT 0,
                    kill_count INTEGER DEFAULT 0,
                    death_count INTEGER DEFAULT 0,
                    avg_fight_duration_ms INTEGER,
                    last_seen INTEGER,
                    notes TEXT DEFAULT '',
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Таблица спавнов мобов в комнатах
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS mob_spawns (
                    room_id TEXT NOT NULL,
                    mob_id TEXT NOT NULL,
                    spawn_count INTEGER DEFAULT 1,
                    last_seen INTEGER,
                    PRIMARY KEY (room_id, mob_id),
                    FOREIGN KEY (mob_id) REFERENCES mobs(id)
                )
            """.trimIndent())

            // Таблица статистики зон
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS zone_stats (
                    zone_id TEXT PRIMARY KEY,
                    level_min INTEGER,
                    level_max INTEGER,
                    avg_exp_per_hour REAL,
                    danger_level REAL,
                    total_kills INTEGER DEFAULT 0,
                    total_deaths INTEGER DEFAULT 0,
                    total_exp_gained INTEGER DEFAULT 0,
                    total_gold_gained INTEGER DEFAULT 0,
                    total_time_spent_ms INTEGER DEFAULT 0,
                    last_visited INTEGER,
                    notes TEXT DEFAULT '',
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Таблица данных для ML обучения
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS training_data (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    action TEXT NOT NULL,
                    reward REAL,
                    next_state TEXT,
                    episode_id TEXT,
                    success INTEGER
                )
            """.trimIndent())

            // Таблица сессий бота
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS bot_sessions (
                    id TEXT PRIMARY KEY,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER,
                    mode TEXT NOT NULL,
                    zone_id TEXT,
                    total_kills INTEGER DEFAULT 0,
                    total_deaths INTEGER DEFAULT 0,
                    total_exp_gained INTEGER DEFAULT 0,
                    total_gold_gained INTEGER DEFAULT 0,
                    total_items_looted INTEGER DEFAULT 0,
                    total_rooms_explored INTEGER DEFAULT 0,
                    notes TEXT DEFAULT ''
                )
            """.trimIndent())

            // Таблица истории боёв для анализа
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS combat_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT,
                    timestamp INTEGER NOT NULL,
                    room_id TEXT,
                    mob_id TEXT,
                    mob_name TEXT,
                    event_type TEXT NOT NULL,
                    damage_dealt INTEGER,
                    damage_received INTEGER,
                    skill_used TEXT,
                    hp_before INTEGER,
                    hp_after INTEGER,
                    mana_before INTEGER,
                    mana_after INTEGER,
                    outcome TEXT
                )
            """.trimIndent())

            // Таблица предметов
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS items (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    short_name TEXT,
                    type TEXT,
                    level INTEGER,
                    value INTEGER,
                    weight REAL,
                    stats TEXT,
                    dropped_by TEXT,
                    drop_count INTEGER DEFAULT 0,
                    last_seen INTEGER,
                    notes TEXT DEFAULT '',
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Индексы
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_mobs_zone ON mobs(zone_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_mobs_level ON mobs(level)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_mob_spawns_room ON mob_spawns(room_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_training_type ON training_data(type)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_training_episode ON training_data(episode_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_session ON combat_log(session_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_mob ON combat_log(mob_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_items_type ON items(type)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_items_dropped_by ON items(dropped_by)")

            statement?.close()
            logger.info { "Bot database tables created/verified successfully" }
        } catch (e: Exception) {
            logger.error { "Error creating bot database tables: ${e.message}" }
            e.printStackTrace()
        }
    }

    // ============================================
    // Операции с мобами
    // ============================================

    fun saveMob(mob: MobData) {
        try {
            val now = Instant.now().epochSecond
            val stmt = connection?.prepareStatement("""
                INSERT OR REPLACE INTO mobs
                (id, name, short_name, level, avg_hp, exp_reward, gold_reward, zone_id,
                 aggressive, kill_count, death_count, avg_fight_duration_ms, last_seen, notes, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            stmt?.setString(1, mob.id)
            stmt?.setString(2, mob.name)
            stmt?.setString(3, mob.shortName)
            stmt?.setInt(4, mob.level ?: 0)
            stmt?.setInt(5, mob.avgHp ?: 0)
            stmt?.setInt(6, mob.expReward ?: 0)
            stmt?.setInt(7, mob.goldReward ?: 0)
            stmt?.setString(8, mob.zoneId)
            stmt?.setInt(9, if (mob.aggressive) 1 else 0)
            stmt?.setInt(10, mob.killCount)
            stmt?.setInt(11, mob.deathCount)
            stmt?.setLong(12, mob.avgFightDurationMs ?: 0)
            stmt?.setLong(13, mob.lastSeen ?: now)
            stmt?.setString(14, mob.notes)
            stmt?.setLong(15, now)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error saving mob: ${e.message}" }
        }
    }

    fun getMob(mobId: String): MobData? {
        return try {
            val stmt = connection?.prepareStatement("SELECT * FROM mobs WHERE id = ?")
            stmt?.setString(1, mobId)
            val rs = stmt?.executeQuery()
            val mob = if (rs?.next() == true) {
                MobData(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    shortName = rs.getString("short_name"),
                    level = rs.getInt("level").takeIf { it > 0 },
                    avgHp = rs.getInt("avg_hp").takeIf { it > 0 },
                    expReward = rs.getInt("exp_reward").takeIf { it > 0 },
                    goldReward = rs.getInt("gold_reward").takeIf { it > 0 },
                    zoneId = rs.getString("zone_id"),
                    aggressive = rs.getInt("aggressive") == 1,
                    killCount = rs.getInt("kill_count"),
                    deathCount = rs.getInt("death_count"),
                    avgFightDurationMs = rs.getLong("avg_fight_duration_ms").takeIf { it > 0 },
                    lastSeen = rs.getLong("last_seen").takeIf { it > 0 },
                    notes = rs.getString("notes") ?: ""
                )
            } else null
            rs?.close()
            stmt?.close()
            mob
        } catch (e: Exception) {
            logger.error { "Error getting mob: ${e.message}" }
            null
        }
    }

    fun findMobsByName(namePattern: String): List<MobData> {
        return try {
            val result = mutableListOf<MobData>()
            val stmt = connection?.prepareStatement(
                "SELECT * FROM mobs WHERE name LIKE ? OR short_name LIKE ?"
            )
            val pattern = "%$namePattern%"
            stmt?.setString(1, pattern)
            stmt?.setString(2, pattern)
            val rs = stmt?.executeQuery()
            while (rs?.next() == true) {
                result.add(MobData(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    shortName = rs.getString("short_name"),
                    level = rs.getInt("level").takeIf { it > 0 },
                    avgHp = rs.getInt("avg_hp").takeIf { it > 0 },
                    expReward = rs.getInt("exp_reward").takeIf { it > 0 },
                    goldReward = rs.getInt("gold_reward").takeIf { it > 0 },
                    zoneId = rs.getString("zone_id"),
                    aggressive = rs.getInt("aggressive") == 1,
                    killCount = rs.getInt("kill_count"),
                    deathCount = rs.getInt("death_count"),
                    avgFightDurationMs = rs.getLong("avg_fight_duration_ms").takeIf { it > 0 },
                    lastSeen = rs.getLong("last_seen").takeIf { it > 0 },
                    notes = rs.getString("notes") ?: ""
                ))
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error finding mobs: ${e.message}" }
            emptyList()
        }
    }

    fun getMobsByZone(zoneId: String): List<MobData> {
        return try {
            val result = mutableListOf<MobData>()
            val stmt = connection?.prepareStatement("SELECT * FROM mobs WHERE zone_id = ?")
            stmt?.setString(1, zoneId)
            val rs = stmt?.executeQuery()
            while (rs?.next() == true) {
                result.add(MobData(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    shortName = rs.getString("short_name"),
                    level = rs.getInt("level").takeIf { it > 0 },
                    avgHp = rs.getInt("avg_hp").takeIf { it > 0 },
                    expReward = rs.getInt("exp_reward").takeIf { it > 0 },
                    goldReward = rs.getInt("gold_reward").takeIf { it > 0 },
                    zoneId = rs.getString("zone_id"),
                    aggressive = rs.getInt("aggressive") == 1,
                    killCount = rs.getInt("kill_count"),
                    deathCount = rs.getInt("death_count"),
                    avgFightDurationMs = rs.getLong("avg_fight_duration_ms").takeIf { it > 0 },
                    lastSeen = rs.getLong("last_seen").takeIf { it > 0 },
                    notes = rs.getString("notes") ?: ""
                ))
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error getting mobs by zone: ${e.message}" }
            emptyList()
        }
    }

    fun incrementMobKillCount(mobId: String) {
        try {
            val stmt = connection?.prepareStatement(
                "UPDATE mobs SET kill_count = kill_count + 1, last_seen = ? WHERE id = ?"
            )
            stmt?.setLong(1, Instant.now().epochSecond)
            stmt?.setString(2, mobId)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error incrementing mob kill count: ${e.message}" }
        }
    }

    fun incrementMobDeathCount(mobId: String) {
        try {
            val stmt = connection?.prepareStatement(
                "UPDATE mobs SET death_count = death_count + 1, last_seen = ? WHERE id = ?"
            )
            stmt?.setLong(1, Instant.now().epochSecond)
            stmt?.setString(2, mobId)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error incrementing mob death count: ${e.message}" }
        }
    }

    // ============================================
    // Операции со спавнами мобов
    // ============================================

    fun saveMobSpawn(roomId: String, mobId: String, count: Int = 1) {
        try {
            val now = Instant.now().epochSecond
            val stmt = connection?.prepareStatement("""
                INSERT INTO mob_spawns (room_id, mob_id, spawn_count, last_seen)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(room_id, mob_id) DO UPDATE SET
                    spawn_count = spawn_count + excluded.spawn_count,
                    last_seen = excluded.last_seen
            """.trimIndent())
            stmt?.setString(1, roomId)
            stmt?.setString(2, mobId)
            stmt?.setInt(3, count)
            stmt?.setLong(4, now)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error saving mob spawn: ${e.message}" }
        }
    }

    fun getMobSpawnsInRoom(roomId: String): List<MobSpawn> {
        return try {
            val result = mutableListOf<MobSpawn>()
            val stmt = connection?.prepareStatement(
                "SELECT * FROM mob_spawns WHERE room_id = ?"
            )
            stmt?.setString(1, roomId)
            val rs = stmt?.executeQuery()
            while (rs?.next() == true) {
                result.add(MobSpawn(
                    roomId = rs.getString("room_id"),
                    mobId = rs.getString("mob_id"),
                    spawnCount = rs.getInt("spawn_count"),
                    lastSeen = rs.getLong("last_seen")
                ))
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error getting mob spawns: ${e.message}" }
            emptyList()
        }
    }

    // ============================================
    // Операции со статистикой зон
    // ============================================

    fun saveZoneStats(stats: ZoneStats) {
        try {
            val now = Instant.now().epochSecond
            val stmt = connection?.prepareStatement("""
                INSERT OR REPLACE INTO zone_stats
                (zone_id, level_min, level_max, avg_exp_per_hour, danger_level,
                 total_kills, total_deaths, total_exp_gained, total_gold_gained,
                 total_time_spent_ms, last_visited, notes, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            stmt?.setString(1, stats.zoneId)
            stmt?.setInt(2, stats.levelMin ?: 0)
            stmt?.setInt(3, stats.levelMax ?: 0)
            stmt?.setDouble(4, stats.avgExpPerHour ?: 0.0)
            stmt?.setDouble(5, stats.dangerLevel ?: 0.0)
            stmt?.setInt(6, stats.totalKills)
            stmt?.setInt(7, stats.totalDeaths)
            stmt?.setLong(8, stats.totalExpGained)
            stmt?.setLong(9, stats.totalGoldGained)
            stmt?.setLong(10, stats.totalTimeSpentMs)
            stmt?.setLong(11, stats.lastVisited ?: now)
            stmt?.setString(12, stats.notes)
            stmt?.setLong(13, now)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error saving zone stats: ${e.message}" }
        }
    }

    fun getZoneStats(zoneId: String): ZoneStats? {
        return try {
            val stmt = connection?.prepareStatement("SELECT * FROM zone_stats WHERE zone_id = ?")
            stmt?.setString(1, zoneId)
            val rs = stmt?.executeQuery()
            val stats = if (rs?.next() == true) {
                ZoneStats(
                    zoneId = rs.getString("zone_id"),
                    levelMin = rs.getInt("level_min").takeIf { it > 0 },
                    levelMax = rs.getInt("level_max").takeIf { it > 0 },
                    avgExpPerHour = rs.getDouble("avg_exp_per_hour").takeIf { it > 0 },
                    dangerLevel = rs.getDouble("danger_level").takeIf { it > 0 },
                    totalKills = rs.getInt("total_kills"),
                    totalDeaths = rs.getInt("total_deaths"),
                    totalExpGained = rs.getLong("total_exp_gained"),
                    totalGoldGained = rs.getLong("total_gold_gained"),
                    totalTimeSpentMs = rs.getLong("total_time_spent_ms"),
                    lastVisited = rs.getLong("last_visited").takeIf { it > 0 },
                    notes = rs.getString("notes") ?: ""
                )
            } else null
            rs?.close()
            stmt?.close()
            stats
        } catch (e: Exception) {
            logger.error { "Error getting zone stats: ${e.message}" }
            null
        }
    }

    fun getZonesForLevel(level: Int): List<ZoneStats> {
        return try {
            val result = mutableListOf<ZoneStats>()
            val stmt = connection?.prepareStatement(
                "SELECT * FROM zone_stats WHERE level_min <= ? AND level_max >= ? ORDER BY avg_exp_per_hour DESC"
            )
            stmt?.setInt(1, level)
            stmt?.setInt(2, level)
            val rs = stmt?.executeQuery()
            while (rs?.next() == true) {
                result.add(ZoneStats(
                    zoneId = rs.getString("zone_id"),
                    levelMin = rs.getInt("level_min").takeIf { it > 0 },
                    levelMax = rs.getInt("level_max").takeIf { it > 0 },
                    avgExpPerHour = rs.getDouble("avg_exp_per_hour").takeIf { it > 0 },
                    dangerLevel = rs.getDouble("danger_level").takeIf { it > 0 },
                    totalKills = rs.getInt("total_kills"),
                    totalDeaths = rs.getInt("total_deaths"),
                    totalExpGained = rs.getLong("total_exp_gained"),
                    totalGoldGained = rs.getLong("total_gold_gained"),
                    totalTimeSpentMs = rs.getLong("total_time_spent_ms"),
                    lastVisited = rs.getLong("last_visited").takeIf { it > 0 },
                    notes = rs.getString("notes") ?: ""
                ))
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error getting zones for level: ${e.message}" }
            emptyList()
        }
    }

    // ============================================
    // Операции с данными для обучения
    // ============================================

    fun saveTrainingData(data: TrainingData) {
        try {
            val stmt = connection?.prepareStatement("""
                INSERT INTO training_data
                (type, timestamp, state, action, reward, next_state, episode_id, success)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            stmt?.setString(1, data.type)
            stmt?.setLong(2, data.timestamp)
            stmt?.setString(3, data.state)
            stmt?.setString(4, data.action)
            stmt?.setDouble(5, data.reward ?: 0.0)
            stmt?.setString(6, data.nextState)
            stmt?.setString(7, data.episodeId)
            stmt?.setInt(8, if (data.success == true) 1 else if (data.success == false) 0 else -1)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error saving training data: ${e.message}" }
        }
    }

    fun getTrainingData(type: String, limit: Int = 1000): List<TrainingData> {
        return try {
            val result = mutableListOf<TrainingData>()
            val stmt = connection?.prepareStatement(
                "SELECT * FROM training_data WHERE type = ? ORDER BY timestamp DESC LIMIT ?"
            )
            stmt?.setString(1, type)
            stmt?.setInt(2, limit)
            val rs = stmt?.executeQuery()
            while (rs?.next() == true) {
                val successInt = rs.getInt("success")
                result.add(TrainingData(
                    id = rs.getLong("id"),
                    type = rs.getString("type"),
                    timestamp = rs.getLong("timestamp"),
                    state = rs.getString("state"),
                    action = rs.getString("action"),
                    reward = rs.getDouble("reward").takeIf { it != 0.0 },
                    nextState = rs.getString("next_state"),
                    episodeId = rs.getString("episode_id"),
                    success = if (successInt == -1) null else successInt == 1
                ))
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error getting training data: ${e.message}" }
            emptyList()
        }
    }

    // ============================================
    // Операции с логом боёв
    // ============================================

    fun saveCombatEvent(event: CombatLogEntry) {
        try {
            val stmt = connection?.prepareStatement("""
                INSERT INTO combat_log
                (session_id, timestamp, room_id, mob_id, mob_name, event_type,
                 damage_dealt, damage_received, skill_used, hp_before, hp_after,
                 mana_before, mana_after, outcome)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            stmt?.setString(1, event.sessionId)
            stmt?.setLong(2, event.timestamp)
            stmt?.setString(3, event.roomId)
            stmt?.setString(4, event.mobId)
            stmt?.setString(5, event.mobName)
            stmt?.setString(6, event.eventType.name)
            stmt?.setInt(7, event.damageDealt ?: 0)
            stmt?.setInt(8, event.damageReceived ?: 0)
            stmt?.setString(9, event.skillUsed)
            stmt?.setInt(10, event.hpBefore ?: 0)
            stmt?.setInt(11, event.hpAfter ?: 0)
            stmt?.setInt(12, event.manaBefore ?: 0)
            stmt?.setInt(13, event.manaAfter ?: 0)
            stmt?.setString(14, event.outcome)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error saving combat event: ${e.message}" }
        }
    }

    // ============================================
    // Операции с сессиями бота
    // ============================================

    fun startBotSession(sessionId: String, mode: BotMode, zoneId: String? = null): BotSession {
        val session = BotSession(
            id = sessionId,
            startTime = Instant.now().epochSecond,
            mode = mode,
            zoneId = zoneId
        )
        try {
            val stmt = connection?.prepareStatement("""
                INSERT INTO bot_sessions
                (id, start_time, mode, zone_id)
                VALUES (?, ?, ?, ?)
            """.trimIndent())
            stmt?.setString(1, session.id)
            stmt?.setLong(2, session.startTime)
            stmt?.setString(3, session.mode.name)
            stmt?.setString(4, session.zoneId)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error starting bot session: ${e.message}" }
        }
        return session
    }

    fun endBotSession(session: BotSession) {
        try {
            val stmt = connection?.prepareStatement("""
                UPDATE bot_sessions SET
                    end_time = ?,
                    total_kills = ?,
                    total_deaths = ?,
                    total_exp_gained = ?,
                    total_gold_gained = ?,
                    total_items_looted = ?,
                    total_rooms_explored = ?,
                    notes = ?
                WHERE id = ?
            """.trimIndent())
            stmt?.setLong(1, Instant.now().epochSecond)
            stmt?.setInt(2, session.totalKills)
            stmt?.setInt(3, session.totalDeaths)
            stmt?.setLong(4, session.totalExpGained)
            stmt?.setLong(5, session.totalGoldGained)
            stmt?.setInt(6, session.totalItemsLooted)
            stmt?.setInt(7, session.totalRoomsExplored)
            stmt?.setString(8, session.notes)
            stmt?.setString(9, session.id)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error ending bot session: ${e.message}" }
        }
    }

    // ============================================
    // Операции с предметами
    // ============================================

    fun saveItem(item: ItemData) {
        try {
            val now = Instant.now().epochSecond
            val stmt = connection?.prepareStatement("""
                INSERT OR REPLACE INTO items
                (id, name, short_name, type, level, value, weight, stats,
                 dropped_by, drop_count, last_seen, notes, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            stmt?.setString(1, item.id)
            stmt?.setString(2, item.name)
            stmt?.setString(3, item.shortName)
            stmt?.setString(4, item.type)
            stmt?.setInt(5, item.level ?: 0)
            stmt?.setInt(6, item.value ?: 0)
            stmt?.setDouble(7, item.weight ?: 0.0)
            stmt?.setString(8, item.stats)
            stmt?.setString(9, item.droppedBy)
            stmt?.setInt(10, item.dropCount)
            stmt?.setLong(11, item.lastSeen ?: now)
            stmt?.setString(12, item.notes)
            stmt?.setLong(13, now)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error saving item: ${e.message}" }
        }
    }

    fun getItem(itemId: String): ItemData? {
        return try {
            val stmt = connection?.prepareStatement("SELECT * FROM items WHERE id = ?")
            stmt?.setString(1, itemId)
            val rs = stmt?.executeQuery()
            val item = if (rs?.next() == true) {
                ItemData(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    shortName = rs.getString("short_name"),
                    type = rs.getString("type"),
                    level = rs.getInt("level").takeIf { it > 0 },
                    value = rs.getInt("value").takeIf { it > 0 },
                    weight = rs.getDouble("weight").takeIf { it > 0 },
                    stats = rs.getString("stats"),
                    droppedBy = rs.getString("dropped_by"),
                    dropCount = rs.getInt("drop_count"),
                    lastSeen = rs.getLong("last_seen").takeIf { it > 0 },
                    notes = rs.getString("notes") ?: ""
                )
            } else null
            rs?.close()
            stmt?.close()
            item
        } catch (e: Exception) {
            logger.error { "Error getting item: ${e.message}" }
            null
        }
    }

    fun close() {
        try {
            connection?.close()
            logger.info { "Bot database connection closed" }
        } catch (e: Exception) {
            logger.error { "Error closing bot database: ${e.message}" }
        }
    }
}

// ============================================
// Модели данных
// ============================================

data class MobData(
    val id: String,
    val name: String,
    val shortName: String? = null,
    val level: Int? = null,
    val avgHp: Int? = null,
    val expReward: Int? = null,
    val goldReward: Int? = null,
    val zoneId: String? = null,
    val aggressive: Boolean = false,
    val killCount: Int = 0,
    val deathCount: Int = 0,
    val avgFightDurationMs: Long? = null,
    val lastSeen: Long? = null,
    val notes: String = ""
)

data class MobSpawn(
    val roomId: String,
    val mobId: String,
    val spawnCount: Int = 1,
    val lastSeen: Long? = null
)

data class ZoneStats(
    val zoneId: String,
    val levelMin: Int? = null,
    val levelMax: Int? = null,
    val avgExpPerHour: Double? = null,
    val dangerLevel: Double? = null,
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val totalExpGained: Long = 0,
    val totalGoldGained: Long = 0,
    val totalTimeSpentMs: Long = 0,
    val lastVisited: Long? = null,
    val notes: String = ""
)

data class TrainingData(
    val id: Long? = null,
    val type: String,
    val timestamp: Long,
    val state: String,
    val action: String,
    val reward: Double? = null,
    val nextState: String? = null,
    val episodeId: String? = null,
    val success: Boolean? = null
)

data class CombatLogEntry(
    val sessionId: String?,
    val timestamp: Long,
    val roomId: String?,
    val mobId: String?,
    val mobName: String?,
    val eventType: CombatEventType,
    val damageDealt: Int? = null,
    val damageReceived: Int? = null,
    val skillUsed: String? = null,
    val hpBefore: Int? = null,
    val hpAfter: Int? = null,
    val manaBefore: Int? = null,
    val manaAfter: Int? = null,
    val outcome: String? = null
)

enum class CombatEventType {
    COMBAT_START,
    COMBAT_END,
    DAMAGE_DEALT,
    DAMAGE_RECEIVED,
    SKILL_USED,
    HEAL_RECEIVED,
    BUFF_APPLIED,
    DEBUFF_APPLIED,
    MOB_KILLED,
    PLAYER_DEATH,
    FLEE_SUCCESS,
    FLEE_FAILED
}

data class BotSession(
    val id: String,
    val startTime: Long,
    var endTime: Long? = null,
    val mode: BotMode,
    val zoneId: String? = null,
    var totalKills: Int = 0,
    var totalDeaths: Int = 0,
    var totalExpGained: Long = 0,
    var totalGoldGained: Long = 0,
    var totalItemsLooted: Int = 0,
    var totalRoomsExplored: Int = 0,
    var notes: String = ""
)

enum class BotMode {
    LEVELING,    // Фарм мобов для опыта
    FARMING,     // Фарм предметов
    GATHERING,   // Сбор ресурсов
    TRADING,     // Торговля
    EXPLORING,   // Исследование новых зон
    IDLE         // Ожидание
}

data class ItemData(
    val id: String,
    val name: String,
    val shortName: String? = null,
    val type: String? = null,
    val level: Int? = null,
    val value: Int? = null,
    val weight: Double? = null,
    val stats: String? = null,
    val droppedBy: String? = null,
    val dropCount: Int = 0,
    val lastSeen: Long? = null,
    val notes: String = ""
)
