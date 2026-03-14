package ru.privatenull.cases.animation;

import ru.privatenull.pnCases;

public class AnimationRegistry {

    private final AnvilAnimation anvil;
    private final DynamiteAnimation dynamite;
    private final PortalAnimation portal;

    public AnimationRegistry(pnCases plugin) {
        this.anvil    = new AnvilAnimation(plugin);
        this.dynamite = new DynamiteAnimation(plugin);
        this.portal   = new PortalAnimation(plugin);
    }

    public CaseAnimation get(AnimationType type) {
        return switch (type) {
            case ANVIL -> anvil;
            case DYNAMITE -> dynamite;
            case PORTAL -> portal;
        };
    }

    public void shutdownAll() {
        anvil.cancelAll();
        dynamite.cancelAll();
        portal.cancelAll();
    }
}