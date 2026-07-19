package ru.privatenull.cases.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseConfigRepositoryTest {

    @Test
    void newCaseHasItsOwnKeyAndMinimalAnimationSettings() {
        YamlConfiguration target = new YamlConfiguration();
        CaseConfigRepository.writeNewCaseDefaults(target, "donate");

        assertEquals("donate", target.getString("id"));
        assertTrue(target.contains("blocks"));
        assertFalse(target.contains("template"));
        assertEquals("KEY", target.getString("cost.type"));
        assertEquals("donate", target.getString("cost.key"));
        assertFalse(target.contains("animation.duration_ticks"));
        assertFalse(target.contains("animation.cycle_every_ticks"));
        assertFalse(target.contains("animation.rise_blocks"));
        assertFalse(target.contains("animation.spin_degrees_per_tick"));
        assertEquals("ITEM", target.getMapList("rewards").get(0).get("type"));
    }

    @Test
    void fileNameIsTheCanonicalCaseId() {
        assertEquals("money", CaseConfigRepository.fileId("Money.yml"));
        assertEquals("custom_case", CaseConfigRepository.fileId("custom_case.YML"));
        assertTrue(CaseConfigRepository.isValidId(CaseConfigRepository.fileId("safe-case.yml")));
    }

    @Test
    void generatedGuiTitleFollowsCaseDisplayName() {
        assertEquals("&dДонат", CaseDefinitionLoader.resolveGuiTitle("&dДонат", "{display-name}"));
        assertEquals("&8Магазин", CaseDefinitionLoader.resolveGuiTitle("&dДонат", "&8Магазин"));
    }

    @Test
    void incompleteAutoExportPrefersEmbeddedCaseOverStandaloneCopy() {
        assertTrue(CaseConfigRepository.preferEmbeddedSource(true, true, true));
        assertFalse(CaseConfigRepository.preferEmbeddedSource(false, true, true));
        assertFalse(CaseConfigRepository.preferEmbeddedSource(true, false, true));
        assertFalse(CaseConfigRepository.preferEmbeddedSource(true, true, false));

        assertTrue(CaseConfigRepository.replaceStandaloneWithEmbedded(true, true));
        assertFalse(CaseConfigRepository.replaceStandaloneWithEmbedded(true, false));
        assertFalse(CaseConfigRepository.replaceStandaloneWithEmbedded(false, true));

        assertTrue(CaseConfigRepository.declaredSourceExists(false, 1));
        assertTrue(CaseConfigRepository.declaredSourceExists(true, 0));
        assertFalse(CaseConfigRepository.declaredSourceExists(false, 0));

        assertTrue(CaseConfigRepository.missingCaseDirectoryIsEmpty(false, true));
        assertFalse(CaseConfigRepository.missingCaseDirectoryIsEmpty(false, false));
        assertFalse(CaseConfigRepository.missingCaseDirectoryIsEmpty(true, true));
    }

    @Test
    void repeatedMigrationRecognizesAlreadyExportedCaseWithoutRotatingBackupAgain() {
        YamlConfiguration embedded = new YamlConfiguration();
        embedded.set("id", "money");
        embedded.set("gui.title", "Custom title");
        embedded.set("blocks", List.of(Map.of("world", "world", "x", 4, "y", 70, "z", -2)));

        YamlConfiguration exported = new YamlConfiguration();
        exported.set("id", "money");
        exported.set("gui.title", "Custom title");
        exported.set("blocks", List.of(Map.of("world", "world", "x", 4, "y", 70, "z", -2)));

        assertTrue(CaseConfigRepository.sameCaseContent(embedded, exported));
        exported.set("gui.title", "Old standalone title");
        assertFalse(CaseConfigRepository.sameCaseContent(embedded, exported));
    }

    @Test
    void explicitGuiSizeWinsOverLegacyRowsAfterMachineEdit() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("size", 45);
        gui.set("rows", 6);

        assertEquals(45, CaseViewConfigParser.resolveSize(gui));
    }

    @Test
    void rollbackRestoresNestedYamlSectionsInsteadOfPlainMaps() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", "rollback_case");
        yaml.set("gui.title", "Custom GUI");
        yaml.set("cost.type", "KEY");
        yaml.set("cost.key", "rollback_key");
        yaml.set("animation.fixed", "AQUARIUM");
        yaml.set("rewards", List.of(Map.of("type", "ITEM", "chance", 100)));

        Map<String, Object> snapshot = CaseConfigRepository.snapshotSection(yaml);
        for (String key : List.copyOf(yaml.getKeys(false))) yaml.set(key, null);
        CaseConfigRepository.restoreSection(yaml, snapshot);

        assertTrue(yaml.isConfigurationSection("gui"));
        assertTrue(yaml.isConfigurationSection("cost"));
        assertTrue(yaml.isConfigurationSection("animation"));
        assertEquals("Custom GUI", yaml.getString("gui.title"));
        assertEquals("rollback_key", yaml.getString("cost.key"));
        assertEquals("AQUARIUM", yaml.getString("animation.fixed"));
        assertEquals("ITEM", yaml.getMapList("rewards").get(0).get("type"));
    }

}
