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
- [ ] Скрипты в триггерах (Python/Lua/JavaScript/Perl)

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

### Windows & Tabs (Вкладки и окна)
- [ ] Система вкладок:
  - [ ] Главная вкладка (Main)
  - [ ] Создание новых вкладок
  - [ ] Переключение между вкладками (Ctrl+Tab, Ctrl+1-9)
  - [ ] Закрытие вкладок
  - [ ] Переименование вкладок
- [ ] Перенаправление вывода:
  - [ ] Redirect по regex паттернам
  - [ ] Gag (скрыть) + redirect (скопировать в другую вкладку)
  - [ ] Highlight + redirect
  - [ ] Примеры:
    - Tells/шепоты в вкладку "Chat"
    - Бой в вкладку "Combat"
    - Каналы в вкладку "Channels"
    - Групповые сообщения в "Party"
- [ ] Управление вкладками:
  - [ ] UI для создания redirect rules
  - [ ] Сохранение конфигурации вкладок
  - [ ] Автосоздание вкладок при первом redirect
- [ ] Split windows:
  - [ ] Горизонтальный split
  - [ ] Вертикальный split
  - [ ] Несколько окон одновременно
- [ ] Отдельные окна (detach):
  - [ ] Вынос вкладки в отдельное окно
  - [ ] Всегда поверх других окон (always on top)
  - [ ] Прозрачность окон

### Chat (устарел, объединен с Windows & Tabs)
- [x] Отдельное окно для чата (см. Windows & Tabs)
- [x] Фильтры каналов (см. redirect rules)
- [x] Табы для разных каналов (см. система вкладок)
- [ ] Уведомления при новых сообщениях
- [ ] Звуковые уведомления

### Scripting (multi-language support)
- [ ] Python скрипты:
  - [ ] GraalVM Python (рекомендуется)
  - [ ] или Jython (Python 2.7)
  - [ ] или внешний CPython процесс
  - [ ] Автозагрузка .py файлов из scripts/
- [ ] Lua скрипты:
  - [ ] Embedded LuaJ
  - [ ] Автозагрузка .lua файлов
- [ ] JavaScript скрипты:
  - [ ] GraalVM JavaScript
  - [ ] или Nashorn (deprecated но работает)
  - [ ] Автозагрузка .js файлов
- [ ] Perl скрипты:
  - [ ] Внешний процесс (perl interpreter)
  - [ ] Автозагрузка .pl файлов
- [ ] API для скриптов:
  - [ ] send(command) - отправка команд
  - [ ] echo(text) - вывод текста
  - [ ] add_trigger/remove_trigger
  - [ ] add_alias/remove_alias
  - [ ] get_variable/set_variable
  - [ ] MSDP данные
  - [ ] Mapper API
- [ ] Редактор скриптов встроенный
- [ ] Отладчик для Python/Lua
- [ ] Hot reload скриптов

### Plugins
- [ ] Plugin API (Kotlin/Java):
  - [ ] Plugin interface
  - [ ] Lifecycle hooks (onLoad, onUnload, onCommand)
  - [ ] Event system
- [ ] Multi-language плагины:
  - [ ] Python (.py) плагины + requirements.txt
  - [ ] Lua (.lua) плагины
  - [ ] JavaScript (.js) плагины + package.json/node_modules?
  - [ ] Perl (.pl) плагины + cpanfile?
  - [ ] Виртуальное окружение для каждого плагина
- [ ] Plugin загрузчик (plugins/ директория)
- [ ] Sandbox для плагинов
- [ ] Конфигурация плагинов (YAML/JSON)
- [ ] UI для управления плагинами
- [ ] Marketplace? (шаринг плагинов)

## Phase 7: Polish

### UI/UX
- [ ] Темы оформления
- [ ] Кастомизация layout:
  - [ ] Изменение размеров панелей (draggable splitters)
  - [ ] Скрытие/показ панелей (статус, ввод)
  - [ ] Сохранение layout конфигурации
- [ ] Горячие клавиши:
  - [ ] Настройка биндов клавиш (F1-F12, Ctrl+, Alt+)
  - [ ] Макросы на клавиши (отправка команд)
  - [ ] Numpad направления (8=север, 2=юг, 4=запад, 6=восток)
  - [ ] Редактор горячих клавиш в UI
  - [ ] Импорт/экспорт конфигов
  - [ ] Глобальные и локальные биндинги
  - [ ] Биндинги на переключение вкладок
- [ ] Multiple profiles (персонажи)
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
