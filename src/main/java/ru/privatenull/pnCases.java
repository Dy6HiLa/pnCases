package ru.privatenull;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.commands.CasesCMD;
import ru.privatenull.commands.CasesCMDTabCompliter;
import ru.privatenull.config.ConfigValidator;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.hologram.HologramService;
import ru.privatenull.integrations.PlayerPointsProvider;
import ru.privatenull.integrations.VaultEconomyProvider;
import ru.privatenull.listeners.MachineGuiListener;
import ru.privatenull.storage.KeyStorage;
import ru.privatenull.storage.PendingRewardStorage;
import ru.privatenull.storage.SqliteDatabase;
import ru.privatenull.update.UpdateChecker;

import java.util.UUID;

public final class pnCases extends JavaPlugin {

    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";

    private CaseManager caseManager;
    private KeyStorage keyStorage;
    private HologramService holograms;
    private VaultEconomyProvider vaultEconomy;
    private PlayerPointsProvider playerPoints;
    private MessagesConfig messages;
    private SqliteDatabase database;
    private PendingRewardStorage pendingRewards;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        validateCurrentConfig();

        database = new SqliteDatabase(this);
        keyStorage = new KeyStorage(database);
        pendingRewards = new PendingRewardStorage(database);

        messages = new MessagesConfig(this);
        holograms = new HologramService(this);

        caseManager = new CaseManager(this);
        caseManager.exportMainCasesToFilesIfMissing();
        caseManager.reloadFromConfig();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (caseManager != null) {
                caseManager.reloadFromConfig();
            }
        }, 40L);
        setupUpdateChecker();

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
            public void onWorldLoad(WorldLoadEvent e) {
                getServer().getScheduler().runTaskLater(pnCases.this, () -> {
                    if (caseManager != null) {
                        caseManager.reloadFromConfig();
                    }
                }, 20L);
            }

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
        var machineGui = new MachineGuiListener(caseManager);
        var exec = new CasesCMD(this, caseManager, machineGui);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(new CasesCMDTabCompliter(caseManager));

        getServer().getPluginManager().registerEvents(
                new ru.privatenull.listeners.CaseBlockListener(caseManager), this
        );
        getServer().getPluginManager().registerEvents(
                new ru.privatenull.listeners.CaseGuiListener(caseManager), this
        );
        getServer().getPluginManager().registerEvents(machineGui, this);

        getLogger().info("██████╗░██████╗░██╗██╗░░░██╗░█████╗░████████╗███████╗███╗░░██╗██╗░░░██╗██╗░░░░░██╗░░░░░");
        getLogger().info("██╔══██╗██╔══██╗██║██║░░░██║██╔══██╗╚══██╔══╝██╔════╝████╗░██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██████╔╝██████╔╝██║╚██╗░██╔╝███████║░░░██║░░░█████╗░░██╔██╗██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██╔═══╝░██╔══██╗██║░╚████╔╝░██╔══██║░░░██║░░░██╔══╝░░██║╚████║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██║░░░░░██║░░██║██║░░╚██╔╝░░██║░░██║░░░██║░░░███████╗██║░╚███║╚██████╔╝███████╗███████╗");
        getLogger().info("╚═╝░░░░░╚═╝░░╚═╝╚═╝░░░╚═╝░░░╚═╝░░╚═╝░░░╚═╝░░░╚══════╝╚═╝░░╚══╝░╚═════╝░╚══════╝╚══════╝");
        getLogger().info("");
        getLogger().info("pnCases v" + getDescription().getVersion() + " успешно включён!");
        getLogger().info("Поддержка pnCases: " + SUPPORT_DISCORD);
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.cancel();
        }

        if (holograms != null) {
            holograms.shutdown();
        }

        if (caseManager != null) {
            caseManager.shutdown();
        }

        if (database != null) {
            database.close();
        }

        getLogger().info("██████╗░██████╗░██╗██╗░░░██╗░█████╗░████████╗███████╗███╗░░██╗██╗░░░██╗██╗░░░░░██╗░░░░░");
        getLogger().info("██╔══██╗██╔══██╗██║██║░░░██║██╔══██╗╚══██╔══╝██╔════╝████╗░██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██████╔╝██████╔╝██║╚██╗░██╔╝███████║░░░██║░░░█████╗░░██╔██╗██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██╔═══╝░██╔══██╗██║░╚████╔╝░██╔══██║░░░██║░░░██╔══╝░░██║╚████║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██║░░░░░██║░░██║██║░░╚██╔╝░░██║░░██║░░░██║░░░███████╗██║░╚███║╚██████╔╝███████╗███████╗");
        getLogger().info("╚═╝░░░░░╚═╝░░╚═╝╚═╝░░░╚═╝░░░╚═╝░░╚═╝░░░╚═╝░░░╚══════╝╚═╝░░╚══╝░╚═════╝░╚══════╝╚══════╝");
        getLogger().info("");
        getLogger().info("pnCases отключён");
        getLogger().info("Поддержка pnCases: " + SUPPORT_DISCORD);
    }

    public CaseManager getCaseManager() {
        return caseManager;
    }

    public KeyStorage getKeyStorage() {
        return keyStorage;
    }

    public SqliteDatabase getDatabase() {
        return database;
    }

    public HologramService getHolograms() {
        return holograms;
    }

    public VaultEconomyProvider getVaultEconomy() {
        if (vaultEconomy == null && getServer().getPluginManager().isPluginEnabled("Vault")) {
            try {
                vaultEconomy = new VaultEconomyProvider(this);
            } catch (Throwable ignored) {
                vaultEconomy = null;
            }
        }
        return vaultEconomy;
    }

    public PlayerPointsProvider getPlayerPoints() {
        if (playerPoints == null && getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            try {
                playerPoints = new PlayerPointsProvider();
            } catch (Throwable ignored) {
                playerPoints = null;
            }
        }
        return playerPoints;
    }

    public MessagesConfig getMessages() {
        return messages;
    }

    public PendingRewardStorage getPendingRewards() {
        return pendingRewards;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public String getSupportDiscord() {
        return SUPPORT_DISCORD;
    }

    public void reloadRuntimeConfig() {
        if (updateChecker != null) {
            updateChecker.reload();
        }
    }

    public ConfigValidator.Result validateCurrentConfig() {
        return ConfigValidator.validateAndPatch(this);
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        updateChecker.reload();
    }
}
