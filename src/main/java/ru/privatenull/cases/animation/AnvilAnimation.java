package ru.privatenull.cases.animation;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Vector3f;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnvilAnimation extends CaseAnimation {

    public AnvilAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World w = base.getWorld();
        if (w == null) { onFinish.run(); return; }

        final double ANVIL_START_Y = 6.0;
        final double ANVIL_LAND_Y  = 1.01;
        final int PHASE1_END = 20;
        final int PHASE2_END = 80;
        final int PHASE3_END = 120;

        ItemStack rewardVisual = buildRewardVisual(reward, def);

        BlockDisplay anvil = (BlockDisplay) w.spawnEntity(base.clone().add(0, ANVIL_START_Y, 0), EntityType.BLOCK_DISPLAY);
        anvil.setBlock(Material.ANVIL.createBlockData());
        org.bukkit.util.Transformation tf = anvil.getTransformation();
        anvil.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(-0.5f, 0f, -0.5f),
                tf.getLeftRotation(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                tf.getRightRotation()
        ));
        track(anvil);

        ItemDisplay rewardDisplay = (ItemDisplay) w.spawnEntity(base.clone().add(0, ANVIL_LAND_Y + 0.3, 0), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        org.bukkit.util.Transformation rtf = rewardDisplay.getTransformation();
        rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                rtf.getTranslation(), rtf.getLeftRotation(),
                new Vector3f(0f, 0f, 0f),
                rtf.getRightRotation()
        ));
        track(rewardDisplay);

        TextDisplay label = (TextDisplay) w.spawnEntity(base.clone().add(0, ANVIL_LAND_Y + 1.8, 0), EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(true);
        label.setDefaultBackground(false);
        label.setText("");
        track(label);

        Random rng = new Random();
        List<Integer> hitTicks = new ArrayList<>();
        for (int t = PHASE1_END + 2; t < PHASE2_END; t += 5 + rng.nextInt(5)) hitTicks.add(t);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            int t = 0;
            float rewardYaw = 0f;

            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(); return; }
                t++;

                if (t <= PHASE1_END) {
                    double progress = (double) t / PHASE1_END;
                    double eased = progress * progress;
                    double currentY = ANVIL_START_Y + (ANVIL_LAND_Y - ANVIL_START_Y) * eased;
                    anvil.teleport(base.clone().add(0, currentY, 0));
                    if (t % 4 == 0) w.playSound(base, Sound.ENTITY_ARROW_SHOOT, 0.3f, 0.5f + (float) eased);
                }

                if (t == PHASE1_END) {
                    anvil.teleport(base.clone().add(0, ANVIL_LAND_Y, 0));
                    w.playSound(base, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.8f);
                    w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
                    w.playSound(base, Sound.BLOCK_STONE_BREAK, 0.8f, 0.6f);
                    w.spawnParticle(Particle.BLOCK, base.clone().add(0, ANVIL_LAND_Y, 0),
                            60, 0.4, 0.1, 0.4, 0.15, Material.STONE.createBlockData());
                    w.spawnParticle(Particle.CLOUD, base.clone().add(0, ANVIL_LAND_Y, 0),
                            20, 0.5, 0.1, 0.5, 0.05);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.7f);
                }

                if (t > PHASE1_END && t <= PHASE2_END) {
                    Location anvilTop = base.clone().add(0, ANVIL_LAND_Y + 0.5, 0);
                    if (hitTicks.contains(t)) {
                        w.playSound(base, Sound.BLOCK_ANVIL_USE, 0.9f, 0.9f + rng.nextFloat() * 0.3f);
                        w.spawnParticle(Particle.LAVA, anvilTop, 8, 0.3, 0.1, 0.3, 0.0);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, anvilTop, 15, 0.25, 0.15, 0.25, 0.05);
                        w.spawnParticle(Particle.CRIT, anvilTop, 10, 0.3, 0.2, 0.3, 0.1);
                        double jitter = (rng.nextDouble() - 0.5) * 0.04;
                        anvil.teleport(base.clone().add(jitter, ANVIL_LAND_Y, jitter));
                    } else {
                        anvil.teleport(base.clone().add(0, ANVIL_LAND_Y, 0));
                    }
                    if (t % 3 == 0) w.spawnParticle(Particle.SMOKE, anvilTop, 2, 0.15, 0.05, 0.15, 0.01);

                    if (t > PHASE2_END - 8) {
                        float glowScale = (float)(t - (PHASE2_END - 8)) / 8f * 0.4f;
                        org.bukkit.util.Transformation gt = rewardDisplay.getTransformation();
                        rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                                gt.getTranslation(), gt.getLeftRotation(),
                                new Vector3f(glowScale, glowScale, glowScale),
                                gt.getRightRotation()
                        ));
                        w.spawnParticle(Particle.ENCHANT, anvilTop, 5, 0.1, 0.1, 0.1, 0.3);
                    }
                }

                if (t > PHASE2_END && t <= PHASE3_END) {
                    double phaseProgress = (double)(t - PHASE2_END) / (PHASE3_END - PHASE2_END);
                    double eased = 1.0 - Math.pow(1.0 - phaseProgress, 2);
                    double rewardY = ANVIL_LAND_Y + 0.3 + eased * 1.5;
                    rewardYaw += 4f + (float)((1.0 - eased) * 6f);
                    if (rewardYaw >= 360f) rewardYaw -= 360f;

                    Location rewardLoc = base.clone().add(0, rewardY, 0);
                    rewardDisplay.teleport(rewardLoc);
                    rewardDisplay.setRotation(rewardYaw, 0f);

                    float sc = Math.min(1.0f, 0.4f + (float) eased * 0.6f);
                    org.bukkit.util.Transformation st = rewardDisplay.getTransformation();
                    rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                            st.getTranslation(), st.getLeftRotation(),
                            new Vector3f(sc, sc, sc),
                            st.getRightRotation()
                    ));

                    if (t == PHASE2_END + 5) setLabel(label, rewardVisual);

                    if (t % 2 == 0) {
                        w.spawnParticle(Particle.ENCHANT, rewardLoc, 5, 0.1, 0.1, 0.1, 0.2);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, rewardLoc, 3, 0.15, 0.1, 0.15, 0.02);
                    }
                    if (t % 8 == 0) w.playSound(base, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 0.8f + (float) eased * 0.6f);
                }

                if (t >= PHASE3_END) {
                    Location rewardLoc = base.clone().add(0, ANVIL_LAND_Y + 1.8, 0);
                    spawnFinalParticles(w, rewardLoc);
                    w.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.1f);
                    w.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
                    w.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.5f);
                    cleanup();
                }
            }

            private void cleanup() {
                safeRemove(anvil); safeRemove(rewardDisplay); safeRemove(label);
                untrack(anvil); untrack(rewardDisplay); untrack(label);
                if (taskHolder[0] != null) untrack(taskHolder[0]);
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private static void spawnFinalParticles(World w, Location loc) {
        w.spawnParticle(Particle.LAVA, loc, 30, 0.4, 0.3, 0.4, 0.0);
        w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 80, 0.4, 0.3, 0.4, 0.06);
        w.spawnParticle(Particle.END_ROD, loc, 40, 0.3, 0.3, 0.3, 0.04);
        w.spawnParticle(Particle.ENCHANT, loc, 60, 0.5, 0.4, 0.5, 0.4);
        w.spawnParticle(Particle.FIREWORK, loc, 25, 0.3, 0.3, 0.3, 0.03);
        for (int ring = 0; ring < 16; ring++) {
            double angle = (2 * Math.PI * ring) / 16;
            w.spawnParticle(Particle.ELECTRIC_SPARK,
                    loc.clone().add(0.6 * Math.cos(angle), 0, 0.6 * Math.sin(angle)), 2, 0, 0, 0, 0.02);
        }
    }

    private static void setLabel(TextDisplay td, ItemStack it) {
        String name = null;
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) name = meta.getDisplayName();
        if (name == null || name.isBlank()) name = "§f" + it.getType().name();
        td.setText(ChatColor.translateAlternateColorCodes('&', name));
    }

    private static ItemStack buildRewardVisual(Reward reward, CaseDefinition def) {
        return (reward.type() == Reward.Type.ITEM && reward.item() != null)
                ? reward.item().clone()
                : def.animationItems().get(0).clone();
    }

    private static void safeRemove(Entity e) {
        try { e.remove(); } catch (Exception ignored) {}
    }
}