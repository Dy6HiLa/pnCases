package ru.privatenull.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.model.Reward;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PendingRewardStorage {

    private final File file;
    private final YamlConfiguration cfg;

    public PendingRewardStorage(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "pending_rewards.yml");
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { throw new RuntimeException(e); }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save(UUID uuid, Reward reward) {
        String base = "players." + uuid;
        cfg.set(base + ".type",   reward.type().name());
        cfg.set(base + ".chance", reward.chance());
        if (reward.displayName() != null) cfg.set(base + ".displayName", reward.displayName());
        if (reward.message()  != null) cfg.set(base + ".message", reward.message());
        if (reward.lpGroup() != null) cfg.set(base + ".lpGroup", reward.lpGroup());
        if (reward.lpNode() != null) cfg.set(base + ".lpNode", reward.lpNode());
        if (reward.lpDuration() != null) cfg.set(base + ".lpDuration", reward.lpDuration());

        if (reward.type() == Reward.Type.ITEM && reward.item() != null) {
            try {
                String b64 = Base64.getEncoder().encodeToString(reward.item().serializeAsBytes());
                cfg.set(base + ".item", b64);
            } catch (Exception ignored) {
            }
        }
        save();
    }

    public synchronized Reward load(UUID uuid) {
        String base = "players." + uuid;
        if (!cfg.contains(base + ".type")) return null;

        String typeStr = cfg.getString(base + ".type", "ITEM");
        Reward.Type type;
        try { type = Reward.Type.valueOf(typeStr); }
        catch (Exception e) { type = Reward.Type.ITEM; }

        int chance = cfg.getInt(base + ".chance", 100);
        String displayName = cfg.getString(base + ".displayName");
        String message = cfg.getString(base + ".message");
        String lpGroup = cfg.getString(base + ".lpGroup");
        String lpNode = cfg.getString(base + ".lpNode");
        String lpDuration = cfg.getString(base + ".lpDuration");

        ItemStack item = null;
        if (type == Reward.Type.ITEM) {
            String b64 = cfg.getString(base + ".item");
            if (b64 != null) {
                try { item = ItemStack.deserializeBytes(Base64.getDecoder().decode(b64)); }
                catch (Exception ignored) {
                }
            }
        }

        return new Reward(chance, type, item, lpGroup, lpNode, lpDuration, message, displayName);
    }

    public synchronized void clear(UUID uuid) {
        cfg.set("players." + uuid, null);
        save();
    }

    public synchronized Set<UUID> getAll() {
        Set<UUID> uuids = new HashSet<>();
        ConfigurationSection sec = cfg.getConfigurationSection("players");
        if (sec == null) return uuids;
        for (String key : sec.getKeys(false)) {
            try { uuids.add(UUID.fromString(key)); }
            catch (Exception ignored) {}
        }
        return uuids;
    }

    private void save() {
        try { cfg.save(file); }
        catch (IOException e) { throw new RuntimeException(e); }
    }
}