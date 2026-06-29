package ru.privatenull.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OpenHistoryStorage {

    public record Entry(String playerName, String rewardName, long openedAt) {}

    private static final int LIMIT = 9;

    private final SqliteDatabase database;

    public OpenHistoryStorage(SqliteDatabase database) {
        this.database = database;
    }

    public synchronized void add(String caseName, String playerName, String rewardName) {
        String normalizedCase = normalizeCase(caseName);

        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO open_history(case_name, player_name, reward_name, opened_at)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, normalizedCase);
            statement.setString(2, playerName == null ? "Unknown" : playerName);
            statement.setString(3, rewardName == null ? "Награда" : rewardName);
            statement.setLong(4, Instant.now().getEpochSecond());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить историю открытия в SQLite.", ex);
        }

        trim(normalizedCase);
    }

    public synchronized List<Entry> get(String caseName) {
        List<Entry> entries = new ArrayList<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT player_name, reward_name, opened_at
                FROM open_history
                WHERE case_name = ?
                ORDER BY opened_at DESC, id DESC
                LIMIT ?
                """)) {
            statement.setString(1, normalizeCase(caseName));
            statement.setInt(2, LIMIT);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new Entry(
                            result.getString("player_name"),
                            result.getString("reward_name"),
                            result.getLong("opened_at")
                    ));
                }
            }
            return entries;
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось прочитать историю открытия из SQLite.", ex);
        }
    }

    private void trim(String caseName) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                DELETE FROM open_history
                WHERE case_name = ?
                  AND id NOT IN (
                      SELECT id FROM open_history
                      WHERE case_name = ?
                      ORDER BY opened_at DESC, id DESC
                      LIMIT ?
                  )
                """)) {
            statement.setString(1, caseName);
            statement.setString(2, caseName);
            statement.setInt(3, LIMIT);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось очистить старую историю открытия в SQLite.", ex);
        }
    }

    private static String normalizeCase(String caseName) {
        return (caseName == null ? "" : caseName).toLowerCase(Locale.ROOT);
    }
}
