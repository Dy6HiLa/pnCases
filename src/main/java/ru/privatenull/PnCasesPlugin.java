package ru.privatenull;

import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
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
import ru.privatenull.gui.machine.MachineGuiHolder;
import ru.privatenull.gui.GuiUpdateService;
import ru.privatenull.gui.GuiOpenAnimationService;
import ru.privatenull.listeners.CaseBlockListener;
import ru.privatenull.gui.caseview.CaseGuiListener;
import ru.privatenull.gui.caseview.CaseGuiHolder;
import ru.privatenull.gui.caseview.CaseMenuService;
import ru.privatenull.gui.caseview.AnimationSelectHolder;
import ru.privatenull.listeners.RuntimeListener;
import ru.privatenull.pnlibrary.lifecycle.PluginBanner;
import ru.privatenull.pnlibrary.database.DatabaseSettings;
import ru.privatenull.pnlibrary.database.JdbcSettings;
import ru.privatenull.pnlibrary.database.MongoSettings;
import ru.privatenull.pnlibrary.update.UpdateChecker;
import ru.privatenull.pnlibrary.update.UpdateSettings;
import ru.privatenull.storage.CasesDatabase;
import ru.privatenull.storage.JdbcCasesDatabase;
import ru.privatenull.storage.KeyStorage;
import ru.privatenull.storage.MongoCasesDatabase;
import ru.privatenull.storage.PendingRewardStorage;
import ru.privatenull.util.ServerCompatibility;

public final class PnCasesPlugin extends JavaPlugin {

    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";
    private static final String GITHUB_REPOSITORY = "Dy6HiLa/pnCases";
    private static final int BSTATS_PLUGIN_ID = 32592;

    private CaseManager caseManager;
    private KeyStorage keyStorage;
    private HologramService holograms;
    private VaultEconomyProvider vaultEconomy;
    private PlayerPointsProvider playerPoints;
    private MessagesConfig messages;
    private GuiConfig guiConfig;
    private GuiUpdateService guiUpdates;
    private GuiOpenAnimationService guiOpenAnimations;
    private CasesDatabase database;
    private PendingRewardStorage pendingRewards;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        if (!ServerCompatibility.isSupportedServer()) {
            getLogger().severe("pnCases 1.5.0 поддерживает Minecraft 1.16.5 - 1.21.11. Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ServerCompatibility.logCompatibility(this);

        saveDefaultConfig();
        validateCurrentConfig();

        try {
            database = createDatabase();
            database.open(getDataFolder());
            getLogger().info("Database connected: " + database.type());
        } catch (Exception exception) {
            getLogger().severe("Could not initialize pnCases database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        keyStorage = new KeyStorage(database);
        pendingRewards = new PendingRewardStorage(database);

        messages = new MessagesConfig(this);
        guiConfig = new GuiConfig(this);
        guiUpdates = new GuiUpdateService(this);
        guiOpenAnimations = new GuiOpenAnimationService(this);
        holograms = new HologramService(this);

        caseManager = new CaseManager(this);
        caseManager.setCaseView(new CaseMenuService(this, caseManager));
        caseManager.exportMainCasesToFilesIfMissing();
        caseManager.reloadFromConfig();
        setupUpdateChecker();
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("database_type", () -> database.type().name()));

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
        closePnCasesGuis();
        if (guiOpenAnimations != null) {
            guiOpenAnimations.shutdown();
        }
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

    public CasesDatabase getDatabase() {
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

    public GuiUpdateService getGuiUpdates() {
        return guiUpdates;
    }

    public GuiOpenAnimationService getGuiOpenAnimations() {
        return guiOpenAnimations;
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
            updateChecker.restart(updateSettings());
        }
    }

    public ConfigValidator.Result validateCurrentConfig() {
        return ConfigValidator.validateAndPatch(this);
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(this, updateSettings());
        updateChecker.start();
    }

    private UpdateSettings updateSettings() {
        return new UpdateSettings(true, GITHUB_REPOSITORY, "pncases.admin", 6L, SUPPORT_DISCORD);
    }

    private CasesDatabase createDatabase() {
        DatabaseSettings settings = DatabaseSettings.from(getConfig().getConfigurationSection("database"), getDataFolder());
        if (settings instanceof JdbcSettings jdbc) return new JdbcCasesDatabase(jdbc);
        if (settings instanceof MongoSettings mongo) return new MongoCasesDatabase(mongo);
        throw new IllegalStateException("Unsupported database type: " + settings.type());
    }

    private void closePnCasesGuis() {
        for (var player : getServer().getOnlinePlayers()) {
            var top = player.getOpenInventory().getTopInventory();
            Object holder = top == null ? null : top.getHolder();
            if (holder instanceof MachineGuiHolder
                    || holder instanceof CaseGuiHolder
                    || holder instanceof AnimationSelectHolder) {
                player.closeInventory();
            }
        }
    }
}
