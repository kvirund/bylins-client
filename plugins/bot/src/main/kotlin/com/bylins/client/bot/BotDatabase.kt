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

            // Таблица выученных боевых паттернов
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS combat_patterns (
                    message TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    confidence REAL NOT NULL DEFAULT 1.0,
                    hit_count INTEGER DEFAULT 1,
                    last_seen INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Таблица эмпирической статистики ударов (для обучения силы удара по exp)
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS hit_exp_stats (
                    hit_text TEXT PRIMARY KEY,
                    total_exp INTEGER NOT NULL DEFAULT 0,
                    sample_count INTEGER NOT NULL DEFAULT 0,
                    min_exp INTEGER,
                    max_exp INTEGER,
                    avg_exp REAL NOT NULL DEFAULT 0.0,
                    last_updated INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Combat profiles table for detailed combat recording
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS combat_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER,
                    zone_id TEXT,
                    room_id TEXT,
                    kills_count INTEGER DEFAULT 0,
                    mobs_killed TEXT,
                    hp_before INTEGER,
                    hp_after INTEGER,
                    move_before INTEGER,
                    move_after INTEGER,
                    exp_gained INTEGER DEFAULT 0,
                    gold_gained INTEGER DEFAULT 0,
                    result TEXT,
                    duration_ms INTEGER,
                    raw_data TEXT
                )
            """.trimIndent())

            // Индексы
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_profiles_zone ON combat_profiles(zone_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_profiles_time ON combat_profiles(started_at)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_profiles_kills ON combat_profiles(kills_count)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_mobs_zone ON mobs(zone_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_mobs_level ON mobs(level)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_mob_spawns_room ON mob_spawns(room_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_training_type ON training_data(type)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_training_episode ON training_data(episode_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_session ON combat_log(session_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_mob ON combat_log(mob_id)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_items_type ON items(type)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_items_dropped_by ON items(dropped_by)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_combat_patterns_type ON combat_patterns(type)")

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
    // Операции с выученными боевыми паттернами
    // ============================================

    /**
     * Получить все выученные боевые паттерны
     * @return Map<message, type>
     */
    fun getLearnedCombatPatterns(): Map<String, String> {
        return try {
            val result = mutableMapOf<String, String>()
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery("SELECT message, type FROM combat_patterns")
            while (rs?.next() == true) {
                result[rs.getString("message")] = rs.getString("type")
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error getting learned combat patterns: ${e.message}" }
            emptyMap()
        }
    }

    /**
     * Сохранить выученный боевой паттерн
     */
    fun saveCombatPattern(message: String, type: String, confidence: Double) {
        try {
            val now = Instant.now().epochSecond
            val stmt = connection?.prepareStatement("""
                INSERT INTO combat_patterns (message, type, confidence, hit_count, last_seen, created_at)
                VALUES (?, ?, ?, 1, ?, ?)
                ON CONFLICT(message) DO UPDATE SET
                    type = excluded.type,
                    confidence = excluded.confidence,
                    hit_count = hit_count + 1,
                    last_seen = excluded.last_seen
            """.trimIndent())
            stmt?.setString(1, message)
            stmt?.setString(2, type)
            stmt?.setDouble(3, confidence)
            stmt?.setLong(4, now)
            stmt?.setLong(5, now)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error saving combat pattern: ${e.message}" }
        }
    }

    /**
     * Удалить боевой паттерн (помечен как ошибочный)
     */
    fun removeCombatPattern(message: String) {
        try {
            val stmt = connection?.prepareStatement("DELETE FROM combat_patterns WHERE message = ?")
            stmt?.setString(1, message)
            stmt?.executeUpdate()
            stmt?.close()
        } catch (e: Exception) {
            logger.error { "Error removing combat pattern: ${e.message}" }
        }
    }

    // ============================================
    // Операции со статистикой ударов (эмпирическое обучение)
    // ============================================

    /**
     * Записать статистику урона по тексту удара
     * @param hitText текст сообщения об ударе (например, "Вы легонько ударили волка.")
     * @param expGained опыт, полученный за этот удар
     */
    fun recordHitExp(hitText: String, expGained: Int) {
        if (expGained <= 0) return

        try {
            val now = Instant.now().epochSecond
            val stmt = connection?.prepareStatement("""
                INSERT INTO hit_exp_stats (hit_text, total_exp, sample_count, min_exp, max_exp, avg_exp, last_updated, created_at)
                VALUES (?, ?, 1, ?, ?, ?, ?, ?)
                ON CONFLICT(hit_text) DO UPDATE SET
                    total_exp = total_exp + excluded.total_exp,
                    sample_count = sample_count + 1,
                    min_exp = MIN(min_exp, excluded.min_exp),
                    max_exp = MAX(max_exp, excluded.max_exp),
                    avg_exp = (total_exp + excluded.total_exp) * 1.0 / (sample_count + 1),
                    last_updated = excluded.last_updated
            """.trimIndent())
            stmt?.setString(1, hitText)
            stmt?.setInt(2, expGained)
            stmt?.setInt(3, expGained)
            stmt?.setInt(4, expGained)
            stmt?.setDouble(5, expGained.toDouble())
            stmt?.setLong(6, now)
            stmt?.setLong(7, now)
            stmt?.executeUpdate()
            stmt?.close()
            logger.debug { "Recorded hit exp: '$hitText' -> $expGained exp" }
        } catch (e: Exception) {
            logger.error { "Error recording hit exp: ${e.message}" }
        }
    }

    /**
     * Получить среднее значение опыта для текста удара
     * @return средний опыт или null если нет данных
     */
    fun getAvgExpForHit(hitText: String): Double? {
        return try {
            val stmt = connection?.prepareStatement(
                "SELECT avg_exp FROM hit_exp_stats WHERE hit_text = ?"
            )
            stmt?.setString(1, hitText)
            val rs = stmt?.executeQuery()
            val avgExp = if (rs?.next() == true) rs.getDouble("avg_exp") else null
            rs?.close()
            stmt?.close()
            avgExp
        } catch (e: Exception) {
            logger.error { "Error getting avg exp for hit: ${e.message}" }
            null
        }
    }

    /**
     * Получить полную статистику по тексту удара
     */
    fun getHitExpStats(hitText: String): HitExpStats? {
        return try {
            val stmt = connection?.prepareStatement(
                "SELECT * FROM hit_exp_stats WHERE hit_text = ?"
            )
            stmt?.setString(1, hitText)
            val rs = stmt?.executeQuery()
            val stats = if (rs?.next() == true) {
                HitExpStats(
                    hitText = rs.getString("hit_text"),
                    totalExp = rs.getLong("total_exp"),
                    sampleCount = rs.getInt("sample_count"),
                    minExp = rs.getInt("min_exp"),
                    maxExp = rs.getInt("max_exp"),
                    avgExp = rs.getDouble("avg_exp"),
                    lastUpdated = rs.getLong("last_updated")
                )
            } else null
            rs?.close()
            stmt?.close()
            stats
        } catch (e: Exception) {
            logger.error { "Error getting hit exp stats: ${e.message}" }
            null
        }
    }

    /**
     * Получить всю статистику ударов (для аналитики и отображения)
     */
    fun getAllHitExpStats(): List<HitExpStats> {
        return try {
            val result = mutableListOf<HitExpStats>()
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery("SELECT * FROM hit_exp_stats ORDER BY avg_exp DESC")
            while (rs?.next() == true) {
                result.add(HitExpStats(
                    hitText = rs.getString("hit_text"),
                    totalExp = rs.getLong("total_exp"),
                    sampleCount = rs.getInt("sample_count"),
                    minExp = rs.getInt("min_exp"),
                    maxExp = rs.getInt("max_exp"),
                    avgExp = rs.getDouble("avg_exp"),
                    lastUpdated = rs.getLong("last_updated")
                ))
            }
            rs?.close()
            stmt?.close()
            result
        } catch (e: Exception) {
            logger.error { "Error getting all hit exp stats: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Получить статистику по категориям качества ударов
     * Возвращает отсортированный список уникальных "качеств" удара с их средним exp
     */
    fun getHitQualityStats(): List<HitQualityStat> {
        return try {
            // Извлекаем качество удара из текста (слово перед "ударили" или аналогичным)
            val allStats = getAllHitExpStats()
            val qualityMap = mutableMapOf<String, MutableList<HitExpStats>>()

            for (stat in allStats) {
                val quality = extractHitQuality(stat.hitText)
                qualityMap.getOrPut(quality) { mutableListOf() }.add(stat)
            }

            qualityMap.map { (quality, stats) ->
                HitQualityStat(
                    quality = quality,
                    avgExp = stats.map { it.avgExp }.average(),
                    totalSamples = stats.sumOf { it.sampleCount },
                    hitCount = stats.size
                )
            }.sortedBy { it.avgExp }
        } catch (e: Exception) {
            logger.error { "Error getting hit quality stats: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Извлечь качество удара из текста сообщения
     */
    private fun extractHitQuality(hitText: String): String {
        val text = hitText.lowercase()
        return when {
            text.contains("прекрасн") -> "прекрасный"
            text.contains("меткое попадание") -> "меткое"
            text.contains("великолепн") -> "великолепный"
            text.contains("очень хорошо") -> "очень хорошо"
            text.contains("хорошо") -> "хорошо"
            text.contains("сильно") -> "сильно"
            text.contains("слегка") -> "слегка"
            text.contains("легонько") -> "легонько"
            text.contains("оцарапал") -> "оцарапал"
            else -> "прочее"
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

    // ============================================
    // Combat profile operations
    // ============================================

    /**
     * Create a new combat profile (at combat start)
     * @return ID of the created profile
     */
    fun createCombatProfile(profile: CombatProfile): Long {
        return try {
            val stmt = connection?.prepareStatement("""
                INSERT INTO combat_profiles
                (started_at, zone_id, room_id, hp_before, move_before, mobs_killed, raw_data)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(), java.sql.Statement.RETURN_GENERATED_KEYS)
            stmt?.setLong(1, profile.startedAt)
            stmt?.setString(2, profile.zoneId)
            stmt?.setString(3, profile.roomId)
            stmt?.setInt(4, profile.hpBefore ?: 0)
            stmt?.setInt(5, profile.moveBefore ?: 0)
            stmt?.setString(6, "[]") // Empty mobs_killed list
            stmt?.setString(7, profile.rawData ?: "{}")
            stmt?.executeUpdate()

            val generatedKeys = stmt?.generatedKeys
            val id = if (generatedKeys?.next() == true) generatedKeys.getLong(1) else -1L
            generatedKeys?.close()
            stmt?.close()

            logger.debug { "Created combat profile #$id" }
            id
        } catch (e: Exception) {
            logger.error { "Error creating combat profile: ${e.message}" }
            -1L
        }
    }

    /**
     * Update combat profile (at combat end)
     */
    fun updateCombatProfile(profile: CombatProfile) {
        try {
            val stmt = connection?.prepareStatement("""
                UPDATE combat_profiles SET
                    ended_at = ?,
                    kills_count = ?,
                    mobs_killed = ?,
                    hp_after = ?,
                    move_after = ?,
                    exp_gained = ?,
                    gold_gained = ?,
                    result = ?,
                    duration_ms = ?,
                    raw_data = ?
                WHERE id = ?
            """.trimIndent())
            stmt?.setLong(1, profile.endedAt ?: Instant.now().epochSecond)
            stmt?.setInt(2, profile.killsCount)
            stmt?.setString(3, profile.mobsKilled)
            stmt?.setInt(4, profile.hpAfter ?: 0)
            stmt?.setInt(5, profile.moveAfter ?: 0)
            stmt?.setLong(6, profile.expGained)
            stmt?.setLong(7, profile.goldGained)
            stmt?.setString(8, profile.result)
            stmt?.setLong(9, profile.durationMs ?: 0)
            stmt?.setString(10, profile.rawData)
            stmt?.setLong(11, profile.id ?: return)
            stmt?.executeUpdate()
            stmt?.close()

            logger.debug { "Updated combat profile #${profile.id}" }
        } catch (e: Exception) {
            logger.error { "Error updating combat profile: ${e.message}" }
        }
    }

    /**
     * Get combat profile by ID
     */
    fun getCombatProfile(id: Long): CombatProfile? {
        return try {
            val stmt = connection?.prepareStatement("SELECT * FROM combat_profiles WHERE id = ?")
            stmt?.setLong(1, id)
            val rs = stmt?.executeQuery()
            val profile = if (rs?.next() == true) {
                CombatProfile(
                    id = rs.getLong("id"),
                    startedAt = rs.getLong("started_at"),
                    endedAt = rs.getLong("ended_at").takeIf { it > 0 },
                    zoneId = rs.getString("zone_id"),
                    roomId = rs.getString("room_id"),
                    killsCount = rs.getInt("kills_count"),
                    mobsKilled = rs.getString("mobs_killed"),
                    hpBefore = rs.getInt("hp_before").takeIf { it > 0 },
                    hpAfter = rs.getInt("hp_after").takeIf { it > 0 },
                    moveBefore = rs.getInt("move_before").takeIf { it > 0 },
                    moveAfter = rs.getInt("move_after").takeIf { it > 0 },
                    expGained = rs.getLong("exp_gained"),
                    goldGained = rs.getLong("gold_gained"),
                    result = rs.getString("result"),
                    durationMs = rs.getLong("duration_ms").takeIf { it > 0 },
                    rawData = rs.getString("raw_data")
                )
            } else null
            rs?.close()
            stmt?.close()
            profile
        } catch (e: Exception) {
            logger.error { "Error getting combat profile: ${e.message}" }
            null
        }
    }

    /**
     * Get combat profiles with optional filtering
     * @param limit Maximum number of profiles to return
     * @param zoneId Filter by zone (optional)
     * @param minKills Filter by minimum kills count (optional)
     * @param result Filter by result (optional): 'win', 'flee', 'death'
     * @param fromTime Filter by start time (optional)
     * @param toTime Filter by end time (optional)
     */
    fun getCombatProfiles(
        limit: Int = 100,
        zoneId: String? = null,
        minKills: Int? = null,
        result: String? = null,
        fromTime: Long? = null,
        toTime: Long? = null
    ): List<CombatProfile> {
        return try {
            val conditions = mutableListOf<String>()
            if (zoneId != null) conditions.add("zone_id = ?")
            if (minKills != null) conditions.add("kills_count >= ?")
            if (result != null) conditions.add("result = ?")
            if (fromTime != null) conditions.add("started_at >= ?")
            if (toTime != null) conditions.add("started_at <= ?")

            val whereClause = if (conditions.isNotEmpty()) "WHERE ${conditions.joinToString(" AND ")}" else ""
            val sql = "SELECT * FROM combat_profiles $whereClause ORDER BY started_at DESC LIMIT ?"

            val stmt = connection?.prepareStatement(sql)
            var paramIndex = 1
            if (zoneId != null) stmt?.setString(paramIndex++, zoneId)
            if (minKills != null) stmt?.setInt(paramIndex++, minKills)
            if (result != null) stmt?.setString(paramIndex++, result)
            if (fromTime != null) stmt?.setLong(paramIndex++, fromTime)
            if (toTime != null) stmt?.setLong(paramIndex++, toTime)
            stmt?.setInt(paramIndex, limit)

            val rs = stmt?.executeQuery()
            val profiles = mutableListOf<CombatProfile>()
            while (rs?.next() == true) {
                profiles.add(CombatProfile(
                    id = rs.getLong("id"),
                    startedAt = rs.getLong("started_at"),
                    endedAt = rs.getLong("ended_at").takeIf { it > 0 },
                    zoneId = rs.getString("zone_id"),
                    roomId = rs.getString("room_id"),
                    killsCount = rs.getInt("kills_count"),
                    mobsKilled = rs.getString("mobs_killed"),
                    hpBefore = rs.getInt("hp_before").takeIf { it > 0 },
                    hpAfter = rs.getInt("hp_after").takeIf { it > 0 },
                    moveBefore = rs.getInt("move_before").takeIf { it > 0 },
                    moveAfter = rs.getInt("move_after").takeIf { it > 0 },
                    expGained = rs.getLong("exp_gained"),
                    goldGained = rs.getLong("gold_gained"),
                    result = rs.getString("result"),
                    durationMs = rs.getLong("duration_ms").takeIf { it > 0 },
                    rawData = rs.getString("raw_data")
                ))
            }
            rs?.close()
            stmt?.close()

            logger.debug { "Retrieved ${profiles.size} combat profiles" }
            profiles
        } catch (e: Exception) {
            logger.error { "Error getting combat profiles: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Get combat statistics summary
     */
    fun getCombatStats(): CombatStatsSummary {
        return try {
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery("""
                SELECT
                    COUNT(*) as total_fights,
                    SUM(kills_count) as total_kills,
                    SUM(exp_gained) as total_exp,
                    SUM(gold_gained) as total_gold,
                    SUM(CASE WHEN result = 'win' THEN 1 ELSE 0 END) as wins,
                    SUM(CASE WHEN result = 'flee' THEN 1 ELSE 0 END) as flees,
                    SUM(CASE WHEN result = 'death' THEN 1 ELSE 0 END) as deaths,
                    AVG(duration_ms) as avg_duration
                FROM combat_profiles
            """.trimIndent())

            val summary = if (rs?.next() == true) {
                CombatStatsSummary(
                    totalFights = rs.getInt("total_fights"),
                    totalKills = rs.getInt("total_kills"),
                    totalExp = rs.getLong("total_exp"),
                    totalGold = rs.getLong("total_gold"),
                    wins = rs.getInt("wins"),
                    flees = rs.getInt("flees"),
                    deaths = rs.getInt("deaths"),
                    avgDurationMs = rs.getLong("avg_duration")
                )
            } else {
                CombatStatsSummary()
            }
            rs?.close()
            stmt?.close()

            summary
        } catch (e: Exception) {
            logger.error { "Error getting combat stats: ${e.message}" }
            CombatStatsSummary()
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

/**
 * Статистика опыта за конкретный текст удара
 */
data class HitExpStats(
    val hitText: String,
    val totalExp: Long,
    val sampleCount: Int,
    val minExp: Int,
    val maxExp: Int,
    val avgExp: Double,
    val lastUpdated: Long
)

/**
 * Агрегированная статистика по качеству ударов
 */
data class HitQualityStat(
    val quality: String,
    val avgExp: Double,
    val totalSamples: Int,
    val hitCount: Int
)

/**
 * Combat profile - detailed record of a single combat (may include multiple mobs)
 */
data class CombatProfile(
    val id: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val zoneId: String? = null,
    val roomId: String? = null,
    val killsCount: Int = 0,
    val mobsKilled: String? = null, // JSON array: ["mob1", "mob2"]
    val hpBefore: Int? = null,
    val hpAfter: Int? = null,
    val moveBefore: Int? = null,
    val moveAfter: Int? = null,
    val expGained: Long = 0,
    val goldGained: Long = 0,
    val result: String? = null, // 'win', 'flee', 'death'
    val durationMs: Long? = null,
    val rawData: String? = null // JSON with detailed combat data
)

/**
 * Combat statistics summary
 */
data class CombatStatsSummary(
    val totalFights: Int = 0,
    val totalKills: Int = 0,
    val totalExp: Long = 0,
    val totalGold: Long = 0,
    val wins: Int = 0,
    val flees: Int = 0,
    val deaths: Int = 0,
    val avgDurationMs: Long = 0
)
