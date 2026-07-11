package ru.privatenull.gui.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotRoleTest {

    @Test
    void nextAndPreviousAreInverseOperations() {
        for (SlotRole role : SlotRole.values()) {
            assertEquals(role, role.next().previous());
            assertEquals(role, role.previous().next());
        }
    }

    @Test
    void cyclesThroughEveryRole() {
        SlotRole role = SlotRole.EMPTY;
        for (int index = 0; index < SlotRole.values().length; index++) role = role.next();
        assertEquals(SlotRole.EMPTY, role);
    }
}
