<img src="assets/pncases-cover-1.4.7.png"
     alt="Paper и Purpur 1.19–1.21.11"
     style="width: 100%;">
<p align="center">
  <a href="https://github.com/Dy6HiLa/pnCases/releases -cover-v2.png" alt="pnCases — плагин кейсов для Minecraft" width="100%">
  </a>
</p>

<h1 align="center">pnCases</h1>

<p align="center">
  Бесплатный плагин кейсов для серверов Paper и Purpur
  <br>
  с анимациями, наградами, голограммами и удобной настройкой.
</p>

<p align="center">
  <a href="https://github.com/Dy6HiLa/pnCases/releases/download/v1.4.7/pnCases-1.4.7.jar">
    <img src="https://img.shields.io/badge/Скачать-1.4.7-429F91?style=for-the-badge&labelColor=17241F" alt="Скачать pnCases 1.4.7">
  </a>
  <a href="https://github.com/Dy6HiLa/pnCases/releases/tag/v1.4.7">
    <img src="https://img.shields.io/badge/Changelog-1.4.7-D8DF9D?style=for-the-badge&labelColor=17241F" alt="Changelog pnCases 1.4.7">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-429F91?style=for-the-badge&labelColor=17241F" alt="Лицензия MIT">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper%20%2F%20Purpur-1.19--1.21.11-429F91?style=flat-square" alt="Paper и Purpur 1.19–1.21.11">
  <img src="https://img.shields.io/badge/Java-17%2B-D8DF9D?style=flat-square" alt="Java 17+">
  <img src="https://img.shields.io/badge/Хранилище-SQLite-429F91?style=flat-square" alt="SQLite">
</p>

<p align="center">
  <a href="#-возможности">Возможности</a>
  ·
  <a href="#-установка">Установка</a>
  ·
  <a href="#-команды">Команды</a>
  ·
  <a href="#-награды">Награды</a>
  ·
  <a href="#-анимации">Анимации</a>
  ·
  <a href="#-поддержка">Поддержка</a>
</p>

<hr>

<h2 id="-возможности">✨ Возможности</h2>

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>🎁 Система кейсов</h3>
      <p>
        Создание кейсов с собственными ключами, наградами,
        редкостями и шансами выпадения.
      </p>
    </td>
    <td width="50%" valign="top">
      <h3>🎬 Анимации открытия</h3>
      <p>
        Несколько визуальных сценариев открытия.
        Игрок может выбрать понравившуюся анимацию через GUI.
      </p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>🖥 Удобный GUI</h3>
      <p>
        Просмотр содержимого кейса, выбор анимации,
        история открытий и предварительный просмотр наград.
      </p>
    </td>
    <td width="50%" valign="top">
      <h3>💎 Разные типы наград</h3>
      <p>
        Поддерживаются предметы, команды, Vault,
        PlayerPoints и LuckPerms.
      </p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>🌌 Витрина кейса</h3>
      <p>
        Настраиваемый предмет и визуальные эффекты
        отображаются над свободным кейсом.
      </p>
    </td>
    <td width="50%" valign="top">
      <h3>💬 Голограммы</h3>
      <p>
        Автоматическая интеграция с FancyHolograms
        и DecentHolograms.
      </p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>💾 SQLite</h3>
      <p>
        История, выбор анимации и остальные данные
        безопасно хранятся в базе данных.
      </p>
    </td>
    <td width="50%" valign="top">
      <h3>🔄 Проверка обновлений</h3>
      <p>
        Плагин автоматически проверяет новые версии
        через GitHub Releases и манифест обновлений.
      </p>
    </td>
  </tr>
</table>

<hr>

<h2>🚀 pnCases 1.4.7</h2>

<p>
  <strong>pnCases 1.4.7</strong> — небольшое обновление команд выдачи ключей.
  Основная цель — дать скрытно выдавать ключи, например при автоматической
  выдаче после покупки на сайте.
</p>

<table>
  <tr>
    <td>
      <strong>Версия плагина</strong>
    </td>
    <td>
      <code>1.4.7</code>
    </td>
  </tr>
  <tr>
    <td>
      <strong>Поддерживаемые серверы</strong>
    </td>
    <td>
      Paper / Purpur <code>1.19–1.21.11</code>
    </td>
  </tr>
  <tr>
    <td>
      <strong>Версия Java</strong>
    </td>
    <td>
      Java <code>17+</code>
    </td>
  </tr>
  <tr>
    <td>
      <strong>API Version</strong>
    </td>
    <td>
      <code>1.19</code>
    </td>
  </tr>
</table>

<h3>Что нового</h3>

<ul>
  <li>
    У команды <code>/pncases givekey &lt;игрок&gt; &lt;ключ&gt; &lt;кол-во&gt;</code>
    появился необязательный флаг <code>-s</code> (или <code>-silent</code>).
  </li>
  <li>
    Если указан флаг <code>-s</code>, игрок не получает сообщение о выдаче ключа в чат.
  </li>
  <li>
    Ключ всё равно добавляется на баланс игрока как обычно.
  </li>
  <li>
    Администратору или системе, которая выполнила команду
    (например, сайт через RCON), по-прежнему приходит подтверждение выдачи.
  </li>
  <li>
    Флаг добавлен в автодополнение команды.
  </li>
</ul>

<details>
  <summary><strong>Пример использования</strong></summary>

  <br>

<pre><code>/pncases givekey Evgeniy51234 donate_key 1 -s</code></pre>

  <p>
    Ключ будет выдан скрытно — игрок не увидит уведомление в чате, но получит
    ключ на баланс. Удобно для связки с сайтом: после доната сайт выполняет
    команду через RCON или консоль.
  </p>
</details>

<p>
  Предыдущие списки изменений находятся в
  <a href="docs/releases/">архиве релизов</a>.
</p>

<hr>

<h2 id="-установка">📦 Установка</h2>

<ol>
  <li>
    Скачайте
    <a href="https://github.com/Dy6HiLa/pnCases/releases/download/v1.4.7/pnCases-1.4.7.jar">
      <code>pnCases-1.4.7.jar</code>
    </a>.
  </li>
  <li>Переместите JAR-файл в папку <code>plugins/</code>.</li>
  <li>Перезапустите сервер.</li>
  <li>
    Настройте файлы
    <code>plugins/pnCases/config.yml</code>
    и
    <code>plugins/pnCases/messages.yml</code>.
  </li>
</ol>

<blockquote>
  <strong>Важно:</strong> не используйте PlugMan и другие средства горячей
  загрузки для первой установки плагина. Выполните полноценный перезапуск сервера.
</blockquote>

<h3>Зависимости</h3>

<table>
  <thead>
    <tr>
      <th align="left">Зависимость</th>
      <th align="center">Обязательность</th>
      <th align="left">Назначение</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Paper / Purpur 1.19–1.21.11</td>
      <td align="center"><strong>Обязательно</strong></td>
      <td>Серверное ядро</td>
    </tr>
    <tr>
      <td>LuckPerms</td>
      <td align="center">Опционально</td>
      <td>Выдача групп и прав</td>
    </tr>
    <tr>
      <td>Vault</td>
      <td align="center">Опционально</td>
      <td>Денежные награды</td>
    </tr>
    <tr>
      <td>PlayerPoints</td>
      <td align="center">Опционально</td>
      <td>Поинтовые награды</td>
    </tr>
    <tr>
      <td>FancyHolograms</td>
      <td align="center">Опционально</td>
      <td>Голограммы на новых версиях</td>
    </tr>
    <tr>
      <td>DecentHolograms</td>
      <td align="center">Опционально</td>
      <td>Голограммы и совместимость со старыми версиями</td>
    </tr>
  </tbody>
</table>

<hr>

<h2 id="-команды">⌨️ Команды</h2>

<table>
  <thead>
    <tr>
      <th align="left">Команда</th>
      <th align="left">Описание</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>/pncases</code></td>
      <td>Показать команды, версию плагина и статус обновления</td>
    </tr>
    <tr>
      <td><code>/pncases reload</code></td>
      <td>Перезагрузить <code>config.yml</code> и <code>messages.yml</code></td>
    </tr>
    <tr>
      <td><code>/pncases setcase &lt;кейс&gt;</code></td>
      <td>Установить кейс на блок, на который смотрит администратор</td>
    </tr>
    <tr>
      <td><code>/pncases delcase &lt;кейс&gt;</code></td>
      <td>Удалить установленные блоки кейса без удаления его настроек</td>
    </tr>
    <tr>
      <td><code>/pncases givekey &lt;игрок&gt; &lt;ключ&gt; &lt;количество&gt; [-s]</code></td>
      <td>Выдать игроку ключи (<code>-s</code> — скрытно)</td>
    </tr>
    <tr>
      <td><code>/pncases takekey &lt;игрок&gt; &lt;ключ&gt; &lt;количество&gt;</code></td>
      <td>Забрать ключи у игрока</td>
    </tr>
  </tbody>
</table>

<h3>Право администратора</h3>

```yaml
pncases.admin
```

<hr>

<h2 id="-награды">🎁 Награды</h2>

<p>
  pnCases поддерживает предметные, денежные, поинтовые
  и серверные награды.
</p>

<table>
  <thead>
    <tr>
      <th align="left">Тип</th>
      <th align="left">Назначение</th>
      <th align="left">Зависимость</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>ITEM</code></td>
      <td>Выдача предмета</td>
      <td>Не требуется</td>
    </tr>
    <tr>
      <td><code>COMMAND</code></td>
      <td>Выполнение серверной команды</td>
      <td>Не требуется</td>
    </tr>
    <tr>
      <td><code>VAULT</code></td>
      <td>Выдача денег на баланс</td>
      <td>Vault и плагин экономики</td>
    </tr>
    <tr>
      <td><code>PLAYERPOINTS</code></td>
      <td>Выдача поинтов</td>
      <td>PlayerPoints</td>
    </tr>
    <tr>
      <td><code>LUCKPERMS</code></td>
      <td>Выдача группы или прав</td>
      <td>LuckPerms</td>
    </tr>
  </tbody>
</table>

<details open>
  <summary><strong>Пример предметной награды</strong></summary>

  <br>

```yaml
rewards:
  - chance: 45
    rarity: COMMON
    type: ITEM
    item:
      material: DIAMOND
      amount: 8
      name: '&bАлмазы &8x8'
```

</details>

<details>
  <summary><strong>Пример денежной награды через Vault</strong></summary>

  <br>

```yaml
rewards:
  - chance: 30
    rarity: RARE
    type: VAULT
    vault:
      amount: 2500
    visual:
      material: GOLD_INGOT
      name: '&e2500 монет'
    message: '&aВы получили &f{amount}&a на баланс!'
```

</details>

<blockquote>
  Для наград <code>VAULT</code>, <code>PLAYERPOINTS</code>
  и <code>LUCKPERMS</code> секция <code>visual</code>
  используется только для отображения награды в GUI и анимациях.
</blockquote>

<hr>

<h2 id="-анимации">🎬 Анимации</h2>

<p>
  Игрок выбирает анимацию через интерфейс кейса.
  Выбранный вариант сохраняется в SQLite.
</p>

<p>
  На Minecraft 1.21+ используется полный визуальный режим.
  На Minecraft 1.19–1.20 автоматически включается совместимый режим.
</p>

<table>
  <thead>
    <tr>
      <th align="left">Анимация</th>
      <th align="left">Описание</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>⚒️ Наковальня</td>
      <td>Падение наковальни и появление награды</td>
    </tr>
    <tr>
      <td>🧨 Динамит</td>
      <td>Полёт TNT, взрыв и выдача приза</td>
    </tr>
    <tr>
      <td>🌀 Портал</td>
      <td>Портальные эффекты и появление награды</td>
    </tr>
    <tr>
      <td>☠️ Отравление</td>
      <td>Ядовитый слайм, частицы и выдача приза</td>
    </tr>
    <tr>
      <td>🌌 Астральный разлом</td>
      <td>Магический разлом с вращением наград</td>
    </tr>
  </tbody>
</table>

<details>
  <summary><strong>Настройка направления слайма</strong></summary>

  <br>

```yaml
animation:
  poison:
    slime-facing: PLAYER
    slime-pitch: 0
```

  <p>
    Доступные значения:
    <code>NORTH</code>,
    <code>SOUTH</code>,
    <code>EAST</code>,
    <code>WEST</code>,
    <code>PLAYER</code>
    или собственное значение <code>yaw</code>.
  </p>
</details>

<hr>

<h2>🌟 Витрина кейса</h2>

<p>
  Витрина отображает выбранный предмет над свободным кейсом.
  Предмет настраивается через <code>/pncases machine</code>,
  а визуальные эффекты можно включить или отключить отдельно.
</p>

<details open>
  <summary><strong>Пример конфигурации витрины</strong></summary>

  <br>

```yaml
idle-particles:
  enabled: true
  effects: true
  style: AURORA
  theme: MAGIC
  interval_ticks: 2
  radius: 0.85
  height: 1.35
  speed: 0.14
  view_distance: 28

  item:
    material: NETHER_STAR
    name: "&aДонат кейс"
```

</details>

<table>
  <thead>
    <tr>
      <th align="left">Параметр</th>
      <th align="left">Назначение</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>enabled</code></td>
      <td>Полностью включает или отключает витрину</td>
    </tr>
    <tr>
      <td><code>effects</code></td>
      <td>Включает или отключает эффекты вокруг предмета</td>
    </tr>
    <tr>
      <td><code>style</code></td>
      <td>Определяет стиль движения частиц</td>
    </tr>
    <tr>
      <td><code>theme</code></td>
      <td>Определяет визуальную тему эффектов</td>
    </tr>
    <tr>
      <td><code>view_distance</code></td>
      <td>Максимальная дистанция отображения витрины</td>
    </tr>
  </tbody>
</table>

<hr>

<h2>💬 Голограммы</h2>

<details open>
  <summary><strong>Пример конфигурации</strong></summary>

  <br>

```yaml
hologram:
  enabled: true
  type: TEXT
  y: 1.5
  lines:
    - "&a&lДонат кейс"
    - "&7ПКМ, чтобы открыть"
```

</details>

<p>
  pnCases автоматически выбирает доступный провайдер:
</p>

<ul>
  <li><strong>FancyHolograms</strong>;</li>
  <li><strong>DecentHolograms</strong>.</li>
</ul>

<p>
  Если ни один поддерживаемый плагин голограмм не установлен,
  кейсы продолжат работать без голограмм.
</p>

<hr>

<h2>🔄 Обновления</h2>

<p>
  Проверка обновлений работает через GitHub
  и не требует дополнительной настройки в <code>config.yml</code>.
</p>

<p>При проверке используются:</p>

<ul>
  <li><code>update-manifest.json</code>;</li>
  <li>последний GitHub Release;</li>
  <li>git tags;</li>
  <li>версия из <code>plugin.yml</code>.</li>
</ul>

<p>
  Когда доступна более новая версия, администраторы с правом
  <code>pncases.admin</code> получают уведомление
  со ссылкой на скачивание.
</p>

<hr>

<h2>📁 Файлы плагина</h2>

```text
plugins/pnCases/
├── config.yml
├── messages.yml
└── data.db
```

<p>
  Старые YAML-файлы с данными автоматически переносятся в SQLite.
</p>

<hr>

<h2>📚 Документация</h2>

<ul>
  <li>
    <a href="docs/releases/">Архив предыдущих релизов</a>
  </li>
  <li>
    <a href="https://github.com/Dy6HiLa/pnCases/releases">
      Все версии pnCases
    </a>
  </li>
</ul>

<hr>

<h2 id="-поддержка">💬 Поддержка</h2>

<p>
  На официальном Discord-сервере можно:
</p>

<ul>
  <li>получить помощь с установкой;</li>
  <li>задать вопрос по настройке;</li>
  <li>сообщить об ошибке;</li>
  <li>предложить новую функцию;</li>
  <li>следить за развитием pnCases.</li>
</ul>

<p align="center">
  <a href="https://discord.gg/J2cTeTQsy8">
    <img src="https://img.shields.io/badge/Discord-Получить%20поддержку-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord pnCases">
  </a>
  <a href="https://github.com/Dy6HiLa/pnCases/issues">
    <img src="https://img.shields.io/badge/GitHub-Сообщить%20об%20ошибке-429F91?style=for-the-badge&logo=github&logoColor=white" alt="Сообщить об ошибке">
  </a>
</p>

<hr>

<p align="center">
  <strong>pnCases</strong>
  <br>
  Бесплатная и настраиваемая система кейсов для Minecraft-серверов.
</p>

<p align="center">
  <a href="https://github.com/Dy6HiLa/pnCases/releases/download/v1.4.7/pnCases-1.4.7.jar">
    Скачать последнюю версию
  </a>
  ·
  <a href="https://github.com/Dy6HiLa/pnCases">
    GitHub
  </a>
  ·
  <a href="https://discord.gg/J2cTeTQsy8">
    Discord
  </a>
</p>
