package com.bylins.client.scripting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * Universal key-value storage for scripts.
 * Stores arbitrary JSON data per script or globally.
 */
private val logger = KotlinLogging.logger("ScriptStorage")

class ScriptStorage {
    private var connection: Connection? = null
    private val dbPath: String
    private val objectMapper = ObjectMapper().registerKotlinModule()

    init {
        // Create directory for DB if doesn't exist
        val dataDir = Paths.get(System.getProperty("user.home"), ".bylins-client", "data")
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir)
        }

        dbPath = dataDir.resolve("script_storage.db").toString()
        connect()
        createTables()
    }

    private fun connect() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            logger.info { "Connected to script storage database: $dbPath" }
        } catch (e: Exception) {
            logger.error { "Error connecting to script storage database: ${e.message}" }
            e.printStackTrace()
        }
    }

    private fun createTables() {
        try {
            val statement = connection?.createStatement()

            // Universal key-value storage table
            statement?.execute("""
                CREATE TABLE IF NOT EXISTS script_storage (
                    script_id TEXT NOT NULL,
                    key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (script_id, key)
                )
            """.trimIndent())

            // Index for key search
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_script_storage_key ON script_storage(key)")
            statement?.execute("CREATE INDEX IF NOT EXISTS idx_script_storage_script ON script_storage(script_id)")

            statement?.close()
            logger.info { "Script storage tables created/verified successfully" }
        } catch (e: Exception) {
            logger.error { "Error creating script storage tables: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Save data with a key.
     * @param scriptId ID of the script (or "global" for shared data)
     * @param key Data key
     * @param value Any value that can be serialized to JSON
     * @return true if successful
     */
    fun setData(scriptId: String, key: String, value: Any): Boolean {
        return try {
            val now = Instant.now().epochSecond
            val jsonValue = objectMapper.writeValueAsString(value)

            val stmt = connection?.prepareStatement("""
                INSERT INTO script_storage (script_id, key, value, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(script_id, key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = excluded.updated_at
            """.trimIndent())
            stmt?.setString(1, scriptId)
            stmt?.setString(2, key)
            stmt?.setString(3, jsonValue)
            stmt?.setLong(4, now)
            stmt?.setLong(5, now)
            val result = stmt?.executeUpdate() ?: 0
            stmt?.close()

            logger.debug { "setData: [$scriptId] $key = $jsonValue" }
            result > 0
        } catch (e: Exception) {
            logger.error { "Error setting data [$scriptId].$key: ${e.message}" }
            false
        }
    }

    /**
     * Get data by key.
     * @param scriptId ID of the script (or "global" for shared data)
     * @param key Data key
     * @return Value or null if not found
     */
    fun getData(scriptId: String, key: String): Any? {
        return try {
            val stmt = connection?.prepareStatement(
                "SELECT value FROM script_storage WHERE script_id = ? AND key = ?"
            )
            stmt?.setString(1, scriptId)
            stmt?.setString(2, key)
            val rs = stmt?.executeQuery()
            val value = if (rs?.next() == true) {
                val jsonValue = rs.getString("value")
                deserializeJson(jsonValue)
            } else null
            rs?.close()
            stmt?.close()

            logger.debug { "getData: [$scriptId] $key = $value" }
            value
        } catch (e: Exception) {
            logger.error { "Error getting data [$scriptId].$key: ${e.message}" }
            null
        }
    }

    /**
     * Delete data by key.
     * @param scriptId ID of the script (or "global" for shared data)
     * @param key Data key
     * @return true if deleted
     */
    fun deleteData(scriptId: String, key: String): Boolean {
        return try {
            val stmt = connection?.prepareStatement(
                "DELETE FROM script_storage WHERE script_id = ? AND key = ?"
            )
            stmt?.setString(1, scriptId)
            stmt?.setString(2, key)
            val result = stmt?.executeUpdate() ?: 0
            stmt?.close()

            logger.debug { "deleteData: [$scriptId] $key -> deleted=$result" }
            result > 0
        } catch (e: Exception) {
            logger.error { "Error deleting data [$scriptId].$key: ${e.message}" }
            false
        }
    }

    /**
     * List all keys for a script.
     * @param scriptId ID of the script (or "global" for shared data)
     * @param prefix Optional prefix to filter keys
     * @return List of keys
     */
    fun listDataKeys(scriptId: String, prefix: String? = null): List<String> {
        return try {
            val keys = mutableListOf<String>()
            val sql = if (prefix != null) {
                "SELECT key FROM script_storage WHERE script_id = ? AND key LIKE ?"
            } else {
                "SELECT key FROM script_storage WHERE script_id = ?"
            }
            val stmt = connection?.prepareStatement(sql)
            stmt?.setString(1, scriptId)
            if (prefix != null) {
                stmt?.setString(2, "$prefix%")
            }
            val rs = stmt?.executeQuery()
            while (rs?.next() == true) {
                keys.add(rs.getString("key"))
            }
            rs?.close()
            stmt?.close()

            logger.debug { "listDataKeys: [$scriptId] prefix=$prefix -> ${keys.size} keys" }
            keys
        } catch (e: Exception) {
            logger.error { "Error listing data keys [$scriptId]: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Get all data for a script.
     * @param scriptId ID of the script (or "global" for shared data)
     * @return Map of key -> value
     */
    fun getAllData(scriptId: String): Map<String, Any?> {
        return try {
            val result = mutableMapOf<String, Any?>()
            val stmt = connection?.prepareStatement(
                "SELECT key, value FROM script_storage WHERE script_id = ?"
            )
            stmt?.setString(1, scriptId)
            val rs = stmt?.executeQuery()
            while (rs?.next() == true) {
                val key = rs.getString("key")
                val jsonValue = rs.getString("value")
                result[key] = deserializeJson(jsonValue)
            }
            rs?.close()
            stmt?.close()

            logger.debug { "getAllData: [$scriptId] -> ${result.size} entries" }
            result
        } catch (e: Exception) {
            logger.error { "Error getting all data [$scriptId]: ${e.message}" }
            emptyMap()
        }
    }

    /**
     * Clear all data for a script.
     * @param scriptId ID of the script (or "global" for shared data)
     * @return Number of deleted entries
     */
    fun clearData(scriptId: String): Int {
        return try {
            val stmt = connection?.prepareStatement(
                "DELETE FROM script_storage WHERE script_id = ?"
            )
            stmt?.setString(1, scriptId)
            val result = stmt?.executeUpdate() ?: 0
            stmt?.close()

            logger.info { "clearData: [$scriptId] -> deleted $result entries" }
            result
        } catch (e: Exception) {
            logger.error { "Error clearing data [$scriptId]: ${e.message}" }
            0
        }
    }

    /**
     * Deserialize JSON string to appropriate type.
     */
    private fun deserializeJson(json: String): Any? {
        return try {
            objectMapper.readValue(json, Any::class.java)
        } catch (e: Exception) {
            // If deserialization fails, return as string
            json
        }
    }

    /**
     * Close database connection.
     */
    fun close() {
        try {
            connection?.close()
            logger.info { "Script storage database connection closed" }
        } catch (e: Exception) {
            logger.error { "Error closing script storage database: ${e.message}" }
        }
    }
}
