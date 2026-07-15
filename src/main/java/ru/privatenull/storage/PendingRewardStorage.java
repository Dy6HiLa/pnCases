package ru.privatenull.storage;

import ru.privatenull.cases.model.Reward;

import java.util.Set;
import java.util.UUID;

public final class PendingRewardStorage {
    private final CasesDatabase database;

    public PendingRewardStorage(CasesDatabase database) {
        this.database = database;
    }

    public void save(UUID uuid, Reward reward) {
        database.savePending(uuid, reward);
    }

    public Reward load(UUID uuid) {
        return database.loadPending(uuid);
    }

    public void clear(UUID uuid) {
        database.clearPending(uuid);
    }

    public Set<UUID> getAll() {
        return database.getPendingPlayers();
    }
}
