package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.logging.Level;

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
            new LegacyYamlMigrator(plugin.getDataFolder(), connection).migrate();
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
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Не удалось закрыть SQLite базу pnCases", exception);
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

}
