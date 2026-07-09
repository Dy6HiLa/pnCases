package ru.privatenull.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import ru.privatenull.util.ColorUtil;

public final class GuiConfig {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gui.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("gui.yml"),
                StandardCharsets.UTF_8
        )) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            cfg.setDefaults(defaults);
            copyMissingDefaults(defaults);
        } catch (IOException | NullPointerException ex) {
            plugin.getLogger().warning("Не удалось загрузить defaults для gui.yml: " + ex.getMessage());
        }
    }

    public String text(String path, String fallback, String... replacements) {
        String raw = cfg.getString(path, fallback == null ? "" : fallback);
        return color(replace(raw, replacements));
    }

    public List<String> list(String path, List<String> fallback, String... replacements) {
        List<String> rawLines = cfg.isList(path)
                ? cfg.getStringList(path)
                : fallback == null ? List.of() : fallback;
        if (path.startsWith("machine.")) {
            rawLines = removeMachineSectionHeadings(rawLines);
        }
        List<String> result = new ArrayList<>();
        for (String raw : decorateMachineLore(path, rawLines)) {
            String formatted = replace(raw, replacements);
            if (path.startsWith("machine.")) {
                formatted = normalizeMachineLore(formatted);
            }
            result.add(color(formatted));
        }
        return result;
    }

    public boolean contains(String path) {
        return cfg != null && cfg.contains(path, true);
    }

    private String replace(String raw, String... replacements) {
        String value = raw == null ? "" : raw;
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be key-value pairs");
        }
        for (int i = 0; i < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return value;
    }

    private List<String> decorateMachineLore(String path, List<String> rawLines) {
        if (!path.startsWith("machine.")
                || !cfg.getBoolean("machine.lore-style.enabled", true)) {
            return rawLines;
        }

        String[] parts = path.split("\\.");
        if (parts.length < 2) {
            return rawLines;
        }

        String section = parts[1];
        String basePath = "machine.lore-style.sections." + section;
        String color = normalizeMachineSectionColor(cfg.getString(basePath + ".color", defaultSectionColor(section)));
        if (color == null || color.isBlank()) {
            return rawLines;
        }

        List<String> decorated = new ArrayList<>(rawLines.size() + 7);
        int start = 0;
        if (!rawLines.isEmpty() && (rawLines.get(0) == null || rawLines.get(0).isBlank())) {
            decorated.add(rawLines.get(0));
            start = 1;
        }

        decorated.add(color + " «Основное»");
        boolean actionAdded = false;
        for (int index = start; index < rawLines.size(); index++) {
            String line = rawLines.get(index);
            String plain = line == null ? "" : line.replaceAll("(?i)&[0-9a-fk-or]", "");
            boolean actionLine = plain.contains("ЛКМ") || plain.contains("ПКМ") || plain.contains("Shift")
                    || plain.contains("Предмет на курсоре");
            if (actionLine && !actionAdded) {
                addSeparator(decorated);
                decorated.add(cfg.getString("machine.lore-style.action-color", "&6") + " «Действие»");
                actionAdded = true;
            }
            decorated.add(line);
        }
        return decorated;
    }

    private static List<String> removeMachineSectionHeadings(List<String> rawLines) {
        List<String> cleaned = new ArrayList<>(rawLines.size());
        for (String line : rawLines) {
            if (line != null && line.contains("«") && line.contains("»")) {
                continue;
            }
            cleaned.add(line);
        }
        return cleaned;
    }

    private static void addSeparator(List<String> lines) {
        if (lines.isEmpty() || lines.get(lines.size() - 1) == null || !lines.get(lines.size() - 1).isBlank()) {
            lines.add("");
        }
    }

    private static String normalizeMachineLore(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = value
                .replaceAll("(?i)&#[0-9a-f]{6}(?=\\s*«)", "&e")
                .replaceAll("(?i)&x(?:&[0-9a-f]){6}(?=\\s*«)", "&e");
        if (normalized.contains("«") && normalized.contains("»")) {
            return normalized.replaceFirst("(?i)^\\s*(?:&#[0-9a-f]{6}|&x(?:&[0-9a-f]){6}|&[0-9a-f])?\\s*", "&6 ");
        }

        String plain = normalized.replaceAll("(?i)&[0-9a-fk-or]", "");
        boolean actionLine = plain.contains("ЛКМ") || plain.contains("ПКМ") || plain.contains("Shift")
                || plain.contains("Предмет на курсоре");
        if (actionLine) {
            String action = normalized
                    .replace("&8—", " - ")
                    .replace("—", " - ")
                    .replaceAll("^\\s*&[7f]-\\s*", "")
                    .replaceFirst("^\\s*&[7f]\\s*", "")
                    .replaceAll("\\s{2,}", " ");
            return "&f- " + action.trim();
        }
        if (normalized.matches("^\\s*&[7f]-.*")) {
            return normalized.replaceFirst("^\\s*&[7f]-\\s*", "&f- ");
        }
        if (normalized.matches("^\\s*&[7f].*")) {
            return normalized.replaceFirst("^\\s*&[7f]\\s*", "&f- ");
        }
        return normalized;
    }

    private static String normalizeMachineSectionColor(String color) {
        if (color == null || color.isBlank()) {
            return color;
        }
        if (color.matches("(?i)&#[0-9a-f]{6}") || color.matches("(?i)&x(?:&[0-9a-f]){6}")) {
            return "&e";
        }
        return color;
    }

    private static String defaultSectionTitle(String section) {
        return switch (section) {
            case "animation" -> "Анимация";
            case "hologram" -> "Голограмма";
            case "showcase" -> "Витрина";
            case "menu" -> "Меню кейса";
            case "purchase" -> "Покупка";
            case "toggles" -> "Быстрые настройки";
            case "layout" -> "Разметка";
            case "buttons" -> "Навигация";
            default -> "Настройка";
        };
    }

    private static String defaultSectionColor(String section) {
        return switch (section) {
            case "animation" -> "&x&8&2&D&C&F&F";
            case "hologram" -> "&x&9&6&B&7&F&F";
            case "showcase" -> "&x&9&A&E&F&8&A";
            case "menu", "purchase" -> "&x&F&F&C&6&7&A";
            case "toggles" -> "&x&F&2&7&E&C&8";
            case "layout", "buttons" -> "&x&6&E&A&8&F&F";
            default -> "&x&4&D&D&B&F&F";
        };
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }

    private void copyMissingDefaults(YamlConfiguration defaults) {
        boolean changed = false;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || cfg.contains(path, true)) {
                continue;
            }
            cfg.set(path, defaults.get(path));
            changed = true;
        }

        if (!changed) {
            return;
        }

        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось обновить gui.yml новыми настройками: " + ex.getMessage());
        }
    }
}
