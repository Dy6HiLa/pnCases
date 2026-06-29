package ru.privatenull.storage;

import ru.privatenull.cases.animation.AnimationType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public final class PlayerPrefsStorage {

    private final SqliteDatabase database;

    public PlayerPrefsStorage(SqliteDatabase database) {
        this.database = database;
    }

    public synchronized AnimationType getAnimation(UUID uuid) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT animation FROM player_prefs WHERE player_uuid = ?
                """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return AnimationType.ANVIL;
                return parseAnimation(result.getString("animation"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось прочитать анимацию игрока из SQLite.", ex);
        }
    }

    public synchronized void setAnimation(UUID uuid, AnimationType type) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO player_prefs(player_uuid, animation)
                VALUES (?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET animation = excluded.animation
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type.name());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить анимацию игрока в SQLite.", ex);
        }
    }

    private static AnimationType parseAnimation(String value) {
        if (value == null || value.isBlank()) return AnimationType.ANVIL;
        try {
            return AnimationType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AnimationType.ANVIL;
        }
    }
}
