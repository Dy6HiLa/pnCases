package ru.privatenull.cases.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseBlockCodecTest {

    @Test
    void invalidCoordinatesDoNotSilentlyBindCaseAtZero() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", List.of(Map.of(
                "world", "world", "x", "broken", "y", 64, "z", 2
        )));

        assertTrue(new CaseBlockCodec().readConfiguredBlocks(yaml).isEmpty());
    }

    @Test
    void numericStringCoordinatesRemainSupported() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", List.of(Map.of(
                "world", "world", "x", "-4", "y", "70", "z", "12"
        )));

        Map<String, Object> block = new CaseBlockCodec().readConfiguredBlocks(yaml).get(0);
        assertEquals(-4, block.get("x"));
        assertEquals(70, block.get("y"));
        assertEquals(12, block.get("z"));
    }
}
