package ru.privatenull.config;

import org.bukkit.configuration.ConfigurationSection;

public final class ConfigSections {

    private ConfigSections() {
    }

    public static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection existing = parent.getConfigurationSection(path);
        return existing == null ? parent.createSection(path) : existing;
    }
}
