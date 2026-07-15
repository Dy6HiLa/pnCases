package ru.privatenull.storage;

import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnlibrary.database.DatabaseType;
import ru.privatenull.pnlibrary.database.JdbcDatabase;
import ru.privatenull.pnlibrary.database.JdbcMigration;
import ru.privatenull.pnlibrary.database.JdbcSettings;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JdbcCasesDatabase implements CasesDatabase {
    private final JdbcDatabase database;
    private final DatabaseType type;

    public JdbcCasesDatabase(JdbcSettings settings) {
        database = new JdbcDatabase(settings);
        type = settings.type();
    }

    @Override
    public DatabaseType type() {
        return type;
    }

    @Override
    public void open(File dataFolder) throws Exception {
        database.open();
        database.migrate("pncases", migrations());
        ensureRarityColumn();
        importLegacyMarkers();
        new LegacyYamlMigrator(dataFolder, this).migrate();
    }

    @Override
    public synchronized void addKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return;
        String sql = type == DatabaseType.MYSQL
                ? "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount)"
                : "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON CONFLICT(player_uuid,key_id) DO UPDATE SET amount=player_keys.amount+excluded.amount";
        execute(sql, statement -> {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(keyId));
            statement.setInt(3, amount);
        }, "add keys");
    }

    @Override
    public synchronized int getKeys(UUID uuid, String keyId) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT amount FROM player_keys WHERE player_uuid=? AND key_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(keyId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.max(0, result.getInt(1)) : 0;
            }
        } catch (Exception exception) {
            throw failure("read keys", exception);
        }
    }

    @Override
    public synchronized boolean takeKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return true;
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE player_keys SET amount=amount-? WHERE player_uuid=? AND key_id=? AND amount>=?")) {
                    statement.setInt(1, amount);
                    statement.setString(2, uuid.toString());
                    statement.setString(3, normalize(keyId));
                    statement.setInt(4, amount);
                    changed = statement.executeUpdate();
                }
                if (changed > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM player_keys WHERE player_uuid=? AND key_id=? AND amount<=0")) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, normalize(keyId));
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return changed > 0;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw failure("take keys", exception);
        }
    }

    @Override
    public synchronized Map<String, Integer> getAllKeys(UUID uuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT key_id,amount FROM player_keys WHERE player_uuid=? AND amount>0 ORDER BY key_id")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getString(1), Math.max(0, rows.getInt(2)));
            }
            return result;
        } catch (Exception exception) {
            throw failure("read key balances", exception);
        }
    }

    @Override
    public synchronized AnimationType getAnimation(UUID uuid) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT animation FROM player_prefs WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? parseAnimation(result.getString(1)) : AnimationType.ANVIL;
            }
        } catch (Exception exception) {
            throw failure("read player animation", exception);
        }
    }

    @Override
    public synchronized void setAnimation(UUID uuid, AnimationType animation) {
        String sql = type == DatabaseType.MYSQL
                ? "INSERT INTO player_prefs(player_uuid,animation) VALUES (?,?) ON DUPLICATE KEY UPDATE animation=VALUES(animation)"
                : "INSERT INTO player_prefs(player_uuid,animation) VALUES (?,?) ON CONFLICT(player_uuid) DO UPDATE SET animation=excluded.animation";
        execute(sql, statement -> {
            statement.setString(1, uuid.toString());
            statement.setString(2, animation.name());
        }, "save player animation");
    }

    @Override
    public synchronized void addHistory(String caseName, String playerName, String rewardName, long openedAt) {
        execute("INSERT INTO open_history(case_name,player_name,reward_name,opened_at) VALUES (?,?,?,?)", statement -> {
            statement.setString(1, normalize(caseName));
            statement.setString(2, playerName == null ? "Unknown" : playerName);
            statement.setString(3, rewardName == null ? "Reward" : rewardName);
            statement.setLong(4, openedAt);
        }, "save open history");
        trimHistory(normalize(caseName), 9);
    }

    @Override
    public synchronized List<OpenHistoryStorage.Entry> getHistory(String caseName, int limit) {
        List<OpenHistoryStorage.Entry> result = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_name,reward_name,opened_at FROM open_history WHERE case_name=? ORDER BY opened_at DESC,id DESC LIMIT ?")) {
            statement.setString(1, normalize(caseName));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new OpenHistoryStorage.Entry(rows.getString(1), rows.getString(2), rows.getLong(3)));
            }
            return result;
        } catch (Exception exception) {
            throw failure("read open history", exception);
        }
    }

    @Override
    public synchronized void savePending(UUID uuid, Reward reward) {
        String columns = "player_uuid,type,chance,rarity,item_base64,lp_group,lp_node,lp_duration,vault_amount,player_points_amount,message,display_name";
        String update = type == DatabaseType.MYSQL
                ? "type=VALUES(type),chance=VALUES(chance),rarity=VALUES(rarity),item_base64=VALUES(item_base64),lp_group=VALUES(lp_group),lp_node=VALUES(lp_node),lp_duration=VALUES(lp_duration),vault_amount=VALUES(vault_amount),player_points_amount=VALUES(player_points_amount),message=VALUES(message),display_name=VALUES(display_name)"
                : "type=excluded.type,chance=excluded.chance,rarity=excluded.rarity,item_base64=excluded.item_base64,lp_group=excluded.lp_group,lp_node=excluded.lp_node,lp_duration=excluded.lp_duration,vault_amount=excluded.vault_amount,player_points_amount=excluded.player_points_amount,message=excluded.message,display_name=excluded.display_name";
        String sql = "INSERT INTO pending_rewards(" + columns + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
                + (type == DatabaseType.MYSQL ? "ON DUPLICATE KEY UPDATE " : "ON CONFLICT(player_uuid) DO UPDATE SET ") + update;
        execute(sql, statement -> writeReward(statement, uuid, reward), "save pending reward");
    }

    @Override
    public synchronized Reward loadPending(UUID uuid) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT type,chance,rarity,item_base64,lp_group,lp_node,lp_duration,vault_amount,player_points_amount,message,display_name FROM pending_rewards WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return readReward(result);
            }
        } catch (Exception exception) {
            throw failure("read pending reward", exception);
        }
    }

    @Override
    public synchronized void clearPending(UUID uuid) {
        execute("DELETE FROM pending_rewards WHERE player_uuid=?", statement -> statement.setString(1, uuid.toString()), "delete pending reward");
    }

    @Override
    public synchronized Set<UUID> getPendingPlayers() {
        Set<UUID> result = new HashSet<>();
        try (Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT player_uuid FROM pending_rewards")) {
            while (rows.next()) addUuid(result, rows.getString(1));
            return result;
        } catch (Exception exception) {
            throw failure("read pending players", exception);
        }
    }

    @Override
    public synchronized boolean isMigrationApplied(String key) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT marker_key FROM pncases_migration_markers WHERE marker_key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (Exception exception) {
            throw failure("read migration marker", exception);
        }
    }

    @Override
    public synchronized void markMigrationApplied(String key) {
        String sql = type == DatabaseType.MYSQL
                ? "INSERT IGNORE INTO pncases_migration_markers(marker_key) VALUES (?)"
                : "INSERT OR IGNORE INTO pncases_migration_markers(marker_key) VALUES (?)";
        execute(sql, statement -> statement.setString(1, key), "save migration marker");
    }

    @Override
    public void close() {
        database.close();
    }

    private void trimHistory(String caseName, int limit) {
        String sql = "DELETE FROM open_history WHERE case_name=? AND id NOT IN (SELECT id FROM (SELECT id FROM open_history WHERE case_name=? ORDER BY opened_at DESC,id DESC LIMIT ?) kept)";
        execute(sql, statement -> {
            statement.setString(1, caseName);
            statement.setString(2, caseName);
            statement.setInt(3, limit);
        }, "trim open history");
    }

    private void writeReward(PreparedStatement statement, UUID uuid, Reward reward) throws SQLException {
        statement.setString(1, uuid.toString());
        statement.setString(2, reward.type().name());
        statement.setInt(3, reward.chance());
        statement.setString(4, reward.rarityId());
        statement.setString(5, ItemCodec.serialize(reward.item()));
        statement.setString(6, reward.lpGroup());
        statement.setString(7, reward.lpNode());
        statement.setString(8, reward.lpDuration());
        statement.setDouble(9, reward.vaultAmount());
        statement.setInt(10, reward.playerPointsAmount());
        statement.setString(11, reward.message());
        statement.setString(12, reward.displayName());
    }

    private Reward readReward(ResultSet result) throws SQLException {
        return new Reward(result.getInt("chance"), parseType(result.getString("type")),
                ItemCodec.deserialize(result.getString("item_base64")), result.getString("lp_group"),
                result.getString("lp_node"), result.getString("lp_duration"), result.getDouble("vault_amount"),
                result.getInt("player_points_amount"), result.getString("message"), result.getString("display_name"),
                result.getString("rarity"));
    }

    private void execute(String sql, SqlBinder binder, String action) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (Exception exception) {
            throw failure(action, exception);
        }
    }

    private void ensureRarityColumn() throws Exception {
        try (Connection connection = database.connection()) {
            boolean exists = false;
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "pending_rewards", "rarity")) {
                exists = columns.next();
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE pending_rewards ADD COLUMN rarity VARCHAR(64)");
                }
            }
        }
    }

    private void importLegacyMarkers() throws Exception {
        List<String> markers = new ArrayList<>();
        try (Connection connection = database.connection()) {
            boolean oldMetaExists;
            try (ResultSet tables = connection.getMetaData().getTables(null, null, "schema_meta", new String[]{"TABLE"})) {
                oldMetaExists = tables.next();
            }
            if (!oldMetaExists) return;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT key FROM schema_meta WHERE key LIKE 'legacy.%' AND value='true'");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) markers.add(rows.getString(1));
            }
        }
        for (String marker : markers) markMigrationApplied(marker);
    }

    private List<JdbcMigration> migrations() {
        return List.of(new JdbcMigration(1, "create pnCases storage", List.of(
                "CREATE TABLE IF NOT EXISTS player_keys(player_uuid TEXT NOT NULL,key_id TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>=0),PRIMARY KEY(player_uuid,key_id))",
                "CREATE TABLE IF NOT EXISTS player_prefs(player_uuid TEXT PRIMARY KEY,animation TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS open_history(id INTEGER PRIMARY KEY AUTOINCREMENT,case_name TEXT NOT NULL,player_name TEXT NOT NULL,reward_name TEXT NOT NULL,opened_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_open_history_case_time ON open_history(case_name,opened_at DESC,id DESC)",
                "CREATE TABLE IF NOT EXISTS pending_rewards(player_uuid TEXT PRIMARY KEY,type TEXT NOT NULL,chance INTEGER NOT NULL,rarity VARCHAR(64),item_base64 TEXT,lp_group TEXT,lp_node TEXT,lp_duration TEXT,vault_amount REAL NOT NULL DEFAULT 0,player_points_amount INTEGER NOT NULL DEFAULT 0,message TEXT,display_name TEXT)",
                "CREATE TABLE IF NOT EXISTS pncases_migration_markers(marker_key TEXT PRIMARY KEY)"
        ), List.of(
                "CREATE TABLE IF NOT EXISTS player_keys(player_uuid VARCHAR(36) NOT NULL,key_id VARCHAR(191) NOT NULL,amount INT NOT NULL,PRIMARY KEY(player_uuid,key_id)) ENGINE=InnoDB",
                "CREATE TABLE IF NOT EXISTS player_prefs(player_uuid VARCHAR(36) PRIMARY KEY,animation VARCHAR(64) NOT NULL) ENGINE=InnoDB",
                "CREATE TABLE IF NOT EXISTS open_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,case_name VARCHAR(191) NOT NULL,player_name VARCHAR(191) NOT NULL,reward_name TEXT NOT NULL,opened_at BIGINT NOT NULL,INDEX idx_open_history_case_time(case_name,opened_at,id)) ENGINE=InnoDB",
                "CREATE TABLE IF NOT EXISTS pending_rewards(player_uuid VARCHAR(36) PRIMARY KEY,type VARCHAR(64) NOT NULL,chance INT NOT NULL,rarity VARCHAR(64),item_base64 LONGTEXT,lp_group VARCHAR(191),lp_node TEXT,lp_duration VARCHAR(191),vault_amount DOUBLE NOT NULL DEFAULT 0,player_points_amount INT NOT NULL DEFAULT 0,message TEXT,display_name TEXT) ENGINE=InnoDB",
                "CREATE TABLE IF NOT EXISTS pncases_migration_markers(marker_key VARCHAR(191) PRIMARY KEY) ENGINE=InnoDB"
        )));
    }

    private static String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT);
    }

    private static AnimationType parseAnimation(String value) {
        try {
            return AnimationType.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AnimationType.ANVIL;
        }
    }

    private static Reward.Type parseType(String value) {
        try {
            return Reward.Type.valueOf(value);
        } catch (Exception ignored) {
            return Reward.Type.ITEM;
        }
    }

    private static void addUuid(Set<UUID> output, String value) {
        try {
            output.add(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private IllegalStateException failure(String action, Exception exception) {
        return new IllegalStateException("Could not " + action + " in " + type + " database", exception);
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws Exception;
    }
}
