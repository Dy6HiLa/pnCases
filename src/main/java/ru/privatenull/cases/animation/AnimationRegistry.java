package ru.privatenull.cases.animation;

import ru.privatenull.pnCases;
import ru.privatenull.util.ServerCompatibility;

import java.lang.reflect.Constructor;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

public class AnimationRegistry {

    private final Map<AnimationType, CaseAnimation> animations = new EnumMap<>(AnimationType.class);

    public AnimationRegistry(pnCases plugin) {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            CaseAnimation fortuneRing = new LegacyFortuneRingAnimation(plugin);
            for (AnimationType type : AnimationType.values()) {
                animations.put(type, fortuneRing);
            }
            return;
        }

        boolean modern = ServerCompatibility.useModernAnimations();
        animations.put(AnimationType.ANVIL, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.AnvilAnimation", AnimationType.ANVIL)
                : new LegacyCaseAnimation(plugin, AnimationType.ANVIL));
        animations.put(AnimationType.DYNAMITE, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.DynamiteAnimation", AnimationType.DYNAMITE)
                : new LegacyCaseAnimation(plugin, AnimationType.DYNAMITE));
        animations.put(AnimationType.PORTAL, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.PortalAnimation", AnimationType.PORTAL)
                : new LegacyCaseAnimation(plugin, AnimationType.PORTAL));
        animations.put(AnimationType.POISON, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.PoisonAnimation", AnimationType.POISON)
                : new LegacyCaseAnimation(plugin, AnimationType.POISON));
        animations.put(AnimationType.CAULDRON, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.CauldronRouletteAnimation", AnimationType.CAULDRON)
                : new LegacyCaseAnimation(plugin, AnimationType.CAULDRON));
        animations.put(AnimationType.FORTUNE_RING, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.FortuneRingAnimation", AnimationType.FORTUNE_RING)
                : new LegacyCaseAnimation(plugin, AnimationType.FORTUNE_RING));
        animations.put(AnimationType.PILLAGER_RAID, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.PillagerRaidAnimation", AnimationType.PILLAGER_RAID)
                : new LegacyCaseAnimation(plugin, AnimationType.PILLAGER_RAID));
    }

    public CaseAnimation get(AnimationType type) {
        return animations.getOrDefault(type, animations.get(AnimationType.ANVIL));
    }

    public void shutdownAll() {
        for (CaseAnimation animation : new HashSet<>(animations.values())) {
            animation.cancelAll();
        }
    }

    private static CaseAnimation createModern(pnCases plugin, String className, AnimationType fallbackType) {
        try {
            Class<?> rawClass = Class.forName(className);
            Constructor<?> constructor = rawClass.getConstructor(pnCases.class);
            Object instance = constructor.newInstance(plugin);
            if (instance instanceof CaseAnimation animation) {
                return animation;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Анимация " + fallbackType.name()
                    + " недоступна в полном режиме, включаю совместимый вариант: " + t.getMessage());
        }
        return new LegacyCaseAnimation(plugin, fallbackType);
    }
}
