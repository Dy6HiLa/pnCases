package ru.privatenull.cases.config;

import org.junit.jupiter.api.Test;
import ru.privatenull.cases.model.Reward;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RewardConfigValuesTest {

    @Test
    void supportsCurrentAndLegacyTypeNames() {
        assertEquals(Reward.Type.VAULT, RewardConfigValues.parseType("money"));
        assertEquals(Reward.Type.PLAYERPOINTS, RewardConfigValues.parseType("player_points"));
        assertEquals(Reward.Type.LUCKPERMS, RewardConfigValues.parseType("luckperms"));
        assertNull(RewardConfigValues.parseType("unknown"));
    }

    @Test
    void infersVirtualCurrencyFromNestedData() {
        assertEquals(Reward.Type.VAULT,
                RewardConfigValues.inferType("ITEM", Map.of("vault", Map.of("amount", 500))));
        assertEquals(Reward.Type.PLAYERPOINTS,
                RewardConfigValues.inferType("ITEM", Map.of("playerpoints", Map.of("points", 25))));
    }

    @Test
    void resolvesNestedValueBeforeLegacyRootValue() {
        Map<String, Object> config = Map.of("vault", Map.of("amount", 500), "amount", 100);
        Map<?, ?> vault = RewardConfigValues.nestedMap(config, "vault");
        assertEquals(500, RewardConfigValues.firstPresent(vault, config, "amount"));
    }
}
