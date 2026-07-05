package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;
import ru.privatenull.util.ParticleCompat;
import ru.privatenull.util.VisualEntity;

public final class LegacyCaseAnimation extends CaseAnimation {

    private final AnimationType type;

    public LegacyCaseAnimation(pnCases plugin, AnimationType type) {
        super(plugin);
        this.type = type;
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        ItemStack rewardVisual = resolveRewardVisual(reward, def);
        VisualEntity display = VisualEntity.item(base.clone().add(0.5, 1.35, 0.5), rewardVisual);
        VisualEntity label = VisualEntity.text(base.clone().add(0.5, 2.85, 0.5), resolveRewardName(reward, rewardVisual));
        track(display.entity());
        track(label.entity());

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            int tick;
            float yaw;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup();
                    return;
                }

                tick++;
                yaw = (yaw + 7.5f) % 360.0f;

                double progress = Math.min(1.0, tick / 92.0);
                double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
                double bob = Math.sin(tick * 0.22) * 0.10;
                Location center = base.clone().add(0.5, 1.15 + eased * 0.75 + bob, 0.5);
                display.teleport(center);
                display.setRotation(yaw, 0.0f);

                drawRing(world, base.clone().add(0.5, 1.20 + eased * 0.55, 0.5), tick, progress);

                if (tick % 18 == 0) {
                    world.playSound(base, soundForType(), 0.18f, 1.15f + (float) progress * 0.45f);
                }

                if (tick >= 96) {
                    ParticleCompat.spawn(world, center, new String[]{"FIREWORK", "FIREWORKS_SPARK"}, 30, 0.32, 0.22, 0.32, 0.05);
                    ParticleCompat.spawn(world, center, new String[]{"END_ROD"}, 24, 0.25, 0.22, 0.25, 0.04);
                    world.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.45f);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.24f, 1.55f);
                    cleanup();
                }
            }

            private void cleanup() {
                safeRemove(display);
                safeRemove(label);
                untrack(display.entity());
                untrack(label.entity());
                if (taskHolder[0] != null) {
                    untrack(taskHolder[0]);
                }
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private void drawRing(World world, Location center, int tick, double progress) {
        double radius = 0.48 + progress * 0.55;
        double rotation = tick * 0.16;
        String[] primary = primaryParticle();
        String[] secondary = secondaryParticle();

        for (int i = 0; i < 8; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / 8.0;
            Location loc = center.clone().add(Math.cos(angle) * radius, Math.sin(angle * 1.3) * 0.14, Math.sin(angle) * radius);
            ParticleCompat.spawn(world, loc, i % 2 == 0 ? primary : secondary, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private String[] primaryParticle() {
        return switch (type) {
            case DYNAMITE -> new String[]{"FLAME"};
            case PORTAL, CAULDRON -> new String[]{"PORTAL"};
            case POISON -> new String[]{"WITCH", "SPELL_WITCH"};
            case FORTUNE_RING -> new String[]{"END_ROD"};
            case ANVIL -> new String[]{"CRIT"};
        };
    }

    private String[] secondaryParticle() {
        return switch (type) {
            case DYNAMITE -> new String[]{"SMOKE", "SMOKE_NORMAL"};
            case PORTAL, CAULDRON -> new String[]{"REVERSE_PORTAL", "PORTAL"};
            case POISON -> new String[]{"ENCHANT", "ENCHANTMENT_TABLE"};
            case FORTUNE_RING -> new String[]{"ELECTRIC_SPARK", "END_ROD"};
            case ANVIL -> new String[]{"CLOUD"};
        };
    }

    private Sound soundForType() {
        return switch (type) {
            case DYNAMITE -> Sound.ENTITY_TNT_PRIMED;
            case PORTAL, CAULDRON -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case POISON -> Sound.ENTITY_SPIDER_AMBIENT;
            case FORTUNE_RING -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case ANVIL -> Sound.BLOCK_ANVIL_USE;
        };
    }

    private static void safeRemove(VisualEntity visual) {
        try {
            visual.remove();
        } catch (Exception ignored) {
        }
    }
}
