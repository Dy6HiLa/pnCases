package ru.privatenull.cases;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaseManagerBlockOwnershipTest {

    @Test
    void targetedRefreshOnlyRemovesMappingsOwnedByEditedCase() {
        CaseManager.BlockKey shared = new CaseManager.BlockKey("World", 1, 64, 1);
        Map<CaseManager.BlockKey, String> current = Map.of(shared, "other");

        Map<CaseManager.BlockKey, String> result = CaseManager.replaceOwnedBlocks(
                current, "edited", List.of(shared), List.of());

        assertEquals("other", result.get(shared));
    }

    @Test
    void targetedRefreshRejectsBlockOwnedByAnotherCase() {
        CaseManager.BlockKey occupied = new CaseManager.BlockKey("world", 2, 64, 2);
        Map<CaseManager.BlockKey, String> current = Map.of(occupied, "other");

        assertNull(CaseManager.replaceOwnedBlocks(
                current, "edited", List.of(), List.of(occupied)));
    }

    @Test
    void targetedRefreshMovesOnlyEditedCaseMappings() {
        CaseManager.BlockKey oldBlock = new CaseManager.BlockKey("world", 3, 64, 3);
        CaseManager.BlockKey newBlock = new CaseManager.BlockKey("WORLD", 4, 64, 4);
        Map<CaseManager.BlockKey, String> current = Map.of(oldBlock, "edited");

        Map<CaseManager.BlockKey, String> result = CaseManager.replaceOwnedBlocks(
                current, "edited", List.of(oldBlock), List.of(newBlock));

        assertNull(result.get(oldBlock));
        assertEquals("edited", result.get(newBlock));
    }
}
