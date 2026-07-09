package ru.privatenull.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerCompatibility {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private ServerCompatibility() {
    }

    public static boolean isSupportedServer() {
        Version version = currentVersion();
        if (version.major() != 1) {
            return version.major() > 1;
        }
        if (version.minor() > 16) {
            return true;
        }
        return version.minor() == 16 && version.patch() >= 5;
    }

    public static boolean useModernAnimations() {
        Version version = currentVersion();
        return version.isAtLeast(1, 21, 0) && hasDisplayEntities();
    }

    public static boolean useMinecraft1165AnimationMode() {
        Version version = currentVersion();
        return version.major() == 1 && version.minor() == 16 && version.patch() >= 5;
    }

    public static boolean hasDisplayEntities() {
        try {
            EntityType.valueOf("ITEM_DISPLAY");
            EntityType.valueOf("TEXT_DISPLAY");
            EntityType.valueOf("BLOCK_DISPLAY");
            Class.forName("org.bukkit.entity.ItemDisplay", false, ServerCompatibility.class.getClassLoader());
            Class.forName("org.bukkit.entity.TextDisplay", false, ServerCompatibility.class.getClassLoader());
            Class.forName("org.bukkit.entity.BlockDisplay", false, ServerCompatibility.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Version currentVersion() {
        Matcher matcher = VERSION_PATTERN.matcher(Bukkit.getBukkitVersion());
        if (!matcher.find()) {
            return new Version(1, 21, 0);
        }
        return new Version(
                parseInt(matcher.group(1), 1),
                parseInt(matcher.group(2), 21),
                parseInt(matcher.group(3), 0)
        );
    }

    public static void logCompatibility(Plugin plugin) {
        Version version = currentVersion();
        plugin.getLogger().info("Режим совместимости сервера: Minecraft " + version);
        if (useModernAnimations()) {
            plugin.getLogger().info("Анимации: полный режим pnCases для 1.21+.");
        } else if (useMinecraft1165AnimationMode()) {
            plugin.getLogger().info("Анимации: режим 1.16.5, доступен только Круг фортуны.");
        } else {
            plugin.getLogger().info("Анимации: совместимый режим для 1.17-1.20.");
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public record Version(int major, int minor, int patch) {
        public boolean isAtLeast(int targetMajor, int targetMinor, int targetPatch) {
            if (major != targetMajor) return major > targetMajor;
            if (minor != targetMinor) return minor > targetMinor;
            return patch >= targetPatch;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
