package ru.privatenull.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.logging.Level;

public final class EntityCleanup {

    private EntityCleanup() {
    }

    public static void remove(Entity entity) {
        if (entity == null || entity.isDead()) return;
        try {
            entity.remove();
        } catch (RuntimeException exception) {
            Bukkit.getLogger().log(Level.FINE, "pnCases could not remove animation entity", exception);
        }
    }
}
