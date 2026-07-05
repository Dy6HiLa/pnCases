# pnCases 1.4.1

Bug fix после релиза 1.4.

## Исправлено

- VAULT и PlayerPoints больше не используют `display_name` внутри виртуальной валюты.
- Для виртуальных наград теперь действует чистая структура:
  - `vault.amount` - сумма денег;
  - `playerpoints.amount` - количество поинтов;
  - `visual` - только предмет для GUI, preview и анимации.
- Если `visual.name` указан, он используется только для отображения награды.
- Если `visual.name` не указан, pnCases сам покажет сумму через `reward-symbols.vault` или `reward-symbols.playerpoints`.
- Исправлен пример `3500` монет, где визуально было написано `$500`.

## Скачать

[Скачать pnCases 1.4.1](https://github.com/Dy6HiLa/pnCases/releases/download/v1.4.1/pnCases-1.4.1.jar)

После замены JAR перезапустите сервер.
