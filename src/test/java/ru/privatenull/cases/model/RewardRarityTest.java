package ru.privatenull.cases.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardRarityTest {

    @Test
    void preservesCustomRarityIdentifier() {
        assertEquals("donate", Reward.normalizeRarityId("DONATE", 50));
        assertEquals("premium_plus", Reward.normalizeRarityId(" Premium Plus ", 50));
    }

    @Test
    void keepsLegacyAliasesCompatible() {
        assertEquals("legendary", Reward.normalizeRarityId("ЛЕГЕНДАРНАЯ", 50));
        assertEquals("mythic", Reward.normalizeRarityId("MYTHICAL", 50));
    }

    @Test
    void derivesRarityWhenConfigurationOmitsIt() {
        assertEquals("mythic", Reward.normalizeRarityId(null, 2));
        assertEquals("common", Reward.normalizeRarityId("", 100));
    }
}
