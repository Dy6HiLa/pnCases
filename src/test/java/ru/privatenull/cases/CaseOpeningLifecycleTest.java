package ru.privatenull.cases;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseOpeningLifecycleTest {

    @Test
    void activeAnimationProtectsItsPendingRewardFromJoinTasks() {
        assertTrue(CaseManager.pendingDeliveryBlocked(true, false));
        assertTrue(CaseManager.pendingDeliveryBlocked(false, true));
        assertFalse(CaseManager.pendingDeliveryBlocked(false, false));
    }

    @Test
    void worldUnloadMatchesOnlySessionsFromThatWorld() {
        UUID unloading = UUID.randomUUID();
        assertTrue(CaseManager.belongsToWorld(unloading, unloading));
        assertFalse(CaseManager.belongsToWorld(UUID.randomUUID(), unloading));
        assertFalse(CaseManager.belongsToWorld(null, unloading));
    }
}
