package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.util.EntityCleanup;
import ru.privatenull.util.ParticleCompat;
import ru.privatenull.util.SkullUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A temporary glass capsule with a water core and a smooth reward reveal. */
public final class AquariumAnimation extends CaseAnimation {

    private static final String HEART_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTExM2VlNjEwODQxZGVkMjE1YWNkMmI0Y2FhZWVmODdkZmQ2ZTNkNDc2OGU3YWI0ZTE5ZWI3NmIzZDgxMjFjZiJ9fX0=";
    private final Set<HiddenBlock> hiddenBlocks = ConcurrentHashMap.newKeySet();

    public AquariumAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        Block caseBlock = base.getBlock();
        HiddenBlock hidden = hideAsGlass(caseBlock, onFinish);
        Location center = caseBlock.getLocation().add(0.5, 0.0, 0.5);

        ItemDisplay heart = item(world, center.clone().add(0.0, 1.02, 0.0),
                SkullUtil.fromBase64(HEART_TEXTURE, "&#4FB6FFСердце моря"), 1.18f);
        ItemStack rewardVisual = resolveRewardVisual(reward, def);
        ItemDisplay rewardDisplay = item(world, center.clone().add(0.0, 0.60, 0.0), rewardVisual, 0.01f);
        ItemDisplay[] crystals = new ItemDisplay[4];
        for (int index = 0; index < crystals.length; index++) {
            crystals[index] = item(world, center.clone().add(0.0, 1.05, 0.0),
                    new ItemStack(Material.PRISMARINE_CRYSTALS), 0.24f);
            track(crystals[index], onFinish);
        }
        track(heart, onFinish);
        track(rewardDisplay, onFinish);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            private int tick;
            private boolean finished;

            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }
                if (!player.isOnline()) {
                    finish();
                    return;
                }

                tick++;
                if (tick == 1) {
                    world.playSound(center, Sound.BLOCK_GLASS_PLACE, 0.45f, 1.45f);
                    world.playSound(center, Sound.ENTITY_PLAYER_SPLASH, 0.28f, 1.20f);
                }

                if (tick <= 72) {
                    double bob = Math.sin(tick * 0.16) * 0.10;
                    heart.teleport(center.clone().add(0.0, 1.02 + bob, 0.0));
                    heart.setRotation((tick * 4.5f) % 360.0f, 0.0f);
                    updateCrystals(crystals, center, tick, 0.52, 1.02, 0.24f);
                    waterParticles(world, center, tick, 0.48, 5);
                    if (tick % 18 == 0) world.playSound(center, Sound.ENTITY_PLAYER_SPLASH, 0.12f, 1.65f);
                } else if (tick <= 150) {
                    double progress = easeOut((tick - 72) / 78.0);
                    float heartScale = (float) (1.18 * (1.0 - progress));
                    scale(heart, Math.max(0.01f, heartScale));
                    heart.teleport(center.clone().add(0.0, 1.02 + progress * 0.28, 0.0));
                    updateCrystals(crystals, center, tick, 0.52 + progress * 1.10, 1.02 + progress * 0.65,
                            (float) (0.24 * (1.0 - progress)));

                    double rise = 0.60 + progress * 1.65;
                    rewardDisplay.teleport(center.clone().add(0.0, rise, 0.0));
                    rewardDisplay.setRotation((tick * 6.0f) % 360.0f, 0.0f);
                    scale(rewardDisplay, (float) (0.18 + progress * 0.82));
                    waterParticles(world, center.clone().add(0.0, progress * 0.65, 0.0), tick, 0.56, 5);
                    if (tick == 86 && hidden != null && hiddenBlocks.contains(hidden)
                            && caseBlock.getType() == Material.GLASS) {
                        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.35f, 1.70f);
                        caseBlock.setType(Material.AIR, false);
                        world.spawnParticle(Particle.BLOCK, center.clone().add(0.0, 0.55, 0.0), 18,
                                0.32, 0.32, 0.32, 0.08, Material.GLASS.createBlockData());
                    }
                    if (tick == 128) {
                        world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.22f, 1.55f);
                    }
                } else {
                    ParticleCompat.spawn(world, rewardDisplay.getLocation(),
                            new String[]{"BUBBLE", "WATER_BUBBLE", "BUBBLE_POP"},
                            10, 0.28, 0.28, 0.28, 0.05);
                    finish();
                }
            }

            private void finish() {
                if (finished) return;
                finished = true;
                EntityCleanup.remove(heart);
                EntityCleanup.remove(rewardDisplay);
                for (ItemDisplay crystal : crystals) {
                    EntityCleanup.remove(crystal);
                    untrack(crystal);
                }
                untrack(heart);
                untrack(rewardDisplay);
                restore(hidden);
                if (taskHolder[0] != null) untrack(taskHolder[0]);
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0], world, onFinish);
    }

    @Override
    public void cancelAll() {
        super.cancelAll();
        restoreHiddenBlocks();
    }

    @Override
    public void onWorldUnload(World world) {
        restoreHiddenBlocks(world);
    }

    @Override
    protected void onCancelRun(Runnable owner) {
        for (HiddenBlock hidden : hiddenBlocks) {
            if (hidden.owner == owner && hiddenBlocks.remove(hidden)) hidden.restore();
        }
    }

    private HiddenBlock hideAsGlass(Block block, Runnable owner) {
        if (block == null || block.getType().isAir()) return null;
        HiddenBlock hidden = new HiddenBlock(block, block.getState(), owner);
        hiddenBlocks.add(hidden);
        block.setType(Material.GLASS, false);
        return hidden;
    }

    private void restore(HiddenBlock hidden) {
        if (hidden == null) return;
        if (hiddenBlocks.remove(hidden)) hidden.restore();
    }

    private void restoreHiddenBlocks() {
        for (HiddenBlock hidden : hiddenBlocks) {
            if (hiddenBlocks.remove(hidden)) hidden.restore();
        }
    }

    private void restoreHiddenBlocks(World world) {
        for (HiddenBlock hidden : hiddenBlocks) {
            if (hidden.isIn(world) && hiddenBlocks.remove(hidden)) hidden.restore();
        }
    }

    private static ItemDisplay item(World world, Location location, ItemStack stack, float scale) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack);
        display.setBillboard(Display.Billboard.FIXED);
        scale(display, scale);
        return display;
    }

    private static void scale(ItemDisplay display, float scale) {
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(
                current.getTranslation(), current.getLeftRotation(),
                new Vector3f(scale, scale, scale), current.getRightRotation()));
    }

    private static void waterParticles(World world, Location center, int tick, double radius, int amount) {
        for (int index = 0; index < amount; index++) {
            double angle = tick * 0.24 + (Math.PI * 2.0 * index / amount);
            Location point = center.clone().add(Math.cos(angle) * radius,
                    0.30 + Math.sin(tick * 0.12 + index) * 0.40, Math.sin(angle) * radius);
            ParticleCompat.spawn(world, point, new String[]{"BUBBLE", "WATER_BUBBLE", "BUBBLE_POP"},
                    1, 0.02, 0.04, 0.02, 0.015);
            if (tick % 3 == 0) {
                ParticleCompat.spawn(world, point, new String[]{"DRIPPING_WATER", "DRIP_WATER", "WATER_DROP"},
                        1, 0.03, 0.04, 0.03, 0.0);
            }
        }
    }

    private static void updateCrystals(ItemDisplay[] crystals, Location center, int tick,
                                       double radius, double height, float scale) {
        for (int index = 0; index < crystals.length; index++) {
            ItemDisplay crystal = crystals[index];
            if (crystal == null || crystal.isDead()) continue;
            double angle = tick * 0.18 + (Math.PI * 2.0 * index / crystals.length);
            double bob = Math.sin(tick * 0.20 + index) * 0.18;
            crystal.teleport(center.clone().add(Math.cos(angle) * radius, height + bob, Math.sin(angle) * radius));
            crystal.setRotation((float) Math.toDegrees(angle), 0.0f);
            scale(crystal, Math.max(0.01f, scale));
        }
    }

    private static double easeOut(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }

    private record HiddenBlock(Block block, BlockState state, Runnable owner) {
        private boolean isIn(World world) {
            return block != null && world != null && block.getWorld().equals(world);
        }

        private void restore() {
            if (block == null || state == null || (block.getType() != Material.GLASS && !block.getType().isAir())) return;
            state.update(true, false);
        }
    }
}
