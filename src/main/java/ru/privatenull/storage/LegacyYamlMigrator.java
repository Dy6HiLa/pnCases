package ru.privatenull.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.privatenull.cases.model.Reward;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

final class LegacyYamlMigrator {

    private final File dataFolder;
    private final Connection connection;

    LegacyYamlMigrator(File dataFolder, Connection connection) {
        this.dataFolder = dataFolder;
        this.connection = connection;
    }

    void migrate() throws SQLException {
        inTransaction(this::migrateKeys);
        inTransaction(this::migratePlayerPrefs);
        inTransaction(this::migrateOpenHistory);
        inTransaction(this::migratePendingRewards);
    }

    private void inTransaction(Migration migration) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            migration.run();
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void migrateKeys() throws SQLException {
        String migration = "legacy.keys.yml";
        if (isMigrated(migration)) return;
        File file = legacyFile("keys.yml");
        if (!file.exists()) {
            markMigrated(migration);
            return;
        }

        ConfigurationSection players = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
        if (players != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_keys(player_uuid, key_id, amount)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid, key_id) DO UPDATE SET amount = player_keys.amount + excluded.amount
                    """)) {
                for (String uuid : players.getKeys(false)) {
                    if (!isUuid(uuid)) continue;
                    ConfigurationSection keys = players.getConfigurationSection(uuid);
                    if (keys == null) continue;
                    for (String keyId : keys.getKeys(false)) {
                        int amount = Math.max(0, keys.getInt(keyId, 0));
                        if (amount <= 0) continue;
                        statement.setString(1, uuid);
                        statement.setString(2, keyId.toLowerCase(Locale.ROOT));
                        statement.setInt(3, amount);
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            }
        }
        markMigrated(migration);
    }

    private void migratePlayerPrefs() throws SQLException {
        String migration = "legacy.player_prefs.yml";
        if (isMigrated(migration)) return;
        File file = legacyFile("player_prefs.yml");
        if (!file.exists()) {
            markMigrated(migration);
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR REPLACE INTO player_prefs(player_uuid, animation)
                    VALUES (?, ?)
                    """)) {
                for (String uuid : players.getKeys(false)) {
                    if (!isUuid(uuid)) continue;
                    String animation = yaml.getString("players." + uuid + ".animation");
                    if (animation == null || animation.isBlank()) continue;
                    statement.setString(1, uuid);
                    statement.setString(2, animation.toUpperCase(Locale.ROOT));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
        markMigrated(migration);
    }

    private void migrateOpenHistory() throws SQLException {
        String migration = "legacy.open_history.yml";
        if (isMigrated(migration)) return;
        File file = legacyFile("open_history.yml");
        if (!file.exists()) {
            markMigrated(migration);
            return;
        }

        ConfigurationSection cases = YamlConfiguration.loadConfiguration(file).getConfigurationSection("cases");
        if (cases != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO open_history(case_name, player_name, reward_name, opened_at)
                    VALUES (?, ?, ?, ?)
                    """)) {
                for (String caseName : cases.getKeys(false)) {
                    ConfigurationSection entries = cases.getConfigurationSection(caseName);
                    if (entries == null) continue;
                    for (String entryKey : entries.getKeys(false)) {
                        ConfigurationSection entry = entries.getConfigurationSection(entryKey);
                        if (entry == null) continue;
                        statement.setString(1, caseName.toLowerCase(Locale.ROOT));
                        statement.setString(2, entry.getString("player", "Unknown"));
                        statement.setString(3, entry.getString("reward", "Награда"));
                        statement.setLong(4, Math.max(0L, entry.getLong("time", 0L)));
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            }
        }
        markMigrated(migration);
    }

    private void migratePendingRewards() throws SQLException {
        String migration = "legacy.pending_rewards.yml";
        if (isMigrated(migration)) return;
        File file = legacyFile("pending_rewards.yml");
        if (!file.exists()) {
            markMigrated(migration);
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR REPLACE INTO pending_rewards(
                        player_uuid, type, chance, rarity, item_base64, lp_group, lp_node, lp_duration,
                        vault_amount, player_points_amount, message, display_name
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (String uuid : players.getKeys(false)) {
                    if (!isUuid(uuid)) continue;
                    String base = "players." + uuid;
                    int chance = yaml.getInt(base + ".chance", 100);
                    statement.setString(1, uuid);
                    statement.setString(2, yaml.getString(base + ".type", "ITEM"));
                    statement.setInt(3, chance);
                    statement.setString(4, yaml.getString(base + ".rarity", Reward.Rarity.fromChance(chance).name()));
                    statement.setString(5, yaml.getString(base + ".item"));
                    statement.setString(6, yaml.getString(base + ".lpGroup"));
                    statement.setString(7, yaml.getString(base + ".lpNode"));
                    statement.setString(8, yaml.getString(base + ".lpDuration"));
                    statement.setDouble(9, yaml.getDouble(base + ".vaultAmount", 0.0));
                    statement.setInt(10, yaml.getInt(base + ".playerPointsAmount", 0));
                    statement.setString(11, yaml.getString(base + ".message"));
                    statement.setString(12, yaml.getString(base + ".displayName"));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
        markMigrated(migration);
    }

    private boolean isMigrated(String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM schema_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && "true".equalsIgnoreCase(result.getString("value"));
            }
        }
    }

    private void markMigrated(String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO schema_meta(key, value)
                VALUES (?, 'true')
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    private File legacyFile(String name) {
        return new File(dataFolder, name);
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @FunctionalInterface
    private interface Migration {
        void run() throws SQLException;
    }
}
