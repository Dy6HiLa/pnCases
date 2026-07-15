package ru.privatenull.cases.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseGuiLayoutRulesTest {

    @Test
    void normalizesRowsAndSlots() {
        assertEquals(9, CaseGuiLayoutRules.normalizeSize(1));
        assertEquals(54, CaseGuiLayoutRules.normalizeSize(6));
        assertEquals(27, CaseGuiLayoutRules.normalizeSize(27));
        assertEquals(36, CaseGuiLayoutRules.normalizeSize(28));
        assertEquals(9, CaseGuiLayoutRules.normalizeSize(-5));
        assertEquals(54, CaseGuiLayoutRules.normalizeSize(100));
    }

    @Test
    void filtersInvalidAndDuplicateSlotsWithoutChangingOrder() {
        assertEquals(List.of(5, 2, 53),
                CaseGuiLayoutRules.filterSlots(List.of(5, -1, 2, 5, 54, 53), 54));
    }

    @Test
    void reservesUniqueSlotsWhenConfigurationContainsCollisions() {
        Set<Integer> reserved = new LinkedHashSet<>();
        assertEquals(4, CaseGuiLayoutRules.reserveSlot(4, 4, 54, reserved));
        assertEquals(53, CaseGuiLayoutRules.reserveSlot(4, 4, 54, reserved));
        assertEquals(Set.of(4, 53), reserved);
    }

    @Test
    void resolvesEveryRoleWithoutCollisionsForEveryInventorySize() {
        for (int size = 9; size <= 54; size += 9) {
            int inventorySize = size;
            CaseGuiLayoutRules.ResolvedSlots slots = CaseGuiLayoutRules.resolveSlots(
                    inventorySize,
                    4, CaseGuiLayout.DEFAULT_OPEN_SLOT,
                    4, CaseGuiLayout.DEFAULT_ANIMATION_SLOT,
                    4, CaseGuiLayout.DEFAULT_PREVIEW_SLOT,
                    List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 53),
                    List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 53)
            );

            List<Integer> all = new java.util.ArrayList<>();
            all.add(slots.openSlot());
            all.add(slots.animationSlot());
            all.add(slots.previewSlot());
            all.addAll(slots.historySlots());
            all.addAll(slots.decorSlots());

            assertEquals(all.size(), new LinkedHashSet<>(all).size(), "size=" + inventorySize);
            assertTrue(all.stream().allMatch(slot -> slot >= 0 && slot < inventorySize), "size=" + inventorySize);
            assertEquals(4, slots.openSlot(), "open button must keep priority at size=" + inventorySize);
        }
    }

    @Test
    void keepsDefaultPrimarySlotsAndRemovesThemFromLists() {
        CaseGuiLayoutRules.ResolvedSlots slots = CaseGuiLayoutRules.resolveSlots(
                54,
                22, 22,
                49, 49,
                50, 50,
                List.of(22, 45, 49, 50, 51),
                List.of(0, 22, 45, 49, 50, 51)
        );

        assertEquals(22, slots.openSlot());
        assertEquals(49, slots.animationSlot());
        assertEquals(50, slots.previewSlot());
        assertEquals(List.of(45, 51), slots.historySlots());
        assertEquals(List.of(0), slots.decorSlots());
    }
}
