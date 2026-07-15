package ru.privatenull.storage;

import java.util.Map;
import java.util.UUID;

public final class KeyStorage {
    private final CasesDatabase database;

    public KeyStorage(CasesDatabase database) {
        this.database = database;
    }

    public void add(UUID uuid, String keyId, int amount) {
        database.addKeys(uuid, keyId, amount);
    }

    public int get(UUID uuid, String keyId) {
        return database.getKeys(uuid, keyId);
    }

    public boolean take(UUID uuid, String keyId, int amount) {
        return database.takeKeys(uuid, keyId, amount);
    }

    public Map<String, Integer> getAll(UUID uuid) {
        return database.getAllKeys(uuid);
    }
}
