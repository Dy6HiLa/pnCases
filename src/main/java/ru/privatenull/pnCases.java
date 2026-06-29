package ru.privatenull;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.commands.CasesCMD;
import ru.privatenull.commands.CasesCMDTabCompliter;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.integrations.FancyHologramsHook;
import ru.privatenull.storage.KeyStorage;
import ru.privatenull.update.UpdateChecker;

public final class pnCases extends JavaPlugin {

    private CaseManager caseManager;
    private KeyStorage keyStorage;
    private FancyHologramsHook fancyHolograms;
    private MessagesConfig messages;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messages = new MessagesConfig(this);
        this.fancyHolograms = new FancyHologramsHook(this);

        this.caseManager = new CaseManager(this);
        this.caseManager.reloadFromConfig();
        setupUpdateChecker();

        this.keyStorage = new KeyStorage(this);

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                if (updateChecker != null) {
                    updateChecker.notifyAdminOnJoin(e.getPlayer());
                }
            }
        }, this);

        var cmd = getCommand("pncases");
        var exec = new CasesCMD(this, caseManager);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(new CasesCMDTabCompliter(caseManager));

        getServer().getPluginManager().registerEvents(new ru.privatenull.listeners.CaseBlockListener(caseManager), this);
        getServer().getPluginManager().registerEvents(new ru.privatenull.listeners.CaseGuiListener(caseManager), this);
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
        if (updateChecker != null) updateChecker.cancel();
        if (fancyHolograms != null) fancyHolograms.shutdown();
        if (caseManager != null) caseManager.shutdown();
        getLogger().info("██████╗░██████╗░██╗██╗░░░██╗░█████╗░████████╗███████╗███╗░░██╗██╗░░░██╗██╗░░░░░██╗░░░░░");
        getLogger().info("██╔══██╗██╔══██╗██║██║░░░██║██╔══██╗╚══██╔══╝██╔════╝████╗░██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██████╔╝██████╔╝██║╚██╗░██╔╝███████║░░░██║░░░█████╗░░██╔██╗██║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██╔═══╝░██╔══██╗██║░╚████╔╝░██╔══██║░░░██║░░░██╔══╝░░██║╚████║██║░░░██║██║░░░░░██║░░░░░");
        getLogger().info("██║░░░░░██║░░██║██║░░╚██╔╝░░██║░░██║░░░██║░░░███████╗██║░╚███║╚██████╔╝███████╗███████╗");
        getLogger().info("╚═╝░░░░░╚═╝░░╚═╝╚═╝░░░╚═╝░░░╚═╝░░╚═╝░░░╚═╝░░░╚══════╝╚═╝░░╚══╝░╚═════╝░╚══════╝╚══════╝");
        getLogger().info("");
        getLogger().info("pnCases отключён | ds: privatenull");
    }

    public CaseManager getCaseManager() { return caseManager; }
    public KeyStorage getKeyStorage()   { return keyStorage; }
    public FancyHologramsHook getFancyHolograms() { return fancyHolograms; }
    public MessagesConfig getMessages() { return messages; }

    public void reloadRuntimeConfig() {
        if (updateChecker != null) {
            updateChecker.reload();
        }
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        updateChecker.reload();
    }
}
