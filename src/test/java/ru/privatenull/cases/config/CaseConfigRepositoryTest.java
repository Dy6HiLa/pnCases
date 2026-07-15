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
    void templateSwitchOnlyChangesCanonicalTypeAndRewards() {
        YamlConfiguration target = new YamlConfiguration();
        target.set("id", "custom_case");
        target.set("template", "items");
        target.set("display-name", "Custom RGB title");
        target.set("blocks", List.of(Map.of("world", "world", "x", 1, "y", 64, "z", 2)));
        target.set("cost.type", "KEY");
        target.set("cost.key", "custom_key");
        target.set("gui.title", "Custom GUI");
        target.set("hologram.lines", List.of("Custom hologram"));
        target.set("idle-particles.style", "CUSTOM_STYLE");
        target.set("animation.fixed", "AQUARIUM");
        target.set("animation.duration_ticks", 123);
        target.set("rewards", List.of(Map.of("type", "ITEM", "chance", 100)));

        List<?> moneyRewards = List.of(Map.of(
                "type", "VAULT",
                "chance", 100,
                "vault", Map.of("amount", 500)
        ));
        CaseConfigRepository.applyTemplateValues(target, "custom_case", "money", moneyRewards);

        assertEquals("custom_case", target.getString("id"));
        assertEquals("money", target.getString("template"));
        assertEquals("Custom RGB title", target.getString("display-name"));
        assertEquals("custom_key", target.getString("cost.key"));
        assertEquals("Custom GUI", target.getString("gui.title"));
        assertEquals(List.of("Custom hologram"), target.getStringList("hologram.lines"));
        assertEquals("CUSTOM_STYLE", target.getString("idle-particles.style"));
        assertEquals("AQUARIUM", target.getString("animation.fixed"));
        assertEquals(123, target.getInt("animation.duration_ticks"));
        assertEquals("world", target.getMapList("blocks").get(0).get("world"));
        assertEquals("VAULT", target.getMapList("rewards").get(0).get("type"));
        assertEquals(500, nested(target, "rewards", "vault").get("amount"));
    }

    @Test
    void fileNameIsTheCanonicalCaseId() {
        assertEquals("money", CaseConfigRepository.fileId("Money.yml"));
        assertEquals("custom_case", CaseConfigRepository.fileId("custom_case.YML"));
        assertTrue(CaseConfigRepository.isValidId(CaseConfigRepository.fileId("safe-case.yml")));
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

    @Test
    void missingOrInvalidMoneyTemplateUsesVaultRewards() {
        List<?> missingFallback = CaseConfigRepository.moneyRewardsOrFallback(null);
        Map<?, ?> missingReward = (Map<?, ?>) missingFallback.get(0);
        assertEquals("VAULT", missingReward.get("type"));
        assertEquals(100, ((Map<?, ?>) missingReward.get("vault")).get("amount"));

        List<?> itemFallback = CaseConfigRepository.moneyRewardsOrFallback(List.of(
                Map.of("type", "ITEM", "chance", 100, "item", Map.of("material", "DIAMOND"))
        ));
        assertEquals("VAULT", ((Map<?, ?>) itemFallback.get(0)).get("type"));

        List<?> configuredVault = List.of(Map.of(
                "type", "VAULT", "chance", 100, "vault", Map.of("amount", 750)
        ));
        Map<?, ?> copiedReward = (Map<?, ?>) CaseConfigRepository.moneyRewardsOrFallback(configuredVault).get(0);
        assertEquals(750, ((Map<?, ?>) copiedReward.get("vault")).get("amount"));
    }

    private Map<?, ?> nested(ConfigurationSection section, String listPath, String nestedKey) {
        Object nested = section.getMapList(listPath).get(0).get(nestedKey);
        return (Map<?, ?>) nested;
    }
}
