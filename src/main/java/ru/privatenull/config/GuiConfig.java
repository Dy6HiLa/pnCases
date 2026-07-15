package ru.privatenull.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import ru.privatenull.pnlibrary.text.ColorUtil;

public final class GuiConfig {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration machineDesign = MachineGuiDesign.load();
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
        YamlConfiguration defaults = loadDefaults();
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("gui.yml повреждён и не был перезаписан: " + ex.getMessage());
            backupBrokenFile();
            if (cfg == null) {
                cfg = defaults;
            } else {
                cfg.setDefaults(defaults);
            }
            return;
        }

        cfg = loaded;
        cfg.setDefaults(defaults);
        migrateLegacyMessagesGui();
        removeLegacyMachineSection();
        copyMissingDefaults(defaults);
    }

    private YamlConfiguration loadDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = plugin.getResource("gui.yml")) {
            if (input == null) throw new IOException("resource gui.yml not found");
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Не удалось прочитать встроенный gui.yml: " + ex.getMessage());
        }
        return defaults;
    }

    private void backupBrokenFile() {
        try {
            Files.copy(file.toPath(), file.toPath().resolveSibling(file.getName() + ".broken.bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupFailure) {
            plugin.getLogger().warning("Не удалось создать gui.yml.broken.bak: "
                    + backupFailure.getMessage());
        }
    }

    public String text(String path, String fallback, String... replacements) {
        if (isMachinePath(path)) {
            return color(replace(machineDesign.getString(path, fallback == null ? "" : fallback), replacements));
        }
        String raw = cfg.getString(path, fallback == null ? "" : fallback);
        return color(replace(raw, replacements));
    }

    public List<String> list(String path, List<String> fallback, String... replacements) {
        List<String> rawLines = isMachinePath(path)
                ? machineDesign.isList(path)
                    ? machineDesign.getStringList(path)
                    : fallback == null ? List.of() : fallback
                : cfg.isList(path)
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
            for (String line : formatted.split("\\R", -1)) {
                result.add(color(line));
            }
        }
        return result;
    }

    public boolean contains(String path) {
        return isMachinePath(path)
                ? machineDesign.contains(path, true)
                : cfg != null && cfg.contains(path, true);
    }

    public String raw(String path, String fallback) {
        return cfg == null ? fallback : cfg.getString(path, fallback);
    }

    public int integer(String path, int fallback) {
        return cfg == null ? fallback : cfg.getInt(path, fallback);
    }

    public double decimal(String path, double fallback) {
        return cfg == null ? fallback : cfg.getDouble(path, fallback);
    }

    public boolean bool(String path, boolean fallback) {
        return cfg == null ? fallback : cfg.getBoolean(path, fallback);
    }

    public List<Integer> integerList(String path, List<Integer> fallback) {
        if (cfg == null || !cfg.isList(path)) {
            return fallback == null ? List.of() : List.copyOf(fallback);
        }

        List<Integer> result = new ArrayList<>();
        for (Object value : cfg.getList(path, List.of())) {
            if (value instanceof Number number) {
                result.add(number.intValue());
                continue;
            }
            try {
                result.add(Integer.parseInt(String.valueOf(value)));
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("gui.yml: '" + path + "' содержит неверный номер слота: " + value);
            }
        }
        return result;
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
                || !machineDesign.getBoolean("machine.lore-style.enabled", true)) {
            return rawLines;
        }

        String[] parts = path.split("\\.");
        if (parts.length < 2) {
            return rawLines;
        }

        String section = parts[1];
        String basePath = "machine.lore-style.sections." + section;
        String color = normalizeMachineSectionColor(machineDesign.getString(basePath + ".color", defaultSectionColor(section)));
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
                decorated.add(machineDesign.getString("machine.lore-style.action-color", "&6") + " «Действие»");
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

        AtomicYamlFiles.save(cfg, file, plugin.getLogger());
    }

    private void removeLegacyMachineSection() {
        if (!cfg.contains("machine")) {
            return;
        }
        cfg.set("machine", null);
        AtomicYamlFiles.save(cfg, file, plugin.getLogger());
    }

    private static boolean isMachinePath(String path) {
        return path != null && (path.equals("machine") || path.startsWith("machine."));
    }

    private void migrateLegacyMessagesGui() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.isFile()) {
            return;
        }

        YamlConfiguration messages = new YamlConfiguration();
        try {
            messages.load(messagesFile);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Раздел gui не перенесён: messages.yml повреждён и оставлен без изменений: "
                    + ex.getMessage());
            return;
        }
        org.bukkit.configuration.ConfigurationSection legacy = messages.getConfigurationSection("gui");
        if (legacy == null) {
            return;
        }

        for (String relativePath : legacy.getKeys(true)) {
            if (legacy.isConfigurationSection(relativePath)) {
                continue;
            }
            String targetPath = "gui." + relativePath;
            if (!cfg.contains(targetPath, true)) {
                cfg.set(targetPath, legacy.get(relativePath));
            }
        }
        messages.set("gui", null);

        if (AtomicYamlFiles.save(cfg, file, plugin.getLogger())
                && AtomicYamlFiles.save(messages, messagesFile, plugin.getLogger())) {
            plugin.getLogger().info("Раздел gui перенесён из messages.yml в gui.yml.");
        }
    }
}
