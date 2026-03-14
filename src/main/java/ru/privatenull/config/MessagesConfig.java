package ru.privatenull.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
        cfg = YamlConfiguration.loadConfiguration(file);

        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                        plugin.getResource("messages.yml"),
                        java.nio.charset.StandardCharsets.UTF_8
                )
        );
        cfg.setDefaults(defaults);
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
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}