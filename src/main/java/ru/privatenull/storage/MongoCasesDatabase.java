package ru.privatenull.storage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnlibrary.database.DatabaseType;
import ru.privatenull.pnlibrary.database.MongoDatabaseManager;
import ru.privatenull.pnlibrary.database.MongoSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.compoundIndex;
import static com.mongodb.client.model.Indexes.descending;
import static com.mongodb.client.model.Sorts.orderBy;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.setOnInsert;

public final class MongoCasesDatabase implements CasesDatabase {
    private final MongoDatabaseManager manager;
    private final String prefix;
    private MongoCollection<Document> keys;
    private MongoCollection<Document> preferences;
    private MongoCollection<Document> history;
    private MongoCollection<Document> pending;
    private MongoCollection<Document> migrations;

    public MongoCasesDatabase(MongoSettings settings) {
        manager = new MongoDatabaseManager(settings);
        prefix = settings.collection();
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.MONGODB;
    }

    @Override
    public void open(File dataFolder) {
        manager.open();
        var database = manager.database();
        keys = database.getCollection(prefix + "_keys");
        preferences = database.getCollection(prefix + "_preferences");
        history = database.getCollection(prefix + "_history");
        pending = database.getCollection(prefix + "_pending_rewards");
        migrations = database.getCollection(prefix + "_migrations");
        keys.createIndex(compoundIndex(ascending("player_uuid"), ascending("key_id")), new IndexOptions().unique(true));
        preferences.createIndex(ascending("player_uuid"), new IndexOptions().unique(true));
        history.createIndex(compoundIndex(ascending("case_name"), descending("opened_at")));
        pending.createIndex(ascending("player_uuid"), new IndexOptions().unique(true));
        migrations.createIndex(ascending("key"), new IndexOptions().unique(true));
        new LegacyYamlMigrator(dataFolder, this).migrate();
    }

    @Override
    public synchronized void addKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return;
        keys.updateOne(keyFilter(uuid, keyId), combine(
                setOnInsert("player_uuid", uuid.toString()),
                setOnInsert("key_id", normalize(keyId)),
                inc("amount", amount)
        ), new UpdateOptions().upsert(true));
    }

    @Override
    public synchronized int getKeys(UUID uuid, String keyId) {
        Document document = keys.find(keyFilter(uuid, keyId)).first();
        return document == null ? 0 : Math.max(0, document.getInteger("amount", 0));
    }

    @Override
    public synchronized boolean takeKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return true;
        Document changed = keys.findOneAndUpdate(and(keyFilter(uuid, keyId), gte("amount", amount)),
                inc("amount", -amount), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (changed == null) return false;
        if (changed.getInteger("amount", 0) <= 0) keys.deleteOne(eq("_id", changed.getObjectId("_id")));
        return true;
    }

    @Override
    public synchronized Map<String, Integer> getAllKeys(UUID uuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Document document : keys.find(eq("player_uuid", uuid.toString())).sort(ascending("key_id"))) {
            int amount = document.getInteger("amount", 0);
            if (amount > 0) result.put(document.getString("key_id"), amount);
        }
        return result;
    }

    @Override
    public synchronized AnimationType getAnimation(UUID uuid) {
        Document document = preferences.find(eq("player_uuid", uuid.toString())).first();
        return parseAnimation(document == null ? null : document.getString("animation"));
    }

    @Override
    public synchronized void setAnimation(UUID uuid, AnimationType type) {
        preferences.updateOne(eq("player_uuid", uuid.toString()), combine(
                setOnInsert("player_uuid", uuid.toString()), set("animation", type.name())
        ), new UpdateOptions().upsert(true));
    }

    @Override
    public synchronized void addHistory(String caseName, String playerName, String rewardName, long openedAt) {
        String normalized = normalize(caseName);
        history.insertOne(new Document("case_name", normalized)
                .append("player_name", playerName == null ? "Unknown" : playerName)
                .append("reward_name", rewardName == null ? "Reward" : rewardName)
                .append("opened_at", openedAt));
        List<Object> kept = history.find(eq("case_name", normalized))
                .sort(orderBy(descending("opened_at"), descending("_id"))).limit(9)
                .map(document -> document.get("_id")).into(new ArrayList<>());
        if (!kept.isEmpty()) history.deleteMany(and(eq("case_name", normalized), com.mongodb.client.model.Filters.nin("_id", kept)));
    }

    @Override
    public synchronized List<OpenHistoryStorage.Entry> getHistory(String caseName, int limit) {
        List<OpenHistoryStorage.Entry> result = new ArrayList<>();
        for (Document document : history.find(eq("case_name", normalize(caseName)))
                .sort(orderBy(descending("opened_at"), descending("_id"))).limit(limit)) {
            result.add(new OpenHistoryStorage.Entry(document.getString("player_name"),
                    document.getString("reward_name"), longValue(document, "opened_at")));
        }
        return result;
    }

    @Override
    public synchronized void savePending(UUID uuid, Reward reward) {
        Document value = new Document("player_uuid", uuid.toString())
                .append("type", reward.type().name()).append("chance", reward.chance())
                .append("rarity", reward.rarityId()).append("item_base64", ItemCodec.serialize(reward.item()))
                .append("lp_group", reward.lpGroup()).append("lp_node", reward.lpNode())
                .append("lp_duration", reward.lpDuration()).append("vault_amount", reward.vaultAmount())
                .append("player_points_amount", reward.playerPointsAmount()).append("message", reward.message())
                .append("display_name", reward.displayName());
        pending.replaceOne(eq("player_uuid", uuid.toString()), value, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    @Override
    public synchronized Reward loadPending(UUID uuid) {
        Document value = pending.find(eq("player_uuid", uuid.toString())).first();
        if (value == null) return null;
        int chance = value.getInteger("chance", 100);
        return new Reward(chance, parseType(value.getString("type")), ItemCodec.deserialize(value.getString("item_base64")),
                value.getString("lp_group"), value.getString("lp_node"), value.getString("lp_duration"),
                doubleValue(value, "vault_amount"), value.getInteger("player_points_amount", 0),
                value.getString("message"), value.getString("display_name"), value.getString("rarity"));
    }

    @Override
    public synchronized void clearPending(UUID uuid) {
        pending.deleteOne(eq("player_uuid", uuid.toString()));
    }

    @Override
    public synchronized Set<UUID> getPendingPlayers() {
        Set<UUID> result = new HashSet<>();
        for (Document document : pending.find()) {
            try {
                result.add(UUID.fromString(document.getString("player_uuid")));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Override
    public synchronized boolean isMigrationApplied(String key) {
        return migrations.find(eq("key", key)).first() != null;
    }

    @Override
    public synchronized void markMigrationApplied(String key) {
        migrations.updateOne(eq("key", key), setOnInsert("key", key), new UpdateOptions().upsert(true));
    }

    @Override
    public void close() {
        manager.close();
    }

    private Bson keyFilter(UUID uuid, String keyId) {
        return and(eq("player_uuid", uuid.toString()), eq("key_id", normalize(keyId)));
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

    private static long longValue(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private static double doubleValue(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value == null ? 0.0 : value.doubleValue();
    }
}
