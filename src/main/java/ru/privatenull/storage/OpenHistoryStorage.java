package ru.privatenull.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OpenHistoryStorage {

    public record Entry(String playerName, String rewardName, long openedAt) {}

    private static final int LIMIT = 9;

    private final File file;
    private final YamlConfiguration cfg;

    public OpenHistoryStorage(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "open_history.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void add(String caseName, String playerName, String rewardName) {
        String base = "cases." + caseName.toLowerCase();
        List<Entry> entries = new ArrayList<>(get(caseName));
        entries.add(0, new Entry(playerName, rewardName, Instant.now().getEpochSecond()));
        if (entries.size() > LIMIT) {
            entries = new ArrayList<>(entries.subList(0, LIMIT));
        }

        cfg.set(base, null);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String path = base + "." + i;
            cfg.set(path + ".player", entry.playerName());
            cfg.set(path + ".reward", entry.rewardName());
            cfg.set(path + ".time", entry.openedAt());
        }
        save();
    }

    public synchronized List<Entry> get(String caseName) {
        ConfigurationSection sec = cfg.getConfigurationSection("cases." + caseName.toLowerCase());
        List<Entry> entries = new ArrayList<>();
        if (sec == null) return entries;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection item = sec.getConfigurationSection(key);
            if (item == null) continue;
            String player = item.getString("player", "Unknown");
            String reward = item.getString("reward", "Награда");
            long time = item.getLong("time", 0L);
            entries.add(new Entry(player, reward, time));
        }

        entries.sort(Comparator.comparingLong(Entry::openedAt).reversed());
        if (entries.size() > LIMIT) {
            return new ArrayList<>(entries.subList(0, LIMIT));
        }
        return entries;
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
