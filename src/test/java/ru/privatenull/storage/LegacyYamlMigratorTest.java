package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyYamlMigratorTest {

    @TempDir
    Path dataFolder;

    @Test
    void migratesEveryLegacyFileExactlyOnce() throws Exception {
        String uuid = UUID.randomUUID().toString();
        Files.writeString(dataFolder.resolve("keys.yml"), """
                players:
                  %s:
                    donate_key: 3
                """.formatted(uuid));
        Files.writeString(dataFolder.resolve("player_prefs.yml"), """
                players:
                  %s:
                    animation: portal
                """.formatted(uuid));
        Files.writeString(dataFolder.resolve("open_history.yml"), """
                cases:
                  money:
                    first:
                      player: Tester
                      reward: 500 монет
                      time: 123
                """);
        Files.writeString(dataFolder.resolve("pending_rewards.yml"), """
                players:
                  %s:
                    type: VAULT
                    chance: 25
                    vaultAmount: 500
                    displayName: 500 монет
                """.formatted(uuid));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSchema(connection);
            LegacyYamlMigrator migrator = new LegacyYamlMigrator(dataFolder.toFile(), connection);
            migrator.migrate();
            migrator.migrate();

            assertEquals(3, integer(connection,
                    "SELECT amount FROM player_keys WHERE player_uuid = '" + uuid + "'"));
            assertEquals("PORTAL", text(connection,
                    "SELECT animation FROM player_prefs WHERE player_uuid = '" + uuid + "'"));
            assertEquals(1, integer(connection, "SELECT COUNT(*) FROM open_history"));
            assertEquals(500, integer(connection,
                    "SELECT vault_amount FROM pending_rewards WHERE player_uuid = '" + uuid + "'"));
            assertEquals(4, integer(connection,
                    "SELECT COUNT(*) FROM schema_meta WHERE key LIKE 'legacy.%' AND value = 'true'"));
        }
    }

    private void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("""
                    CREATE TABLE player_keys(
                        player_uuid TEXT NOT NULL, key_id TEXT NOT NULL, amount INTEGER NOT NULL,
                        PRIMARY KEY(player_uuid, key_id)
                    )
                    """);
            statement.execute("CREATE TABLE player_prefs(player_uuid TEXT PRIMARY KEY, animation TEXT NOT NULL)");
            statement.execute("""
                    CREATE TABLE open_history(
                        id INTEGER PRIMARY KEY AUTOINCREMENT, case_name TEXT NOT NULL,
                        player_name TEXT NOT NULL, reward_name TEXT NOT NULL, opened_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE pending_rewards(
                        player_uuid TEXT PRIMARY KEY, type TEXT NOT NULL, chance INTEGER NOT NULL,
                        rarity TEXT, item_base64 TEXT, lp_group TEXT, lp_node TEXT, lp_duration TEXT,
                        vault_amount REAL NOT NULL DEFAULT 0, player_points_amount INTEGER NOT NULL DEFAULT 0,
                        message TEXT, display_name TEXT
                    )
                    """);
        }
    }

    private int integer(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private String text(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }
}
