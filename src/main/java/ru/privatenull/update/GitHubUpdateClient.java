package ru.privatenull.update;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubUpdateClient {

    static final String DEFAULT_DOWNLOAD_URL = "https://github.com/Dy6HiLa/pnCases/releases/latest";

    private static final Pattern VERSION = Pattern.compile(
            "v?\\d+(?:\\.\\d+){0,3}(?:[-+][A-Za-z0-9._-]+)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SOURCE_VERSION = Pattern.compile(
            "(?m)^\\s*version\\s*[:=]\\s*['\"]?([^'\"\\r\\n]+)['\"]?"
    );
    private static final Pattern JSON_VERSION = Pattern.compile(
            "\"(?:version|latestVersion|latest_version|tag_name|name)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_DOWNLOAD = Pattern.compile(
            "\"(?:downloadUrl|download_url|html_url)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAR_DOWNLOAD = Pattern.compile(
            "\"browser_download_url\"\\s*:\\s*\"([^\"]+?\\.jar[^\"]*)\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<Source> SOURCES = List.of(
            new Source("https://raw.githubusercontent.com/Dy6HiLa/pnCases/master/update-manifest.json", false),
            new Source("https://api.github.com/repos/Dy6HiLa/pnCases/releases/latest", false),
            new Source("https://api.github.com/repos/Dy6HiLa/pnCases/tags", false),
            new Source("https://raw.githubusercontent.com/Dy6HiLa/pnCases/master/src/main/resources/plugin.yml", true)
    );

    UpdateInfo fetchLatest() throws Exception {
        List<UpdateInfo> candidates = new ArrayList<>();
        Exception lastFailure = null;
        for (Source source : SOURCES) {
            try {
                UpdateInfo candidate = source.pluginYaml()
                        ? fromPluginYaml(fetch(source.url()))
                        : fromJson(fetch(source.url()));
                if (candidate.version() != null && !candidate.version().isBlank()) candidates.add(candidate);
            } catch (Exception failure) {
                lastFailure = failure;
            }
        }
        UpdateInfo best = null;
        for (UpdateInfo candidate : candidates) {
            if (best == null || VersionComparator.compare(candidate.version(), best.version()) > 0) best = candidate;
        }
        if (best != null) return best;
        if (lastFailure != null) throw new IllegalStateException("Все источники GitHub недоступны", lastFailure);
        return new UpdateInfo(null, DEFAULT_DOWNLOAD_URL);
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "pnCases UpdateChecker");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status + " for " + url);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append('\n');
                return body.toString().trim();
            }
        } finally {
            connection.disconnect();
        }
    }

    private UpdateInfo fromJson(String raw) {
        String version = extractVersion(raw);
        String download = extractDownloadUrl(raw);
        if ((download == null || download.isBlank()) && version != null && !version.isBlank()) {
            download = "https://github.com/Dy6HiLa/pnCases/releases/tag/" + version;
        }
        return new UpdateInfo(version, download);
    }

    private UpdateInfo fromPluginYaml(String raw) {
        if (raw == null) return new UpdateInfo(null, DEFAULT_DOWNLOAD_URL);
        Matcher matcher = SOURCE_VERSION.matcher(raw);
        return matcher.find()
                ? new UpdateInfo(cleanVersion(matcher.group(1)), DEFAULT_DOWNLOAD_URL)
                : new UpdateInfo(null, DEFAULT_DOWNLOAD_URL);
    }

    private String extractVersion(String raw) {
        if (raw == null) return null;
        Matcher matcher = JSON_VERSION.matcher(raw);
        while (matcher.find()) {
            String cleaned = cleanVersion(unescape(matcher.group(1)));
            if (cleaned != null && VERSION.matcher(cleaned).matches()) return cleaned;
        }
        if (looksLikeJson(raw)) return null;
        Matcher plain = VERSION.matcher(raw.trim());
        return plain.find() ? cleanVersion(plain.group()) : null;
    }

    private String extractDownloadUrl(String raw) {
        if (raw == null) return null;
        Matcher jar = JAR_DOWNLOAD.matcher(raw);
        if (jar.find()) return unescape(jar.group(1));
        Matcher generic = JSON_DOWNLOAD.matcher(raw);
        return generic.find() ? unescape(generic.group(1)) : null;
    }

    private String cleanVersion(String value) {
        if (value == null) return null;
        Matcher matcher = VERSION.matcher(value.trim());
        return matcher.find() ? matcher.group() : value.trim();
    }

    private String unescape(String value) {
        return value == null ? null : value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private boolean looksLikeJson(String raw) {
        String trimmed = raw.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private record Source(String url, boolean pluginYaml) {
    }
}
