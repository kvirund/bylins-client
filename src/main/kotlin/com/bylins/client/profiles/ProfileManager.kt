package com.bylins.client.profiles

import com.bylins.client.aliases.Alias
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.scripting.ScriptManager
import com.bylins.client.triggers.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger("ProfileManager")

/**
 * Менеджер профилей персонажей.
 * Управляет загрузкой, сохранением и стеком активных профилей.
 */
class ProfileManager(
    private val configDir: Path,
    private val scriptManager: ScriptManager
) {
    private val profilesDir = configDir.resolve("profiles").toFile()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Все доступные профили
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles

    // Активный стек профилей (порядок важен!)
    private val _activeStack = MutableStateFlow<List<String>>(emptyList())
    val activeStack: StateFlow<List<String>> = _activeStack

    init {
        // Создаем директорию для профилей если её нет
        if (!profilesDir.exists()) {
            profilesDir.mkdirs()
            logger.info { "Created profiles directory: ${profilesDir.absolutePath}" }
        }
    }

    /**
     * Загружает все профили из папок profiles/
     */
    fun loadProfiles() {
        val loadedProfiles = mutableListOf<Profile>()

        if (!profilesDir.exists() || !profilesDir.isDirectory) {
            logger.info { "Profiles directory does not exist or is not a directory" }
            _profiles.value = emptyList()
            return
        }

        val profileDirs = profilesDir.listFiles { file -> file.isDirectory } ?: emptyArray()

        for (dir in profileDirs) {
            val profileFile = File(dir, "profile.json")
            if (!profileFile.exists()) {
                logger.warn { "Profile directory ${dir.name} has no profile.json, skipping" }
                continue
            }

            try {
                val content = profileFile.readText(Charsets.UTF_8)
                val dto = json.decodeFromString<ProfileDto>(content)

                // Определяем папку скриптов профиля
                val scriptsDir = File(dir, "scripts")
                val scriptsDirPath = if (scriptsDir.exists() && scriptsDir.isDirectory) {
                    scriptsDir.toPath()
                } else {
                    null
                }

                val profile = dto.toProfile(scriptsDirPath)
                loadedProfiles.add(profile)
                logger.info { "Loaded profile: ${profile.name} (${profile.id})" }
            } catch (e: Exception) {
                logger.error(e) { "Error loading profile from ${dir.name}: ${e.message}" }
            }
        }

        _profiles.value = loadedProfiles
        logger.info { "Loaded ${loadedProfiles.size} profiles" }
    }

    /**
     * Сохраняет профиль на диск
     */
    fun saveProfile(profile: Profile) {
        val profileDir = File(profilesDir, profile.id)
        if (!profileDir.exists()) {
            profileDir.mkdirs()
        }

        val profileFile = File(profileDir, "profile.json")
        val dto = ProfileDto.fromProfile(profile.copy(updatedAt = Instant.now()))

        try {
            profileFile.writeText(json.encodeToString(ProfileDto.serializer(), dto), Charsets.UTF_8)

            // Обновляем профиль в списке
            _profiles.value = _profiles.value.map {
                if (it.id == profile.id) profile.copy(updatedAt = Instant.now())
                else it
            }

            logger.info { "Saved profile: ${profile.name} (${profile.id})" }
        } catch (e: Exception) {
            logger.error(e) { "Error saving profile ${profile.id}: ${e.message}" }
            throw e
        }
    }

    /**
     * Создает новый профиль
     */
    fun createProfile(name: String, description: String = ""): Profile {
        val id = generateProfileId(name)
        val profile = Profile(
            id = id,
            name = name,
            description = description,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // Создаем папку профиля
        val profileDir = File(profilesDir, id)
        profileDir.mkdirs()

        // Создаем папку scripts
        File(profileDir, "scripts").mkdirs()

        // Сохраняем profile.json
        saveProfile(profile.copy(scriptsDir = File(profileDir, "scripts").toPath()))

        // Добавляем в список
        val newProfile = profile.copy(scriptsDir = File(profileDir, "scripts").toPath())
        _profiles.value = _profiles.value + newProfile

        logger.info { "Created profile: ${profile.name} (${profile.id})" }
        return newProfile
    }

    /**
     * Удаляет профиль
     */
    fun deleteProfile(id: String) {
        // Сначала удаляем из стека (с каскадом)
        if (id in _activeStack.value) {
            removeFromStack(id)
        }

        // Удаляем папку профиля
        val profileDir = File(profilesDir, id)
        if (profileDir.exists()) {
            profileDir.deleteRecursively()
        }

        // Удаляем из списка
        _profiles.value = _profiles.value.filter { it.id != id }

        logger.info { "Deleted profile: $id" }
    }

    /**
     * Дублирует профиль
     */
    fun duplicateProfile(id: String, newName: String): Profile? {
        val original = _profiles.value.find { it.id == id } ?: return null

        val newId = generateProfileId(newName)
        val newProfile = original.copy(
            id = newId,
            name = newName,
            scriptsDir = null,  // Будет установлено после создания папки
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // Создаем папку нового профиля
        val newProfileDir = File(profilesDir, newId)
        newProfileDir.mkdirs()
        File(newProfileDir, "scripts").mkdirs()

        // Копируем скрипты если есть
        original.scriptsDir?.toFile()?.let { srcScripts ->
            if (srcScripts.exists()) {
                srcScripts.copyRecursively(File(newProfileDir, "scripts"), overwrite = true)
            }
        }

        // Сохраняем с правильным scriptsDir
        val finalProfile = newProfile.copy(scriptsDir = File(newProfileDir, "scripts").toPath())
        saveProfile(finalProfile)

        _profiles.value = _profiles.value + finalProfile

        logger.info { "Duplicated profile ${original.name} to ${newProfile.name}" }
        return finalProfile
    }

    // === Управление стеком ===

    /**
     * Добавляет профиль в стек (в конец)
     * @return Result.success если успешно, Result.failure с DependencyException если зависимости не удовлетворены
     */
    fun pushProfile(id: String): Result<Unit> {
        if (id in _activeStack.value) {
            return Result.success(Unit)  // Уже в стеке
        }

        val profile = _profiles.value.find { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Profile not found: $id"))

        // Проверяем зависимости
        val depResult = checkDependencies(id)
        if (depResult is DependencyResult.Missing) {
            val missing = depResult.missingIds.joinToString(", ") { missingId ->
                _profiles.value.find { it.id == missingId }?.name ?: missingId
            }
            return Result.failure(DependencyException("Требуются профили: $missing"))
        }

        // Добавляем в стек
        _activeStack.value = _activeStack.value + id

        // Загружаем скрипты профиля
        profile.scriptsDir?.let { scriptsDir ->
            scriptManager.loadScriptsFromDirectory(scriptsDir.toFile(), profileId = id)
        }

        logger.info { "Pushed profile to stack: ${profile.name} (${profile.id})" }
        return Result.success(Unit)
    }

    /**
     * Удаляет профиль из стека (с каскадным удалением зависимых)
     * @return список ID удалённых профилей (включая зависимые)
     */
    fun removeFromStack(id: String): List<String> {
        if (id !in _activeStack.value) {
            return emptyList()
        }

        // Находим все профили, которые зависят от удаляемого
        val dependents = findDependentProfiles(id)
        val toRemove = (dependents + id).distinct()

        // Удаляем в обратном порядке (сначала зависимые)
        toRemove.reversed().forEach { profileId ->
            _activeStack.value = _activeStack.value - profileId
            scriptManager.unloadScriptsByProfileId(profileId)
        }

        if (dependents.isNotEmpty()) {
            val dependentNames = dependents.mapNotNull { depId ->
                _profiles.value.find { it.id == depId }?.name
            }
            logger.info { "Removed profile $id from stack, also removed dependents: $dependentNames" }
        } else {
            logger.info { "Removed profile $id from stack" }
        }

        return toRemove
    }

    /**
     * Изменяет порядок профилей в стеке
     */
    fun reorderStack(newOrder: List<String>) {
        // Проверяем что все профили из нового порядка были в старом стеке
        val currentStack = _activeStack.value.toSet()
        if (newOrder.toSet() != currentStack) {
            logger.warn { "Invalid reorder: new order contains different profiles" }
            return
        }

        // Проверяем зависимости в новом порядке
        val positionMap = newOrder.withIndex().associate { it.value to it.index }
        for ((index, profileId) in newOrder.withIndex()) {
            val profile = _profiles.value.find { it.id == profileId } ?: continue
            for (reqId in profile.requires) {
                val reqPosition = positionMap[reqId]
                if (reqPosition != null && reqPosition > index) {
                    // Зависимость идёт после зависимого - недопустимо
                    logger.warn { "Invalid reorder: $profileId requires $reqId which would come after it" }
                    return
                }
            }
        }

        _activeStack.value = newOrder
        logger.info { "Reordered stack: $newOrder" }
    }

    /**
     * Очищает стек (выгружает все профили)
     */
    fun clearStack() {
        // Выгружаем в обратном порядке
        _activeStack.value.reversed().forEach { profileId ->
            scriptManager.unloadScriptsByProfileId(profileId)
        }
        _activeStack.value = emptyList()
        logger.info { "Cleared profile stack" }
    }

    /**
     * Перезагружает профиль (с каскадной перезагрузкой зависимых)
     */
    fun reloadProfile(id: String) {
        val wasInStack = id in _activeStack.value
        if (!wasInStack) {
            // Просто перечитываем с диска
            loadProfiles()
            return
        }

        // Находим зависимые профили
        val dependents = findDependentProfiles(id)
        val toReload = listOf(id) + dependents

        // Сохраняем позиции в стеке
        val positions = toReload.associateWith { _activeStack.value.indexOf(it) }

        // Выгружаем (в обратном порядке)
        toReload.reversed().forEach { profileId ->
            _activeStack.value = _activeStack.value - profileId
            scriptManager.unloadScriptsByProfileId(profileId)
        }

        // Перечитываем профили с диска
        loadProfiles()

        // Загружаем обратно (в исходном порядке)
        toReload.sortedBy { positions[it] ?: Int.MAX_VALUE }.forEach { profileId ->
            pushProfile(profileId)
        }

        logger.info { "Reloaded profile $id (and ${dependents.size} dependents)" }
    }

    // === Зависимости ===

    /**
     * Проверяет, удовлетворены ли зависимости профиля
     */
    fun checkDependencies(profileId: String): DependencyResult {
        val profile = _profiles.value.find { it.id == profileId }
            ?: return DependencyResult.Satisfied  // Профиль не найден - считаем что зависимостей нет

        if (profile.requires.isEmpty()) {
            return DependencyResult.Satisfied
        }

        val currentStack = _activeStack.value.toSet()
        val missing = profile.requires.filter { it !in currentStack }

        return if (missing.isEmpty()) {
            DependencyResult.Satisfied
        } else {
            DependencyResult.Missing(missing)
        }
    }

    /**
     * Находит профили в стеке, которые зависят от данного (рекурсивно)
     */
    private fun findDependentProfiles(id: String): List<String> {
        val result = mutableListOf<String>()
        for (profileId in _activeStack.value) {
            val profile = _profiles.value.find { it.id == profileId } ?: continue
            if (id in profile.requires) {
                result.add(profileId)
                // Рекурсивно ищем зависимые от зависимого
                result.addAll(findDependentProfiles(profileId))
            }
        }
        return result.distinct()
    }

    // === Получение активных профилей ===

    /**
     * Возвращает профили в порядке стека
     */
    fun getActiveProfiles(): List<Profile> {
        return _activeStack.value.mapNotNull { id ->
            _profiles.value.find { it.id == id }
        }
    }

    // === Работа с триггерами/алиасами/хоткеями профиля ===

    /**
     * Добавляет триггер в профиль
     */
    fun addTriggerToProfile(profileId: String, trigger: Trigger) {
        updateProfile(profileId) { profile ->
            profile.copy(triggers = profile.triggers + trigger)
        }
    }

    /**
     * Удаляет триггер из профиля
     */
    fun removeTriggerFromProfile(profileId: String, triggerId: String) {
        updateProfile(profileId) { profile ->
            profile.copy(triggers = profile.triggers.filter { it.id != triggerId })
        }
    }

    /**
     * Обновляет триггер в профиле
     */
    fun updateTriggerInProfile(profileId: String, trigger: Trigger) {
        updateProfile(profileId) { profile ->
            profile.copy(triggers = profile.triggers.map {
                if (it.id == trigger.id) trigger else it
            })
        }
    }

    /**
     * Добавляет алиас в профиль
     */
    fun addAliasToProfile(profileId: String, alias: Alias) {
        updateProfile(profileId) { profile ->
            profile.copy(aliases = profile.aliases + alias)
        }
    }

    /**
     * Удаляет алиас из профиля
     */
    fun removeAliasFromProfile(profileId: String, aliasId: String) {
        updateProfile(profileId) { profile ->
            profile.copy(aliases = profile.aliases.filter { it.id != aliasId })
        }
    }

    /**
     * Обновляет алиас в профиле
     */
    fun updateAliasInProfile(profileId: String, alias: Alias) {
        updateProfile(profileId) { profile ->
            profile.copy(aliases = profile.aliases.map {
                if (it.id == alias.id) alias else it
            })
        }
    }

    /**
     * Добавляет хоткей в профиль
     */
    fun addHotkeyToProfile(profileId: String, hotkey: Hotkey) {
        updateProfile(profileId) { profile ->
            profile.copy(hotkeys = profile.hotkeys + hotkey)
        }
    }

    /**
     * Удаляет хоткей из профиля
     */
    fun removeHotkeyFromProfile(profileId: String, hotkeyId: String) {
        updateProfile(profileId) { profile ->
            profile.copy(hotkeys = profile.hotkeys.filter { it.id != hotkeyId })
        }
    }

    /**
     * Обновляет хоткей в профиле
     */
    fun updateHotkeyInProfile(profileId: String, hotkey: Hotkey) {
        updateProfile(profileId) { profile ->
            profile.copy(hotkeys = profile.hotkeys.map {
                if (it.id == hotkey.id) hotkey else it
            })
        }
    }

    // === Context Command Rules ===

    /**
     * Добавляет правило контекстной команды в профиль
     */
    fun addContextRuleToProfile(profileId: String, rule: com.bylins.client.contextcommands.ContextCommandRule) {
        updateProfile(profileId) { profile ->
            profile.copy(contextCommandRules = profile.contextCommandRules + rule)
        }
    }

    /**
     * Удаляет правило контекстной команды из профиля
     */
    fun removeContextRuleFromProfile(profileId: String, ruleId: String) {
        updateProfile(profileId) { profile ->
            profile.copy(contextCommandRules = profile.contextCommandRules.filter { it.id != ruleId })
        }
    }

    /**
     * Обновляет правило контекстной команды в профиле
     */
    fun updateContextRuleInProfile(profileId: String, rule: com.bylins.client.contextcommands.ContextCommandRule) {
        updateProfile(profileId) { profile ->
            profile.copy(contextCommandRules = profile.contextCommandRules.map {
                if (it.id == rule.id) rule else it
            })
        }
    }

    /**
     * Устанавливает переменную в профиле
     */
    fun setVariableInProfile(profileId: String, name: String, value: String) {
        updateProfile(profileId) { profile ->
            profile.copy(variables = profile.variables + (name to value))
        }
    }

    /**
     * Удаляет переменную из профиля
     */
    fun removeVariableFromProfile(profileId: String, name: String) {
        updateProfile(profileId) { profile ->
            profile.copy(variables = profile.variables - name)
        }
    }

    /**
     * Обновляет зависимости профиля
     */
    fun updateProfileDependencies(profileId: String, requires: List<String>) {
        updateProfile(profileId) { profile ->
            profile.copy(requires = requires)
        }
    }

    // === Утилиты ===

    private fun updateProfile(profileId: String, transform: (Profile) -> Profile) {
        val profile = _profiles.value.find { it.id == profileId } ?: return
        val updated = transform(profile)

        // Обновляем в списке
        _profiles.value = _profiles.value.map {
            if (it.id == profileId) updated else it
        }

        // Сохраняем на диск
        saveProfile(updated)
    }

    private fun generateProfileId(name: String): String {
        // Транслитерация и очистка
        val clean = name.lowercase()
            .replace(Regex("[^a-zA-Zа-яА-Я0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(20)

        // Если ID уже существует - добавляем суффикс
        val existingIds = _profiles.value.map { it.id }.toSet()
        var id = clean.ifEmpty { "profile" }
        var counter = 1
        while (id in existingIds) {
            id = "${clean}-$counter"
            counter++
        }

        return id
    }

    /**
     * Восстанавливает стек из сохранённого списка ID
     */
    fun restoreStack(savedStack: List<String>) {
        logger.info { "Restoring profile stack: $savedStack" }
        for (profileId in savedStack) {
            val result = pushProfile(profileId)
            if (result.isFailure) {
                logger.warn { "Failed to restore profile $profileId: ${result.exceptionOrNull()?.message}" }
            }
        }
    }
}
