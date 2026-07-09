package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;
import ru.privatenull.util.MaterialCompat;
import ru.privatenull.util.ParticleCompat;
import ru.privatenull.util.VisualEntity;

import java.util.ArrayList;
import java.util.List;

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
        Location centerBase = base.clone().add(0.5, 0.0, 0.5);
        VisualEntity display = VisualEntity.item(centerBase.clone().add(0.0, 1.35, 0.0), rewardVisual);
        VisualEntity label = VisualEntity.text(base.clone().add(0.5, 2.85, 0.5), resolveRewardName(reward, rewardVisual));
        VisualEntity prop = createMainProp(type, centerBase);
        List<VisualEntity> orbit = createOrbit(type, def, rewardVisual, centerBase);
        track(display.entity());
        track(label.entity());
        if (prop != null) {
            track(prop.entity());
        }
        for (VisualEntity visual : orbit) {
            track(visual.entity());
        }

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
                Location rewardCenter = centerBase.clone().add(0.0, 1.15 + eased * 0.75 + bob, 0.0);
                updateMainProp(prop, centerBase, tick, progress, eased);
                updateOrbit(orbit, centerBase, tick, progress);
                display.teleport(rewardCenter);
                display.setRotation(yaw, 0.0f);

                drawRing(world, base.clone().add(0.5, 1.20 + eased * 0.55, 0.5), tick, progress);

                if (tick % 18 == 0) {
                    world.playSound(base, soundForType(), 0.18f, 1.15f + (float) progress * 0.45f);
                }

                if (tick >= 96) {
                    ParticleCompat.spawn(world, rewardCenter, new String[]{"FIREWORK", "FIREWORKS_SPARK"}, 30, 0.32, 0.22, 0.32, 0.05);
                    ParticleCompat.spawn(world, rewardCenter, new String[]{"END_ROD"}, 24, 0.25, 0.22, 0.25, 0.04);
                    world.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.45f);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.24f, 1.55f);
                    cleanup();
                }
            }

            private void cleanup() {
                safeRemove(display);
                safeRemove(label);
                safeRemove(prop);
                for (VisualEntity visual : orbit) {
                    safeRemove(visual);
                }
                untrack(display.entity());
                untrack(label.entity());
                if (prop != null) {
                    untrack(prop.entity());
                }
                for (VisualEntity visual : orbit) {
                    untrack(visual.entity());
                }
                if (taskHolder[0] != null) {
                    untrack(taskHolder[0]);
                }
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private VisualEntity createMainProp(AnimationType type, Location centerBase) {
        return switch (type) {
            case ANVIL -> VisualEntity.block(centerBase.clone().add(0.0, 3.6, 0.0), Material.ANVIL);
            case DYNAMITE -> VisualEntity.block(centerBase.clone().add(-1.45, 1.75, -1.45), Material.TNT);
            case PORTAL, CAULDRON -> VisualEntity.item(centerBase.clone().add(0.0, 2.05, 0.0), new ItemStack(Material.ENDER_EYE));
            case POISON -> VisualEntity.item(centerBase.clone().add(0.0, 1.65, 0.0), new ItemStack(Material.SLIME_BALL));
            case FORTUNE_RING, PILLAGER_RAID -> VisualEntity.item(centerBase.clone().add(0.0, 2.15, 0.0), new ItemStack(Material.NETHER_STAR));
        };
    }

    private List<VisualEntity> createOrbit(AnimationType type, CaseDefinition def, ItemStack rewardVisual, Location centerBase) {
        List<VisualEntity> visuals = new ArrayList<>();
        if (type != AnimationType.FORTUNE_RING) {
            return visuals;
        }

        List<ItemStack> source = new ArrayList<>();
        if (def.animationItems() != null) {
            for (ItemStack item : def.animationItems()) {
                if (item != null && !item.getType().isAir()) {
                    source.add(item.clone());
                }
            }
        }
        if (source.isEmpty()) {
            source.add(new ItemStack(Material.EMERALD));
            source.add(new ItemStack(Material.DIAMOND));
            source.add(new ItemStack(Material.GOLD_INGOT));
            source.add(new ItemStack(MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR")));
        }

        for (int i = 0; i < 6; i++) {
            ItemStack item = source.get(i % source.size()).clone();
            item.setAmount(1);
            VisualEntity visual = VisualEntity.item(centerBase.clone().add(0.0, 1.7, 0.0), item);
            visuals.add(visual);
        }
        visuals.add(VisualEntity.item(centerBase.clone().add(0.0, 2.15, 0.0), rewardVisual));
        return visuals;
    }

    private void updateMainProp(VisualEntity prop, Location centerBase, int tick, double progress, double eased) {
        if (prop == null) {
            return;
        }

        switch (type) {
            case ANVIL -> {
                double y = 3.8 - Math.min(2.75, eased * 3.2);
                if (progress > 0.72) {
                    y += Math.sin(tick * 0.45) * 0.035;
                }
                prop.teleport(centerBase.clone().add(0.0, y, 0.0));
                prop.setRotation((tick * 4.0f) % 360.0f, 0.0f);
            }
            case DYNAMITE -> {
                double fly = Math.min(1.0, tick / 54.0);
                double arc = Math.sin(fly * Math.PI) * 0.75;
                double x = -1.45 + 1.45 * fly;
                double z = -1.45 + 1.45 * fly;
                prop.teleport(centerBase.clone().add(x, 1.1 + arc, z));
                prop.setRotation((tick * 13.0f) % 360.0f, (tick * 6.0f) % 360.0f);
            }
            case FORTUNE_RING, PILLAGER_RAID -> {
                prop.teleport(centerBase.clone().add(0.0, 2.12 + Math.sin(tick * 0.16) * 0.08, 0.0));
                prop.setRotation((tick * 9.0f) % 360.0f, 0.0f);
            }
            default -> {
                prop.teleport(centerBase.clone().add(0.0, 1.9 + Math.sin(tick * 0.12) * 0.12, 0.0));
                prop.setRotation((tick * 6.5f) % 360.0f, 0.0f);
            }
        }
    }

    private void updateOrbit(List<VisualEntity> orbit, Location centerBase, int tick, double progress) {
        if (orbit.isEmpty()) {
            return;
        }

        int rewardIndex = orbit.size() - 1;
        double radius = 1.05 - Math.min(0.35, progress * 0.25);
        double speed = 0.20 - Math.min(0.10, progress * 0.08);
        for (int i = 0; i < orbit.size(); i++) {
            VisualEntity visual = orbit.get(i);
            if (i == rewardIndex) {
                visual.teleport(centerBase.clone().add(0.0, 2.05 + Math.sin(tick * 0.13) * 0.08, 0.0));
                visual.setRotation((tick * 7.0f) % 360.0f, 0.0f);
                continue;
            }

            double angle = tick * speed + (Math.PI * 2.0 * i) / rewardIndex;
            double wave = Math.sin(angle * 1.7) * 0.20;
            visual.teleport(centerBase.clone().add(Math.cos(angle) * radius, 1.75 + wave, Math.sin(angle) * radius));
            visual.setRotation((float) Math.toDegrees(angle), 0.0f);
        }
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
            case FORTUNE_RING, PILLAGER_RAID -> new String[]{"END_ROD"};
            case ANVIL -> new String[]{"CRIT"};
        };
    }

    private String[] secondaryParticle() {
        return switch (type) {
            case DYNAMITE -> new String[]{"SMOKE", "SMOKE_NORMAL"};
            case PORTAL, CAULDRON -> new String[]{"REVERSE_PORTAL", "PORTAL"};
            case POISON -> new String[]{"ENCHANT", "ENCHANTMENT_TABLE"};
            case FORTUNE_RING, PILLAGER_RAID -> new String[]{"ELECTRIC_SPARK", "END_ROD"};
            case ANVIL -> new String[]{"CLOUD"};
        };
    }

    private Sound soundForType() {
        return switch (type) {
            case DYNAMITE -> Sound.ENTITY_TNT_PRIMED;
            case PORTAL, CAULDRON -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case POISON -> Sound.ENTITY_SPIDER_AMBIENT;
            case FORTUNE_RING, PILLAGER_RAID -> Sound.BLOCK_NOTE_BLOCK_PLING;
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
