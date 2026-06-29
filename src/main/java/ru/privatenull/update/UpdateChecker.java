package ru.privatenull.update;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pnCases;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private static final Pattern VERSION_PATTERN = Pattern.compile("v?\\d+(?:\\.\\d+){0,3}(?:[-+][A-Za-z0-9._-]+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_VERSION_PATTERN = Pattern.compile("(?m)^\\s*version\\s*[:=]\\s*['\"]?([^'\"\\r\\n]+)['\"]?");
    private static final Pattern JSON_VERSION_PATTERN = Pattern.compile("\"(?:version|latestVersion|latest_version|tag_name|name)\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_DOWNLOAD_PATTERN = Pattern.compile("\"(?:downloadUrl|download_url|html_url)\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_ASSET_DOWNLOAD_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+?\\.jar[^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/Dy6HiLa/pnCases/releases/latest";
    private static final String GITHUB_TAGS_URL = "https://api.github.com/repos/Dy6HiLa/pnCases/tags";
    private static final String GITHUB_PLUGIN_YML_URL = "https://raw.githubusercontent.com/Dy6HiLa/pnCases/master/src/main/resources/plugin.yml";
    private static final String DEFAULT_DOWNLOAD_URL = "https://github.com/Dy6HiLa/pnCases/releases/latest";
    private static final long CHECK_DELAY_TICKS = 100L;
    private static final long CHECK_PERIOD_MINUTES = 360L;
    private static final boolean NOTIFY_ADMINS_ON_JOIN = true;

    private final pnCases plugin;
    private BukkitTask task;
    private String latestVersion;
    private String downloadUrl = DEFAULT_DOWNLOAD_URL;
    private boolean updateAvailable;

    public UpdateChecker(pnCases plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        cancel();

        updateAvailable = false;
        latestVersion = null;
        downloadUrl = DEFAULT_DOWNLOAD_URL;

        long periodTicks = CHECK_PERIOD_MINUTES * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, CHECK_DELAY_TICKS, periodTicks);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void notifyAdminOnJoin(Player player) {
        if (!NOTIFY_ADMINS_ON_JOIN || !updateAvailable || player == null || !player.hasPermission("pncases.admin")) {
            return;
        }

        player.sendMessage(formatAdminMessage());
    }

    private void check() {
        try {
            UpdateInfo updateInfo = fetchLatestUpdateInfo();
            String found = updateInfo.version();
            if (found == null || found.isBlank()) {
                plugin.getLogger().warning("Update checker: GitHub response did not contain a version.");
                return;
            }

            String current = plugin.getDescription().getVersion();
            if (compareVersions(found, current) <= 0) {
                updateAvailable = false;
                latestVersion = found;
                return;
            }

            boolean firstNotice = !updateAvailable || latestVersion == null || !latestVersion.equalsIgnoreCase(found);
            updateAvailable = true;
            latestVersion = found;
            downloadUrl = updateInfo.downloadUrl() == null || updateInfo.downloadUrl().isBlank()
                    ? DEFAULT_DOWNLOAD_URL
                    : updateInfo.downloadUrl();

            if (firstNotice) {
                plugin.getLogger().warning(formatConsoleMessage());
                Bukkit.getScheduler().runTask(plugin, this::notifyOnlineAdmins);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Update checker error: " + ex.getMessage());
        }
    }

    private void notifyOnlineAdmins() {
        if (!updateAvailable) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            notifyAdminOnJoin(player);
        }
    }

    private static UpdateInfo fetchLatestUpdateInfo() throws Exception {
        try {
            UpdateInfo releaseInfo = extractUpdateInfo(fetch(GITHUB_RELEASES_URL), false);
            if (releaseInfo.version() != null && !releaseInfo.version().isBlank()) {
                return releaseInfo;
            }
        } catch (Exception ignored) {
            // If there are no GitHub releases yet, use the latest git tag.
        }

        try {
            UpdateInfo tagInfo = extractUpdateInfo(fetch(GITHUB_TAGS_URL), false);
            if (tagInfo.version() != null && !tagInfo.version().isBlank()) {
                return tagInfo;
            }
        } catch (Exception ignored) {
            // Fall back to the source plugin.yml version from the default branch.
        }

        return extractSourceUpdateInfo(fetch(GITHUB_PLUGIN_YML_URL));
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "pnCases UpdateChecker");

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + status);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append('\n');
            }
            return body.toString().trim();
        } finally {
            connection.disconnect();
        }
    }

    private static UpdateInfo extractUpdateInfo(String raw, boolean allowPlainTextVersion) {
        String version = extractVersion(raw, allowPlainTextVersion);
        String download = extractDownloadUrl(raw);
        if ((download == null || download.isBlank()) && version != null && !version.isBlank()) {
            download = "https://github.com/Dy6HiLa/pnCases/releases/tag/" + version;
        }
        return new UpdateInfo(version, download);
    }

    private static UpdateInfo extractSourceUpdateInfo(String raw) {
        if (raw == null) {
            return new UpdateInfo(null, DEFAULT_DOWNLOAD_URL);
        }

        Matcher matcher = SOURCE_VERSION_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return new UpdateInfo(null, DEFAULT_DOWNLOAD_URL);
        }

        return new UpdateInfo(cleanVersion(matcher.group(1)), DEFAULT_DOWNLOAD_URL);
    }

    private static String extractVersion(String raw, boolean allowPlainTextVersion) {
        if (raw == null) {
            return null;
        }

        Matcher jsonMatcher = JSON_VERSION_PATTERN.matcher(raw);
        while (jsonMatcher.find()) {
            String cleaned = cleanVersion(unescapeJson(jsonMatcher.group(1)));
            if (cleaned != null && VERSION_PATTERN.matcher(cleaned).matches()) {
                return cleaned;
            }
        }

        if (!allowPlainTextVersion && looksLikeJson(raw)) {
            return null;
        }

        Matcher matcher = VERSION_PATTERN.matcher(raw.trim());
        return matcher.find() ? cleanVersion(matcher.group()) : null;
    }

    private static boolean looksLikeJson(String raw) {
        String trimmed = raw.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static String extractDownloadUrl(String raw) {
        if (raw == null) {
            return null;
        }

        Matcher jarAssetMatcher = GITHUB_ASSET_DOWNLOAD_PATTERN.matcher(raw);
        if (jarAssetMatcher.find()) {
            return unescapeJson(jarAssetMatcher.group(1));
        }

        Matcher jsonMatcher = JSON_DOWNLOAD_PATTERN.matcher(raw);
        return jsonMatcher.find() ? unescapeJson(jsonMatcher.group(1)) : null;
    }

    private static String cleanVersion(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher = VERSION_PATTERN.matcher(value.trim());
        return matcher.find() ? matcher.group() : value.trim();
    }

    private static String unescapeJson(String value) {
        if (value == null) {
            return null;
        }

        return value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String formatConsoleMessage() {
        String message = "New pnCases version available: " + latestVersion
                + " (installed " + plugin.getDescription().getVersion() + ")";
        if (downloadUrl != null && !downloadUrl.isBlank()) {
            message += " | " + downloadUrl;
        }
        return message;
    }

    private String formatAdminMessage() {
        String message = "\u00a76pnCases \u00a78| \u00a7fAvailable update: \u00a7a" + latestVersion
                + " \u00a77(installed \u00a7f" + plugin.getDescription().getVersion() + "\u00a77)";
        if (downloadUrl != null && !downloadUrl.isBlank()) {
            message += "\n\u00a77Download: \u00a7f" + downloadUrl;
        }
        return message;
    }

    private static int compareVersions(String latest, String current) {
        Version left = Version.parse(latest);
        Version right = Version.parse(current);

        for (int i = 0; i < Math.max(left.parts.length, right.parts.length); i++) {
            int l = i < left.parts.length ? left.parts[i] : 0;
            int r = i < right.parts.length ? right.parts[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }

        if (left.snapshot != right.snapshot) {
            return left.snapshot ? -1 : 1;
        }

        return 0;
    }

    private record Version(int[] parts, boolean snapshot) {
        static Version parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            boolean snapshot = normalized.contains("snapshot");
            normalized = normalized.replaceFirst("^v", "");
            String base = normalized.split("[-+]", 2)[0];
            String[] rawParts = base.split("\\.");
            int[] parts = new int[rawParts.length];
            for (int i = 0; i < rawParts.length; i++) {
                try {
                    parts[i] = Integer.parseInt(rawParts[i].replaceAll("\\D", ""));
                } catch (NumberFormatException ex) {
                    parts[i] = 0;
                }
            }
            return new Version(parts, snapshot);
        }
    }

    private record UpdateInfo(String version, String downloadUrl) {
    }
}
