package ru.privatenull.cases.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.cases.model.CaseGuiLayoutRules;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.ItemFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static ru.privatenull.config.ConfigValues.decimal;
import static ru.privatenull.config.ConfigValues.integer;

final class CaseViewConfigParser {

    CaseGuiLayout parseLayout(ConfigurationSection gui) {
        CaseGuiLayout defaults = CaseGuiLayout.defaults();
        if (gui == null) return defaults;

        int size = resolveSize(gui);

        int openSlot = integer(gui, defaults.openSlot(), "open_slot", "open-slot", "open_item_slot", "open-item-slot");
        int animationSlot = integer(gui, defaults.animationSlot(), "animation_slot", "animation-slot");
        int previewSlot = integer(gui, defaults.previewSlot(), "preview_slot", "preview-slot", "rewards_slot", "rewards-slot");

        ConfigurationSection decor = gui.getConfigurationSection("decor");
        ConfigurationSection history = gui.getConfigurationSection("history");
        CaseGuiLayoutRules.ResolvedSlots slots = CaseGuiLayoutRules.resolveSlots(
                size,
                openSlot, defaults.openSlot(),
                animationSlot, defaults.animationSlot(),
                previewSlot, defaults.previewSlot(),
                readSlots(history, defaults.historySlots(), "slots"),
                readSlots(decor, defaults.decorSlots(), "slots")
        );
        return new CaseGuiLayout(
                size,
                slots.openSlot(),
                slots.animationSlot(),
                slots.previewSlot(),
                slots.historySlots(),
                slots.decorSlots(),
                readItem(decor, "item", null, Material.GRAY_STAINED_GLASS_PANE, " "),
                readItem(gui, "animation-item", null, null, null),
                readItem(gui, "preview-item", null, null, null),
                readItem(history, "empty-item", null, Material.BARRIER, "&8История пуста")
        );
    }

    static int resolveSize(ConfigurationSection gui) {
        if (gui == null) return CaseGuiLayout.DEFAULT_SIZE;
        return CaseGuiLayoutRules.normalizeSize(integer(
                gui, CaseGuiLayout.DEFAULT_SIZE, "size", "rows"));
    }

    IdleParticleSettings parseIdleParticles(ConfigurationSection section) {
        IdleParticleSettings defaults = IdleParticleSettings.defaults();
        if (section == null) return defaults;

        return new IdleParticleSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getBoolean("effects", section.getBoolean("effects_enabled", defaults.effectsEnabled())),
                enumValue(section.getString("style"), defaults.style()),
                enumValue(section.getString("theme"), defaults.theme()),
                Math.max(2, integer(section, defaults.intervalTicks(), "interval_ticks", "interval-ticks", "period_ticks", "period-ticks")),
                clamp(decimal(section, defaults.radius(), "radius"), 0.25, 2.50),
                clamp(decimal(section, defaults.height(), "height"), 0.30, 3.00),
                clamp(decimal(section, defaults.speed(), "speed"), 0.02, 0.80),
                clamp(decimal(section, defaults.viewDistance(), "view_distance", "view-distance"), 4.0, 64.0),
                readItem(section, "item", defaults.displayItem(), null, null)
        );
    }

    private ItemStack readItem(ConfigurationSection root, String key, ItemStack fallback, Material material, String name) {
        if (root != null) {
            ItemStack item = ItemFactory.fromSection(root.getConfigurationSection(key));
            if (item != null) return item;
        }
        if (fallback != null) return fallback.clone();
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && name != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Integer> readSlots(ConfigurationSection section, List<Integer> fallback, String key) {
        if (section == null || !section.isList(key)) return fallback;
        List<Integer> result = new ArrayList<>();
        for (Object raw : section.getList(key, Collections.emptyList())) {
            if (raw instanceof Number number) {
                result.add(number.intValue());
            } else {
                try {
                    result.add(Integer.parseInt(String.valueOf(raw)));
                } catch (NumberFormatException ignored) {
                    // Invalid slots are skipped and the remaining layout stays usable.
                }
            }
        }
        return result;
    }

    private IdleParticleSettings.Style enumValue(String raw, IdleParticleSettings.Style fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return IdleParticleSettings.Style.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private IdleParticleSettings.Theme enumValue(String raw, IdleParticleSettings.Theme fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return IdleParticleSettings.Theme.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String normalizeEnum(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
