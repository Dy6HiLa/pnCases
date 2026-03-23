# pnCases

Бесплатный плагин кейсов для Paper 1.21.x. три анимации, поддержка FancyHolograms, LuckPerms-награды.

---

## Установка

1. Скинь `pnCases.jar` в папку `plugins/`
2. Рестарт сервера
3. В `plugins/pnCases/` появятся `config.yml` и `messages.yml`

**Зависимости**
- Paper 1.21.x (обязательно)
- FancyHolograms (опционально — голограммы над кейсами)
- LuckPerms (опционально — выдача привилегий как награда)

---

## Команды и права

| Команда | Описание |
|---|---|
| `/pncases setcase <name>` | Привязать кейс к блоку (смотри на блок) |
| `/pncases givekey <player\|uuid> <key> <amount>` | Выдать виртуальные ключи |
| `/pncases reload` | Перезагрузить config.yml и messages.yml |

**Право:** `pncases.admin` — доступ ко всем командам.

---

## Настройка кейса

### 1. Создай кейс в config.yml

```yaml
cases:
  donate:                          # ID кейса (только латиница, без пробелов)
    block:
      world: world
      x: 0
      y: 64
      z: 0

    gui:
      title: "&5Донат кейс"
      open-item:
        material: ENDER_CHEST
        name: "&5Открыть донат кейс"
        lore:
          - "&7Испытай удачу!"

    cost:
      type: KEY                    # NONE / KEY / XP_LEVELS
      key: donate_key              # ID ключа из секции keys:
      amount: 1                    # сколько ключей нужно
      buy_xp_levels: 10            # купить ключ за XP (0 = выключено)

    animation:
      duration_ticks: 80
      cycle_every_ticks: 2
      rise_blocks: 1.2
      spin_degrees_per_tick: 18
      items:
        - material: DIAMOND
          name: "&bАлмаз"
        - material: NETHERITE_INGOT
          name: "&8Незеритовый слиток"

    rewards:
      - chance: 70
        type: ITEM
        item:
          material: DIAMOND
          amount: 1
          name: "&bАлмаз"
        message: "&aТебе повезло — алмаз!"

      - chance: 25
        type: ITEM
        item:
          material: NETHERITE_INGOT
          amount: 1
          name: "&8Незеритовый слиток"

      - chance: 5
        type: LUCKPERMS
        luckperms:
          group: vip
          duration: "30d"
          display_name: "&6VIP на 30 дней"
        message: "&6Поздравляем — ты получил VIP!"
```

### 2. Привяжи кейс к блоку

Встань перед блоком, выполни:
```
/pncases setcase donate
```

### 3. Готово. Игрок нажимает ПКМ на блок — открывается GUI.

---

## Ключи

Ключи только виртуальные — хранятся в `keys.yml`, не занимают инвентарь.

```yaml
keys:
  donate_key:
    name: "&5Донат ключ"

  vip_key:
    name: "&6VIP ключ"
```

**Выдать ключ игроку:**
```
/pncases givekey Steve donate_key 3
/pncases givekey 069a79f4-44e9-4726-a5be-fca90e38aaf1 donate_key 1
```

---

## Типы стоимости (cost.type)

| Тип | Описание |
|---|---|
| `NONE` | Бесплатно, без условий |
| `KEY` | Нужен виртуальный ключ |
| `XP_LEVELS` | Нужны уровни опыта |

При `KEY` можно включить покупку ключа за XP прямо из GUI — параметр `buy_xp_levels`.

---

## Типы наград (rewards.type)

### ITEM — выдать предмет

```yaml
- chance: 80
  type: ITEM
  item:
    material: DIAMOND
    amount: 3
    name: "&bАлмазы"
    lore:
      - "&7Хорошая добыча"
  message: "&aТы получил алмазы!"  # необязательно
```

Поддерживается `base64` для скинов голов:
```yaml
  item:
    base64: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA..."
    name: "&aКастомная голова"
```

### LUCKPERMS — выдать группу или перм

```yaml
- chance: 5
  type: LUCKPERMS
  luckperms:
    group: vip           # группа (необязательно)
    node: "some.perm"    # перм (необязательно)
    duration: "30d"      # срок (необязательно, без — навсегда)
    display_name: "&6VIP на 30 дней"
  message: "&6Поздравляем!"
```

**Шансы** суммируются и нормализуются автоматически. `chance: 70` + `chance: 30` = 70% и 30%.

---

## Анимации

Игрок выбирает анимацию сам через кнопку в GUI кейса (слот 49).

| Анимация | Описание |
|---|---|
| **Anvil** | Наковальня падает с высоты и выбивает награду |
| **Dynamite** | TNT летит по параболе, взрывается, появляется награда |
| **Portal** | Пурпурные столбы, портал, из него вырывается награда |

Выбор сохраняется в `player_prefs.yml` и запоминается между сессиями.

---

## Голограммы (FancyHolograms)

Установи FancyHolograms и добавь секцию `hologram:` к кейсу:

```yaml
cases:
  donate:
    hologram:
      enabled: true
      type: TEXT          # TEXT / ITEM / BLOCK
      y: 2.0              # высота над блоком
      lines:
        - "&5✦ Донат кейс ✦"
        - "&7Открой и получи привилегию"
        - "&8Кейс: &f{case}"
      billboard: CENTER
      text_shadow: true
      visibility_distance: 32
```

**Плейсхолдеры в lines:**

| Плейсхолдер | Значение |
|---|---|
| `{case}` | ID кейса |
| `{gui_title}` | Название с цветами |
| `{gui_title_plain}` | Название без цветов |
| `{world}` | Название мира |
| `{x}` `{y}` `{z}` | Координаты блока |

**Тип ITEM:**
```yaml
hologram:
  enabled: true
  type: ITEM
  y: 1.5
  item:
    material: ENDER_CHEST
    name: "&5Донат кейс"
```

**Тип BLOCK:**
```yaml
hologram:
  enabled: true
  type: BLOCK
  y: 1.5
  block: ENDER_CHEST
```

---

## messages.yml

Все сообщения плагина редактируются здесь. Перезагрузка через `/pncases reload`.

```yaml
prefix: "§x§8§7§3§E§8§0§lᴄ..."   # префикс — вставляется в {prefix}

broadcast:                          # список строк — можно добавить/убрать
  - ""
  - "&8&m          "
  - " {prefix}"
  - " &7Игрок &f{player} &7открыл кейс"
  - " &7и получил: {reward}"
  - "&8&m          "
  - ""
```

**Доступные плейсхолдеры** зависят от сообщения — `{player}`, `{reward}`, `{amount}`, `{key_name}`, `{need}`, `{have}`, `{case}`, `{world}`, `{x}`, `{y}`, `{z}`, `{levels}`, `{animation}`.

---

## Структура файлов

```
plugins/pnCases/
├── config.yml        — кейсы, ключи
├── messages.yml      — все сообщения
├── keys.yml          — балансы ключей игроков
└── player_prefs.yml  — настройки анимации игроков
```

---

## FAQ

**Кейс не открывается — «ключ не настроен»**
→ Убедись что `cost.key` в кейсе совпадает с ID в секции `keys:` config.yml.

**Голограмма не появляется**
→ Проверь что FancyHolograms установлен и включён. В логах будет предупреждение если что-то пошло не так.

**Хочу несколько кейсов**
→ Просто добавь несколько секций в `cases:` с разными ID, привяжи каждый к своему блоку через `/pncases setcase`.

**Выдать ключ через другой плагин**
→ Используй `/pncases givekey` через консоль или CommandAPI — работает с UUID.
