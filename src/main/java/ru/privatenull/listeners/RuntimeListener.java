package ru.privatenull.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.storage.PendingRewardStorage;

import java.util.UUID;

public final class RuntimeListener implements Listener {

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final PendingRewardStorage pendingRewards;

    public RuntimeListener(PnCasesPlugin plugin, CaseManager caseManager,
                           PendingRewardStorage pendingRewards) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.pendingRewards = pendingRewards;
    }

    public void deliverPendingToOnlinePlayers() {
        for (UUID playerId : pendingRewards.getAll()) {
            var player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            Reward pending = pendingRewards.load(playerId);
            if (pending == null) continue;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                caseManager.giveReward(player, pending);
                pendingRewards.clear(playerId);
                plugin.getLogger().info("Выдал отложенную награду игроку " + player.getName()
                        + " после перезапуска.");
            });
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, caseManager::reloadFromConfig, 20L);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        caseManager.onWorldUnload(event.getWorld());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getUpdateChecker() != null) {
            plugin.getUpdateChecker().notifyAdminOnJoin(event.getPlayer());
        }

        UUID playerId = event.getPlayer().getUniqueId();
        Reward pending = pendingRewards.load(playerId);
        if (pending == null) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            caseManager.giveReward(event.getPlayer(), pending);
            pendingRewards.clear(playerId);
            plugin.getLogger().info("Выдал отложенную награду игроку " + event.getPlayer().getName()
                    + " после перезапуска во время анимации.");
        }, 20L);
    }
}
