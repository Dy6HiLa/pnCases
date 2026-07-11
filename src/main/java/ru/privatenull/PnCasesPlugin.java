package ru.privatenull;

import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.commands.CasesCommand;
import ru.privatenull.commands.CasesCommandCompleter;
import ru.privatenull.config.ConfigValidator;
import ru.privatenull.config.GuiConfig;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.hologram.HologramService;
import ru.privatenull.integrations.PlayerPointsProvider;
import ru.privatenull.integrations.VaultEconomyProvider;
import ru.privatenull.gui.machine.MachineGuiListener;
import ru.privatenull.listeners.CaseBlockListener;
import ru.privatenull.gui.caseview.CaseGuiListener;
import ru.privatenull.gui.caseview.CaseMenuService;
import ru.privatenull.listeners.RuntimeListener;
import ru.privatenull.lifecycle.PluginBanner;
import ru.privatenull.storage.KeyStorage;
import ru.privatenull.storage.PendingRewardStorage;
import ru.privatenull.storage.SqliteDatabase;
import ru.privatenull.update.UpdateChecker;
import ru.privatenull.util.ServerCompatibility;

public final class PnCasesPlugin extends JavaPlugin {

    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";

    private CaseManager caseManager;
    private KeyStorage keyStorage;
    private HologramService holograms;
    private VaultEconomyProvider vaultEconomy;
    private PlayerPointsProvider playerPoints;
    private MessagesConfig messages;
    private GuiConfig guiConfig;
    private SqliteDatabase database;
    private PendingRewardStorage pendingRewards;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        if (!ServerCompatibility.isSupportedServer()) {
            getLogger().severe("pnCases 1.4.9 поддерживает Minecraft 1.16.5 - 1.21.11. Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ServerCompatibility.logCompatibility(this);

        saveDefaultConfig();
        validateCurrentConfig();

        database = new SqliteDatabase(this);
        keyStorage = new KeyStorage(database);
        pendingRewards = new PendingRewardStorage(database);

        messages = new MessagesConfig(this);
        guiConfig = new GuiConfig(this);
        holograms = new HologramService(this);

        caseManager = new CaseManager(this);
        caseManager.setCaseView(new CaseMenuService(this, caseManager));
        caseManager.exportMainCasesToFilesIfMissing();
        caseManager.reloadFromConfig();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (caseManager != null) {
                caseManager.reloadFromConfig();
            }
        }, 40L);
        setupUpdateChecker();

        var cmd = getCommand("pncases");
        if (cmd == null) {
            throw new IllegalStateException("Команда pncases отсутствует в plugin.yml");
        }
        var machineGui = new MachineGuiListener(caseManager);
        var exec = new CasesCommand(this, caseManager, machineGui);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(new CasesCommandCompleter(caseManager));

        getServer().getPluginManager().registerEvents(
                new CaseBlockListener(caseManager), this
        );
        getServer().getPluginManager().registerEvents(
                new CaseGuiListener(caseManager), this
        );
        getServer().getPluginManager().registerEvents(machineGui, this);
        var runtimeListener = new RuntimeListener(this, caseManager, pendingRewards);
        getServer().getPluginManager().registerEvents(runtimeListener, this);
        runtimeListener.deliverPendingToOnlinePlayers();

        PluginBanner.enabled(this, SUPPORT_DISCORD);
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

        PluginBanner.disabled(this, SUPPORT_DISCORD);
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
            } catch (RuntimeException | LinkageError ex) {
                getLogger().warning("Не удалось подключить Vault: " + ex.getMessage());
                vaultEconomy = null;
            }
        }
        return vaultEconomy;
    }

    public PlayerPointsProvider getPlayerPoints() {
        if (playerPoints == null && getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            try {
                playerPoints = new PlayerPointsProvider();
            } catch (RuntimeException | LinkageError ex) {
                getLogger().warning("Не удалось подключить PlayerPoints: " + ex.getMessage());
                playerPoints = null;
            }
        }
        return playerPoints;
    }

    public MessagesConfig getMessages() {
        return messages;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
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
