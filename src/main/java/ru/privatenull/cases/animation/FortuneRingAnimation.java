package ru.privatenull.cases.animation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;

import java.util.ArrayList;
import java.util.List;

public final class FortuneRingAnimation extends CaseAnimation {

    private static final int ITEM_COUNT = 10;
    private static final int FORM_END = 28;
    private static final int SPIN_END = 118;
    private static final int SLOW_END = 178;
    private static final int REVEAL_END = 226;

    public FortuneRingAnimation(pnCases plugin) {
        super(plugin);
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
        List<ItemDisplay> orbitItems = new ArrayList<>();
        Vector rightAxis = rightAxis(player, base);
        Location center = base.clone().add(0.0, 1.55, 0.0);

        for (int i = 0; i < ITEM_COUNT; i++) {
            ItemDisplay display = (ItemDisplay) world.spawnEntity(center, EntityType.ITEM_DISPLAY);
            display.setItemStack(visuals.get(i % visuals.size()));
            display.setBillboard(Display.Billboard.VERTICAL);
            setScale(display, 0.0f);
            orbitItems.add(display);
            track(display);
        }

        ItemDisplay rewardDisplay = (ItemDisplay) world.spawnEntity(center, EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        setScale(rewardDisplay, 0.0f);
        track(rewardDisplay);

        TextDisplay label = (TextDisplay) world.spawnEntity(base.clone().add(0.0, 3.20, 0.0), EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(true);
        label.setDefaultBackground(false);
        label.setText("");
        track(label);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            int tick = 0;
            double rotation = 0.0;
            boolean finished = false;

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

                Location rewardLoc = base.clone().add(0.0, 1.70, 0.0);
                spawnFinalBurst(world, rewardLoc);
                world.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.26f, 1.20f);
                world.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.22f, 1.55f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.55f);
                cleanup();
            }

            private void playForm() {
                double progress = clamp(tick / (double) FORM_END);
                double eased = easeOut(progress);
                rotation += 0.08 + progress * 0.10;

                updateOrbit(eased, 0.35 + eased * 1.10, 1.55, rotation, 0.30f + (float) eased * 0.45f);
                drawRing(world, base, rightAxis, 0.55 + eased * 1.05, 1.55, rotation, Particle.END_ROD);

                if (tick == 1) {
                    world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.20f, 1.35f);
                    world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.15f, 1.85f);
                }
                if (tick % 7 == 0) {
                    world.playSound(base, Sound.BLOCK_NOTE_BLOCK_BELL, 0.10f, 1.2f + (float) progress * 0.35f);
                }
            }

            private void playSpin() {
                double progress = clamp((tick - FORM_END) / (double) (SPIN_END - FORM_END));
                rotation += 0.28 + progress * 0.10;

                updateOrbit(1.0, 1.45, 1.55 + Math.sin(tick * 0.10) * 0.03, rotation, 0.76f);
                drawRing(world, base, rightAxis, 1.45, 1.55, rotation, Particle.ELECTRIC_SPARK);

                if (tick % 5 == 0) {
                    world.playSound(base, Sound.BLOCK_NOTE_BLOCK_PLING, 0.08f, 1.45f + (float) Math.sin(tick * 0.07) * 0.18f);
                }
                if (tick % 3 == 0) {
                    world.spawnParticle(Particle.ENCHANT, center, 5, 0.55, 0.15, 0.55, 0.22);
                }
            }

            private void playSlow() {
                double progress = clamp((tick - SPIN_END) / (double) (SLOW_END - SPIN_END));
                double speed = 0.32 * Math.pow(1.0 - progress, 2.0) + 0.045;
                double radius = 1.45 - progress * 0.42;
                rotation += speed;

                updateOrbit(1.0, radius, 1.55 + progress * 0.10, rotation, 0.76f);
                drawRing(world, base, rightAxis, radius, 1.60, rotation, Particle.END_ROD);
                world.spawnParticle(Particle.PORTAL, center, 8, 0.50, 0.20, 0.50, 0.16);

                if (tick % 10 == 0) {
                    world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.11f, 1.65f - (float) progress * 0.35f);
                }
            }

            private void playReveal() {
                double progress = clamp((tick - SLOW_END) / (double) (REVEAL_END - SLOW_END));
                double eased = easeOutBack(progress);
                rotation += 0.035;

                for (int i = 0; i < orbitItems.size(); i++) {
                    ItemDisplay display = orbitItems.get(i);
                    double angle = angle(i, orbitItems.size(), rotation);
                    double radius = 1.02 * (1.0 - progress);
                    double centerY = 1.60 + progress * 0.08 + Math.sin(tick * 0.12 + i) * 0.02;
                    display.teleport(verticalPoint(base, rightAxis, radius, centerY, angle));
                    setScale(display, (float) Math.max(0.0, 0.72 * (1.0 - progress)));
                }

                Location rewardLoc = base.clone().add(0.0, 1.55 + eased * 0.28, 0.0);
                rewardDisplay.teleport(rewardLoc);
                rewardDisplay.setRotation((float) ((tick * 5.0) % 360.0), 0.0f);
                setScale(rewardDisplay, (float) Math.min(1.0, 0.25 + eased * 0.80));

                if (tick == SLOW_END + 5) {
                    label.setText(resolveRewardName(reward, rewardVisual));
                    world.playSound(base, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.18f, 1.55f);
                }

                drawRing(world, base, rightAxis, 0.42 + progress * 0.22, 1.65, -rotation, Particle.FIREWORK);
                world.spawnParticle(Particle.END_ROD, rewardLoc, 8, 0.22, 0.16, 0.22, 0.025);
            }

            private void updateOrbit(double alpha, double radius, double centerY, double rotation, float scale) {
                for (int i = 0; i < orbitItems.size(); i++) {
                    ItemDisplay display = orbitItems.get(i);
                    double angle = angle(i, orbitItems.size(), rotation);
                    double bob = Math.sin(tick * 0.15 + i * 0.70) * 0.035;
                    Location loc = verticalPoint(base, rightAxis, radius, centerY + bob, angle);

                    display.teleport(loc);
                    display.setRotation((float) Math.toDegrees(angle) + 90.0f, 0.0f);
                    setScale(display, Math.max(0.0f, scale * (float) alpha));
                }
            }

            private void cleanup() {
                if (finished) {
                    return;
                }
                finished = true;

                for (ItemDisplay display : orbitItems) {
                    safeRemove(display);
                    untrack(display);
                }
                safeRemove(rewardDisplay);
                safeRemove(label);
                untrack(rewardDisplay);
                untrack(label);
                if (taskHolder[0] != null) {
                    untrack(taskHolder[0]);
                }
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private List<ItemStack> buildVisuals(CaseDefinition def, ItemStack rewardVisual) {
        List<ItemStack> visuals = new ArrayList<>();
        if (def != null && def.animationItems() != null) {
            for (ItemStack item : def.animationItems()) {
                if (item != null && !item.getType().isAir()) {
                    visuals.add(item.clone());
                }
            }
        }
        if (rewardVisual != null && !rewardVisual.getType().isAir()) {
            visuals.add(rewardVisual.clone());
        }
        if (visuals.isEmpty()) {
            visuals.add(new ItemStack(Material.AMETHYST_SHARD));
        }
        return visuals;
    }

    private static void drawRing(World world, Location base, Vector rightAxis, double radius, double centerY, double rotation, Particle particle) {
        for (int i = 0; i < 24; i++) {
            double angle = angle(i, 24, rotation);
            Location loc = verticalPoint(base, rightAxis, radius, centerY, angle);
            world.spawnParticle(particle, loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnFinalBurst(World world, Location loc) {
        world.spawnParticle(Particle.FIREWORK, loc, 28, 0.45, 0.28, 0.45, 0.04);
        world.spawnParticle(Particle.END_ROD, loc, 45, 0.40, 0.35, 0.40, 0.035);
        world.spawnParticle(Particle.ENCHANT, loc, 55, 0.55, 0.40, 0.55, 0.35);
        for (int i = 0; i < 32; i++) {
            double angle = angle(i, 32, 0.0);
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    loc.clone().add(Math.cos(angle) * 0.9, 0.05, Math.sin(angle) * 0.9),
                    1, 0.0, 0.0, 0.0, 0.02);
        }
    }

    private static void setScale(ItemDisplay display, float scale) {
        org.bukkit.util.Transformation tf = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
        ));
    }

    private static double angle(int index, int total, double rotation) {
        return (Math.PI * 2.0 * index / Math.max(1, total)) + rotation;
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

    private static void safeRemove(Entity entity) {
        try {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        } catch (Exception ignored) {
        }
    }
}
