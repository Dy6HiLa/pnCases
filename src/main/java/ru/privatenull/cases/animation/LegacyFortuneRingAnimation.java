package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;
import ru.privatenull.util.MaterialCompat;
import ru.privatenull.util.ParticleCompat;
import ru.privatenull.util.SoundCompat;
import ru.privatenull.util.VisualEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyFortuneRingAnimation extends CaseAnimation {

    private static final int ITEM_COUNT = 8;
    private static final int FORM_END = 26;
    private static final int SPIN_END = 112;
    private static final int SLOW_END = 168;
    private static final int REVEAL_END = 214;

    private final Set<HiddenCaseBlock> hiddenBlocks = ConcurrentHashMap.newKeySet();

    public LegacyFortuneRingAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void cancelAll() {
        super.cancelAll();
        restoreHiddenBlocks();
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        ItemStack rewardVisual = resolveRewardVisual(reward, def);
        List<ItemStack> visuals = buildVisuals(def, rewardVisual);
        Vector rightAxis = rightAxis(player, base);
        HiddenCaseBlock hiddenBlock = hideCaseBlock(base);

        List<VisualEntity> orbitItems = new ArrayList<>();
        Location center = base.clone().add(0.0, 1.50, 0.0);
        for (int i = 0; i < ITEM_COUNT; i++) {
            VisualEntity visual = VisualEntity.item(center, visuals.get(i % visuals.size()));
            orbitItems.add(visual);
            track(visual.entity());
        }

        VisualEntity rewardDisplay = VisualEntity.item(base.clone().add(0.0, -4.0, 0.0), rewardVisual);
        rewardDisplay.setScale(0.55f);
        track(rewardDisplay.entity());

        VisualEntity label = VisualEntity.text(base.clone().add(0.0, 3.15, 0.0), "");
        track(label.entity());

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            int tick;
            double rotation;
            boolean finished;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup();
                    return;
                }

                tick++;

                if (tick <= FORM_END) {
                    playForm();
                    return;
                }

                if (tick <= SPIN_END) {
                    playSpin();
                    return;
                }

                if (tick <= SLOW_END) {
                    playSlow();
                    return;
                }

                if (tick <= REVEAL_END) {
                    playReveal();
                    return;
                }

                Location rewardLoc = base.clone().add(0.0, 1.86, 0.0);
                spawnFinalBurst(world, rewardLoc);
                SoundCompat.play(world, base, new String[]{"UI_TOAST_CHALLENGE_COMPLETE", "ENTITY_PLAYER_LEVELUP"}, 0.24f, 1.25f);
                SoundCompat.play(player, new String[]{"ENTITY_PLAYER_LEVELUP"}, 0.20f, 1.55f);
                cleanup();
            }

            private void playForm() {
                double progress = clamp(tick / (double) FORM_END);
                double eased = easeOut(progress);
                rotation += 0.07 + progress * 0.08;

                updateOrbit(eased, 0.30 + eased * 1.05, 1.48, rotation, 0.52f);
                rewardDisplay.teleport(base.clone().add(0.0, -4.0, 0.0));
                rewardDisplay.setScale(0.0f);
                if (tick % 2 == 0) {
                    drawVerticalRing(world, base, rightAxis, 0.45 + eased * 1.0, 1.48, rotation, new String[]{"END_ROD"});
                }
                if (tick % 4 == 0) {
                    drawHorizontalTrace(world, base, 0.70 + eased * 0.45, 0.82, rotation, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"});
                }

                if (tick == 1) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_PLING"}, 0.18f, 1.35f);
                    SoundCompat.play(world, base, new String[]{"BLOCK_BEACON_ACTIVATE", "BLOCK_END_PORTAL_FRAME_FILL"}, 0.12f, 1.65f);
                }
                if (tick % 8 == 0) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_NOTE_BLOCK_BELL", "BLOCK_NOTE_BLOCK_PLING"}, 0.09f, 1.20f + (float) progress * 0.35f);
                }
            }

            private void playSpin() {
                double progress = clamp((tick - FORM_END) / (double) (SPIN_END - FORM_END));
                rotation += 0.24 + progress * 0.08;

                updateOrbit(1.0, 1.35, 1.52 + Math.sin(tick * 0.10) * 0.03, rotation, 0.62f);
                rewardDisplay.teleport(base.clone().add(0.0, -4.0, 0.0));
                rewardDisplay.setRotation((float) ((tick * 7.0) % 360.0), 0.0f);
                rewardDisplay.setScale(0.0f);

                if (tick % 3 == 0) {
                    drawVerticalRing(world, base, rightAxis, 1.35, 1.52, rotation, new String[]{"END_ROD"});
                }
                if (tick % 6 == 0) {
                    drawHorizontalTrace(world, base, 1.0, 0.86, -rotation * 0.7, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"});
                }

                if (tick % 6 == 0) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_NOTE_BLOCK_PLING"}, 0.07f, 1.45f + (float) Math.sin(tick * 0.08) * 0.18f);
                }
                if (tick % 8 == 0) {
                    ParticleCompat.spawn(world, base.clone().add(0.0, 1.45, 0.0),
                            new String[]{"ENCHANT", "ENCHANTMENT_TABLE"}, 2, 0.34, 0.10, 0.34, 0.12);
                }
            }

            private void playSlow() {
                double progress = clamp((tick - SPIN_END) / (double) (SLOW_END - SPIN_END));
                double speed = 0.25 * Math.pow(1.0 - progress, 2.0) + 0.04;
                double radius = 1.35 - progress * 0.38;
                rotation += speed;

                updateOrbit(1.0, radius, 1.55 + progress * 0.10, rotation, 0.62f);
                rewardDisplay.teleport(base.clone().add(0.0, -4.0, 0.0));
                rewardDisplay.setRotation((float) ((tick * 5.0) % 360.0), 0.0f);
                rewardDisplay.setScale(0.0f);

                if (tick % 3 == 0) {
                    drawVerticalRing(world, base, rightAxis, radius, 1.58, rotation, new String[]{"END_ROD"});
                }
                if (tick % 4 == 0) {
                    ParticleCompat.spawn(world, base.clone().add(0.0, 1.48, 0.0), new String[]{"PORTAL"}, 3, 0.35, 0.14, 0.35, 0.08);
                }

                if (tick % 10 == 0) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_PLING"}, 0.10f, 1.60f - (float) progress * 0.25f);
                }
            }

            private void playReveal() {
                double progress = clamp((tick - SLOW_END) / (double) (REVEAL_END - SLOW_END));
                double eased = easeOutBack(progress);
                rotation += 0.035;

                for (int i = 0; i < orbitItems.size(); i++) {
                    VisualEntity visual = orbitItems.get(i);
                    double angle = angle(i, orbitItems.size(), rotation);
                    double radius = 0.95 * (1.0 - progress);
                    double centerY = 1.58 + progress * 0.12 + Math.sin(tick * 0.12 + i) * 0.02;
                    visual.teleport(verticalPoint(base, rightAxis, radius, centerY, angle));
                    visual.setScale((float) Math.max(0.0, 0.62 * (1.0 - progress)));
                }

                Location rewardLoc = base.clone().add(0.0, 1.54 + eased * 0.34, 0.0);
                rewardDisplay.teleport(rewardLoc);
                rewardDisplay.setRotation((float) ((tick * 5.5) % 360.0), 0.0f);
                rewardDisplay.setScale((float) Math.min(0.72, 0.18 + eased * 0.54));

                if (tick == SLOW_END + 4) {
                    label.setText(resolveRewardName(reward, rewardVisual));
                    SoundCompat.play(world, base, new String[]{"BLOCK_END_PORTAL_FRAME_FILL", "ENTITY_PLAYER_LEVELUP"}, 0.16f, 1.50f);
                }

                if (tick % 2 == 0) {
                    drawVerticalRing(world, base, rightAxis, 0.35 + progress * 0.20, 1.66, -rotation, new String[]{"FIREWORK", "FIREWORKS_SPARK"});
                }
                ParticleCompat.spawn(world, rewardLoc, new String[]{"END_ROD"}, 3, 0.16, 0.10, 0.16, 0.015);
            }

            private void updateOrbit(double alpha, double radius, double centerY, double rotation, float scale) {
                for (int i = 0; i < orbitItems.size(); i++) {
                    VisualEntity visual = orbitItems.get(i);
                    double angle = angle(i, orbitItems.size(), rotation);
                    double bob = Math.sin(tick * 0.14 + i * 0.65) * 0.035;
                    visual.teleport(verticalPoint(base, rightAxis, radius, centerY + bob, angle));
                    visual.setRotation((float) Math.toDegrees(angle) + 90.0f, 0.0f);
                    visual.setScale(Math.max(0.0f, scale * (float) alpha));
                }
            }

            private void cleanup() {
                if (finished) {
                    return;
                }
                finished = true;

                for (VisualEntity visual : orbitItems) {
                    safeRemove(visual);
                    untrack(visual.entity());
                }
                safeRemove(rewardDisplay);
                safeRemove(label);
                untrack(rewardDisplay.entity());
                untrack(label.entity());
                restoreHiddenBlock(hiddenBlock);
                if (taskHolder[0] != null) {
                    untrack(taskHolder[0]);
                }
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private HiddenCaseBlock hideCaseBlock(Location base) {
        Block block = base.getBlock();
        if (block.getType().isAir()) {
            return null;
        }

        HiddenCaseBlock hiddenBlock = new HiddenCaseBlock(block, block.getState());
        hiddenBlocks.add(hiddenBlock);
        block.setType(Material.AIR, false);
        return hiddenBlock;
    }

    private void restoreHiddenBlock(HiddenCaseBlock hiddenBlock) {
        if (hiddenBlock == null) {
            return;
        }
        hiddenBlocks.remove(hiddenBlock);
        hiddenBlock.restore();
    }

    private void restoreHiddenBlocks() {
        for (HiddenCaseBlock hiddenBlock : hiddenBlocks) {
            hiddenBlock.restore();
        }
        hiddenBlocks.clear();
    }

    private List<ItemStack> buildVisuals(CaseDefinition def, ItemStack rewardVisual) {
        List<ItemStack> visuals = new ArrayList<>();
        if (def != null && def.animationItems() != null) {
            for (ItemStack item : def.animationItems()) {
                if (item != null && !item.getType().isAir()) {
                    ItemStack clone = item.clone();
                    clone.setAmount(1);
                    visuals.add(clone);
                }
            }
        }
        if (rewardVisual != null && !rewardVisual.getType().isAir()) {
            ItemStack clone = rewardVisual.clone();
            clone.setAmount(1);
            visuals.add(clone);
        }
        if (visuals.isEmpty()) {
            visuals.add(new ItemStack(Material.EMERALD));
            visuals.add(new ItemStack(Material.DIAMOND));
            visuals.add(new ItemStack(Material.GOLD_INGOT));
            visuals.add(new ItemStack(MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR")));
        }
        return visuals;
    }

    private static void drawVerticalRing(World world, Location base, Vector rightAxis, double radius, double centerY, double rotation, String[] particles) {
        for (int i = 0; i < 10; i++) {
            double angle = angle(i, 10, rotation);
            ParticleCompat.spawn(world, verticalPoint(base, rightAxis, radius, centerY, angle),
                    particles, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawHorizontalTrace(World world, Location base, double radius, double y, double rotation, String[] particles) {
        for (int i = 0; i < 5; i++) {
            double angle = angle(i, 5, rotation);
            Location loc = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            ParticleCompat.spawn(world, loc, particles, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static void spawnFinalBurst(World world, Location loc) {
        ParticleCompat.spawn(world, loc, new String[]{"FIREWORK", "FIREWORKS_SPARK"}, 12, 0.32, 0.18, 0.32, 0.025);
        ParticleCompat.spawn(world, loc, new String[]{"END_ROD"}, 16, 0.28, 0.22, 0.28, 0.022);
        ParticleCompat.spawn(world, loc, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"}, 18, 0.36, 0.24, 0.36, 0.16);
        for (int i = 0; i < 10; i++) {
            double angle = angle(i, 10, 0.0);
            Location spark = loc.clone().add(Math.cos(angle) * 0.82, 0.03, Math.sin(angle) * 0.82);
            ParticleCompat.spawn(world, spark, new String[]{"END_ROD"}, 1, 0.0, 0.0, 0.0, 0.015);
        }
    }

    private static Location verticalPoint(Location base, Vector rightAxis, double radius, double centerY, double angle) {
        return base.clone()
                .add(rightAxis.clone().multiply(Math.cos(angle) * radius))
                .add(0.0, centerY + Math.sin(angle) * radius, 0.0);
    }

    private static Vector rightAxis(Player player, Location base) {
        Vector fromCaseToPlayer = player.getLocation().toVector().subtract(base.toVector());
        fromCaseToPlayer.setY(0.0);
        if (fromCaseToPlayer.lengthSquared() < 0.0001) {
            fromCaseToPlayer = player.getLocation().getDirection().multiply(-1.0);
            fromCaseToPlayer.setY(0.0);
        }
        if (fromCaseToPlayer.lengthSquared() < 0.0001) {
            return new Vector(1.0, 0.0, 0.0);
        }
        fromCaseToPlayer.normalize();
        return new Vector(-fromCaseToPlayer.getZ(), 0.0, fromCaseToPlayer.getX()).normalize();
    }

    private static double angle(int index, int total, double rotation) {
        return (Math.PI * 2.0 * index / Math.max(1, total)) + rotation;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double easeOut(double value) {
        double x = clamp(value);
        return 1.0 - Math.pow(1.0 - x, 3.0);
    }

    private static double easeOutBack(double value) {
        double x = clamp(value);
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
    }

    private static void safeRemove(VisualEntity visual) {
        try {
            if (visual != null) {
                visual.remove();
            }
        } catch (Exception ignored) {
        }
    }

    private record HiddenCaseBlock(Block block, BlockState state) {
        private void restore() {
            if (block == null || state == null || !block.getType().isAir()) {
                return;
            }
            state.update(true, false);
        }
    }
}
