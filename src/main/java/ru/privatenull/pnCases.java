package ru.privatenull;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.commands.CasesCMD;
import ru.privatenull.commands.CasesCMDTabCompliter;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.integrations.FancyHologramsHook;
import ru.privatenull.storage.KeyStorage;
import ru.privatenull.storage.PendingRewardStorage;
import ru.privatenull.update.UpdateChecker;

import java.util.UUID;

public final class pnCases extends JavaPlugin {

    private CaseManager caseManager;
    private KeyStorage keyStorage;
    private FancyHologramsHook fancyHolograms;
    private MessagesConfig messages;
    private PendingRewardStorage pendingRewards;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new MessagesConfig(this);
        fancyHolograms = new FancyHologramsHook(this);

        caseManager = new CaseManager(this);
        caseManager.reloadFromConfig();
        setupUpdateChecker();

        keyStorage = new KeyStorage(this);
        pendingRewards = new PendingRewardStorage(this);

        for (UUID uuid : pendingRewards.getAll()) {
            var online = getServer().getPlayer(uuid);
            if (online != null && online.isOnline()) {
                Reward pending = pendingRewards.load(uuid);
                if (pending == null) continue;

                getServer().getScheduler().runTask(this, () -> {
                    caseManager.giveReward(online, pending);
                    pendingRewards.clear(uuid);
                    getLogger().info("Выдал отложенную награду игроку " + online.getName() + " после перезапуска.");
                });
            }
        }

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                if (updateChecker != null) {
                    updateChecker.notifyAdminOnJoin(e.getPlayer());
                }

                UUID uuid = e.getPlayer().getUniqueId();
                Reward pending = pendingRewards.load(uuid);
                if (pending == null) return;

                getServer().getScheduler().runTaskLater(pnCases.this, () -> {
                    if (!e.getPlayer().isOnline()) return;

                    caseManager.giveReward(e.getPlayer(), pending);
                    pendingRewards.clear(uuid);

                    getLogger().info("Выдал отложенную награду игроку " + e.getPlayer().getName()
                            + " (перезапуск во время анимации).");
                }, 20L);
            }
        }, this);

        var cmd = getCommand("pncases");
        var exec = new CasesCMD(this, caseManager);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(new CasesCMDTabCompliter(caseManager));

        getServer().getPluginManager().registerEvents(
                new ru.privatenull.listeners.CaseBlockListener(caseManager), this
        );
        getServer().getPluginManager().registerEvents(
                new ru.privatenull.listeners.CaseGuiListener(caseManager), this
        );

        getLogger().info("██████╗░██████╗░██╗██╗░░░██╗░█████╗░████████╗███████╗███╗░░██╗██╗░░░██╗██╗░░░░░██╗░░░░░");
        getLogger().info("██╔══██╗██╔══██╗██║██║░░░██║██╔══██╗╚══██╔══╝██╔════╝████╗░██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██████╔╝██████╔╝██║╚██╗░██╔╝███████║░░░██║░░░█████╗░░██╔██╗██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██╔═══╝░██╔══██╗██║░╚████╔╝░██╔══██║░░░██║░░░██╔══╝░░██║╚████║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██║░░░░░██║░░██║██║░░╚██╔╝░░██║░░██║░░░██║░░░███████╗██║░╚███║╚██████╔╝███████╗███████╗");
        getLogger().info("╚═╝░░░░░╚═╝░░╚═╝╚═╝░░░╚═╝░░░╚═╝░░╚═╝░░░╚═╝░░░╚══════╝╚═╝░░╚══╝░╚═════╝░╚══════╝╚══════╝");
        getLogger().info("");
        getLogger().info("pnCases v" + getDescription().getVersion() + " успешно включён! | ds: privatenull");
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.cancel();
        }

        if (fancyHolograms != null) {
            fancyHolograms.shutdown();
        }

        if (caseManager != null) {
            caseManager.shutdown();
        }

        getLogger().info("██████╗░██████╗░██╗██╗░░░██╗░█████╗░████████╗███████╗███╗░░██╗██╗░░░██╗██╗░░░░░██╗░░░░░");
        getLogger().info("██╔══██╗██╔══██╗██║██║░░░██║██╔══██╗╚══██╔══╝██╔════╝████╗░██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██████╔╝██████╔╝██║╚██╗░██╔╝███████║░░░██║░░░█████╗░░██╔██╗██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██╔═══╝░██╔══██╗██║░╚████╔╝░██╔══██║░░░██║░░░██╔══╝░░██║╚████║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██║░░░░░██║░░██║██║░░╚██╔╝░░██║░░██║░░░██║░░░███████╗██║░╚███║╚██████╔╝███████╗███████╗");
        getLogger().info("╚═╝░░░░░╚═╝░░╚═╝╚═╝░░░╚═╝░░░╚═╝░░╚═╝░░░╚═╝░░░╚══════╝╚═╝░░╚══╝░╚═════╝░╚══════╝╚══════╝");
        getLogger().info("");
        getLogger().info("pnCases отключён | ds: privatenull");
    }

    public CaseManager getCaseManager() {
        return caseManager;
    }

    public KeyStorage getKeyStorage() {
        return keyStorage;
    }

    public FancyHologramsHook getFancyHolograms() {
        return fancyHolograms;
    }

    public MessagesConfig getMessages() {
        return messages;
    }

    public PendingRewardStorage getPendingRewards() {
        return pendingRewards;
    }

    public void reloadRuntimeConfig() {
        if (updateChecker != null) {
            updateChecker.reload();
        }
    }

    public void recordCaseOpening(Player player, CaseDefinition def, Reward reward, String rewardLabel) {
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        updateChecker.reload();
    }

}
