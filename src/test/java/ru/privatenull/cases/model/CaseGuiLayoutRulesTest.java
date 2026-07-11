package ru.privatenull.cases.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
