package ru.privatenull.storage;

import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnlibrary.database.DatabaseType;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CasesDatabase extends AutoCloseable {
    DatabaseType type();

    void open(File dataFolder) throws Exception;

    void addKeys(UUID uuid, String keyId, int amount);

    int getKeys(UUID uuid, String keyId);

    boolean takeKeys(UUID uuid, String keyId, int amount);

    Map<String, Integer> getAllKeys(UUID uuid);

    AnimationType getAnimation(UUID uuid);

    void setAnimation(UUID uuid, AnimationType type);

    void addHistory(String caseName, String playerName, String rewardName, long openedAt);

    List<OpenHistoryStorage.Entry> getHistory(String caseName, int limit);

    void savePending(UUID uuid, Reward reward);

    Reward loadPending(UUID uuid);

    void clearPending(UUID uuid);

    Set<UUID> getPendingPlayers();

    boolean isMigrationApplied(String key);

    void markMigrationApplied(String key);

    @Override
    void close();
}
