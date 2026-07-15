package ru.privatenull.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class MessagesConfig {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration defaults = loadDefaults();
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("messages.yml повреждён и не был перезаписан: " + ex.getMessage());
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
        copyMissingDefaults(defaults);
    }

    private YamlConfiguration loadDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = plugin.getResource("messages.yml")) {
            if (input == null) throw new IOException("resource messages.yml not found");
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Не удалось прочитать встроенный messages.yml: " + ex.getMessage());
        }
        return defaults;
    }

    private void backupBrokenFile() {
        try {
            Files.copy(file.toPath(), file.toPath().resolveSibling(file.getName() + ".broken.bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupFailure) {
            plugin.getLogger().warning("Не удалось создать messages.yml.broken.bak: "
                    + backupFailure.getMessage());
        }
    }

    public String get(String path, String... replacements) {
        String prefix = color(cfg.getString("prefix", ""));
        String raw = cfg.getString(path, "&c[missing: " + path + "]");

        raw = raw.replace("{prefix}", prefix);

        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be key-value pairs");
        }
        for (int i = 0; i < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }

        return color(raw);
    }

    public String getOr(String path, String fallbackPath, String... replacements) {
        String prefix = color(cfg.getString("prefix", ""));
        String raw = cfg.isString(path)
                ? cfg.getString(path, "")
                : cfg.getString(fallbackPath, "&c[missing: " + path + "]");

        raw = raw.replace("{prefix}", prefix);

        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be key-value pairs");
        }
        for (int i = 0; i < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }

        return color(raw);
    }

    public List<String> getList(String path, String... replacements) {
        String prefix = color(cfg.getString("prefix", ""));

        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be key-value pairs");
        }

        List<String> lines = cfg.getStringList(path);
        List<String> result = new ArrayList<>(lines.size());

        for (String raw : lines) {
            raw = raw.replace("{prefix}", prefix);
            for (int i = 0; i < replacements.length; i += 2) {
                raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
            result.add(color(raw));
        }

        return result;
    }

    private static String color(String s) {
        return ColorUtil.colorize(s);
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

}
