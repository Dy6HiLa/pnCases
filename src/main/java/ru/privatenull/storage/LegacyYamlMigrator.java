package ru.privatenull.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.Reward;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

final class LegacyYamlMigrator {
    private final File dataFolder;
    private final CasesDatabase database;

    LegacyYamlMigrator(File dataFolder, CasesDatabase database) {
        this.dataFolder = dataFolder;
        this.database = database;
    }

    void migrate() {
        migrateKeys();
        migratePlayerPrefs();
        migrateOpenHistory();
        migratePendingRewards();
    }

    private void migrateKeys() {
        String marker = "legacy.keys.yml";
        if (database.isMigrationApplied(marker)) return;
        ConfigurationSection players = load("keys.yml").getConfigurationSection("players");
        if (players != null) {
            for (String uuidValue : players.getKeys(false)) {
                UUID uuid = uuid(uuidValue);
                ConfigurationSection keys = players.getConfigurationSection(uuidValue);
                if (uuid == null || keys == null) continue;
                for (String keyId : keys.getKeys(false)) {
                    int amount = Math.max(0, keys.getInt(keyId, 0));
                    if (amount > 0) database.addKeys(uuid, keyId, amount);
                }
            }
        }
        database.markMigrationApplied(marker);
    }

    private void migratePlayerPrefs() {
        String marker = "legacy.player_prefs.yml";
        if (database.isMigrationApplied(marker)) return;
        YamlConfiguration yaml = load("player_prefs.yml");
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String uuidValue : players.getKeys(false)) {
                UUID uuid = uuid(uuidValue);
                String animation = yaml.getString("players." + uuidValue + ".animation");
                if (uuid == null || animation == null) continue;
                try {
                    database.setAnimation(uuid, AnimationType.valueOf(animation.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        database.markMigrationApplied(marker);
    }

    private void migrateOpenHistory() {
        String marker = "legacy.open_history.yml";
        if (database.isMigrationApplied(marker)) return;
        ConfigurationSection cases = load("open_history.yml").getConfigurationSection("cases");
        if (cases != null) {
            for (String caseName : cases.getKeys(false)) {
                ConfigurationSection entries = cases.getConfigurationSection(caseName);
                if (entries == null) continue;
                for (String entryKey : entries.getKeys(false)) {
                    ConfigurationSection entry = entries.getConfigurationSection(entryKey);
                    if (entry == null) continue;
                    database.addHistory(caseName, entry.getString("player", "Unknown"),
                            entry.getString("reward", "Reward"), Math.max(0L, entry.getLong("time", 0L)));
                }
            }
        }
        database.markMigrationApplied(marker);
    }

    private void migratePendingRewards() {
        String marker = "legacy.pending_rewards.yml";
        if (database.isMigrationApplied(marker)) return;
        YamlConfiguration yaml = load("pending_rewards.yml");
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String uuidValue : players.getKeys(false)) {
                UUID uuid = uuid(uuidValue);
                if (uuid == null) continue;
                String base = "players." + uuidValue;
                int chance = yaml.getInt(base + ".chance", 100);
                Reward reward = new Reward(chance, parseType(yaml.getString(base + ".type", "ITEM")),
                        ItemCodec.deserialize(yaml.getString(base + ".item")), yaml.getString(base + ".lpGroup"),
                        yaml.getString(base + ".lpNode"), yaml.getString(base + ".lpDuration"),
                        yaml.getDouble(base + ".vaultAmount", 0.0),
                        yaml.getInt(base + ".playerPointsAmount", 0), yaml.getString(base + ".message"),
                        yaml.getString(base + ".displayName"),
                        yaml.getString(base + ".rarity", Reward.Rarity.fromChance(chance).name()));
                database.savePending(uuid, reward);
            }
        }
        database.markMigrationApplied(marker);
    }

    private YamlConfiguration load(String name) {
        File file = new File(dataFolder, name);
        return file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Reward.Type parseType(String value) {
        try {
            return Reward.Type.valueOf(value);
        } catch (Exception ignored) {
            return Reward.Type.ITEM;
        }
    }
}
