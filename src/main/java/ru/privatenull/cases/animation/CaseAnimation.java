package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class CaseAnimation {

    protected final pnCases plugin;

    private final Set<Entity> trackedEntities = ConcurrentHashMap.newKeySet();
    private final Set<BukkitTask> trackedTasks = ConcurrentHashMap.newKeySet();

    protected CaseAnimation(pnCases plugin) {
        this.plugin = plugin;
    }

    public abstract void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish);

    protected void track(Entity e) {
        if (e != null) trackedEntities.add(e);
    }

    protected void track(BukkitTask t) {
        if (t != null) trackedTasks.add(t);
    }

    protected void untrack(Entity e) {
        trackedEntities.remove(e);
    }

    protected void untrack(BukkitTask t) {
        trackedTasks.remove(t);
    }

    public void cancelAll() {
        for (BukkitTask task : trackedTasks) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        trackedTasks.clear();

        for (Entity e : trackedEntities) {
            try { if (!e.isDead()) e.remove(); } catch (Exception ignored) {}
        }
        trackedEntities.clear();
    }
}