package ru.privatenull.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourceYamlTest {

    @ParameterizedTest(name = "{0} is valid YAML")
    @ValueSource(strings = {
            "config.yml",
            "gui.yml",
            "messages.yml",
            "plugin.yml"
    })
    void bundledYamlIsValid(String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing resource: " + resource);
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            new YamlConfiguration().loadFromString(yaml);
        }
    }

    @org.junit.jupiter.api.Test
    void guiHistoryEmptyItemHasConfigurableLore() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("gui.yml")) {
            assertNotNull(input, "Missing resource: gui.yml");
            YamlConfiguration gui = new YamlConfiguration();
            gui.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(gui.getStringList("case.history.empty.lore").isEmpty(),
                    "case.history.empty.lore must remain in gui.yml");
        }
    }
}
