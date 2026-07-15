package ru.privatenull.storage;

import java.time.Instant;
import java.util.List;

public final class OpenHistoryStorage {
    public record Entry(String playerName, String rewardName, long openedAt) {
    }

    private static final int LIMIT = 9;
    private final CasesDatabase database;

    public OpenHistoryStorage(CasesDatabase database) {
        this.database = database;
    }

    public void add(String caseName, String playerName, String rewardName) {
        database.addHistory(caseName, playerName, rewardName, Instant.now().getEpochSecond());
    }

    public List<Entry> get(String caseName) {
        return database.getHistory(caseName, LIMIT);
    }
}
