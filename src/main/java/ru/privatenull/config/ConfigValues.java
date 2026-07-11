package ru.privatenull.config;

import org.bukkit.configuration.ConfigurationSection;

public final class ConfigValues {

    private ConfigValues() {
    }

    public static String string(ConfigurationSection section, String fallback, String... keys) {
        if (section == null) return fallback;
        for (String key : keys) {
            if (section.contains(key)) return section.getString(key, fallback);
        }
        return fallback;
    }

    public static int integer(ConfigurationSection section, int fallback, String... keys) {
        if (section == null) return fallback;
        for (String key : keys) {
            if (section.contains(key)) return section.getInt(key, fallback);
        }
        return fallback;
    }

    public static boolean bool(ConfigurationSection section, boolean fallback, String... keys) {
        if (section == null) return fallback;
        for (String key : keys) {
            if (section.contains(key)) return section.getBoolean(key, fallback);
        }
        return fallback;
    }

    public static double decimal(ConfigurationSection section, double fallback, String... keys) {
        if (section == null) return fallback;
        for (String key : keys) {
            if (section.contains(key)) return section.getDouble(key, fallback);
        }
        return fallback;
    }

    public static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
