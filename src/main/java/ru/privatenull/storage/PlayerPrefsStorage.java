package ru.privatenull.storage;

import ru.privatenull.cases.model.AnimationType;

import java.util.UUID;

public final class PlayerPrefsStorage {
    private final CasesDatabase database;

    public PlayerPrefsStorage(CasesDatabase database) {
        this.database = database;
    }

    public AnimationType getAnimation(UUID uuid) {
        return database.getAnimation(uuid);
    }

    public void setAnimation(UUID uuid, AnimationType type) {
        database.setAnimation(uuid, type);
    }
}
