package ru.privatenull.gui.machine;

import org.junit.jupiter.api.Test;
import ru.privatenull.cases.model.AnimationType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationModeCycleTest {

    private static final AnimationType[] MODES = {
            AnimationType.ANVIL,
            AnimationType.DYNAMITE,
            AnimationType.PORTAL
    };

    @Test
    void cyclesForwardThroughPlayerChoiceAndFixedAnimations() {
        assertEquals(AnimationType.ANVIL, AnimationModeCycle.next(null, MODES, false));
        assertEquals(AnimationType.DYNAMITE, AnimationModeCycle.next(AnimationType.ANVIL, MODES, false));
        assertNull(AnimationModeCycle.next(AnimationType.PORTAL, MODES, false));
    }

    @Test
    void cyclesBackwardsInReverseOrder() {
        assertEquals(AnimationType.PORTAL, AnimationModeCycle.next(null, MODES, true));
        assertEquals(AnimationType.ANVIL, AnimationModeCycle.next(AnimationType.DYNAMITE, MODES, true));
        assertNull(AnimationModeCycle.next(AnimationType.ANVIL, MODES, true));
    }

    @Test
    void unknownCurrentModeReturnsToAvailableSequence() {
        assertEquals(AnimationType.ANVIL,
                AnimationModeCycle.next(AnimationType.PILLAGER_RAID, MODES, false));
    }
}
