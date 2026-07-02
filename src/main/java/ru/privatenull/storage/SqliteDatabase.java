package ru.privatenull.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.model.Reward;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class SqliteDatabase implements AutoCloseable {

    private static final int SCHEMA_VERSION = 2;

    private final JavaPlugin plugin;
    private final File file;
    private final Connection connection;

    public SqliteDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Не удалось создать папку данных pnCases.");
        }

        this.file = new File(plugin.getDataFolder(), "data.db");

        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            configure();
            createSchema();
            migrateLegacyYaml();
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось открыть SQLite базу pnCases: " + ex.getMessage(), ex);
        }
    }

    synchronized Connection connection() {
        return connection;
    }

    @Override
    public synchronized void close() {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    private void configure() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = DELETE");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        deleteWalSidecarFiles();
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_keys (
                        player_uuid TEXT NOT NULL,
                        key_id TEXT NOT NULL,
                        amount INTEGER NOT NULL CHECK (amount >= 0),
                        PRIMARY KEY (player_uuid, key_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_prefs (
                        player_uuid TEXT PRIMARY KEY,
                        animation TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS open_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        case_name TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        reward_name TEXT NOT NULL,
                        opened_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_open_history_case_time
                    ON open_history(case_name, opened_at DESC, id DESC)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pending_rewards (
                        player_uuid TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        chance INTEGER NOT NULL,
                        rarity TEXT,
                        item_base64 TEXT,
                        lp_group TEXT,
                        lp_node TEXT,
                        lp_duration TEXT,
                        vault_amount REAL NOT NULL DEFAULT 0,
                        player_points_amount INTEGER NOT NULL DEFAULT 0,
                        message TEXT,
                        display_name TEXT
                    )
                    """);
        }

        ensureColumn("pending_rewards", "rarity", "TEXT");
        setMeta("schema_version", String.valueOf(SCHEMA_VERSION));
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }

        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private void migrateLegacyYaml() throws SQLException {
        runMigration(this::migrateKeys);
        runMigration(this::migratePlayerPrefs);
        runMigration(this::migrateOpenHistory);
        runMigration(this::migratePendingRewards);
    }

    private void runMigration(Migration migration) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            migration.run();
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void migrateKeys() throws SQLException {
        if (isMigrated("legacy.keys.yml")) return;

        File legacy = new File(plugin.getDataFolder(), "keys.yml");
        if (!legacy.exists()) {
            markMigrated("legacy.keys.yml");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_keys(player_uuid, key_id, amount)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid, key_id) DO UPDATE SET amount = player_keys.amount + excluded.amount
                    """)) {
                for (String uuidRaw : players.getKeys(false)) {
                    if (!isUuid(uuidRaw)) continue;
                    ConfigurationSection keys = players.getConfigurationSection(uuidRaw);
                    if (keys == null) continue;

                    for (String keyId : keys.getKeys(false)) {
                        int amount = Math.max(0, keys.getInt(keyId, 0));
                        if (amount <= 0) continue;

                        statement.setString(1, uuidRaw);
                        statement.setString(2, keyId.toLowerCase(Locale.ROOT));
                        statement.setInt(3, amount);
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            }
        }

        markMigrated("legacy.keys.yml");
    }

    private void migratePlayerPrefs() throws SQLException {
        if (isMigrated("legacy.player_prefs.yml")) return;

        File legacy = new File(plugin.getDataFolder(), "player_prefs.yml");
        if (!legacy.exists()) {
            markMigrated("legacy.player_prefs.yml");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR REPLACE INTO player_prefs(player_uuid, animation)
                    VALUES (?, ?)
                    """)) {
                for (String uuidRaw : players.getKeys(false)) {
                    if (!isUuid(uuidRaw)) continue;
                    String animation = yaml.getString("players." + uuidRaw + ".animation");
                    if (animation == null || animation.isBlank()) continue;

                    statement.setString(1, uuidRaw);
                    statement.setString(2, animation.toUpperCase(Locale.ROOT));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        markMigrated("legacy.player_prefs.yml");
    }

    private void migrateOpenHistory() throws SQLException {
        if (isMigrated("legacy.open_history.yml")) return;

        File legacy = new File(plugin.getDataFolder(), "open_history.yml");
        if (!legacy.exists()) {
            markMigrated("legacy.open_history.yml");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection cases = yaml.getConfigurationSection("cases");
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

        markMigrated("legacy.open_history.yml");
    }

    private void migratePendingRewards() throws SQLException {
        if (isMigrated("legacy.pending_rewards.yml")) return;

        File legacy = new File(plugin.getDataFolder(), "pending_rewards.yml");
        if (!legacy.exists()) {
            markMigrated("legacy.pending_rewards.yml");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR REPLACE INTO pending_rewards(
                        player_uuid, type, chance, rarity, item_base64, lp_group, lp_node, lp_duration,
                        vault_amount, player_points_amount, message, display_name
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (String uuidRaw : players.getKeys(false)) {
                    if (!isUuid(uuidRaw)) continue;
                    String base = "players." + uuidRaw;
                    int chance = yaml.getInt(base + ".chance", 100);

                    statement.setString(1, uuidRaw);
                    statement.setString(2, yaml.getString(base + ".type", "ITEM"));
                    statement.setInt(3, chance);
                    statement.setString(4, yaml.getString(base + ".rarity", defaultRarity(chance)));
                    statement.setString(5, yaml.getString(base + ".item", null));
                    statement.setString(6, yaml.getString(base + ".lpGroup", null));
                    statement.setString(7, yaml.getString(base + ".lpNode", null));
                    statement.setString(8, yaml.getString(base + ".lpDuration", null));
                    statement.setDouble(9, yaml.getDouble(base + ".vaultAmount", 0.0));
                    statement.setInt(10, yaml.getInt(base + ".playerPointsAmount", 0));
                    statement.setString(11, yaml.getString(base + ".message", null));
                    statement.setString(12, yaml.getString(base + ".displayName", null));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        markMigrated("legacy.pending_rewards.yml");
    }

    private boolean isMigrated(String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM schema_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && "true".equalsIgnoreCase(result.getString("value"));
            }
        }
    }

    private void markMigrated(String key) throws SQLException {
        setMeta(key, "true");
    }

    private void setMeta(String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO schema_meta(key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void deleteWalSidecarFiles() {
        deleteIfExists(new File(file.getAbsolutePath() + "-wal"));
        deleteIfExists(new File(file.getAbsolutePath() + "-shm"));
    }

    private void deleteIfExists(File sidecar) {
        if (!sidecar.isFile()) {
            return;
        }

        if (!sidecar.delete() && sidecar.exists()) {
            plugin.getLogger().warning("Не удалось удалить старый SQLite файл " + sidecar.getName()
                    + ". Он должен исчезнуть после остановки сервера.");
        }
    }

    static String serializeItem(ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static ItemStack deserializeItem(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String defaultRarity(int chance) {
        return Reward.Rarity.fromChance(chance).name();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @FunctionalInterface
    private interface Migration {
        void run() throws SQLException;
    }
}
