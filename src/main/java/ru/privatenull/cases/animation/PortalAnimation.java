package ru.privatenull.cases.animation;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PortalAnimation extends CaseAnimation {

    private static final int PHASE1_END = 50;
    private static final int PHASE2_END = 110;
    private static final int PHASE3_END = 155;
    private static final int PHASE4_END = 230;

    public PortalAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World w = base.getWorld();
        if (w == null) { onFinish.run(); return; }

        Random rng = new Random();
        ItemStack rewardVisual = buildRewardVisual(reward, def);
        Location portalCenter = base.clone().add(0, 1.7, 0);

        ItemDisplay rewardDisplay = (ItemDisplay) w.spawnEntity(portalCenter.clone(), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.FIXED);
        setScale(rewardDisplay, 0f);
        track(rewardDisplay);

        TextDisplay label = (TextDisplay) w.spawnEntity(base.clone().add(0, 4.2, 0), EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(true);
        label.setDefaultBackground(false);
        label.setTextOpacity((byte) 230);
        label.setText("");
        track(label);

        Material[] fakeMats = {
                Material.EMERALD, Material.GOLD_INGOT, Material.AMETHYST_SHARD,
                Material.LAPIS_LAZULI, Material.QUARTZ, Material.PRISMARINE_CRYSTALS,
                Material.NETHERITE_INGOT, Material.DIAMOND
        };
        int fakeCount = 8;
        List<ItemDisplay> fakeItems = new ArrayList<>();
        double[] fakeAngle  = new double[fakeCount];
        double[] fakeSpeed  = new double[fakeCount];
        double[] fakeRadius = new double[fakeCount];

        for (int i = 0; i < fakeCount; i++) {
            ItemDisplay fd = (ItemDisplay) w.spawnEntity(portalCenter.clone(), EntityType.ITEM_DISPLAY);
            fd.setItemStack(new ItemStack(fakeMats[i % fakeMats.length]));
            fd.setBillboard(Display.Billboard.FIXED);
            setScale(fd, 0f);
            fakeItems.add(fd);
            track(fd);
            fakeAngle[i]  = (2 * Math.PI * i) / fakeCount;
            fakeSpeed[i]  = 0.04 + rng.nextDouble() * 0.03;
            fakeRadius[i] = 0.9 + rng.nextDouble() * 0.4;
        }

        List<BlockDisplay> pillars = new ArrayList<>();
        int pillarCount = 6;
        for (int i = 0; i < pillarCount; i++) {
            double angle = (2 * Math.PI * i) / pillarCount;
            double px = base.getX() + 1.8 * Math.cos(angle);
            double pz = base.getZ() + 1.8 * Math.sin(angle);
            BlockDisplay pillar = (BlockDisplay) w.spawnEntity(
                    new Location(w, px, base.getY() - 0.3, pz), EntityType.BLOCK_DISPLAY);
            pillar.setBlock(Material.PURPUR_PILLAR.createBlockData());
            org.bukkit.util.Transformation tf = pillar.getTransformation();
            pillar.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(-0.15f, 0f, -0.15f),
                    tf.getLeftRotation(),
                    new Vector3f(0.3f, 0f, 0.3f),
                    tf.getRightRotation()
            ));
            pillars.add(pillar);
            track(pillar);
        }

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
                    double ringR = 1.5 * eased;

                    float pillarH = (float)(3.5 * eased);
                    for (int i = 0; i < pillars.size(); i++) {
                        BlockDisplay pillar = pillars.get(i);
                        org.bukkit.util.Transformation tf = pillar.getTransformation();
                        pillar.setTransformation(new org.bukkit.util.Transformation(
                                new Vector3f(-0.15f, 0f, -0.15f),
                                tf.getLeftRotation(),
                                new Vector3f(0.3f, pillarH, 0.3f),
                                tf.getRightRotation()
                        ));
                        double angle = (2 * Math.PI * i) / pillars.size();
                        double px = base.getX() + 1.8 * Math.cos(angle + t * 0.012);
                        double pz = base.getZ() + 1.8 * Math.sin(angle + t * 0.012);
                        pillar.teleport(new Location(w, px, base.getY() - 0.3, pz));

                        if (t % 4 == 0) {
                            Location top = new Location(w, px, base.getY() + pillarH * 0.9, pz);
                            w.spawnParticle(Particle.END_ROD, top, 1, 0.03, 0.05, 0.03, 0.02);
                        }
                    }

                    int pts = 40;
                    for (int i = 0; i < pts; i++) {
                        if (rng.nextInt(3) != 0) continue;
                        double a = (2 * Math.PI * i) / pts + t * 0.2;
                        w.spawnParticle(Particle.PORTAL,
                                portalCenter.getX() + ringR * Math.cos(a),
                                portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.12,
                                portalCenter.getZ() + ringR * Math.sin(a),
                                1, 0, 0, 0, 0);
                        if (rng.nextInt(4) == 0)
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    portalCenter.getX() + ringR * 0.7 * Math.cos(a + 0.15),
                                    portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.2,
                                    portalCenter.getZ() + ringR * 0.7 * Math.sin(a + 0.15),
                                    1, 0, 0, 0, 0);
                    }

                    for (int i = 0; i < 4; i++) {
                        double innerR = ringR * 0.5 * rng.nextDouble();
                        double a = rng.nextDouble() * 2 * Math.PI;
                        w.spawnParticle(Particle.PORTAL,
                                portalCenter.getX() + innerR * Math.cos(a),
                                portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.6,
                                portalCenter.getZ() + innerR * Math.sin(a),
                                1, 0, 0, 0, 0);
                    }

                    if (t % 7 == 0) w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 0.45f + (float) eased * 0.5f);
                    if (t % 14 == 0) w.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 0.65f);

                    if (t == 1) {
                        w.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 0.35f);
                        w.playSound(base, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.5f, 0.4f);
                    }
                }

                if (t > PHASE1_END && t <= PHASE2_END) {
                    double progress = (double)(t - PHASE1_END) / (PHASE2_END - PHASE1_END);

                    for (int i = 0; i < pillars.size(); i++) {
                        BlockDisplay pillar = pillars.get(i);
                        double angle = (2 * Math.PI * i) / pillars.size();
                        double px = base.getX() + 1.8 * Math.cos(angle + t * 0.012);
                        double pz = base.getZ() + 1.8 * Math.sin(angle + t * 0.012);
                        pillar.teleport(new Location(w, px, base.getY() - 0.3, pz));

                        float glow = (float)(0.5 + 0.5 * Math.sin(t * 0.18 + i));
                        if (t % 2 == 0) {
                            Location top = new Location(w, px, base.getY() + 3.0, pz);
                            w.spawnParticle(Particle.END_ROD, top, (int)(glow * 2 + 1), 0.05, 0.1, 0.05, 0.03);
                        }
                    }

                    double ringR = 1.5;
                    for (int i = 0; i < 56; i++) {
                        double a = (2 * Math.PI * i) / 56 + t * 0.24;
                        double wobble = 0.09 * Math.sin(t * 0.28 + i * 0.8);
                        double r = ringR + wobble;
                        w.spawnParticle(Particle.PORTAL,
                                portalCenter.getX() + r * Math.cos(a),
                                portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.14,
                                portalCenter.getZ() + r * Math.sin(a),
                                1, 0, 0, 0, 0);
                        if (i % 3 == 0)
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    portalCenter.getX() + r * 0.85 * Math.cos(a + 0.1),
                                    portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.2,
                                    portalCenter.getZ() + r * 0.85 * Math.sin(a + 0.1),
                                    1, 0, 0, 0, 0);
                    }

                    for (int i = 0; i < 12; i++) {
                        double a = (2 * Math.PI * i) / 12 - t * 0.3;
                        double r = ringR * (0.1 + 0.9 * ((double) i / 12));
                        w.spawnParticle(Particle.PORTAL,
                                portalCenter.getX() + r * Math.cos(a),
                                portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.4,
                                portalCenter.getZ() + r * Math.sin(a),
                                1, 0, 0, 0, 0.04);
                    }

                    for (int i = 0; i < fakeCount; i++) {
                        fakeAngle[i] += fakeSpeed[i];
                        double r = fakeRadius[i] + 0.25 * Math.sin(t * 0.09 + i * 1.3);
                        double oy = 0.35 * Math.sin(t * 0.14 + i * 1.1);
                        double fx = portalCenter.getX() + r * Math.cos(fakeAngle[i]);
                        double fy = portalCenter.getY() + oy;
                        double fz = portalCenter.getZ() + r * Math.sin(fakeAngle[i]);

                        ItemDisplay fd = fakeItems.get(i);
                        fd.teleport(new Location(w, fx, fy, fz, (float)(t * 10 * (i % 2 == 0 ? 1 : -1)), 0));
                        setScale(fd, 0.38f);

                        if (t % 5 == 0)
                            w.spawnParticle(Particle.END_ROD, fx, fy, fz, 1, 0.03, 0.03, 0.03, 0.02);
                        if (t % 11 == i % 11)
                            w.spawnParticle(Particle.PORTAL, fx, fy, fz, 2, 0.05, 0.05, 0.05, 0.06);
                    }

                    if (t == PHASE1_END + 1) {
                        w.playSound(base, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 0.4f);
                        w.playSound(base, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 0.5f);
                    }
                    if (t % 5 == 0) w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.8f + (float) progress * 0.45f);
                }

                if (t == PHASE2_END + 1) {
                    for (ItemDisplay fd : fakeItems) {
                        safeRemove(fd);
                        untrack(fd);
                    }
                    fakeItems.clear();

                    for (int layer = 0; layer < 3; layer++) {
                        double layerR = 0.4 + layer * 0.45;
                        int cnt = 20 + layer * 15;
                        for (int i = 0; i < cnt; i++) {
                            double a = rng.nextDouble() * 2 * Math.PI;
                            double r = rng.nextDouble() * layerR;
                            w.spawnParticle(Particle.PORTAL,
                                    portalCenter.getX() + r * Math.cos(a),
                                    portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.7,
                                    portalCenter.getZ() + r * Math.sin(a),
                                    1, 0, 0, 0, 0.14);
                        }
                        for (int i = 0; i < cnt / 2; i++) {
                            double a = rng.nextDouble() * 2 * Math.PI;
                            double r = rng.nextDouble() * layerR;
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    portalCenter.getX() + r * Math.cos(a),
                                    portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.5,
                                    portalCenter.getZ() + r * Math.sin(a),
                                    1, 0.04, 0.04, 0.04, 0.07);
                        }
                    }
                    for (int ring = 0; ring < 32; ring++) {
                        double a = (2 * Math.PI * ring) / 32;
                        w.spawnParticle(Particle.END_ROD,
                                portalCenter.getX() + 1.6 * Math.cos(a),
                                portalCenter.getY(),
                                portalCenter.getZ() + 1.6 * Math.sin(a),
                                3, 0, 0.08, 0, 0.07);
                    }

                    for (int i = 0; i < pillars.size(); i++) {
                        double angle = (2 * Math.PI * i) / pillars.size();
                        double px = base.getX() + 1.8 * Math.cos(angle + t * 0.012);
                        double pz = base.getZ() + 1.8 * Math.sin(angle + t * 0.012);
                        for (int j = 0; j < 5; j++) {
                            w.spawnParticle(Particle.END_ROD,
                                    px, base.getY() + j * 0.7, pz,
                                    3, 0.06, 0.02, 0.06, 0.05);
                        }
                    }

                    w.playSound(base, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.9f);
                    w.playSound(base, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.7f);
                    w.playSound(base, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.4f);
                }

                if (t > PHASE2_END && t <= PHASE3_END) {
                    double progress = (double)(t - PHASE2_END) / (PHASE3_END - PHASE2_END);
                    double eased = 1.0 - Math.pow(1.0 - progress, 3);
                    double collapseR = 1.5 * (1.0 - eased);

                    for (int i = 0; i < pillars.size(); i++) {
                        BlockDisplay pillar = pillars.get(i);
                        double angle = (2 * Math.PI * i) / pillars.size();
                        double px = base.getX() + 1.8 * Math.cos(angle + t * 0.012);
                        double pz = base.getZ() + 1.8 * Math.sin(angle + t * 0.012);
                        pillar.teleport(new Location(w, px, base.getY() - 0.3, pz));
                        float sc = Math.max(0f, 0.3f * (1.0f - (float) eased));
                        org.bukkit.util.Transformation tf = pillar.getTransformation();
                        pillar.setTransformation(new org.bukkit.util.Transformation(
                                new Vector3f(-sc / 2, 0f, -sc / 2),
                                tf.getLeftRotation(),
                                new Vector3f(sc, 3.5f * (1.0f - (float) eased * 0.3f), sc),
                                tf.getRightRotation()
                        ));
                    }

                    int pts = 44;
                    for (int i = 0; i < pts; i++) {
                        if (rng.nextInt(2) != 0) continue;
                        double a = (2 * Math.PI * i) / pts + t * 0.36;
                        w.spawnParticle(Particle.PORTAL,
                                portalCenter.getX() + collapseR * Math.cos(a),
                                portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.09,
                                portalCenter.getZ() + collapseR * Math.sin(a),
                                1, 0, 0, 0, 0);
                        if (i % 5 == 0)
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    portalCenter.getX() + collapseR * 0.8 * Math.cos(a),
                                    portalCenter.getY() + (rng.nextDouble() - 0.5) * 0.25,
                                    portalCenter.getZ() + collapseR * 0.8 * Math.sin(a),
                                    1, 0, 0, 0, 0.03);
                    }

                    if (t == PHASE2_END + 20) {
                        setScale(rewardDisplay, 0.05f);
                        rewardDisplay.teleport(portalCenter.clone());
                        for (int ring = 0; ring < 28; ring++) {
                            double a = (2 * Math.PI * ring) / 28;
                            w.spawnParticle(Particle.END_ROD,
                                    portalCenter.getX() + 0.2 * Math.cos(a),
                                    portalCenter.getY(),
                                    portalCenter.getZ() + 0.2 * Math.sin(a),
                                    2, 0.02, 0.12, 0.02, 0.1);
                        }
                        w.playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
                        w.playSound(base, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.8f, 0.6f);
                    }

                    if (t > PHASE2_END + 20) {
                        double sub = (double)(t - PHASE2_END - 20) / (PHASE3_END - PHASE2_END - 20);
                        float sc = Math.min(0.95f, 0.05f + (float) sub * 0.9f);
                        rewardYaw += 13f - (float) sub * 6f;
                        rewardDisplay.teleport(portalCenter.clone());
                        rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                                new Vector3f(0, 0, 0),
                                new AxisAngle4f(0, 0, 1, 0),
                                new Vector3f(sc, sc, sc),
                                new AxisAngle4f((float) Math.toRadians(rewardYaw), 0, 1, 0)
                        ));
                        if (t % 2 == 0)
                            w.spawnParticle(Particle.END_ROD, portalCenter, 2, 0.1, 0.08, 0.1, 0.04);
                        if (t % 5 == 0)
                            w.spawnParticle(Particle.PORTAL, portalCenter, 3, 0.15, 0.1, 0.15, 0.08);
                    }

                    if (t % 4 == 0) w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.45f - (float) eased * 0.45f);
                }

                if (t > PHASE3_END && t <= PHASE4_END) {
                    double progress = (double)(t - PHASE3_END) / (PHASE4_END - PHASE3_END);
                    double eased = 1.0 - Math.pow(1.0 - progress, 2);

                    for (BlockDisplay pillar : pillars) safeRemove(pillar);

                    double riseY = 1.7 + eased * 1.8;
                    Location rewardLoc = base.clone().add(0, riseY, 0);
                    rewardDisplay.teleport(rewardLoc);

                    rewardYaw += 3.0f + (float)((1.0 - eased) * 6.0f);
                    if (rewardYaw >= 360f) rewardYaw -= 360f;
                    rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(1.0f, 1.0f, 1.0f),
                            new AxisAngle4f((float) Math.toRadians(rewardYaw), 0, 1, 0)
                    ));

                    double auraR = 0.5 + 0.18 * Math.sin(t * 0.2);
                    int auraCount = 24;
                    for (int i = 0; i < auraCount; i++) {
                        double a = (2 * Math.PI * i) / auraCount + t * 0.14;
                        double py = rewardLoc.getY() + 0.22 * Math.sin(t * 0.19 + i);
                        w.spawnParticle(Particle.PORTAL,
                                rewardLoc.getX() + auraR * Math.cos(a), py,
                                rewardLoc.getZ() + auraR * Math.sin(a),
                                1, 0, 0, 0, 0);
                    }
                    double outerR = auraR * 1.6;
                    for (int i = 0; i < 16; i++) {
                        double a = (2 * Math.PI * i) / 16 - t * 0.1;
                        w.spawnParticle(Particle.REVERSE_PORTAL,
                                rewardLoc.getX() + outerR * Math.cos(a),
                                rewardLoc.getY() + 0.1 * Math.sin(t * 0.22 + i),
                                rewardLoc.getZ() + outerR * Math.sin(a),
                                1, 0, 0, 0, 0);
                    }

                    if (t % 3 == 0) w.spawnParticle(Particle.END_ROD, rewardLoc, 2, 0.14, 0.1, 0.14, 0.04);

                    if (t == PHASE3_END + 14) {
                        setLabel(label, rewardVisual);
                        w.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.95f);
                        w.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        w.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);

                        for (int ring = 0; ring < 40; ring++) {
                            double a = (2 * Math.PI * ring) / 40;
                            w.spawnParticle(Particle.END_ROD,
                                    rewardLoc.getX() + 0.9 * Math.cos(a), rewardLoc.getY(),
                                    rewardLoc.getZ() + 0.9 * Math.sin(a),
                                    2, 0, 0.06, 0, 0.07);
                        }
                        for (int i = 0; i < 70; i++) {
                            double a = rng.nextDouble() * 2 * Math.PI;
                            double r = rng.nextDouble() * 0.8;
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    rewardLoc.getX() + r * Math.cos(a),
                                    rewardLoc.getY() + (rng.nextDouble() - 0.5) * 0.5,
                                    rewardLoc.getZ() + r * Math.sin(a),
                                    1, 0.02, 0.1, 0.02, 0.1);
                        }
                        for (int i = 0; i < 50; i++) {
                            double a = rng.nextDouble() * 2 * Math.PI;
                            double r = rng.nextDouble() * 0.6;
                            w.spawnParticle(Particle.PORTAL,
                                    rewardLoc.getX() + r * Math.cos(a),
                                    rewardLoc.getY() + (rng.nextDouble() - 0.5) * 0.4,
                                    rewardLoc.getZ() + r * Math.sin(a),
                                    1, 0, 0.06, 0, 0.12);
                        }
                    }

                    if (t % 6 == 0) w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.3f, 1.9f);
                }

                if (t >= PHASE4_END) {
                    Location finalLoc = base.clone().add(0, 3.5, 0);

                    for (int i = 0; i < 80; i++) {
                        double a = rng.nextDouble() * 2 * Math.PI;
                        double r = rng.nextDouble() * 0.9;
                        w.spawnParticle(Particle.PORTAL,
                                finalLoc.getX() + r * Math.cos(a),
                                finalLoc.getY() + (rng.nextDouble() - 0.5) * 0.6,
                                finalLoc.getZ() + r * Math.sin(a),
                                1, 0, 0, 0, 0.14);
                    }
                    for (int i = 0; i < 55; i++) {
                        double a = rng.nextDouble() * 2 * Math.PI;
                        double r = rng.nextDouble() * 0.7;
                        w.spawnParticle(Particle.REVERSE_PORTAL,
                                finalLoc.getX() + r * Math.cos(a),
                                finalLoc.getY() + (rng.nextDouble() - 0.5) * 0.4,
                                finalLoc.getZ() + r * Math.sin(a),
                                1, 0, 0.06, 0, 0.08);
                    }
                    for (int ring = 0; ring < 32; ring++) {
                        double a = (2 * Math.PI * ring) / 32;
                        w.spawnParticle(Particle.END_ROD,
                                finalLoc.getX() + 0.8 * Math.cos(a), finalLoc.getY(),
                                finalLoc.getZ() + 0.8 * Math.sin(a),
                                2, 0, 0.05, 0, 0.07);
                    }

                    w.playSound(base, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.4f, 1.7f);
                    w.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    cleanup();
                }
            }

            private void cleanup() {
                for (ItemDisplay fd : fakeItems) { safeRemove(fd); untrack(fd); }
                fakeItems.clear();
                for (BlockDisplay p : pillars) { safeRemove(p); untrack(p); }
                pillars.clear();
                safeRemove(rewardDisplay); untrack(rewardDisplay);
                safeRemove(label); untrack(label);
                if (taskHolder[0] != null) untrack(taskHolder[0]);
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private static void setScale(ItemDisplay id, float scale) {
        org.bukkit.util.Transformation tf = id.getTransformation();
        id.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(), tf.getLeftRotation(),
                new Vector3f(scale, scale, scale), tf.getRightRotation()
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