# Roadmap

## Phase 1: MVP (текущая) ✅

- [x] Базовая структура проекта
- [x] UI с Compose for Desktop
- [x] Telnet клиент
- [x] Поддержка базового Telnet протокола
- [x] История команд
- [x] Панель статуса (UI только)

## Phase 2: Основной функционал

### Telnet & ANSI
- [ ] ANSI цвета (256 colors)
- [ ] RGB/TrueColor support
- [ ] Форматирование (bold, italic, underline)
- [ ] Обработка escape последовательностей
- [ ] UTF-8 encoding

### MSDP Integration
- [ ] Парсинг MSDP данных
- [ ] Автообновление статуса из MSDP
- [ ] MSDP переменные:
  - HEALTH, MANA, MOVEMENT
  - EXPERIENCE, GOLD
  - ROOM_NAME, ROOM_EXITS
  - OPPONENT_HEALTH
  - и др.

### GMCP Support
- [ ] Парсинг GMCP (JSON)
- [ ] Room.Info
- [ ] Char.Vitals
- [ ] Comm.Channel

## Phase 3: Automation

### Triggers
- [ ] Regex триггеры
- [ ] Простые строковые триггеры
- [ ] Цветовые триггеры
- [ ] Multi-line triggers
- [ ] Gag (скрытие строк)
- [ ] Highlight (подсветка)
- [ ] Play sound
- [ ] Выполнение команд
- [ ] Lua/JavaScript скрипты?

### Aliases
- [ ] Простые алиасы
- [ ] Regex алиасы
- [ ] Параметры ($1, $2, etc)
- [ ] Мульти-команды (;)
- [ ] Условные алиасы

### Timers
- [ ] Одноразовые таймеры
- [ ] Повторяющиеся таймеры
- [ ] Интервальные действия

## Phase 4: Mapping

### Автомаппер
- [ ] Автоматическое построение карты
- [ ] Парсинг exits из текста/MSDP
- [ ] Визуализация на Canvas
- [ ] Zoom, pan
- [ ] Несколько уровней (z-axis)
- [ ] Speedwalk (#5n2e)
- [ ] Поиск пути (pathfinding)
- [ ] Метки на карте
- [ ] Экспорт/импорт карт

### Database
- [ ] Хранение карты в SQLite
- [ ] Синхронизация между сессиями

## Phase 5: Statistics

### Сбор данных
- [ ] Damage dealt
- [ ] Damage taken
- [ ] Healing
- [ ] Experience gained
- [ ] Gold looted
- [ ] Kills
- [ ] Deaths

### Визуализация
- [ ] Графики в реальном времени
- [ ] История за сессию
- [ ] Сравнение сессий
- [ ] DPS meter
- [ ] Экспорт в CSV

## Phase 6: Advanced Features

### Logging
- [ ] Полный лог сессии
- [ ] Фильтрация логов
- [ ] HTML экспорт
- [ ] Поиск по логам

### Chat
- [ ] Отдельное окно для чата
- [ ] Фильтры каналов
- [ ] Табы для разных каналов
- [ ] Уведомления

### Scripting
- [ ] Embedded Lua VM
- [ ] API для скриптов
- [ ] Редактор скриптов
- [ ] Отладчик

### Plugins
- [ ] Plugin API
- [ ] Plugin загрузчик
- [ ] Sandbox для плагинов
- [ ] Marketplace?

## Phase 7: Polish

### UI/UX
- [ ] Темы оформления
- [ ] Кастомизация layout
- [ ] Hotkeys настройка
- [ ] Multiple profiles
- [ ] Импорт из других клиентов (Zmud, Mushclient)

### Performance
- [ ] Виртуализация текста
- [ ] Оптимизация рендеринга
- [ ] Lazy loading карты
- [ ] Профилирование

### Testing
- [ ] Unit tests
- [ ] Integration tests
- [ ] Mock telnet сервер

## Phase 8: Distribution

- [ ] Автообновление
- [ ] Installers (MSI, DEB, DMG)
- [ ] Portable версия
- [ ] Docker?

## Community Features (maybe)

- [ ] Шаринг триггеров/алиасов
- [ ] Онлайн backup конфигов
- [ ] Multi-player mapping
- [ ] Wiki интеграция

## Known Issues

- [ ] JAVA_HOME с пробелами в пути (Windows)
- [ ] SSL/TLS поддержка для telnet
- [ ] Большие объемы текста могут лагать UI

## Contributing

Если хотите помочь с разработкой:
1. Выберите задачу из Roadmap
2. Создайте issue
3. Fork + PR
