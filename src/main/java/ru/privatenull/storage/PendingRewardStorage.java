package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.model.Reward;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PendingRewardStorage {

    private final SqliteDatabase database;

    public PendingRewardStorage(SqliteDatabase database) {
        this.database = database;
    }

    public synchronized void save(UUID uuid, Reward reward) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO pending_rewards(
                    player_uuid, type, chance, rarity, item_base64, lp_group, lp_node, lp_duration,
                    vault_amount, player_points_amount, message, display_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    type = excluded.type,
                    chance = excluded.chance,
                    rarity = excluded.rarity,
                    item_base64 = excluded.item_base64,
                    lp_group = excluded.lp_group,
                    lp_node = excluded.lp_node,
                    lp_duration = excluded.lp_duration,
                    vault_amount = excluded.vault_amount,
                    player_points_amount = excluded.player_points_amount,
                    message = excluded.message,
                    display_name = excluded.display_name
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, reward.type().name());
            statement.setInt(3, reward.chance());
            statement.setString(4, reward.rarityId());
            statement.setString(5, SqliteDatabase.serializeItem(reward.item()));
            statement.setString(6, reward.lpGroup());
            statement.setString(7, reward.lpNode());
            statement.setString(8, reward.lpDuration());
            statement.setDouble(9, reward.vaultAmount());
            statement.setInt(10, reward.playerPointsAmount());
            statement.setString(11, reward.message());
            statement.setString(12, reward.displayName());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить отложенную награду в SQLite.", ex);
        }
    }

    public synchronized Reward load(UUID uuid) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT type, chance, rarity, item_base64, lp_group, lp_node, lp_duration,
                       vault_amount, player_points_amount, message, display_name
                FROM pending_rewards
                WHERE player_uuid = ?
                """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;

                Reward.Type type = parseType(result.getString("type"));
                int chance = result.getInt("chance");
                ItemStack item = SqliteDatabase.deserializeItem(result.getString("item_base64"));

                return new Reward(
                        chance,
                        type,
                        item,
                        result.getString("lp_group"),
                        result.getString("lp_node"),
                        result.getString("lp_duration"),
                        result.getDouble("vault_amount"),
                        result.getInt("player_points_amount"),
                        result.getString("message"),
                        result.getString("display_name"),
                        result.getString("rarity")
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось прочитать отложенную награду из SQLite.", ex);
        }
    }

    public synchronized void clear(UUID uuid) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                DELETE FROM pending_rewards WHERE player_uuid = ?
                """)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось удалить отложенную награду из SQLite.", ex);
        }
    }

    public synchronized Set<UUID> getAll() {
        Set<UUID> uuids = new HashSet<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT player_uuid FROM pending_rewards
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                try {
                    uuids.add(UUID.fromString(result.getString("player_uuid")));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
            }
            return uuids;
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось прочитать список отложенных наград из SQLite.", ex);
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
