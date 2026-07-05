package ru.privatenull.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.animation.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.cases.model.IdleParticleSettings;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MachineGuiListener implements Listener {

    private static final int SLOT_MAIN_ANIMATION = 11;
    private static final int SLOT_MAIN_MENU = 13;
    private static final int SLOT_MAIN_HOLOGRAM = 15;
    private static final int SLOT_MAIN_PARTICLES = 22;
    private static final int SLOT_MAIN_PURCHASE = 29;
    private static final int SLOT_MAIN_PREVIEW = 31;
    private static final int SLOT_MAIN_CLOSE = 33;

    private static final int SLOT_ANIMATION_DURATION = 28;
    private static final int SLOT_ANIMATION_CYCLE = 30;
    private static final int SLOT_ANIMATION_RISE = 32;
    private static final int SLOT_ANIMATION_SPIN = 34;

    private static final int SLOT_HOLOGRAM_TOGGLE = 20;
    private static final int SLOT_HOLOGRAM_HEIGHT = 22;
    private static final int SLOT_HOLOGRAM_LINES = 24;

    private static final int SLOT_PARTICLES_TOGGLE = 18;
    private static final int SLOT_PARTICLES_EFFECTS = 20;
    private static final int SLOT_PARTICLES_ITEM = 22;
    private static final int SLOT_PARTICLES_STYLE = 24;
    private static final int SLOT_PARTICLES_THEME = 26;
    private static final int SLOT_PARTICLES_RADIUS = 29;
    private static final int SLOT_PARTICLES_HEIGHT = 31;
    private static final int SLOT_PARTICLES_SPEED = 33;
    private static final int SLOT_PARTICLES_INTERVAL = 35;

    private static final int SLOT_XP_BUY = 21;
    private static final int SLOT_XP_LEVELS = 23;

    private static final int SLOT_GUI_SIZE = 10;
    private static final int SLOT_GUI_TITLE = 12;
    private static final int SLOT_LAYOUT = 14;
    private static final int SLOT_OPEN_ITEM = 28;
    private static final int SLOT_DECOR_ITEM = 30;
    private static final int SLOT_HISTORY_EMPTY_ITEM = 32;
    private static final int SLOT_PREVIEW_CASE = 34;
    private static final int SLOT_BACK = 49;

    private static final int PLAYER_ANIMATION_SLOT = 10;
    private static final int[] FIXED_ANIMATION_SLOTS = {11, 12, 13, 14, 15, 16};

    private final CaseManager caseManager;
    private final Map<UUID, PendingTextEdit> pendingTextEdits = new ConcurrentHashMap<>();

    public MachineGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    public void openMain(Player player, String caseName) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) {
            player.sendMessage(color("&c[pnCases] Кейс не найден: &f" + caseName));
            return;
        }

        Inventory inv = Bukkit.createInventory(
                MachineGuiHolder.main(def.name()),
                54,
                color("&8Настройка кейса: " + def.name())
        );

        fillMainInventory(inv, def);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    private void fillMainInventory(Inventory inv, CaseDefinition def) {
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(4, button(Material.CHEST,
                "&x&4&2&9&F&9&1Настройка кейса",
                List.of(
                        "",
                        "&7Кейс: &f" + def.name(),
                        "&7Выбери раздел ниже.",
                        ""
                )));

        inv.setItem(SLOT_MAIN_ANIMATION, sectionButton(Material.NETHER_STAR,
                "&x&4&2&9&F&9&1Анимация",
                List.of(
                        "&7Фиксированная анимация или выбор игрока.",
                        "&7Скорость, высота и плавность открытия.",
                        "",
                        "&7Сейчас: " + currentAnimationLabel(def),
                        "",
                        "&7ЛКМ &8— &fоткрыть раздел"
                )));
        inv.setItem(SLOT_MAIN_MENU, sectionButton(Material.CRAFTING_TABLE,
                "&x&4&2&9&F&9&1Меню кейса",
                List.of(
                        "&7Размер меню, название, кнопки и декор.",
                        "&7Тут же настраивается пустая история.",
                        "",
                        "&7Размер: &f" + def.guiLayout().size() + " слотов",
                        "",
                        "&7ЛКМ &8— &fоткрыть раздел"
                )));
        inv.setItem(SLOT_MAIN_HOLOGRAM, sectionButton(Material.ARMOR_STAND,
                "&x&4&2&9&F&9&1Голограмма",
                List.of(
                        "&7Текст над кейсом и высота.",
                        "",
                        "&7Статус: " + (isHologramEnabled(def) ? "&aвключена" : "&cвыключена"),
                        "&7Высота: &f" + readHologramHeight(def),
                        "",
                        "&7ЛКМ &8— &fоткрыть раздел"
                )));
        inv.setItem(SLOT_MAIN_PARTICLES, sectionButton(def.idleParticles().theme().icon(),
                "&x&4&2&9&F&9&1Витрина кейса",
                List.of(
                        "&7Предмет над кейсом и аккуратные эффекты вокруг.",
                        "&7Витрина скрывается, когда кейс открывают.",
                        "",
                        "&7Статус: " + (def.idleParticles().enabled() ? "&aвключены" : "&cвыключены"),
                        "&7Эффекты: " + (def.idleParticles().effectsEnabled() ? "&aвключены" : "&cвыключены"),
                        "&7Стиль: &f" + def.idleParticles().style().displayName(),
                        "&7Тема: &f" + def.idleParticles().theme().displayName(),
                        "",
                        "&7ЛКМ &8— &fоткрыть раздел"
                )));
        inv.setItem(SLOT_MAIN_PURCHASE, sectionButton(Material.EXPERIENCE_BOTTLE,
                "&x&4&2&9&F&9&1Покупка за опыт",
                List.of(
                        "&7Можно включить покупку ключа за уровни.",
                        "",
                        "&7Статус: " + (isXpBuyEnabled(def) ? "&aвключена" : "&cвыключена"),
                        "&7Цена: &f" + Math.max(0, def.buyKeyWithXpLevels()) + " уровней",
                        "",
                        "&7ЛКМ &8— &fоткрыть раздел"
                )));
        inv.setItem(SLOT_MAIN_PREVIEW, sectionButton(Material.ENDER_EYE,
                "&x&4&2&9&F&9&1Предпросмотр",
                List.of(
                        "&7Открывает обычное меню кейса",
                        "&7с текущими настройками.",
                        "",
                        "&7ЛКМ &8— &fпосмотреть"
                )));
        inv.setItem(SLOT_MAIN_CLOSE, button(Material.BARRIER,
                "&cЗакрыть",
                List.of("", "&7ЛКМ &8— &fзакрыть настройку")));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MachineGuiHolder holder)) {
            return;
        }

        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (slot >= topSize) {
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
        if (slot < 0) {
            return;
        }

        CaseDefinition def = caseManager.getCaseByName(holder.caseName());
        if (def == null) {
            player.closeInventory();
            player.sendMessage(color("&c[pnCases] Кейс больше не загружен: &f" + holder.caseName()));
            return;
        }

        switch (holder.type()) {
            case MAIN -> handleMainClick(player, event, def, slot);
            case ANIMATION -> handleAnimationClick(player, event, def, slot);
            case LAYOUT -> handleLayoutClick(player, event, def, slot);
            case HOLOGRAM -> handleHologramClick(player, event, def, slot);
            case PARTICLES -> handleParticlesClick(player, event, def, slot);
            case MENU -> handleMenuClick(player, event, def, slot);
            case PURCHASE -> handlePurchaseClick(player, event, def, slot);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingTextEdit pending = pendingTextEdits.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(caseManager.getPlugin(), () -> applyTextEdit(event.getPlayer(), pending, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingTextEdits.remove(event.getPlayer().getUniqueId());
    }

    private void handleMainClick(Player player, InventoryClickEvent event, CaseDefinition def, int slot) {
        if (slot == SLOT_MAIN_CLOSE) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.2f, 0.95f);
            return;
        }

        if (slot == SLOT_MAIN_ANIMATION) {
            openAnimation(player, def.name());
            return;
        }

        if (slot == SLOT_MAIN_MENU) {
            openMenu(player, def.name());
            return;
        }

        if (slot == SLOT_MAIN_HOLOGRAM) {
            openHologram(player, def.name());
            return;
        }

        if (slot == SLOT_MAIN_PARTICLES) {
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_MAIN_PURCHASE) {
            openPurchase(player, def.name());
            return;
        }

        if (slot == SLOT_MAIN_PREVIEW) {
            caseManager.openCaseGui(player, def);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
        }
    }

    private void startTextEdit(Player player, CaseDefinition def, TextEditType type) {
        pendingTextEdits.put(player.getUniqueId(), new PendingTextEdit(def.name(), type));
        player.closeInventory();

        if (type == TextEditType.GUI_TITLE) {
            player.sendMessage(color("&x&4&2&9&F&9&1[pnCases] &fНапиши новое название меню кейса в чат."));
            player.sendMessage(color("&7Текущее: &f" + def.guiTitle()));
        } else {
            player.sendMessage(color("&x&4&2&9&F&9&1[pnCases] &fНапиши строки голограммы через &b|&f."));
            player.sendMessage(color("&7Пример: &aДонат кейс &8| &7ПКМ, чтобы открыть"));
        }
        player.sendMessage(color("&7Для отмены напиши &ccancel &7или &cотмена&7."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void applyTextEdit(Player player, PendingTextEdit pending, String message) {
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("отмена")) {
            player.sendMessage(color("&c[pnCases] Изменение отменено."));
            openMain(player, pending.caseName());
            return;
        }

        if (message.isBlank()) {
            player.sendMessage(color("&c[pnCases] Пустое значение не сохранено."));
            openMain(player, pending.caseName());
            return;
        }

        if (pending.type() == TextEditType.GUI_TITLE) {
            update(player, pending.caseName(), section -> section(section, "gui").set("title", message));
            openMain(player, pending.caseName());
            return;
        }

        List<String> lines = parseHologramLines(message);
        if (lines.isEmpty()) {
            player.sendMessage(color("&c[pnCases] Строки голограммы пустые."));
            openMain(player, pending.caseName());
            return;
        }

        update(player, pending.caseName(), section -> {
            ConfigurationSection hologram = section(section, "hologram");
            hologram.set("enabled", true);
            hologram.set("lines", lines);
        });
        openMain(player, pending.caseName());
    }

    private void openAnimation(Player player, String caseName) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) return;

        Inventory inv = Bukkit.createInventory(
                MachineGuiHolder.animation(caseName),
                54,
                color("&8Настройка анимации")
        );

        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(4, button(Material.NETHER_STAR,
                "&x&4&2&9&F&9&1Раздел: Анимация",
                List.of(
                        "",
                        "&7Выбери, кто задаёт анимацию открытия.",
                        "&7Ниже можно настроить плавность и скорость.",
                        ""
                )));
        inv.setItem(PLAYER_ANIMATION_SLOT, playerChoiceAnimationItem(def.fixedAnimation() == null));

        AnimationType[] types = AnimationType.values();
        for (int i = 0; i < types.length && i < FIXED_ANIMATION_SLOTS.length; i++) {
            AnimationType type = types[i];
            inv.setItem(FIXED_ANIMATION_SLOTS[i], fixedAnimationItem(type, type == def.fixedAnimation()));
        }

        inv.setItem(SLOT_ANIMATION_DURATION, animationNumberItem(Material.REPEATER,
                "&x&4&2&9&F&9&1Длительность",
                def.durationTicks(),
                "тиков",
                List.of("&7Сколько длится открытие кейса.", "", "&7ЛКМ &8— &f+5", "&7ПКМ &8— &f-5", "&7Shift &8— &fшаг 20")));
        inv.setItem(SLOT_ANIMATION_CYCLE, animationNumberItem(Material.COMPARATOR,
                "&x&4&2&9&F&9&1Смена предметов",
                def.cycleEveryTicks(),
                "тиков",
                List.of("&7Меньше значение &8— &fбыстрее рулетка.", "", "&7ЛКМ &8— &f+1", "&7ПКМ &8— &f-1")));
        inv.setItem(SLOT_ANIMATION_RISE, animationNumberItem(Material.FEATHER,
                "&x&4&2&9&F&9&1Высота подъёма",
                round1(def.riseBlocks()),
                "блоков",
                List.of("&7Насколько высоко поднимается награда.", "", "&7ЛКМ &8— &f+0.1", "&7ПКМ &8— &f-0.1", "&7Shift &8— &fшаг 0.5")));
        inv.setItem(SLOT_ANIMATION_SPIN, animationNumberItem(Material.ENDER_EYE,
                "&x&4&2&9&F&9&1Вращение",
                round1(def.spinDegreesPerTick()),
                "град./тик",
                List.of("&7Как быстро крутится награда.", "", "&7ЛКМ &8— &f+1", "&7ПКМ &8— &f-1", "&7Shift &8— &fшаг 10")));
        inv.setItem(SLOT_BACK, backButton());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void handleAnimationClick(Player player, InventoryClickEvent event, CaseDefinition def, int slot) {
        if (slot == SLOT_BACK) {
            openMain(player, def.name());
            return;
        }

        if (slot == PLAYER_ANIMATION_SLOT) {
            update(player, def.name(), section -> section(section, "animation").set("fixed", null));
            openAnimation(player, def.name());
            return;
        }

        if (slot == SLOT_ANIMATION_DURATION) {
            int step = event.isShiftClick() ? 20 : 5;
            if (event.isRightClick()) step = -step;
            int next = Math.max(20, Math.min(300, def.durationTicks() + step));
            update(player, def.name(), section -> section(section, "animation").set("duration_ticks", next));
            openAnimation(player, def.name());
            return;
        }

        if (slot == SLOT_ANIMATION_CYCLE) {
            int step = event.isShiftClick() ? 5 : 1;
            if (event.isRightClick()) step = -step;
            int next = Math.max(1, Math.min(40, def.cycleEveryTicks() + step));
            update(player, def.name(), section -> section(section, "animation").set("cycle_every_ticks", next));
            openAnimation(player, def.name());
            return;
        }

        if (slot == SLOT_ANIMATION_RISE) {
            double step = event.isShiftClick() ? 0.5 : 0.1;
            if (event.isRightClick()) step = -step;
            double next = clamp(round1(def.riseBlocks() + step), 0.0, 8.0);
            update(player, def.name(), section -> section(section, "animation").set("rise_blocks", next));
            openAnimation(player, def.name());
            return;
        }

        if (slot == SLOT_ANIMATION_SPIN) {
            double step = event.isShiftClick() ? 10.0 : 1.0;
            if (event.isRightClick()) step = -step;
            double next = clamp(round1(def.spinDegreesPerTick() + step), 0.0, 180.0);
            update(player, def.name(), section -> section(section, "animation").set("spin_degrees_per_tick", next));
            openAnimation(player, def.name());
            return;
        }

        AnimationType[] types = AnimationType.values();
        for (int i = 0; i < types.length && i < FIXED_ANIMATION_SLOTS.length; i++) {
            if (slot != FIXED_ANIMATION_SLOTS[i]) continue;

            AnimationType chosen = types[i];
            update(player, def.name(), section -> section(section, "animation").set("fixed", chosen.name()));
            openAnimation(player, def.name());
            return;
        }
    }

    private void openHologram(Player player, String caseName) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) return;

        Inventory inv = Bukkit.createInventory(
                MachineGuiHolder.hologram(caseName),
                54,
                color("&8Настройка голограммы")
        );

        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(4, button(Material.ARMOR_STAND,
                "&x&4&2&9&F&9&1Раздел: Голограмма",
                List.of("", "&7Настрой текст над кейсом и его высоту.", "")));
        inv.setItem(SLOT_HOLOGRAM_TOGGLE, hologramToggleItem(def));
        inv.setItem(SLOT_HOLOGRAM_HEIGHT, hologramHeightItem(def));
        inv.setItem(SLOT_HOLOGRAM_LINES, hologramLinesItem(def));
        inv.setItem(SLOT_BACK, backButton());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void handleHologramClick(Player player, InventoryClickEvent event, CaseDefinition def, int slot) {
        if (slot == SLOT_BACK) {
            openMain(player, def.name());
            return;
        }

        if (slot == SLOT_HOLOGRAM_TOGGLE) {
            boolean enabled = isHologramEnabled(def);
            update(player, def.name(), section -> section(section, "hologram").set("enabled", !enabled));
            openHologram(player, def.name());
            return;
        }

        if (slot == SLOT_HOLOGRAM_HEIGHT) {
            double step = event.isShiftClick() ? 1.0 : 0.1;
            if (event.isRightClick()) step = -step;
            double next = clamp(round1(readHologramHeight(def) + step), -5.0, 10.0);
            update(player, def.name(), section -> {
                ConfigurationSection hologram = section(section, "hologram");
                hologram.set("enabled", true);
                hologram.set("y", next);
            });
            openHologram(player, def.name());
            return;
        }

        if (slot == SLOT_HOLOGRAM_LINES) {
            startTextEdit(player, def, TextEditType.HOLOGRAM_LINES);
        }
    }

    private void openParticles(Player player, String caseName) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) return;

        Inventory inv = Bukkit.createInventory(
                MachineGuiHolder.particles(caseName),
                54,
                color("&8Витрина кейса")
        );

        IdleParticleSettings settings = def.idleParticles();
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(4, button(settings.theme().icon(),
                "&x&4&2&9&F&9&1Раздел: Витрина кейса",
                List.of(
                        "",
                        "&7Над свободным кейсом вращается предмет.",
                        "&7Эффекты можно оставить или выключить отдельно.",
                        "&7Когда игрок открывает этот блок, витрина скрывается.",
                        ""
                )));
        inv.setItem(SLOT_PARTICLES_TOGGLE, particlesToggleItem(settings));
        inv.setItem(SLOT_PARTICLES_EFFECTS, particlesEffectsItem(settings));
        inv.setItem(SLOT_PARTICLES_ITEM, particlesDisplayItem(def, settings));
        inv.setItem(SLOT_PARTICLES_STYLE, particlesStyleItem(settings));
        inv.setItem(SLOT_PARTICLES_THEME, particlesThemeItem(settings));
        inv.setItem(SLOT_PARTICLES_RADIUS, particlesNumberItem(Material.ENDER_PEARL,
                "&x&4&2&9&F&9&1Радиус", settings.radius(), "блока",
                List.of("&7ЛКМ &8— &f+0.1", "&7ПКМ &8— &f-0.1", "&7Shift &8— &fшаг 0.5")));
        inv.setItem(SLOT_PARTICLES_HEIGHT, particlesNumberItem(Material.FEATHER,
                "&x&4&2&9&F&9&1Высота", settings.height(), "блока",
                List.of("&7ЛКМ &8— &f+0.1", "&7ПКМ &8— &f-0.1", "&7Shift &8— &fшаг 0.5")));
        inv.setItem(SLOT_PARTICLES_SPEED, particlesNumberItem(Material.REPEATER,
                "&x&4&2&9&F&9&1Скорость", settings.speed(), "",
                List.of("&7ЛКМ &8— &fбыстрее", "&7ПКМ &8— &fмедленнее", "&7Shift &8— &fкрупный шаг")));
        inv.setItem(SLOT_PARTICLES_INTERVAL, particlesNumberItem(Material.COMPARATOR,
                "&x&4&2&9&F&9&1Частота", settings.intervalTicks(), "тиков",
                List.of("&7ЛКМ &8— &fреже", "&7ПКМ &8— &fчаще", "&7Shift &8— &fшаг 4 тика")));
        inv.setItem(SLOT_BACK, backButton());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void handleParticlesClick(Player player, InventoryClickEvent event, CaseDefinition def, int slot) {
        if (slot == SLOT_BACK) {
            openMain(player, def.name());
            return;
        }

        IdleParticleSettings settings = def.idleParticles();
        ItemStack cursor = event.getCursor();
        if (slot == SLOT_PARTICLES_ITEM && isRealItem(cursor)) {
            update(player, def.name(), section -> writeExactItem(section(section, "idle-particles"), "item", cursor));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_TOGGLE) {
            update(player, def.name(), section -> section(section, "idle-particles").set("enabled", !settings.enabled()));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_EFFECTS) {
            update(player, def.name(), section -> section(section, "idle-particles").set("effects", !settings.effectsEnabled()));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_ITEM) {
            if (event.isRightClick()) {
                update(player, def.name(), section -> section(section, "idle-particles").set("item", null));
                openParticles(player, def.name());
            }
            return;
        }

        if (slot == SLOT_PARTICLES_STYLE) {
            IdleParticleSettings.Style next = nextEnum(settings.style(), IdleParticleSettings.Style.values(), event.isRightClick());
            update(player, def.name(), section -> section(section, "idle-particles").set("style", next.name()));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_THEME) {
            IdleParticleSettings.Theme next = nextEnum(settings.theme(), IdleParticleSettings.Theme.values(), event.isRightClick());
            update(player, def.name(), section -> section(section, "idle-particles").set("theme", next.name()));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_RADIUS) {
            double step = event.isShiftClick() ? 0.5 : 0.1;
            if (event.isRightClick()) step = -step;
            double next = clamp(round1(settings.radius() + step), 0.25, 2.50);
            update(player, def.name(), section -> section(section, "idle-particles").set("radius", next));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_HEIGHT) {
            double step = event.isShiftClick() ? 0.5 : 0.1;
            if (event.isRightClick()) step = -step;
            double next = clamp(round1(settings.height() + step), 0.30, 3.00);
            update(player, def.name(), section -> section(section, "idle-particles").set("height", next));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_SPEED) {
            double step = event.isShiftClick() ? 0.10 : 0.02;
            if (event.isRightClick()) step = -step;
            double next = clamp(round2(settings.speed() + step), 0.02, 0.80);
            update(player, def.name(), section -> section(section, "idle-particles").set("speed", next));
            openParticles(player, def.name());
            return;
        }

        if (slot == SLOT_PARTICLES_INTERVAL) {
            int step = event.isShiftClick() ? 4 : 1;
            if (event.isRightClick()) step = -step;
            int next = Math.max(2, Math.min(40, settings.intervalTicks() + step));
            update(player, def.name(), section -> section(section, "idle-particles").set("interval_ticks", next));
            openParticles(player, def.name());
        }
    }

    private void openMenu(Player player, String caseName) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) return;

        Inventory inv = Bukkit.createInventory(
                MachineGuiHolder.menu(caseName),
                54,
                color("&8Настройка меню кейса")
        );

        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(4, button(Material.CRAFTING_TABLE,
                "&x&4&2&9&F&9&1Раздел: Меню кейса",
                List.of(
                        "",
                        "&7Размер, название, кнопки, декор и история.",
                        "&7Предмет из нижнего инвентаря можно взять на курсор",
                        "&7и нажать им по нужной кнопке.",
                        ""
                )));
        inv.setItem(SLOT_GUI_SIZE, guiSizeItem(def));
        inv.setItem(SLOT_GUI_TITLE, guiTitleItem(def));
        inv.setItem(SLOT_LAYOUT, button(Material.CRAFTING_TABLE,
                "&x&4&2&9&F&9&1Расставить слоты",
                List.of(
                        "",
                        "&7Открывает сетку меню кейса.",
                        "&7ЛКМ/ПКМ меняют роль слота.",
                        "&7Предмет на курсоре заменяет предмет роли.",
                        "",
                        "&7ЛКМ &8— &fоткрыть"
                )));
        inv.setItem(SLOT_OPEN_ITEM, copyItemButton(def.openButton(), Material.CHEST,
                "&x&4&2&9&F&9&1Кнопка кейса",
                "Предмет, по которому игрок открывает кейс."));
        inv.setItem(SLOT_DECOR_ITEM, copyItemButton(def.guiLayout().decorItem(), Material.GRAY_STAINED_GLASS_PANE,
                "&x&4&2&9&F&9&1Декор меню",
                "Предмет, которым заполняются декоративные слоты."));
        inv.setItem(SLOT_HISTORY_EMPTY_ITEM, copyItemButton(def.guiLayout().emptyHistoryItem(), Material.BARRIER,
                "&x&4&2&9&F&9&1Пустая история",
                "Предмет, который показывается, когда открытий ещё нет."));
        inv.setItem(SLOT_PREVIEW_CASE, button(Material.ENDER_EYE,
                "&x&4&2&9&F&9&1Предпросмотр меню",
                List.of("", "&7Открывает обычное меню кейса.", "", "&7ЛКМ &8— &fпосмотреть")));
        inv.setItem(SLOT_BACK, backButton());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void handleMenuClick(Player player, InventoryClickEvent event, CaseDefinition def, int slot) {
        if (slot == SLOT_BACK) {
            openMain(player, def.name());
            return;
        }

        if (slot == SLOT_GUI_SIZE) {
            int step = event.isShiftClick() ? 18 : 9;
            int current = def.guiLayout().size();
            int next = event.isRightClick() ? current - step : current + step;
            next = Math.max(9, Math.min(54, ((next + 8) / 9) * 9));
            int finalNext = next;
            update(player, def.name(), section -> section(section, "gui").set("size", finalNext));
            openMenu(player, def.name());
            return;
        }

        if (slot == SLOT_GUI_TITLE) {
            startTextEdit(player, def, TextEditType.GUI_TITLE);
            return;
        }

        if (slot == SLOT_LAYOUT) {
            openLayout(player, def.name());
            return;
        }

        if (slot == SLOT_PREVIEW_CASE) {
            caseManager.openCaseGui(player, def);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
            return;
        }

        ItemStack cursor = event.getCursor();
        if (!isRealItem(cursor)) {
            return;
        }

        if (slot == SLOT_OPEN_ITEM) {
            update(player, def.name(), section -> writeItem(section(section, "gui"), "open-item", cursor));
            openMenu(player, def.name());
            return;
        }
        if (slot == SLOT_DECOR_ITEM) {
            update(player, def.name(), section -> writeItem(section(section(section, "gui"), "decor"), "item", cursor));
            openMenu(player, def.name());
            return;
        }
        if (slot == SLOT_HISTORY_EMPTY_ITEM) {
            update(player, def.name(), section -> writeItem(section(section(section, "gui"), "history"), "empty-item", cursor));
            openMenu(player, def.name());
        }
    }

    private void openPurchase(Player player, String caseName) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) return;

        Inventory inv = Bukkit.createInventory(
                MachineGuiHolder.purchase(caseName),
                54,
                color("&8Покупка за опыт")
        );

        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(4, button(Material.EXPERIENCE_BOTTLE,
                "&x&4&2&9&F&9&1Раздел: Покупка за опыт",
                List.of(
                        "",
                        "&7Если включено, игрок может купить ключ",
                        "&7за уровни опыта прямо в меню кейса.",
                        ""
                )));
        inv.setItem(SLOT_XP_BUY, xpBuyItem(def));
        inv.setItem(SLOT_XP_LEVELS, xpLevelsItem(def));
        inv.setItem(SLOT_BACK, backButton());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void handlePurchaseClick(Player player, InventoryClickEvent event, CaseDefinition def, int slot) {
        if (slot == SLOT_BACK) {
            openMain(player, def.name());
            return;
        }

        if (slot == SLOT_XP_BUY) {
            boolean enabled = isXpBuyEnabled(def);
            update(player, def.name(), section -> section(section, "cost").set("buy_xp_enabled", !enabled));
            openPurchase(player, def.name());
            return;
        }

        if (slot == SLOT_XP_LEVELS) {
            int step = event.isShiftClick() ? 5 : 1;
            if (event.isRightClick()) step = -step;
            int next = Math.max(0, def.buyKeyWithXpLevels() + step);
            update(player, def.name(), section -> section(section, "cost").set("buy_xp_levels", next));
            openPurchase(player, def.name());
        }
    }

    private void openLayout(Player player, String caseName) {
        CaseDefinition def = caseManager.getCaseByName(caseName);
        if (def == null) return;

        Inventory inv = Bukkit.createInventory(
                MachineGuiHolder.layout(caseName),
                54,
                color("&8Разметка меню кейса")
        );

        fillLayoutInventory(inv, def);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.15f);
    }

    private void fillLayoutInventory(Inventory inv, CaseDefinition def) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, layoutSlotItem(def, i));
        }
    }

    private void refreshLayout(Player player, String caseName) {
        CaseDefinition updated = caseManager.getCaseByName(caseName);
        if (updated == null) {
            player.closeInventory();
            return;
        }

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof MachineGuiHolder holder
                && holder.type() == MachineGuiHolder.Type.LAYOUT
                && holder.caseName().equalsIgnoreCase(caseName)) {
            fillLayoutInventory(top, updated);
        } else {
            openLayout(player, caseName);
        }
    }

    private void handleLayoutClick(Player player, InventoryClickEvent event, CaseDefinition def, int slot) {
        SlotRole current = roleAt(def, slot);
        ItemStack cursor = event.getCursor();
        if (isRealItem(cursor)) {
            applyCursorItem(player, def, slot, current, cursor);
            return;
        }

        SlotRole next = event.isRightClick() ? previousRole(current) : nextRole(current);
        if ((current == SlotRole.OPEN || current == SlotRole.ANIMATION) && next != current) {
            player.sendMessage(color("&c[pnCases] Слот " + current.displayName + " нельзя удалить. Перенеси его на другой слот."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            return;
        }

        applyRole(player, def, slot, next);
    }

    private void applyCursorItem(Player player, CaseDefinition def, int slot, SlotRole role, ItemStack cursor) {
        if (role == SlotRole.EMPTY) {
            update(player, def.name(), section -> {
                ConfigurationSection gui = section(section, "gui");
                ConfigurationSection decor = section(gui, "decor");
                writeItem(decor, "item", cursor);
                setRole(gui, def, slot, SlotRole.DECOR);
            });
            refreshLayout(player, def.name());
            return;
        }

        if (role == SlotRole.DECOR) {
            update(player, def.name(), section -> writeItem(section(section(section, "gui"), "decor"), "item", cursor));
            refreshLayout(player, def.name());
            return;
        }

        if (role == SlotRole.OPEN) {
            update(player, def.name(), section -> writeItem(section(section, "gui"), "open-item", cursor));
            refreshLayout(player, def.name());
            return;
        }

        if (role == SlotRole.ANIMATION) {
            update(player, def.name(), section -> writeItem(section(section, "gui"), "animation-item", cursor));
            refreshLayout(player, def.name());
            return;
        }

        if (role == SlotRole.HISTORY) {
            update(player, def.name(), section -> writeItem(section(section(section, "gui"), "history"), "empty-item", cursor));
            refreshLayout(player, def.name());
        }
    }

    private void applyRole(Player player, CaseDefinition def, int slot, SlotRole role) {
        update(player, def.name(), section -> setRole(section(section, "gui"), def, slot, role));
        refreshLayout(player, def.name());
    }

    private void setRole(ConfigurationSection gui, CaseDefinition def, int slot, SlotRole role) {
        Set<Integer> decorSlots = readSlotSet(gui.getConfigurationSection("decor"), def.guiLayout().decorSlots());
        Set<Integer> historySlots = readSlotSet(gui.getConfigurationSection("history"), def.guiLayout().historySlots());
        decorSlots.remove(slot);
        historySlots.remove(slot);

        if (role == SlotRole.OPEN) {
            gui.set("open_slot", slot);
        } else if (role == SlotRole.ANIMATION) {
            gui.set("animation_slot", slot);
        } else if (role == SlotRole.DECOR) {
            decorSlots.add(slot);
        } else if (role == SlotRole.HISTORY) {
            historySlots.add(slot);
        }

        section(gui, "decor").set("slots", new ArrayList<>(decorSlots));
        section(gui, "history").set("slots", new ArrayList<>(historySlots));
    }

    private Set<Integer> readSlotSet(ConfigurationSection section, List<Integer> fallback) {
        if (section == null || !section.isList("slots")) {
            return new TreeSet<>(fallback);
        }

        Set<Integer> slots = new TreeSet<>();
        for (Object raw : section.getList("slots", List.of())) {
            if (raw instanceof Number number) {
                slots.add(number.intValue());
                continue;
            }
            try {
                slots.add(Integer.parseInt(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
            }
        }
        slots.removeIf(slot -> slot == null || slot < 0 || slot >= 54);
        return slots;
    }

    private SlotRole roleAt(CaseDefinition def, int slot) {
        CaseGuiLayout layout = def.guiLayout();
        if (slot == layout.openSlot()) return SlotRole.OPEN;
        if (layout.historySlots().contains(slot)) return SlotRole.HISTORY;
        if (layout.decorSlots().contains(slot)) return SlotRole.DECOR;
        if (def.fixedAnimation() == null && slot == layout.animationSlot()) return SlotRole.ANIMATION;
        return SlotRole.EMPTY;
    }

    private SlotRole nextRole(SlotRole role) {
        return switch (role) {
            case EMPTY -> SlotRole.DECOR;
            case DECOR -> SlotRole.HISTORY;
            case HISTORY -> SlotRole.OPEN;
            case OPEN -> SlotRole.ANIMATION;
            case ANIMATION -> SlotRole.EMPTY;
        };
    }

    private SlotRole previousRole(SlotRole role) {
        return switch (role) {
            case EMPTY -> SlotRole.ANIMATION;
            case ANIMATION -> SlotRole.OPEN;
            case OPEN -> SlotRole.HISTORY;
            case HISTORY -> SlotRole.DECOR;
            case DECOR -> SlotRole.EMPTY;
        };
    }

    private void update(Player player, String caseName, java.util.function.Consumer<ConfigurationSection> updater) {
        boolean ok = caseManager.updateCaseConfig(caseName, updater);
        player.playSound(player.getLocation(), ok ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_VILLAGER_NO, 0.18f, ok ? 1.35f : 1.0f);
        if (!ok) {
            player.sendMessage(color("&c[pnCases] Не удалось сохранить настройки кейса."));
        }
    }

    private void updateMain(Player player, String caseName, java.util.function.Consumer<ConfigurationSection> updater) {
        update(player, caseName, updater);
        CaseDefinition updated = caseManager.getCaseByName(caseName);
        if (updated == null) {
            return;
        }

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof MachineGuiHolder holder
                && holder.type() == MachineGuiHolder.Type.MAIN
                && holder.caseName().equalsIgnoreCase(caseName)) {
            fillMainInventory(top, updated);
        } else {
            openMain(player, caseName);
        }
    }

    private String currentAnimationLabel(CaseDefinition def) {
        AnimationType fixed = def.fixedAnimation();
        return fixed == null ? "&aвыбор игрока" : fixed.displayName();
    }

    private ItemStack animationModeItem(CaseDefinition def) {
        AnimationType fixed = def.fixedAnimation();
        Material material = fixed == null ? Material.CLOCK : fixed.icon();
        String mode = currentAnimationLabel(def);
        return button(material, "&x&4&2&9&F&9&1Анимация кейса", List.of(
                "",
                "&7Текущий режим: " + mode,
                "",
                fixed == null
                        ? "&7Кнопка выбора анимации показывается игроку."
                        : "&7У кейса зафиксирована одна анимация.",
                fixed == null
                        ? "&7Игрок выбирает анимацию сам."
                        : "&7Кнопка выбора анимации в меню кейса скрыта.",
                "",
                "&7ЛКМ &8— &fнастроить"
        ));
    }

    private ItemStack hologramToggleItem(CaseDefinition def) {
        boolean enabled = isHologramEnabled(def);
        return button(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Голограмма",
                List.of(
                        "",
                        "&7Статус: " + (enabled ? "&aвключена" : "&cвыключена"),
                        "&7Высота: &f" + readHologramHeight(def),
                        "",
                        "&7ЛКМ &8— &fпереключить"
                ));
    }

    private ItemStack hologramHeightItem(CaseDefinition def) {
        return button(Material.ARMOR_STAND, "&x&4&2&9&F&9&1Высота голограммы", List.of(
                "",
                "&7Текущая высота: &f" + readHologramHeight(def),
                "",
                "&7ЛКМ &8— &f+0.1",
                "&7ПКМ &8— &f-0.1",
                "&7Shift + ЛКМ &8— &f+1.0",
                "&7Shift + ПКМ &8— &f-1.0"
        ));
    }

    private ItemStack particlesToggleItem(IdleParticleSettings settings) {
        return button(settings.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Витрина",
                List.of(
                        "",
                        "&7Статус: " + (settings.enabled() ? "&aвключена" : "&cвыключена"),
                        "&7Если выключить, над кейсом ничего не будет.",
                        "",
                        "&7ЛКМ &8— &fпереключить"
                ));
    }

    private ItemStack particlesEffectsItem(IdleParticleSettings settings) {
        return button(settings.effectsEnabled() ? Material.AMETHYST_SHARD : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Эффекты витрины",
                List.of(
                        "",
                        "&7Статус: " + (settings.effectsEnabled() ? "&aвключены" : "&cвыключены"),
                        "&7Если выключить, останется только предмет.",
                        "",
                        "&7ЛКМ &8— &fпереключить"
                ));
    }

    private ItemStack particlesDisplayItem(CaseDefinition def, IdleParticleSettings settings) {
        ItemStack configured = settings.displayItem();
        boolean custom = configured != null && !configured.getType().isAir();
        ItemStack item = custom ? configured : def.openButton();
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(Material.CHEST);
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Этот предмет будет вращаться над кейсом.");
        lore.add("&7Сейчас: &f" + readableItemName(item));
        lore.add("&7Режим: " + (custom ? "&aсвой предмет" : "&fпредмет кнопки кейса"));
        lore.add("");
        lore.add("&7Предмет на курсоре &8— &fпоставить новый");
        lore.add("&7ПКМ без предмета &8— &fвернуть предмет кнопки кейса");
        return button(item, "&x&4&2&9&F&9&1Предмет витрины", lore);
    }

    private ItemStack particlesStyleItem(IdleParticleSettings settings) {
        return button(settings.style().icon(),
                "&x&4&2&9&F&9&1Стиль витрины",
                List.of(
                        "",
                        "&7Сейчас: &f" + settings.style().displayName(),
                        "",
                        "&7ЛКМ &8— &fследующий стиль",
                        "&7ПКМ &8— &fпредыдущий стиль"
                ));
    }

    private ItemStack particlesThemeItem(IdleParticleSettings settings) {
        return button(settings.theme().icon(),
                "&x&4&2&9&F&9&1Тема витрины",
                List.of(
                        "",
                        "&7Сейчас: &f" + settings.theme().displayName(),
                        "",
                        "&7ЛКМ &8— &fследующая тема",
                        "&7ПКМ &8— &fпредыдущая тема"
                ));
    }

    private ItemStack particlesNumberItem(Material material, String name, Number value, String unit, List<String> controls) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Сейчас: &f" + value + (unit == null || unit.isBlank() ? "" : " " + unit));
        lore.add("");
        lore.addAll(controls);
        return button(material, name, lore);
    }

    private ItemStack xpBuyItem(CaseDefinition def) {
        boolean enabled = isXpBuyEnabled(def);
        return button(enabled ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                "&x&4&2&9&F&9&1Покупка за опыт",
                List.of(
                        "",
                        "&7Статус: " + (enabled ? "&aвключена" : "&cвыключена"),
                        "&7Цена: &f" + Math.max(0, def.buyKeyWithXpLevels()) + " уровней",
                        "",
                        "&7ЛКМ &8— &fпереключить"
                ));
    }

    private ItemStack xpLevelsItem(CaseDefinition def) {
        return button(Material.EXPERIENCE_BOTTLE,
                "&x&4&2&9&F&9&1Цена покупки за опыт",
                List.of(
                        "",
                        "&7Текущая цена: &f" + Math.max(0, def.buyKeyWithXpLevels()) + " уровней",
                        "&7Статус покупки: " + (isXpBuyEnabled(def) ? "&aвключена" : "&cвыключена"),
                        "",
                        "&7ЛКМ &8— &f+1 уровень",
                        "&7ПКМ &8— &f-1 уровень",
                        "&7Shift &8— &fшаг 5 уровней"
                ));
    }

    private ItemStack guiSizeItem(CaseDefinition def) {
        int rows = def.guiLayout().size() / 9;
        return button(Material.CHEST, "&x&4&2&9&F&9&1Размер меню", List.of(
                "",
                "&7Текущий размер: &f" + def.guiLayout().size() + " слотов",
                "&7Рядов: &f" + rows,
                "",
                "&7ЛКМ &8— &f+1 ряд",
                "&7ПКМ &8— &f-1 ряд",
                "&7Shift &8— &fшаг 2 ряда"
        ));
    }

    private ItemStack animationNumberItem(Material material, String name, Number value, String unit, List<String> controls) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Значение: &f" + value + " " + unit);
        lore.add("");
        lore.addAll(controls);
        return button(material, name, lore);
    }

    private ItemStack guiTitleItem(CaseDefinition def) {
        return button(Material.NAME_TAG,
                "&x&4&2&9&F&9&1Название меню",
                List.of(
                        "",
                        "&7Сейчас:",
                        "&f" + def.guiTitle(),
                        "",
                        "&7ЛКМ &8— &fнаписать новое название в чат"
                ));
    }

    private ItemStack hologramLinesItem(CaseDefinition def) {
        List<String> lines = readHologramLines(def);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Сейчас:");
        if (lines.isEmpty()) {
            lore.add("&8строк нет");
        } else {
            for (String line : lines) {
                lore.add("&f" + line);
            }
        }
        lore.add("");
        lore.add("&7ЛКМ &8— &fнаписать строки в чат");
        lore.add("&7Разделяй строки знаком &b|");
        return button(Material.OAK_SIGN, "&x&4&2&9&F&9&1Текст голограммы", lore);
    }

    private ItemStack layoutSlotItem(CaseDefinition def, int slot) {
        SlotRole role = roleAt(def, slot);
        ItemStack item = layoutRoleItem(def, role);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Слот: &f" + slot);
        lore.add("&7Роль: &f" + role.displayName);
        if (role != SlotRole.EMPTY) {
            lore.add("&7Предмет: &f" + readableItemName(item));
        }
        if (slot >= def.guiLayout().size()) {
            lore.add("&eЭтот слот сохранится, но появится только");
            lore.add("&eкогда размер меню будет больше.");
        }
        if (role == SlotRole.ANIMATION && def.fixedAnimation() != null) {
            lore.add("&cКнопка скрыта, потому что анимация кейса зафиксирована.");
        }
        lore.add("");
        lore.add("&7ЛКМ &8— &fследующая роль");
        lore.add("&7ПКМ &8— &fпредыдущая роль");
        lore.add("&7Предмет на курсоре &8— &fзаменить предмет роли");

        return button(item, role.color + role.displayName, lore);
    }

    private ItemStack layoutRoleItem(CaseDefinition def, SlotRole role) {
        ItemStack item = switch (role) {
            case OPEN -> def.openButton();
            case ANIMATION -> def.guiLayout().animationItem() == null ? new ItemStack(Material.CLOCK) : def.guiLayout().animationItem();
            case HISTORY -> def.guiLayout().emptyHistoryItem();
            case DECOR -> def.guiLayout().decorItem();
            case EMPTY -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        };

        Material fallback = switch (role) {
            case OPEN -> Material.CHEST;
            case ANIMATION -> Material.CLOCK;
            case HISTORY -> Material.BARRIER;
            case DECOR -> Material.GRAY_STAINED_GLASS_PANE;
            case EMPTY -> Material.BLACK_STAINED_GLASS_PANE;
        };

        if (item == null || item.getType().isAir()) {
            return new ItemStack(fallback);
        }
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

    private ItemStack playerChoiceAnimationItem(boolean selected) {
        return selectable(Material.COMPASS, selected, "&aВыбор игрока", List.of(
                "",
                "&7Игрок сам выбирает анимацию в меню кейса.",
                "&7Если выбрать этот режим, кнопка анимации вернётся."
        ));
    }

    private ItemStack fixedAnimationItem(AnimationType type, boolean selected) {
        return selectable(type.icon(), selected, type.displayName(), List.of(
                "",
                "&7Кейс всегда открывается этой анимацией.",
                "&7Кнопка выбора анимации у игрока будет скрыта."
        ));
    }

    private ItemStack selectable(Material material, boolean selected, String name, List<String> lore) {
        ItemStack item = button(material, (selected ? "&a◆ " : "&7◇ ") + name, lore);
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack copyItemButton(ItemStack source, Material fallback, String title, String hint) {
        ItemStack item = source == null || source.getType().isAir() ? new ItemStack(fallback) : source.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7" + hint);
        lore.add("&7Чтобы заменить, возьми новый предмет на курсор");
        lore.add("&7и нажми им по этой кнопке.");
        lore.add("");
        lore.add("&7Сейчас: &f" + readableItemName(item));
        meta.setDisplayName(color(title));
        meta.setLore(lore.stream().map(MachineGuiListener::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack sectionButton(Material material, String name, List<String> lore) {
        List<String> formatted = new ArrayList<>();
        formatted.add("");
        formatted.addAll(lore);
        formatted.add("");
        return button(material, name, formatted);
    }

    private ItemStack backButton() {
        return button(Material.ARROW, "&fНазад", List.of("", "&7ЛКМ &8— &fв главное меню"));
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return button(new ItemStack(material), name, lore);
    }

    private static ItemStack button(ItemStack source, String name, List<String> lore) {
        ItemStack item = source == null || source.getType().isAir() ? new ItemStack(Material.STONE_BUTTON) : source.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(MachineGuiListener::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material, String name, List<String> lore) {
        return button(material, name, lore);
    }

    private void fill(Inventory inv, ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, item.clone());
        }
    }

    private double readHologramHeight(CaseDefinition def) {
        ConfigurationSection section = caseManager.getCaseSection(def.name());
        ConfigurationSection hologram = section == null ? null : section.getConfigurationSection("hologram");
        return hologram == null ? 1.5 : round1(hologram.getDouble("y", hologram.getDouble("height", 1.5)));
    }

    private List<String> readHologramLines(CaseDefinition def) {
        ConfigurationSection section = caseManager.getCaseSection(def.name());
        ConfigurationSection hologram = section == null ? null : section.getConfigurationSection("hologram");
        if (hologram == null) {
            return List.of();
        }
        List<String> lines = hologram.getStringList("lines");
        if (!lines.isEmpty()) {
            return lines;
        }
        String line = hologram.getString("line");
        return line == null || line.isBlank() ? List.of() : List.of(line);
    }

    private List<String> parseHologramLines(String message) {
        List<String> lines = new ArrayList<>();
        for (String part : message.split("\\|")) {
            String line = part.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private boolean isHologramEnabled(CaseDefinition def) {
        ConfigurationSection section = caseManager.getCaseSection(def.name());
        ConfigurationSection hologram = section == null ? null : section.getConfigurationSection("hologram");
        return hologram != null && hologram.getBoolean("enabled", false);
    }

    private boolean isXpBuyEnabled(CaseDefinition def) {
        ConfigurationSection section = caseManager.getCaseSection(def.name());
        ConfigurationSection cost = section == null ? null : section.getConfigurationSection("cost");
        if (cost == null) {
            return false;
        }
        if (cost.contains("buy_xp_enabled")) {
            return cost.getBoolean("buy_xp_enabled", def.buyKeyWithXpLevels() > 0);
        }
        if (cost.contains("buy-xp-enabled")) {
            return cost.getBoolean("buy-xp-enabled", def.buyKeyWithXpLevels() > 0);
        }
        return def.buyKeyWithXpLevels() > 0;
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        return section == null ? parent.createSection(path) : section;
    }

    private static void writeItem(ConfigurationSection parent, String key, ItemStack source) {
        parent.set(key, null);
        ConfigurationSection section = parent.createSection(key);
        ItemStack item = source.clone();
        item.setAmount(Math.max(1, item.getAmount()));

        section.set("material", item.getType().name());
        if (item.getAmount() > 1) {
            section.set("amount", item.getAmount());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (meta.hasDisplayName()) {
            section.set("name", meta.getDisplayName());
        }
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            section.set("lore", meta.getLore());
        }
        if (!meta.getEnchants().isEmpty()) {
            ConfigurationSection enchants = section.createSection("enchantments");
            meta.getEnchants().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getKey().getKey()))
                    .forEach(entry -> enchants.set(entry.getKey().getKey().getKey(), entry.getValue()));
        }
    }

    private static void writeExactItem(ConfigurationSection parent, String key, ItemStack source) {
        ItemStack item = source.clone();
        item.setAmount(1);
        writeItem(parent, key, item);

        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            return;
        }

        try {
            section.set("item_data", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        } catch (IllegalArgumentException ignored) {
            section.set("item_data", null);
        }
    }

    private static boolean isRealItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T extends Enum<T>> T nextEnum(T current, T[] values, boolean backwards) {
        if (values.length == 0) {
            return current;
        }
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                index = i;
                break;
            }
        }
        index = backwards
                ? (index - 1 + values.length) % values.length
                : (index + 1) % values.length;
        return values[index];
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private static String readableItemName(ItemStack item) {
        if (item == null) {
            return "нет";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return readableMaterialName(item.getType());
    }

    private static String readableMaterialName(Material material) {
        return switch (material) {
            case BARRIER -> "Барьер";
            case CHEST -> "Сундук";
            case ENDER_CHEST -> "Эндер-сундук";
            case TRAPPED_CHEST -> "Сундук-ловушка";
            case GRAY_STAINED_GLASS_PANE -> "Серая стеклянная панель";
            case BLACK_STAINED_GLASS_PANE -> "Чёрная стеклянная панель";
            case LIME_DYE -> "Лаймовый краситель";
            case GRAY_DYE -> "Серый краситель";
            case EXPERIENCE_BOTTLE -> "Пузырёк опыта";
            case GLASS_BOTTLE -> "Пустая бутылочка";
            case ARMOR_STAND -> "Стойка для брони";
            case CRAFTING_TABLE -> "Верстак";
            case NAME_TAG -> "Бирка";
            case OAK_SIGN -> "Дубовая табличка";
            case ENDER_EYE -> "Око Эндера";
            case NETHER_STAR -> "Звезда Незера";
            case CLOCK -> "Часы";
            case COMPASS -> "Компас";
            case REPEATER -> "Повторитель";
            case COMPARATOR -> "Компаратор";
            case FEATHER -> "Перо";
            case EMERALD_BLOCK -> "Изумрудный блок";
            case WRITABLE_BOOK -> "Книга с пером";
            case EMERALD -> "Изумруд";
            case GOLD_INGOT -> "Золотой слиток";
            case DIAMOND -> "Алмаз";
            case ANVIL -> "Наковальня";
            case TNT -> "Динамит";
            case SLIME_BALL -> "Сгусток слизи";
            case ECHO_SHARD -> "Эхо-осколок";
            case ARROW -> "Стрела";
            default -> material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        };
    }

    private enum SlotRole {
        EMPTY("Пусто", "&8"),
        DECOR("Декор", "&7"),
        HISTORY("История", "&e"),
        OPEN("Открытие кейса", "&a"),
        ANIMATION("Кнопка анимации", "&b");

        private final String displayName;
        private final String color;

        SlotRole(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    private enum TextEditType {
        GUI_TITLE,
        HOLOGRAM_LINES
    }

    private record PendingTextEdit(String caseName, TextEditType type) {
    }
}
