package ru.privatenull.config;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigValuesTest {

    @Test
    void readsFirstConfiguredAlias() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("legacy-name", 17);
        config.set("new-name", 25);

        assertEquals(25, ConfigValues.integer(config, 5, "new-name", "legacy-name"));
        assertEquals(17, ConfigValues.integer(config, 5, "missing", "legacy-name"));
    }

    @Test
    void usesFallbackForMissingOrInvalidValues() {
        MemoryConfiguration config = new MemoryConfiguration();
        assertFalse(ConfigValues.bool(config, false, "enabled"));
        assertEquals(8, ConfigValues.integer("invalid", 8));
        assertEquals(1.5, ConfigValues.decimal("invalid", 1.5));
    }
}
