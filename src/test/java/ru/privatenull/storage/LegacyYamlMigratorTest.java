package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.pnlibrary.database.JdbcSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyYamlMigratorTest {
    @TempDir
    Path dataFolder;

    @Test
    void migratesEveryLegacyFileExactlyOnce() throws Exception {
        UUID uuid = UUID.randomUUID();
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
                      reward: 500 coins
                      time: 123
                """);
        Files.writeString(dataFolder.resolve("pending_rewards.yml"), """
                players:
                  %s:
                    type: VAULT
                    chance: 25
                    vaultAmount: 500
                    displayName: 500 coins
                """.formatted(uuid));

        try (JdbcCasesDatabase database = new JdbcCasesDatabase(
                JdbcSettings.sqlite(dataFolder.resolve("test.db"), 5_000L))) {
            database.open(dataFolder.toFile());
            new LegacyYamlMigrator(dataFolder.toFile(), database).migrate();

            assertEquals(3, database.getKeys(uuid, "donate_key"));
            assertEquals(AnimationType.PORTAL, database.getAnimation(uuid));
            assertEquals(1, database.getHistory("money", 9).size());
            assertNotNull(database.loadPending(uuid));
            assertEquals(500.0, database.loadPending(uuid).vaultAmount());
        }
    }
}
