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

public class DynamiteAnimation extends CaseAnimation {

    private static final int PHASE0_END = 35;
    private static final int PHASE1_END = 60;
    private static final int PHASE2_END = 65;
    private static final int PHASE3_END = 110;
    private static final int PHASE4_END = 155;

    private static final int DEBRIS_COUNT    = 10;
    private static final int GUNPOWDER_COUNT = 8;

    public DynamiteAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World w = base.getWorld();
        if (w == null) { onFinish.run(); return; }

        Random rng = new Random();
        ItemStack rewardVisual = buildRewardVisual(reward, def);

        Location tntStart = base.clone().add(3.5, 7.0, 0.0);
        Location tntTarget = base.clone().add(0, 1.50, 0);

        BlockDisplay tnt = (BlockDisplay) w.spawnEntity(tntStart, EntityType.BLOCK_DISPLAY);
        tnt.setBlock(Material.TNT.createBlockData());
        centerBlock(tnt, 1.0f);
        track(tnt);

        double dx = tntTarget.getX() - tntStart.getX();
        double dz = tntTarget.getZ() - tntStart.getZ();
        double T = PHASE0_END;
        double g = 0.025;
        double vy0 = (0.5 * g * T * T - 7.0) / T;

        ItemDisplay rewardDisplay = (ItemDisplay) w.spawnEntity(base.clone().add(0, 0.5, 0), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        hideDisplay(rewardDisplay);
        track(rewardDisplay);

        TextDisplay label = (TextDisplay) w.spawnEntity(base.clone().add(0, 3.2, 0), EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(true);
        label.setDefaultBackground(false);
        label.setText("");
        track(label);

        List<BlockDisplay> debris    = new ArrayList<>();
        List<ItemDisplay>  gunpowder = new ArrayList<>();
        double[][] debrisVel = new double[DEBRIS_COUNT][3];
        double[][] gpVel     = new double[GUNPOWDER_COUNT][3];
        double[] debrisX = new double[DEBRIS_COUNT];
        double[] debrisY = new double[DEBRIS_COUNT];
        double[] debrisZ = new double[DEBRIS_COUNT];
        double[] gpX = new double[GUNPOWDER_COUNT];
        double[] gpY = new double[GUNPOWDER_COUNT];
        double[] gpZ = new double[GUNPOWDER_COUNT];

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            int t = 0;
            float rewardYaw = 0f;
            boolean exploded = false;

            double curX = tntStart.getX();
            double curY = tntStart.getY();
            double curZ = tntStart.getZ();
            double velY = vy0;
            double velX = dx / T;
            double velZ = dz / T;

            int bounceCount = 0;
            double bounceVY = 0;
            boolean bouncing = false;

            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(); return; }
                t++;

                if (t <= PHASE0_END) {
                    if (!bouncing) {
                        velY -= g;
                        curX += velX;
                        curY += velY;
                        curZ += velZ;

                        if (curY <= tntTarget.getY()) {
                            curY = tntTarget.getY();
                            bouncing = true;
                            bounceVY = Math.abs(velY) * 0.35;
                            bounceCount = 0;
                            w.playSound(base, Sound.BLOCK_STONE_HIT, 1.0f, 0.8f);
                            w.spawnParticle(Particle.BLOCK,
                                    new Location(w, curX, tntTarget.getY() + 0.05, curZ),
                                    8, 0.2, 0.0, 0.2, 0.02,
                                    Material.DIRT.createBlockData());
                        }
                        tnt.setRotation((float)(t * 12), (float)(t * 5));
                    } else {
                        bounceVY -= g;
                        curY += bounceVY;
                        if (curY <= tntTarget.getY()) {
                            curY = tntTarget.getY();
                            bounceCount++;
                            bounceVY = Math.abs(bounceVY) * 0.4;
                            if (bounceCount >= 2 || bounceVY < 0.01) {
                                bouncing = false;
                                bounceVY = 0;
                                tnt.setRotation(0f, 0f);
                            }
                        }
                    }

                    if (!tnt.isDead()) {
                        tnt.teleport(new Location(w, curX, curY, curZ));
                    }
                    if (t % 2 == 0) {
                        w.spawnParticle(Particle.SMOKE, new Location(w, curX, curY + 0.5, curZ),
                                2, 0.05, 0.05, 0.05, 0.01);
                    }
                    if (t % 6 == 0 && !bouncing) {
                        w.playSound(new Location(w, curX, curY, curZ),
                                Sound.ENTITY_ARROW_SHOOT, 0.4f, 0.6f + (float) t / PHASE0_END * 0.4f);
                    }
                }

                if (t > PHASE0_END && t <= PHASE1_END) {
                    if (!tnt.isDead()) {
                        tnt.teleport(tntTarget);
                        tnt.setRotation(0f, 0f);

                        boolean bright = ((t - PHASE0_END) % 6) < 3;
                        tnt.setBlock(bright ? Material.TNT.createBlockData() : Material.REDSTONE_BLOCK.createBlockData());

                        float jitter = (t > PHASE1_END - 8)
                                ? 1.0f + (rng.nextFloat() - 0.5f) * 0.06f
                                : 1.0f;
                        centerBlock(tnt, jitter);
                    }

                    Location fuseTop = tntTarget.clone().add(0, 1.05, 0);
                    w.spawnParticle(Particle.SMOKE, fuseTop, 2, 0.03, 0.0, 0.03, 0.01);
                    if (t % 3 == 0) w.spawnParticle(Particle.FLAME, fuseTop, 1, 0.02, 0.0, 0.02, 0.01);

                    if (t % 5 == 0) {
                        float progress = (float)(t - PHASE0_END) / (PHASE1_END - PHASE0_END);
                        w.playSound(base, Sound.ENTITY_TNT_PRIMED, 0.6f, 0.7f + progress * 0.8f);
                    }
                }

                if (t == PHASE1_END + 1 && !exploded) {
                    exploded = true;
                    safeRemove(tnt);

                    Location boom = tntTarget.clone().add(0, 0.5, 0);

                    w.spawnParticle(Particle.EXPLOSION, boom, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.SMOKE, boom, 60, 0.7, 0.4, 0.7, 0.08);
                    w.spawnParticle(Particle.LARGE_SMOKE, boom, 25, 0.6, 0.3, 0.6, 0.04);
                    w.spawnParticle(Particle.FLAME, boom, 40, 0.5, 0.3, 0.5, 0.12);
                    w.spawnParticle(Particle.LAVA, boom, 20, 0.4, 0.2, 0.4, 0.0);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, boom, 50, 0.5, 0.3, 0.5, 0.15);
                    w.spawnParticle(Particle.CRIT, boom, 30, 0.4, 0.3, 0.4, 0.2);

                    for (int i = 0; i < 20; i++) {
                        double angle = 2 * Math.PI * i / 20;
                        Location ring = boom.clone().add(0.8 * Math.cos(angle), 0, 0.8 * Math.sin(angle));
                        w.spawnParticle(Particle.SMOKE, ring, 4, 0.05, 0.1, 0.05, 0.05);
                        w.spawnParticle(Particle.FLAME, ring, 2, 0.02, 0.05, 0.02, 0.06);
                    }

                    w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
                    w.playSound(base, Sound.ENTITY_TNT_PRIMED, 1.0f, 0.4f);
                    w.playSound(base, Sound.BLOCK_STONE_BREAK, 0.9f, 0.5f);
                    w.playSound(base, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.7f, 0.6f);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

                    Material[] debrisMats = {
                            Material.DIRT, Material.GRAVEL, Material.STONE,
                            Material.COARSE_DIRT, Material.COBBLESTONE, Material.GRAVEL
                    };
                    for (int i = 0; i < DEBRIS_COUNT; i++) {
                        BlockDisplay bd = (BlockDisplay) w.spawnEntity(boom, EntityType.BLOCK_DISPLAY);
                        bd.setBlock(debrisMats[rng.nextInt(debrisMats.length)].createBlockData());
                        float sc = 0.15f + rng.nextFloat() * 0.2f;
                        org.bukkit.util.Transformation tf = bd.getTransformation();
                        bd.setTransformation(new org.bukkit.util.Transformation(
                                new Vector3f(-sc / 2, -sc / 2, -sc / 2),
                                tf.getLeftRotation(),
                                new Vector3f(sc, sc, sc),
                                tf.getRightRotation()
                        ));
                        debris.add(bd);
                        track(bd);

                        double angle  = rng.nextDouble() * 2 * Math.PI;
                        double hSpeed = 0.12 + rng.nextDouble() * 0.18;
                        debrisVel[i][0] = Math.cos(angle) * hSpeed;
                        debrisVel[i][1] = 0.18 + rng.nextDouble() * 0.22;
                        debrisVel[i][2] = Math.sin(angle) * hSpeed;
                        debrisX[i] = boom.getX();
                        debrisY[i] = boom.getY();
                        debrisZ[i] = boom.getZ();
                    }

                    for (int i = 0; i < GUNPOWDER_COUNT; i++) {
                        ItemDisplay gp = (ItemDisplay) w.spawnEntity(boom, EntityType.ITEM_DISPLAY);
                        gp.setItemStack(new ItemStack(Material.GUNPOWDER));
                        gp.setBillboard(Display.Billboard.VERTICAL);
                        org.bukkit.util.Transformation tf = gp.getTransformation();
                        gp.setTransformation(new org.bukkit.util.Transformation(
                                tf.getTranslation(), tf.getLeftRotation(),
                                new Vector3f(0.35f, 0.35f, 0.35f),
                                tf.getRightRotation()
                        ));
                        gunpowder.add(gp);
                        track(gp);

                        double angle  = rng.nextDouble() * 2 * Math.PI;
                        double hSpeed = 0.08 + rng.nextDouble() * 0.14;
                        gpVel[i][0] = Math.cos(angle) * hSpeed;
                        gpVel[i][1] = 0.14 + rng.nextDouble() * 0.16;
                        gpVel[i][2] = Math.sin(angle) * hSpeed;
                        gpX[i] = boom.getX();
                        gpY[i] = boom.getY();
                        gpZ[i] = boom.getZ();
                    }
                }

                if (t > PHASE1_END && t <= PHASE3_END) {
                    double gravity = 0.012;

                    for (int i = 0; i < debris.size(); i++) {
                        BlockDisplay bd = debris.get(i);
                        if (bd == null || bd.isDead()) continue;
                        debrisVel[i][1] -= gravity;
                        debrisX[i] += debrisVel[i][0];
                        debrisY[i] += debrisVel[i][1];
                        debrisZ[i] += debrisVel[i][2];
                        if (debrisY[i] < base.getY() - 0.3) {
                            w.spawnParticle(Particle.BLOCK,
                                    new Location(w, debrisX[i], base.getY() + 0.1, debrisZ[i]),
                                    6, 0.1, 0.0, 0.1, 0.02, Material.DIRT.createBlockData());
                            safeRemove(bd);
                            continue;
                        }
                        bd.teleport(new Location(w, debrisX[i], debrisY[i], debrisZ[i]));
                        bd.setRotation(bd.getLocation().getYaw() + 9f, bd.getLocation().getPitch() + 6f);
                        if (t % 3 == 0)
                            w.spawnParticle(Particle.SMOKE, bd.getLocation(), 1, 0.03, 0.03, 0.03, 0.005);
                    }

                    double phaseProgress = (double)(t - PHASE1_END) / (PHASE3_END - PHASE1_END);
                    for (int i = 0; i < gunpowder.size(); i++) {
                        ItemDisplay gp = gunpowder.get(i);
                        if (gp == null || gp.isDead()) continue;
                        gpVel[i][1] -= gravity * 0.8;
                        gpX[i] += gpVel[i][0];
                        gpY[i] += gpVel[i][1];
                        gpZ[i] += gpVel[i][2];
                        float sc = Math.max(0f, 0.35f * (1.0f - (float) phaseProgress));
                        org.bukkit.util.Transformation tf = gp.getTransformation();
                        gp.setTransformation(new org.bukkit.util.Transformation(
                                tf.getTranslation(), tf.getLeftRotation(),
                                new Vector3f(sc, sc, sc), tf.getRightRotation()
                        ));
                        if (gpY[i] < base.getY() - 0.2 || sc <= 0) { safeRemove(gp); continue; }
                        gp.teleport(new Location(w, gpX[i], gpY[i], gpZ[i]));
                    }

                    if (t % 2 == 0)
                        w.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0.02);

                    if (t == PHASE3_END - 10) {
                        w.spawnParticle(Particle.END_ROD, base.clone().add(0, 0.2, 0), 20, 0.1, 0.05, 0.1, 0.04);
                        w.playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
                    }
                }

                if (t == PHASE3_END + 1) {
                    org.bukkit.util.Transformation st = rewardDisplay.getTransformation();
                    rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                            st.getTranslation(), st.getLeftRotation(),
                            new Vector3f(0.2f, 0.2f, 0.2f), st.getRightRotation()
                    ));
                    Location pop = base.clone().add(0, 0.5, 0);
                    w.spawnParticle(Particle.END_ROD, pop, 40, 0.3, 0.2, 0.3, 0.08);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, pop, 30, 0.25, 0.15, 0.25, 0.1);
                    w.spawnParticle(Particle.SMOKE, pop, 15, 0.2, 0.1, 0.2, 0.04);
                    w.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.3f);
                    w.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
                }

                if (t > PHASE3_END && t <= PHASE4_END) {
                    double phaseProgress = (double)(t - PHASE3_END) / (PHASE4_END - PHASE3_END);
                    double eased = 1.0 - Math.pow(1.0 - phaseProgress, 2);

                    Location rewardLoc = base.clone().add(0, 0.5 + eased * 1.8, 0);
                    rewardDisplay.teleport(rewardLoc);

                    float sc = 0.2f + (float) eased * 0.8f;
                    org.bukkit.util.Transformation st = rewardDisplay.getTransformation();
                    rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                            st.getTranslation(), st.getLeftRotation(),
                            new Vector3f(sc, sc, sc), st.getRightRotation()
                    ));

                    rewardYaw += 3.5f + (float)((1.0 - eased) * 5f);
                    if (rewardYaw >= 360f) rewardYaw -= 360f;
                    rewardDisplay.setRotation(rewardYaw, 0f);

                    if (t % 5 == 0) {
                        double ringR = 0.5 + 0.3 * Math.sin(phaseProgress * Math.PI * 3);
                        for (int ring = 0; ring < 8; ring++) {
                            double angle = (2 * Math.PI * ring) / 8 + t * 0.08;
                            w.spawnParticle(Particle.SMOKE,
                                    rewardLoc.clone().add(ringR * Math.cos(angle), 0.1, ringR * Math.sin(angle)),
                                    1, 0, 0, 0, 0.005);
                        }
                    }
                    if (t % 3 == 0)
                        w.spawnParticle(Particle.ELECTRIC_SPARK, rewardLoc, 2, 0.25, 0.15, 0.25, 0.04);
                    if (t % 10 == 0)
                        w.playSound(base, Sound.BLOCK_FIRE_AMBIENT, 0.4f, 1.5f + (float) phaseProgress * 0.5f);
                    if (t == PHASE3_END + 10)
                        setLabel(label, rewardVisual);
                }

                if (t >= PHASE4_END) {
                    Location rewardLoc = base.clone().add(0, 2.3, 0);
                    w.spawnParticle(Particle.EXPLOSION, rewardLoc, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, rewardLoc, 60, 0.4, 0.3, 0.4, 0.12);
                    w.spawnParticle(Particle.END_ROD, rewardLoc, 40, 0.3, 0.2, 0.3, 0.06);
                    w.spawnParticle(Particle.SMOKE, rewardLoc, 30, 0.5, 0.3, 0.5, 0.05);
                    w.spawnParticle(Particle.LAVA, rewardLoc, 15, 0.3, 0.2, 0.3, 0.0);
                    w.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    w.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.5f);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
                    cleanup();
                }
            }

            private void cleanup() {
                safeRemove(tnt);
                for (BlockDisplay bd : debris) safeRemove(bd);
                for (ItemDisplay gp : gunpowder) safeRemove(gp);
                safeRemove(rewardDisplay);
                safeRemove(label);
                untrack(tnt); untrack(rewardDisplay); untrack(label);
                if (taskHolder[0] != null) untrack(taskHolder[0]);
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private static void centerBlock(BlockDisplay bd, float scale) {
        float half = scale / 2f;
        org.bukkit.util.Transformation tf = bd.getTransformation();
        bd.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(-half, -half, -half),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
        ));
    }

    private static void hideDisplay(ItemDisplay id) {
        org.bukkit.util.Transformation tf = id.getTransformation();
        id.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(), tf.getLeftRotation(),
                new Vector3f(0f, 0f, 0f), tf.getRightRotation()
        ));
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
        try { if (e != null && !e.isDead()) e.remove(); } catch (Exception ignored) {}
    }
}