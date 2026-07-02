package ru.privatenull.cases.animation;

import ru.privatenull.pnCases;

public class AnimationRegistry {

    private final AnvilAnimation anvil;
    private final DynamiteAnimation dynamite;
    private final PortalAnimation portal;
    private final PoisonAnimation poison;
    private final CauldronRouletteAnimation cauldron;
    private final FortuneRingAnimation fortuneRing;

    public AnimationRegistry(pnCases plugin) {
        this.anvil = new AnvilAnimation(plugin);
        this.dynamite = new DynamiteAnimation(plugin);
        this.portal = new PortalAnimation(plugin);
        this.poison = new PoisonAnimation(plugin);
        this.cauldron = new CauldronRouletteAnimation(plugin);
        this.fortuneRing = new FortuneRingAnimation(plugin);
    }

    public CaseAnimation get(AnimationType type) {
        return switch (type) {
            case ANVIL -> anvil;
            case DYNAMITE -> dynamite;
            case PORTAL -> portal;
            case POISON -> poison;
            case CAULDRON -> cauldron;
            case FORTUNE_RING -> fortuneRing;
        };
    }

    public void shutdownAll() {
        anvil.cancelAll();
        dynamite.cancelAll();
        portal.cancelAll();
        poison.cancelAll();
        cauldron.cancelAll();
        fortuneRing.cancelAll();
    }
}
