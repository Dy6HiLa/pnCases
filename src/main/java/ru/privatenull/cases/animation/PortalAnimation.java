package ru.privatenull.cases.animation;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PortalAnimation extends CaseAnimation {

    private static final int EXPLOSION_END = 50;
    private static final int VORTEX_END = 106;
    private static final int SUCTION_END = 154;
    private static final int COLLAPSE_END = 182;
    private static final int REVEAL_END = 262;

    private static final int ITEM_COUNT = 10;

    public PortalAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World w = base.getWorld();
        if (w == null) { onFinish.run(); return; }

        Random rng = new Random();
        ItemStack rewardVisual = buildRewardVisual(reward, def);

        Location centre = base.clone().add(0, 2.0, 0);

        Material[] mats = {
                Material.EMERALD, Material.GOLD_INGOT, Material.AMETHYST_SHARD,
                Material.LAPIS_LAZULI, Material.QUARTZ, Material.PRISMARINE_CRYSTALS,
                Material.NETHERITE_INGOT, Material.DIAMOND, Material.BLAZE_POWDER,
                Material.NETHER_STAR
        };

        double[] px = new double[ITEM_COUNT];
        double[] py = new double[ITEM_COUNT];
        double[] pz = new double[ITEM_COUNT];
        double[] vx = new double[ITEM_COUNT];
        double[] vy = new double[ITEM_COUNT];
        double[] vz = new double[ITEM_COUNT];
        boolean[] alive = new boolean[ITEM_COUNT];

        List<ItemDisplay> items = new ArrayList<>();

        for (int i = 0; i < ITEM_COUNT; i++) {
            ItemDisplay id = (ItemDisplay) w.spawnEntity(base.clone().add(0, 1.0, 0), EntityType.ITEM_DISPLAY);
            id.setItemStack(new ItemStack(mats[i]));
            id.setBillboard(Display.Billboard.VERTICAL);
            setItemScale(id, 0f);
            items.add(id);
            track(id);
            alive[i] = false;

            px[i] = base.getX() + 0.5;
            py[i] = base.getY() + 1.0;
            pz[i] = base.getZ() + 0.5;
        }

        BlockDisplay singularity = (BlockDisplay) w.spawnEntity(centre.clone(), EntityType.BLOCK_DISPLAY);
        singularity.setBlock(Material.BLACK_CONCRETE.createBlockData());
        setSingularityScale(singularity, 0f);
        track(singularity);

        ItemDisplay rewardDisplay = (ItemDisplay) w.spawnEntity(centre.clone(), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.FIXED);
        setItemScale(rewardDisplay, 0f);
        track(rewardDisplay);

        TextDisplay label = (TextDisplay) w.spawnEntity(base.clone().add(0, 4.8, 0), EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(true);
        label.setDefaultBackground(false);
        label.setText("");
        track(label);

        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = new BukkitRunnable() {
            int t = 0;
            float rewardYaw = 0f;

            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(); return; }
                t++;
                if (t <= EXPLOSION_END) {

                    if (t == 1) {
                        for (int i = 0; i < ITEM_COUNT; i++) {
                            alive[i] = true;
                            double angle  = (2 * Math.PI * i) / ITEM_COUNT + rng.nextDouble() * 0.5;
                            double hSpeed = 0.08 + rng.nextDouble() * 0.13;
                            vx[i] = Math.cos(angle) * hSpeed;
                            vy[i] = 0.10 + rng.nextDouble() * 0.18;
                            vz[i] = Math.sin(angle) * hSpeed;
                            setItemScale(items.get(i), 0.35f);
                        }

                        Location boom = base.clone().add(0.5, 1.0, 0.5);
                        w.spawnParticle(Particle.EXPLOSION,    boom, 2, 0.1, 0.1, 0.1, 0);
                        w.spawnParticle(Particle.SMOKE,        boom, 55, 0.45, 0.3, 0.45, 0.09);
                        w.spawnParticle(Particle.LAVA,         boom, 25, 0.3, 0.2, 0.3, 0.0);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, boom, 40, 0.4, 0.2, 0.4, 0.12);
                        for (int ring = 0; ring < 20; ring++) {
                            double a = (2 * Math.PI * ring) / 20;
                            w.spawnParticle(Particle.SMOKE,
                                    base.getX() + 0.5 + 0.7 * Math.cos(a), base.getY() + 0.5,
                                    base.getZ() + 0.5 + 0.7 * Math.sin(a),
                                    2, 0, 0.05, 0, 0.07);
                        }
                        w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.34f, 0.75f);
                        w.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.26f, 0.5f);
                        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.24f, 0.8f);
                    }

                    double floor = base.getY() + 0.25;
                    for (int i = 0; i < ITEM_COUNT; i++) {
                        if (!alive[i]) continue;
                        vy[i] -= 0.018;
                        px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];

                        if (py[i] < floor) {
                            py[i] = floor;
                            vy[i] = -vy[i] * 0.30;
                            vx[i] *= 0.80; vz[i] *= 0.80;
                            if (Math.abs(vy[i]) < 0.015) vy[i] = 0;
                            w.spawnParticle(Particle.BLOCK,
                                    new Location(w, px[i], floor, pz[i]),
                                    5, 0.1, 0, 0.1, 0.03, Material.GRAVEL.createBlockData());
                        }

                        ItemDisplay id = items.get(i);
                        if (!id.isDead()) {
                            id.teleport(new Location(w, px[i], py[i], pz[i]));
                            id.setRotation(id.getLocation().getYaw() + 5f, 0f);
                        }
                    }

                    if (t % 4 == 0)
                        w.spawnParticle(Particle.SMOKE, base.clone().add(0.5, 0.8, 0.5), 3, 0.5, 0.1, 0.5, 0.02);
                }

                if (t > EXPLOSION_END && t <= VORTEX_END) {
                    double progress = (double)(t - EXPLOSION_END) / (VORTEX_END - EXPLOSION_END);
                    double eased    = progress * progress;

                    setSingularityScale(singularity, (float)(eased * 0.42));

                    double ringR = 1.45 - eased * 0.55;
                    int pts = (int)(progress * 30 + 10);
                    for (int i = 0; i < pts; i++) {
                        if (rng.nextInt(2) == 0) continue;
                        double a = (2 * Math.PI * i) / pts + t * 0.28;
                        w.spawnParticle(Particle.PORTAL,
                                centre.getX() + ringR * Math.cos(a),
                                centre.getY() + (rng.nextDouble() - 0.5) * 0.12,
                                centre.getZ() + ringR * Math.sin(a),
                                1, 0, 0, 0, 0);
                        if (i % 3 == 0)
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    centre.getX() + ringR * 0.65 * Math.cos(a + 0.22),
                                    centre.getY() + (rng.nextDouble() - 0.5) * 0.18,
                                    centre.getZ() + ringR * 0.65 * Math.sin(a + 0.22),
                                    1, 0, 0, 0, 0);
                    }
                    if (t % 2 == 0)
                        w.spawnParticle(Particle.SMOKE, centre, 3, 0.12, 0.08, 0.12, 0.015);

                    double earthGrav = 0.018 * (1.0 - eased * 0.92);
                    double pullStr   = 0.002 + eased * 0.026;

                    double floor = base.getY() + 0.25;
                    for (int i = 0; i < ITEM_COUNT; i++) {
                        if (!alive[i]) continue;

                        vy[i] -= earthGrav;
                        if (py[i] < floor) { py[i] = floor; vy[i] = Math.abs(vy[i]) * 0.22; }

                        double dx = centre.getX() - px[i];
                        double dy = centre.getY() - py[i];
                        double dz = centre.getZ() - pz[i];
                        double dist = Math.max(0.01, Math.sqrt(dx*dx + dy*dy + dz*dz));

                        double pull = pullStr / Math.max(0.30, dist);
                        vx[i] += dx/dist * pull;
                        vy[i] += dy/dist * pull;
                        vz[i] += dz/dist * pull;

                        double tang = 0.009 * (1.0 - eased);
                        vx[i] += -dz/dist * tang;
                        vz[i] +=  dx/dist * tang;

                        double speed = Math.sqrt(vx[i]*vx[i] + vy[i]*vy[i] + vz[i]*vz[i]);
                        if (speed > 0.40) { double s = 0.40/speed; vx[i]*=s; vy[i]*=s; vz[i]*=s; }

                        px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];

                        ItemDisplay id = items.get(i);
                        if (!id.isDead()) {
                            id.teleport(new Location(w, px[i], py[i], pz[i]));
                            id.setRotation(id.getLocation().getYaw() + 8f, 0f);
                        }
                    }

                    if (t == EXPLOSION_END + 1) {
                        w.playSound(base, Sound.ENTITY_WITHER_AMBIENT, 0.18f, 0.4f);
                        w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.24f, 0.3f);
                    }
                    if (t % 10 == 0)
                        w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.16f, 0.35f + (float)eased * 0.35f);
                }

                if (t > VORTEX_END && t <= SUCTION_END) {
                    double progress = (double)(t - VORTEX_END) / (SUCTION_END - VORTEX_END);


                    setSingularityScale(singularity, 0.42f + (float)(0.08 * Math.sin(t * 0.45)));

                    double vortexR = 1.1 - progress * 0.55;
                    for (int i = 0; i < 48; i++) {
                        if (rng.nextInt(2) != 0) continue;
                        double a = (2 * Math.PI * i) / 48 + t * 0.44;
                        w.spawnParticle(Particle.PORTAL,
                                centre.getX() + vortexR * Math.cos(a),
                                centre.getY() + (rng.nextDouble() - 0.5) * 0.10,
                                centre.getZ() + vortexR * Math.sin(a),
                                1, 0, 0, 0, 0.03);
                    }
                    if (t % 2 == 0)
                        w.spawnParticle(Particle.SMOKE, centre, 4, 0.15, 0.10, 0.15, 0.02);

                    for (int i = 0; i < ITEM_COUNT; i++) {
                        if (!alive[i]) continue;

                        double dx   = centre.getX() - px[i];
                        double dy   = centre.getY() - py[i];
                        double dz   = centre.getZ() - pz[i];
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

                        if (dist < 0.30) {
                            alive[i] = false;
                            safeRemove(items.get(i));
                            w.spawnParticle(Particle.PORTAL,         centre, 18, 0.12, 0.10, 0.12, 0.18);
                            w.spawnParticle(Particle.REVERSE_PORTAL, centre, 10, 0.10, 0.08, 0.10, 0.12);
                            w.spawnParticle(Particle.SMOKE,          centre,  6, 0.08, 0.06, 0.08, 0.03);
                            if (rng.nextInt(3) == 0)
                                w.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT,
                                        0.18f, 1.4f + rng.nextFloat() * 0.6f);
                            continue;
                        }

                        dist = Math.max(0.01, dist);
                        double pull = 0.05 / Math.max(0.15, dist);
                        vx[i] += dx/dist * pull;
                        vy[i] += dy/dist * pull;
                        vz[i] += dz/dist * pull;

                        double tang = 0.016 / Math.max(0.20, dist);
                        vx[i] += -dz/dist * tang;
                        vz[i] +=  dx/dist * tang;

                        double speed = Math.sqrt(vx[i]*vx[i] + vy[i]*vy[i] + vz[i]*vz[i]);
                        double maxSp = 0.22 + progress * 0.38;
                        if (speed > maxSp) { double s = maxSp/speed; vx[i]*=s; vy[i]*=s; vz[i]*=s; }

                        px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];

                        ItemDisplay id = items.get(i);
                        if (!id.isDead()) {
                            float sc = Math.min(0.35f, (float)(dist * 0.18f));
                            setItemScale(id, sc);
                            id.teleport(new Location(w, px[i], py[i], pz[i]));
                            id.setRotation(id.getLocation().getYaw() + 12f, 0f);
                        }
                    }

                    if (t % 6 == 0)
                        w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.18f, 0.6f + (float)progress * 0.55f);
                }

                if (t > SUCTION_END && t <= COLLAPSE_END) {
                    double progress = (double)(t - SUCTION_END) / (COLLAPSE_END - SUCTION_END);

                    if (t == SUCTION_END + 1) {
                        for (int i = 0; i < ITEM_COUNT; i++)
                            if (alive[i]) { safeRemove(items.get(i)); alive[i] = false; }

                        w.spawnParticle(Particle.EXPLOSION,      centre, 1, 0, 0, 0, 0);
                        w.spawnParticle(Particle.PORTAL,         centre, 90, 0.6, 0.4, 0.6, 0.18);
                        w.spawnParticle(Particle.REVERSE_PORTAL, centre, 65, 0.5, 0.3, 0.5, 0.13);
                        w.spawnParticle(Particle.END_ROD,        centre, 55, 0.5, 0.4, 0.5, 0.09);
                        w.spawnParticle(Particle.SMOKE,          centre, 45, 0.5, 0.3, 0.5, 0.07);
                        for (int ring = 0; ring < 36; ring++) {
                            double a = (2 * Math.PI * ring) / 36;
                            w.spawnParticle(Particle.END_ROD,
                                    centre.getX() + 0.9 * Math.cos(a), centre.getY(),
                                    centre.getZ() + 0.9 * Math.sin(a),
                                    3, 0, 0.07, 0, 0.10);
                        }
                        w.playSound(base, Sound.ENTITY_WITHER_DEATH, 0.16f, 1.9f);
                        w.playSound(base, Sound.BLOCK_PORTAL_TRAVEL, 0.24f, 1.6f);
                        w.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.14f, 1.8f);
                        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.20f, 0.6f);
                    }

                    float sc = Math.max(0f, 0.42f - (float)progress * 0.42f);
                    setSingularityScale(singularity, sc);

                    if (t > SUCTION_END + 8) {
                        double sub = (t - SUCTION_END - 8.0) / (COLLAPSE_END - SUCTION_END - 8.0);
                        setItemScale(rewardDisplay, (float)(sub * 0.9));
                        rewardDisplay.teleport(centre.clone());
                    }

                    if (t % 2 == 0) {
                        double r = 0.5 * (1.0 - progress);
                        for (int i = 0; i < 8; i++) {
                            double a = (2 * Math.PI * i) / 8 + t * 0.5;
                            w.spawnParticle(Particle.PORTAL,
                                    centre.getX() + r * Math.cos(a), centre.getY(),
                                    centre.getZ() + r * Math.sin(a),
                                    1, 0, 0, 0, 0.06);
                        }
                    }
                }

                if (t > COLLAPSE_END && t <= REVEAL_END) {
                    double progress = (double)(t - COLLAPSE_END) / (REVEAL_END - COLLAPSE_END);
                    double eased    = 1.0 - Math.pow(1.0 - progress, 2);

                    if (t == COLLAPSE_END + 1) {
                        safeRemove(singularity);
                        w.spawnParticle(Particle.END_ROD, centre, 65, 0.4, 0.35, 0.4, 0.09);
                        for (int ring = 0; ring < 40; ring++) {
                            double a = (2 * Math.PI * ring) / 40;
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    centre.getX() + 0.8 * Math.cos(a), centre.getY(),
                                    centre.getZ() + 0.8 * Math.sin(a),
                                    2, 0, 0.05, 0, 0.08);
                        }
                        w.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.32f, 1.5f);
                        w.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.30f, 1.2f);
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.1f);
                    }

                    Location rewardLoc = base.clone().add(0, 2.0 + eased * 1.8, 0);
                    rewardDisplay.teleport(rewardLoc);
                    rewardDisplay.setBillboard(Display.Billboard.FIXED);

                    rewardYaw += 4.0f + (float)((1.0 - eased) * 5.5f);
                    if (rewardYaw >= 360f) rewardYaw -= 360f;
                    rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(1.0f, 1.0f, 1.0f),
                            new AxisAngle4f((float) Math.toRadians(rewardYaw), 0, 1, 0)
                    ));

                    double auraR = 0.52 + 0.13 * Math.sin(t * 0.21);
                    for (int i = 0; i < 22; i++) {
                        double a  = (2 * Math.PI * i) / 22 + t * 0.11;
                        double py2 = rewardLoc.getY() + 0.18 * Math.sin(t * 0.17 + i);
                        w.spawnParticle(Particle.PORTAL,
                                rewardLoc.getX() + auraR * Math.cos(a), py2,
                                rewardLoc.getZ() + auraR * Math.sin(a),
                                1, 0, 0, 0, 0);
                    }
                    double outerR = auraR * 1.6;
                    for (int i = 0; i < 14; i++) {
                        double a = (2 * Math.PI * i) / 14 - t * 0.09;
                        w.spawnParticle(Particle.REVERSE_PORTAL,
                                rewardLoc.getX() + outerR * Math.cos(a),
                                rewardLoc.getY() + 0.1 * Math.sin(t * 0.21 + i),
                                rewardLoc.getZ() + outerR * Math.sin(a),
                                1, 0, 0, 0, 0);
                    }
                    if (t % 3 == 0)
                        w.spawnParticle(Particle.END_ROD, rewardLoc, 2, 0.15, 0.10, 0.15, 0.04);

                    if (t == COLLAPSE_END + 15)
                        setLabel(label, rewardVisual);

                    if (t % 8 == 0)
                        w.playSound(base, Sound.BLOCK_PORTAL_AMBIENT, 0.10f, 1.8f);
                }

                if (t >= REVEAL_END) {
                    Location finalLoc = base.clone().add(0, 3.8, 0);
                    w.spawnParticle(Particle.PORTAL,         finalLoc, 70, 0.45, 0.30, 0.45, 0.12);
                    w.spawnParticle(Particle.END_ROD,        finalLoc, 45, 0.35, 0.28, 0.35, 0.08);
                    w.spawnParticle(Particle.REVERSE_PORTAL, finalLoc, 45, 0.40, 0.25, 0.40, 0.09);
                    w.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.0f);
                    w.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.30f, 1.5f);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.32f, 1.5f);
                    cleanup();
                }
            }

            private void cleanup() {
                for (int i = 0; i < ITEM_COUNT; i++) if (alive[i]) safeRemove(items.get(i));
                for (ItemDisplay id : items) untrack(id);
                safeRemove(singularity);   untrack(singularity);
                safeRemove(rewardDisplay); untrack(rewardDisplay);
                safeRemove(label);         untrack(label);
                if (holder[0] != null) untrack(holder[0]);
                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(holder[0]);
    }

    private static void setItemScale(ItemDisplay id, float scale) {
        org.bukkit.util.Transformation tf = id.getTransformation();
        id.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(), tf.getLeftRotation(),
                new Vector3f(scale, scale, scale), tf.getRightRotation()
        ));
    }

    private static void setSingularityScale(BlockDisplay bd, float scale) {
        float half = scale / 2f;
        org.bukkit.util.Transformation tf = bd.getTransformation();
        bd.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(-half, -half, -half),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
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
        return reward.visualItem() != null
                ? reward.visualItem().clone()
                : def.animationItems().get(0).clone();
    }

    private static void safeRemove(Entity e) {
        try { if (e != null && !e.isDead()) e.remove(); } catch (Exception ignored) {}
    }
}
