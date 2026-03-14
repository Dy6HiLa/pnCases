package ru.privatenull.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.animation.AnimationType;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class PlayerPrefsStorage {

    private final File file;
    private final YamlConfiguration cfg;

    public PlayerPrefsStorage(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "player_prefs.yml");
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { throw new RuntimeException(e); }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized AnimationType getAnimation(UUID uuid) {
        String raw = cfg.getString("players." + uuid + ".animation");
        if (raw == null) return AnimationType.ANVIL;
        try { return AnimationType.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException ignored) { return AnimationType.ANVIL; }
    }

    public synchronized void setAnimation(UUID uuid, AnimationType type) {
        cfg.set("players." + uuid + ".animation", type.name());
        save();
    }

    private void save() {
        try { cfg.save(file); }
        catch (IOException e) { throw new RuntimeException(e); }
    }
}