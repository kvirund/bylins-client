# Bylins Client — База знаний (документация)

Единая точка входа в документацию проекта. Общий обзор стека и модулей — в
[../ARCHITECTURE.md](../ARCHITECTURE.md); быстрый старт — [../QUICKSTART.md](../QUICKSTART.md);
планы — [../ROADMAP.md](../ROADMAP.md); инструкции для агентов — [../CLAUDE.md](../CLAUDE.md).

## Документы

| Документ | О чём |
|----------|-------|
| [../ARCHITECTURE.md](../ARCHITECTURE.md) | Обзор проекта: стек, модули, data flow, state, расширяемость |
| [OUTPUT_PANELS.md](OUTPUT_PANELS.md) | **Панели вывода**: split-scrollback, скролл/якорь, выделение, поиск, рендер. **С разделом «Подводные камни»** (must-read перед правкой скролла/выделения) |
| [TABS_DESIGN.md](TABS_DESIGN.md) | Система вкладок и маршрутизация текста (фильтры, COPY/MOVE) |
| [EXAMPLES.md](EXAMPLES.md) | Примеры использования (триггеры, алиасы, скрипты и т.п.) |

## Подсистемы и где про них читать

- **Вывод / прокрутка / выделение / поиск** → [OUTPUT_PANELS.md](OUTPUT_PANELS.md)
  - код: `ui/scroll/` (чистая логика, тестируемо) + `ui/components/output/` (Compose)
- **Вкладки и фильтрация** → [TABS_DESIGN.md](TABS_DESIGN.md)
  - код: `tabs/Tab.kt`, `tabs/TabManager.kt`, `ui/components/OutputPanel.kt`
- **Сеть/Telnet/MSDP/GMCP** → [../ARCHITECTURE.md](../ARCHITECTURE.md) §Network
  - код: `network/`
- **Плагины/скрипты** → [../ARCHITECTURE.md](../ARCHITECTURE.md), `plugins/`, `scripting/`

## Соглашения

- Документация — на русском, в `docs/` для дизайн-доков подсистем; общий обзор — в
  корневом `ARCHITECTURE.md`.
- При существенной переработке подсистемы — обновляй её документ и, если изменились
  грабли, раздел «Подводные камни».
