package ru.privatenull.cases.animation;

import org.bukkit.World;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.util.ServerCompatibility;

import java.lang.reflect.Constructor;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;

public class AnimationRegistry {

    private final PnCasesPlugin plugin;
    private final Map<AnimationType, CaseAnimation> animations = new EnumMap<>(AnimationType.class);

    public AnimationRegistry(PnCasesPlugin plugin) {
        this.plugin = plugin;
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
        animations.put(AnimationType.AQUARIUM, modern
                ? createModern(plugin, "ru.privatenull.cases.animation.AquariumAnimation", AnimationType.AQUARIUM)
                : new LegacyCaseAnimation(plugin, AnimationType.AQUARIUM));
        animations.put(AnimationType.MOB_HUNT, new MobHuntAnimation(plugin));
    }

    public CaseAnimation get(AnimationType type) {
        return animations.getOrDefault(type, animations.get(AnimationType.ANVIL));
    }

    public void shutdownAll() {
        for (CaseAnimation animation : new HashSet<>(animations.values())) {
            try {
                animation.shutdown();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not shut down case animation", exception);
            }
        }
    }

    public void cancel(CaseAnimation animation) {
        if (animation != null && animations.containsValue(animation)) {
            animation.cancelAll();
        }
    }

    public void onWorldUnload(World world) {
        if (world == null) return;
        for (CaseAnimation animation : new HashSet<>(animations.values())) {
            try {
                animation.onWorldUnload(world);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not restore case animation state before world unload: " + world.getName(),
                        exception);
            }
        }
    }

    public void cancelWorld(World world) {
        if (world == null) return;
        for (CaseAnimation animation : new HashSet<>(animations.values())) {
            try {
                animation.cancelWorld(world);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not cancel case animation state for world unload: " + world.getName(),
                        exception);
            }
        }
    }

    public void cancelWorld(CaseAnimation animation, World world) {
        if (animation == null || world == null || !animations.containsValue(animation)) return;
        animation.cancelWorld(world);
    }

    public void cancelRun(CaseAnimation animation, Runnable owner) {
        if (animation == null || owner == null || !animations.containsValue(animation)) return;
        animation.cancelRun(owner);
    }

    private static CaseAnimation createModern(PnCasesPlugin plugin, String className, AnimationType fallbackType) {
        try {
            Class<?> rawClass = Class.forName(className);
            Constructor<?> constructor = rawClass.getConstructor(PnCasesPlugin.class);
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
