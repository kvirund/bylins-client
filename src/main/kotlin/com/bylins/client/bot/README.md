# AI-Бот для Bylins MUD

Интеллектуальный бот с поддержкой LLM для автоматизации игрового процесса.

## Архитектура

```
bot/
├── BotCore.kt           # Ядро бота, координация подсистем
├── BotManager.kt        # Интеграция с клиентом (BotActions)
├── BotConfig.kt         # Конфигурация бота
├── BotState.kt          # FSM машина состояний
├── BotModels.kt         # Модели данных
├── BotDatabase.kt       # SQLite база данных
│
├── combat/
│   └── CombatManager.kt # Управление боем
│
├── navigation/
│   └── Navigator.kt     # Навигация по карте
│
├── perception/
│   ├── CombatParser.kt  # Парсинг боевых сообщений
│   └── EntityTracker.kt # Отслеживание мобов/игроков
│
└── llm/
    └── LLMParser.kt     # Интеграция с Ollama LLM
```

## Компоненты

### BotCore

Главный координатор бота. Управляет:
- FSM машиной состояний
- Основным циклом обработки (10 тиков/сек)
- Подсистемами (combat, navigation, perception)
- Сессиями и статистикой

```kotlin
val botCore = BotCore(
    sendCommand = { cmd -> ... },
    echoText = { text -> ... },
    getMsdpValue = { key -> ... },
    getCurrentRoom = { ... },
    findPath = { targetId -> ... },
    fireEvent = { event, data -> ... }
)

botCore.start(BotMode.LEVELING)
botCore.stop()
```

### BotManager

Точка интеграции с клиентом. Реализует интерфейс `BotActions` для ScriptAPI.
Обрабатывает команды `#bot ...` и события от клиента.

### BotState (FSM)

Машина состояний с переходами:

```
IDLE ─────► STARTING ─────► TRAVELING ─────► COMBAT
  ▲              │              │              │
  │              │              │              ▼
  │              │              │          LOOTING
  │              │              │              │
  │              │              ▼              │
  │              └────────► RESTING ◄─────────┘
  │                            │
  │                            ▼
  └──────────── STOPPING ◄── BUFFING
                   │
                   ▼
               RETURNING ──► EXPLORING
```

**Состояния:**
- `IDLE` - бот не активен
- `STARTING` - инициализация
- `TRAVELING` - перемещение к цели
- `COMBAT` - в бою
- `LOOTING` - сбор лута
- `RESTING` - восстановление HP/маны
- `BUFFING` - наложение баффов
- `FLEEING` - бегство при низком HP
- `EXPLORING` - исследование новых зон
- `RETURNING` - возврат в безопасную зону
- `ERROR` - ошибка
- `STOPPING` - остановка

### BotConfig

Конфигурация с режимами и параметрами:

```kotlin
data class BotConfig(
    val enabled: Boolean = false,
    var mode: BotMode = BotMode.LEVELING,

    // Пороги HP/маны для действий
    val fleeHpPercent: Int = 20,      // Бежать при HP < 20%
    val restHpPercent: Int = 70,      // Отдыхать при HP < 70%
    val restManaPercent: Int = 50,    // Отдыхать при мане < 50%

    // Боевые настройки
    val autoLoot: Boolean = true,
    val autoAssist: Boolean = false,
    val useCombatSkills: Boolean = true,

    // LLM
    val useLLMParsing: Boolean = true,

    // Логирование
    val verboseLogging: Boolean = false
)
```

**Режимы работы:**
- `LEVELING` - фарм опыта на мобах
- `FARMING` - фарм предметов
- `GATHERING` - сбор ресурсов
- `TRADING` - торговля
- `EXPLORING` - исследование новых зон
- `IDLE` - ожидание

### BotDatabase

SQLite база данных для хранения:

**Таблицы:**
- `mobs` - данные о мобах (имя, уровень, HP, награда)
- `mob_spawns` - спавны мобов в комнатах
- `zone_stats` - статистика зон (exp/час, опасность)
- `bot_sessions` - сессии бота
- `training_data` - данные для ML обучения
- `items` - предметы
- `item_drops` - дроп с мобов

```kotlin
val database = BotDatabase()

// Сохранить данные о мобе
database.saveMob(MobData(
    id = "orc_warrior",
    name = "злобный орк-воин",
    level = 15,
    avgHp = 200,
    expReward = 1500
))

// Получить статистику зоны
val stats = database.getZoneStats("темный_лес")
```

### LLMParser

Интеграция с Ollama для понимания текста игры:

```kotlin
val llmParser = LLMParser(
    baseUrl = "http://localhost:11434",
    modelName = "llama3"
)

llmParser.initialize()

// Парсинг описания комнаты
val roomResult = llmParser.parseRoomDescription("""
    Вы находитесь в темной пещере.
    Здесь стоит злобный орк, готовый к бою.
""")
// → RoomParseResult(mobs=[MobInfo(name="злобный орк", isAggressive=true)])

// Парсинг боевого сообщения
val combatResult = llmParser.parseCombatMessage(
    "Вы нанесли орку 25 повреждений!"
)
// → CombatParseResult(type=DAMAGE_DEALT, target="орк", damage=25)

// Оценка уровня моба
val level = llmParser.estimateMobLevel("Орк выглядит очень сильным", playerLevel = 10)
// → 15
```

**Требования:**
- Установленный Ollama: https://ollama.ai
- Скачанная модель: `ollama pull llama3`

### CombatManager

Управление боем:
- Выбор цели (приоритет по угрозе, HP)
- Ротация скиллов
- Автоатака
- Флиинг при низком HP

```kotlin
combatManager.selectTarget()      // Выбрать цель
combatManager.performAttack()     // Атаковать
combatManager.useSkill("kick")    // Использовать скилл
combatManager.flee()              // Бежать
```

### Navigator

Навигация по карте маппера:
- Поиск пути к цели
- Автоматическое перемещение
- Выбор зон по уровню
- Исследование неизвестных комнат

```kotlin
navigator.moveTo("12345")              // Идти в комнату
navigator.findSafeZone()               // Найти безопасную зону
navigator.getZonesForLevel(15)         // Зоны для 15 уровня
navigator.exploreUnknown()             // Исследовать
```

### EntityTracker

Отслеживание сущностей:
- Мобы в текущей комнате
- Игроки рядом
- Состояние мобов (HP, позиция)

```kotlin
val mobs = entityTracker.getMobsInRoom()
val target = entityTracker.getCurrentTarget()
val players = entityTracker.getPlayersInRoom()
```

### CombatParser

Парсинг боевых сообщений (rule-based + LLM):

```kotlin
val event = combatParser.parseLine("Вы нанесли орку 25 повреждений!")
// → CombatEvent(type=DAMAGE_DEALT, target="орк", damage=25)

val event2 = combatParser.parseLine("Орк мертв!")
// → CombatEvent(type=MOB_KILLED, target="орк")
```

## Использование

### Команды клиента

```
#bot                    - Справка
#bot start [mode]       - Запустить бота
#bot stop               - Остановить
#bot status             - Статус
#bot config             - Показать конфиг
#bot set <key> <value>  - Изменить настройку
#bot llm init [url] [model] - Инициализировать LLM
#bot llm status         - Статус LLM
```

### Примеры

```
#bot start leveling     - Начать фарм опыта
#bot start exploring    - Исследовать новые зоны
#bot set fleeHpPercent 30  - Бежать при HP < 30%
#bot llm init http://localhost:11434 llama3
```

### UI панель

Вкладка "AI-Бот" в интерфейсе клиента:
- Статус бота и текущее состояние
- Выбор режима работы
- Кнопки старт/стоп
- Статистика сессии (убийства, смерти, опыт)
- Состояние персонажа (HP, мана, движение)
- Статус LLM парсера

## Зависимости

```kotlin
// build.gradle.kts
implementation("dev.langchain4j:langchain4j:0.35.0")
implementation("dev.langchain4j:langchain4j-ollama:0.35.0")
```

## Данные

База данных создается в: `~/.bylins-client/bot/bot.db`

## Расширение

### Добавление нового режима

1. Добавить в `BotMode` enum
2. Реализовать логику в `BotCore.tick()` для нового состояния
3. Обновить UI в `BotPanel.kt`

### Добавление нового скилла

1. Добавить в `CombatManager.availableSkills`
2. Настроить приоритет и условия использования

### Интеграция внешней базы мира

Бот может использовать внешнюю базу данных с информацией о мире:
- Мобы с точными характеристиками
- Зоны с рекомендуемыми уровнями
- Квесты и награды

```kotlin
// Опционально загрузить внешнюю базу
botCore.loadExternalWorldDatabase("path/to/world.db")
```

## Планы развития

- [ ] ML модели для оптимизации решений (ONNX Runtime)
- [ ] Автоматический выбор зон по статистике
- [ ] Торговля с использованием базы цен
- [ ] Групповой режим (автоассист, хилинг)
- [ ] Запись и воспроизведение маршрутов
