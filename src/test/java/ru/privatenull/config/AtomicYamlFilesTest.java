package ru.privatenull.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicYamlFilesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validReadbackReplacesTargetAndKeepsPreviousYaml() throws Exception {
        File target = temporaryDirectory.resolve("gui.yml").toFile();
        YamlConfiguration previous = new YamlConfiguration();
        previous.set("title", "previous");
        previous.save(target);

        YamlConfiguration updated = new YamlConfiguration();
        updated.set("title", "updated");
        assertTrue(AtomicYamlFiles.save(updated, target, Logger.getLogger("test")));

        assertEquals("updated", YamlConfiguration.loadConfiguration(target).getString("title"));
        assertEquals("previous", YamlConfiguration.loadConfiguration(
                new File(target.getPath() + ".bak")).getString("title"));
    }
}
