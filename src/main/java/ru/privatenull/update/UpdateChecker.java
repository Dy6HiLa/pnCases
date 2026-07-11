package ru.privatenull.update;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.PnCasesPlugin;

public final class UpdateChecker {

    private static final long CHECK_DELAY_TICKS = 100L;
    private static final long CHECK_PERIOD_MINUTES = 360L;
    private static final boolean NOTIFY_ADMINS_ON_JOIN = true;

    private final PnCasesPlugin plugin;
    private final GitHubUpdateClient client;
    private final UpdateNotifier notifier;

    private BukkitTask task;
    private volatile String latestVersion;
    private volatile String downloadUrl = GitHubUpdateClient.DEFAULT_DOWNLOAD_URL;
    private volatile boolean updateAvailable;
    private volatile boolean checkCompleted;
    private volatile String lastError;

    public UpdateChecker(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.client = new GitHubUpdateClient();
        this.notifier = new UpdateNotifier(plugin);
    }

    public void reload() {
        cancel();
        updateAvailable = false;
        latestVersion = null;
        downloadUrl = GitHubUpdateClient.DEFAULT_DOWNLOAD_URL;
        checkCompleted = false;
        lastError = null;

        long periodTicks = CHECK_PERIOD_MINUTES * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::check,
                CHECK_DELAY_TICKS,
                periodTicks
        );
    }

    public void cancel() {
        if (task == null) return;
        task.cancel();
        task = null;
    }

    public void notifyAdminOnJoin(Player player) {
        if (!NOTIFY_ADMINS_ON_JOIN
                || !updateAvailable
                || player == null
                || !player.hasPermission("pncases.admin")) {
            return;
        }
        notifier.send(player, latestVersion, getDownloadUrl());
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean isCheckCompleted() {
        return checkCompleted;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl == null || downloadUrl.isBlank()
                ? GitHubUpdateClient.DEFAULT_DOWNLOAD_URL
                : downloadUrl;
    }

    public String getLastError() {
        return lastError;
    }

    private void check() {
        try {
            apply(client.fetchLatest());
        } catch (Exception exception) {
            checkCompleted = true;
            lastError = exception.getMessage();
            plugin.getLogger().warning("Ошибка проверки обновлений: " + exception.getMessage());
        }
    }

    private void apply(UpdateInfo info) {
        String found = info.version();
        checkCompleted = true;
        if (found == null || found.isBlank()) {
            lastError = "GitHub не вернул версию";
            plugin.getLogger().warning("Проверка обновлений: GitHub не вернул версию.");
            return;
        }

        lastError = null;
        String current = plugin.getDescription().getVersion();
        if (VersionComparator.compare(found, current) <= 0) {
            updateAvailable = false;
            latestVersion = found;
            return;
        }

        boolean firstNotice = !updateAvailable || latestVersion == null || !latestVersion.equalsIgnoreCase(found);
        updateAvailable = true;
        latestVersion = found;
        downloadUrl = info.downloadUrl() == null || info.downloadUrl().isBlank()
                ? GitHubUpdateClient.DEFAULT_DOWNLOAD_URL
                : info.downloadUrl();
        if (!firstNotice) return;

        plugin.getLogger().warning(System.lineSeparator() + notifier.consoleMessage(found, downloadUrl));
        Bukkit.getScheduler().runTask(plugin, this::notifyOnlineAdmins);
    }

    private void notifyOnlineAdmins() {
        if (!updateAvailable) return;
        for (Player player : Bukkit.getOnlinePlayers()) notifyAdminOnJoin(player);
    }
}
