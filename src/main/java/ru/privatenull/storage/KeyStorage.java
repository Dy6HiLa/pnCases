package ru.privatenull.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class KeyStorage {

    private final SqliteDatabase database;

    public KeyStorage(SqliteDatabase database) {
        this.database = database;
    }

    public synchronized void add(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return;

        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO player_keys(player_uuid, key_id, amount)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, key_id) DO UPDATE SET amount = player_keys.amount + excluded.amount
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalizeKey(keyId));
            statement.setInt(3, amount);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось начислить ключи в SQLite.", ex);
        }
    }

    public synchronized int get(UUID uuid, String keyId) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT amount FROM player_keys WHERE player_uuid = ? AND key_id = ?
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalizeKey(keyId));

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.max(0, result.getInt("amount")) : 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось прочитать ключи из SQLite.", ex);
        }
    }

    public synchronized boolean take(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return true;

        String normalizedKey = normalizeKey(keyId);
        int current = get(uuid, normalizedKey);
        if (current < amount) return false;

        int next = current - amount;
        try {
            if (next <= 0) {
                try (PreparedStatement statement = database.connection().prepareStatement("""
                        DELETE FROM player_keys WHERE player_uuid = ? AND key_id = ?
                        """)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, normalizedKey);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = database.connection().prepareStatement("""
                        UPDATE player_keys SET amount = ? WHERE player_uuid = ? AND key_id = ?
                        """)) {
                    statement.setInt(1, next);
                    statement.setString(2, uuid.toString());
                    statement.setString(3, normalizedKey);
                    statement.executeUpdate();
                }
            }
            return true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось списать ключи в SQLite.", ex);
        }
    }

    public synchronized Map<String, Integer> getAll(UUID uuid) {
        Map<String, Integer> out = new LinkedHashMap<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT key_id, amount FROM player_keys WHERE player_uuid = ? AND amount > 0 ORDER BY key_id
                """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    out.put(result.getString("key_id"), Math.max(0, result.getInt("amount")));
                }
            }
            return out;
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось прочитать баланс ключей из SQLite.", ex);
        }
    }

    private static String normalizeKey(String keyId) {
        return (keyId == null ? "" : keyId).toLowerCase(Locale.ROOT);
    }
}
