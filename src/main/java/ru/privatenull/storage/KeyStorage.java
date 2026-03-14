package ru.privatenull.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class KeyStorage {

    private final File file;
    private final YamlConfiguration cfg;

    public KeyStorage(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "keys.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void add(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return;
        String k = keyId.toLowerCase(Locale.ROOT);
        String path = "players." + uuid + "." + k;
        int cur = cfg.getInt(path, 0);
        cfg.set(path, cur + amount);
        save();
    }

    public synchronized int get(UUID uuid, String keyId) {
        String k = keyId.toLowerCase(Locale.ROOT);
        String path = "players." + uuid + "." + k;
        return Math.max(0, cfg.getInt(path, 0));
    }

    public synchronized boolean take(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return true;

        String k = keyId.toLowerCase(Locale.ROOT);
        String path = "players." + uuid + "." + k;

        int cur = cfg.getInt(path, 0);
        if (cur < amount) return false;

        int next = cur - amount;
        if (next <= 0) cfg.set(path, null);
        else cfg.set(path, next);

        String base = "players." + uuid;
        ConfigurationSection sec = cfg.getConfigurationSection(base);
        if (sec != null && sec.getKeys(false).isEmpty()) cfg.set(base, null);

        save();
        return true;
    }

    public synchronized Map<String, Integer> getAll(UUID uuid) {
        String base = "players." + uuid;
        ConfigurationSection sec = cfg.getConfigurationSection(base);
        Map<String, Integer> out = new LinkedHashMap<>();
        if (sec == null) return out;

        for (String keyId : sec.getKeys(false)) {
            int v = sec.getInt(keyId, 0);
            if (v > 0) out.put(keyId.toLowerCase(Locale.ROOT), v);
        }
        return out;
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}