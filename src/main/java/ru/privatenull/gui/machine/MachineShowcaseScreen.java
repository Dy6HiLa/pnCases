package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.IdleParticleSettings;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.gui.machine.MachineSlots.*;
import static ru.privatenull.util.ItemNames.readableItemName;

final class MachineShowcaseScreen {

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineShowcaseScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        Inventory inventory = Bukkit.createInventory(
                MachineGuiHolder.particles(caseName), 54,
                caseManager.getPlugin().getGuiConfig().text(
                        "machine.titles.showcase", "&8Витрина кейса", items.replacements(definition))
        );
        IdleParticleSettings settings = definition.idleParticles();
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.showcase.header", Material.ITEM_FRAME,
                "&x&4&2&9&F&9&1Раздел: Витрина кейса", List.of(
                        "", "&7Над свободным кейсом вращается предмет.",
                        "&7Эффекты можно оставить или выключить отдельно.",
                        "&7Когда игрок открывает этот блок, витрина скрывается.", ""), definition));
        inventory.setItem(SLOT_PARTICLES_TOGGLE, toggle(definition, settings));
        inventory.setItem(SLOT_PARTICLES_EFFECTS, effects(definition, settings));
        inventory.setItem(SLOT_PARTICLES_ITEM, displayItem(definition, settings));
        inventory.setItem(SLOT_PARTICLES_STYLE, style(definition, settings));
        inventory.setItem(SLOT_PARTICLES_THEME, theme(definition, settings));
        inventory.setItem(SLOT_PARTICLES_RADIUS, number(definition,
                "machine.showcase.radius", Material.ENDER_PEARL,
                "&x&4&2&9&F&9&1Радиус", settings.radius(), "блока",
                List.of("&7ЛКМ &8— &f+0.1", "&7ПКМ &8— &f-0.1", "&7Shift &8— &fшаг 0.5")));
        inventory.setItem(SLOT_PARTICLES_HEIGHT, number(definition,
                "machine.showcase.height", Material.FEATHER,
                "&x&4&2&9&F&9&1Высота", settings.height(), "блока",
                List.of("&7ЛКМ &8— &f+0.1", "&7ПКМ &8— &f-0.1", "&7Shift &8— &fшаг 0.5")));
        inventory.setItem(SLOT_PARTICLES_SPEED, number(definition,
                "machine.showcase.speed", Material.REPEATER,
                "&x&4&2&9&F&9&1Скорость", settings.speed(), "",
                List.of("&7ЛКМ &8— &fбыстрее", "&7ПКМ &8— &fмедленнее", "&7Shift &8— &fкрупный шаг")));
        inventory.setItem(SLOT_PARTICLES_INTERVAL, number(definition,
                "machine.showcase.interval", Material.COMPARATOR,
                "&x&4&2&9&F&9&1Частота", settings.intervalTicks(), "тиков",
                List.of("&7ЛКМ &8— &fреже", "&7ПКМ &8— &fчаще", "&7Shift &8— &fшаг 4 тика")));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private ItemStack toggle(CaseDefinition definition, IdleParticleSettings settings) {
        return items.configuredButton("machine.showcase.toggle",
                settings.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Витрина", List.of(
                        "", "&7Статус: " + (settings.enabled() ? "&aвключена" : "&cвыключена"),
                        "&7Если выключить, над кейсом ничего не будет.", "",
                        "&7ЛКМ &8— &fпереключить"), definition,
                "status", settings.enabled() ? "&aвключена" : "&cвыключена",
                "action", settings.enabled() ? "выключить" : "включить");
    }

    private ItemStack effects(CaseDefinition definition, IdleParticleSettings settings) {
        return items.configuredButton("machine.showcase.effects",
                settings.effectsEnabled() ? Material.GLOWSTONE_DUST : Material.GRAY_DYE,
                "&x&4&2&9&F&9&1Эффекты витрины", List.of(
                        "", "&7Статус: " + (settings.effectsEnabled() ? "&aвключены" : "&cвыключены"),
                        "&7Если выключить, останется только предмет.", "",
                        "&7ЛКМ &8— &fпереключить"), definition,
                "status", settings.effectsEnabled() ? "&aвключены" : "&cвыключены",
                "action", settings.effectsEnabled() ? "выключить" : "включить");
    }

    private ItemStack displayItem(CaseDefinition definition, IdleParticleSettings settings) {
        ItemStack configured = settings.displayItem();
        boolean custom = configured != null && !configured.getType().isAir();
        ItemStack item = custom ? configured : definition.openButton();
        if (item == null || item.getType().isAir()) item = new ItemStack(Material.CHEST);
        List<String> lore = List.of(
                "", "&7Этот предмет будет вращаться над кейсом.",
                "&7Сейчас: &f" + readableItemName(item),
                "&7Режим: " + (custom ? "&aсвой предмет" : "&fпредмет кнопки кейса"), "",
                "&7Предмет на курсоре &8— &fпоставить новый",
                "&7ПКМ без предмета &8— &fвернуть предмет кнопки кейса");
        return items.configuredButton("machine.showcase.display-item", item,
                "&x&4&2&9&F&9&1Предмет витрины", lore, definition,
                "item", readableItemName(item),
                "mode", custom ? "&aсвой предмет" : "&fпредмет кнопки кейса");
    }

    private ItemStack style(CaseDefinition definition, IdleParticleSettings settings) {
        return items.configuredButton("machine.showcase.style", settings.style().icon(),
                "&x&4&2&9&F&9&1Стиль витрины", List.of(
                        "", "&7Сейчас: &f" + settings.style().displayName(), "",
                        "&7ЛКМ &8— &fследующий стиль", "&7ПКМ &8— &fпредыдущий стиль"), definition,
                "value", settings.style().displayName());
    }

    private ItemStack theme(CaseDefinition definition, IdleParticleSettings settings) {
        return items.configuredButton("machine.showcase.theme", settings.theme().icon(),
                "&x&4&2&9&F&9&1Тема витрины", List.of(
                        "", "&7Сейчас: &f" + settings.theme().displayName(), "",
                        "&7ЛКМ &8— &fследующая тема", "&7ПКМ &8— &fпредыдущая тема"), definition,
                "value", settings.theme().displayName());
    }

    private ItemStack number(CaseDefinition definition, String path, Material material, String name,
                             Number value, String unit, List<String> controls) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Сейчас: &f" + value + (unit == null || unit.isBlank() ? "" : " " + unit));
        lore.add("");
        lore.addAll(controls);
        return items.configuredButton(path, material, name, lore, definition,
                "value", String.valueOf(value), "unit", unit == null ? "" : unit);
    }
}
