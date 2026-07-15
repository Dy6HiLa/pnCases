package ru.privatenull.hologram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramServiceTest {

    @Test
    void locationCleanupDoesNotMatchAnotherCaseBlock() {
        assertTrue(HologramService.sameHologramPosition(10.5, 66.8, -2.5, 10.5, 66.8, -2.5));
        assertTrue(HologramService.sameHologramPosition(10.74, 66.8, -2.5, 10.5, 66.8, -2.5));
        assertFalse(HologramService.sameHologramPosition(11.5, 66.8, -2.5, 10.5, 66.8, -2.5));
        assertFalse(HologramService.sameHologramPosition(10.5, 67.8, -2.5, 10.5, 66.8, -2.5));
    }
}
