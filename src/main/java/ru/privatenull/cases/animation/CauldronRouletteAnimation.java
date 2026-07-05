package ru.privatenull.cases.animation;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
import java.util.Locale;

public class CauldronRouletteAnimation extends CaseAnimation {

    private static final int SHARD_COUNT = 8;
    private static final int GHOST_COUNT = 8;

    private static final int FORM_END = 44;
    private static final int CHARGE_END = 106;
    private static final int ORBIT_END = 194;
    private static final int IMPLODE_END = 242;
    private static final int REVEAL_END = 302;

    private static final Material[] SHARD_MATERIALS = {
            Material.CRYING_OBSIDIAN,
            Material.OBSIDIAN,
            Material.AMETHYST_BLOCK,
            Material.SCULK,
            Material.CRYING_OBSIDIAN,
            Material.OBSIDIAN,
            Material.AMETHYST_BLOCK,
            Material.SCULK
    };

    public CauldronRouletteAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        ItemStack rewardVisual = buildRewardVisual(reward, def);
        List<ItemStack> ghostVisuals = buildGhostVisuals(def, rewardVisual);

        List<BlockDisplay> shards = new ArrayList<>();
        for (int i = 0; i < SHARD_COUNT; i++) {
            BlockDisplay shard = (BlockDisplay) world.spawnEntity(base.clone().add(0, -0.4, 0), EntityType.BLOCK_DISPLAY);
            shard.setBlock(SHARD_MATERIALS[i % SHARD_MATERIALS.length].createBlockData());
            setBlockScale(shard, 0f);
            shards.add(shard);
            track(shard);
        }

        BlockDisplay core = (BlockDisplay) world.spawnEntity(base.clone().add(0, 1.45, 0), EntityType.BLOCK_DISPLAY);
        core.setBlock(Material.BLACK_CONCRETE.createBlockData());
        setBlockScale(core, 0f);
        track(core);

        BlockDisplay halo = (BlockDisplay) world.spawnEntity(base.clone().add(0, 1.45, 0), EntityType.BLOCK_DISPLAY);
        halo.setBlock(Material.PURPLE_STAINED_GLASS.createBlockData());
        setBlockScale(halo, 0f);
        track(halo);

        List<ItemDisplay> ghosts = new ArrayList<>();
        for (int i = 0; i < GHOST_COUNT; i++) {
            ItemDisplay ghost = (ItemDisplay) world.spawnEntity(base.clone().add(0, 1.6, 0), EntityType.ITEM_DISPLAY);
            ghost.setItemStack(ghostVisuals.get(i % ghostVisuals.size()));
            ghost.setBillboard(Display.Billboard.VERTICAL);
            setItemScale(ghost, 0f);
            ghosts.add(ghost);
            track(ghost);
        }

        ItemDisplay rewardDisplay = (ItemDisplay) world.spawnEntity(base.clone().add(0, 1.55, 0), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        setItemScale(rewardDisplay, 0f);
        track(rewardDisplay);

        TextDisplay label = (TextDisplay) world.spawnEntity(base.clone().add(0, 3.35, 0), EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(true);
        label.setDefaultBackground(false);
        label.setText("");
        track(label);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            int tick = 0;
            float rewardYaw = 0f;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup();
                    return;
                }

                tick++;
                rewardYaw = (rewardYaw + 6.5f) % 360f;

                if (tick <= FORM_END) {
                    playForm();
                    return;
                }

                if (tick <= CHARGE_END) {
                    playCharge();
                    return;
                }

                if (tick <= ORBIT_END) {
                    playOrbit();
                    return;
                }

                if (tick <= IMPLODE_END) {
                    playImplode();
                    return;
                }

                if (tick <= REVEAL_END) {
                    playReveal();
                    return;
                }

                Location fin = base.clone().add(0, 2.65, 0);
                spawnFinalBurst(world, fin);
                world.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.05f);
                world.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.55f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.26f, 1.55f);
                cleanup();
            }

            private void playForm() {
                double progress = clamp(tick / (double) FORM_END);
                double eased = easeOutBack(progress);

                for (int i = 0; i < shards.size(); i++) {
                    double angle = angle(i, SHARD_COUNT, tick * 0.025);
                    double radius = 0.25 + eased * 1.55;
                    double y = 0.12 + eased * 1.18 + Math.sin(tick * 0.12 + i) * 0.05;
                    Location loc = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);

                    shards.get(i).teleport(loc);
                    shards.get(i).setRotation((float) Math.toDegrees(angle) + tick * 1.8f, (float) (10 + Math.sin(tick * 0.06 + i) * 5));
                    setBlockScale(shards.get(i), (float) (0.22 + eased * 0.42));
                }

                double pulse = 0.18 + eased * 0.58 + Math.sin(tick * 0.25) * 0.04;
                core.teleport(base.clone().add(0, 1.20 + eased * 0.28, 0));
                halo.teleport(base.clone().add(0, 1.20 + eased * 0.28, 0));
                setBlockScale(core, (float) pulse);
                setBlockScale(halo, (float) (pulse * 1.55));

                spawnGroundSigil(world, base.clone().add(0, 0.06, 0), 0.55 + eased * 1.10, tick * 0.08);
                world.spawnParticle(Particle.REVERSE_PORTAL, base.clone().add(0, 1.25, 0), 7, 0.45, 0.30, 0.45, 0.04);

                if (tick == 1) {
                    world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.24f, 1.8f);
                    world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.28f, 0.75f);
                }
                if (tick % 12 == 0) {
                    world.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.18f, 1.0f + (float) progress * 0.45f);
                }
            }

            private void playCharge() {
                double progress = clamp((tick - FORM_END) / (double) (CHARGE_END - FORM_END));
                double speed = 0.065 + progress * 0.13;

                for (int i = 0; i < shards.size(); i++) {
                    double angle = angle(i, SHARD_COUNT, tick * speed);
                    double radius = 1.62 - progress * 0.18 + Math.sin(tick * 0.15 + i) * 0.05;
                    double y = 1.22 + Math.sin(tick * 0.14 + i * 0.65) * 0.30;
                    Location loc = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);

                    shards.get(i).teleport(loc);
                    shards.get(i).setRotation((float) Math.toDegrees(angle) + tick * 3.2f, (float) (24 + Math.sin(tick * 0.09 + i) * 8));
                    setBlockScale(shards.get(i), (float) (0.62 + Math.sin(tick * 0.20 + i) * 0.05));
                }

                double pulse = 0.72 + Math.sin(tick * 0.35) * 0.10 + progress * 0.20;
                core.teleport(base.clone().add(0, 1.48 + Math.sin(tick * 0.14) * 0.04, 0));
                halo.teleport(base.clone().add(0, 1.48 + Math.sin(tick * 0.14) * 0.04, 0));
                setBlockScale(core, (float) pulse);
                setBlockScale(halo, (float) (pulse * 1.75));

                spawnHelix(world, base.clone().add(0, 0.25, 0), tick, 1.2, 2.4, Particle.END_ROD);
                spawnGroundSigil(world, base.clone().add(0, 0.06, 0), 1.55, tick * 0.14);
                world.spawnParticle(Particle.PORTAL, base.clone().add(0, 1.55, 0), 12, 0.45, 0.35, 0.45, 0.22);
                spawnDragonBreath(world, base.clone().add(0, 1.45, 0), 4, 0.25, 0.18, 0.25, 0.01);

                if (tick > CHARGE_END - 22) {
                    double appear = clamp((tick - (CHARGE_END - 22)) / 22.0);
                    updateGhostOrbit(ghosts, appear, 2.05, 1.85, tick * 0.11, true);
                }

                if (tick % 8 == 0) {
                    world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.18f, 0.9f + (float) progress * 0.75f);
                }
            }

            private void playOrbit() {
                double progress = clamp((tick - CHARGE_END) / (double) (ORBIT_END - CHARGE_END));
                double eased = 1.0 - Math.pow(1.0 - progress, 2.6);
                double radius = 2.05 - eased * 0.95;
                double y = 1.85 + Math.sin(tick * 0.12) * 0.08;
                double rotation = tick * (0.15 + progress * 0.34);

                updateGhostOrbit(ghosts, 1.0, radius, y, rotation, false);

                for (int i = 0; i < shards.size(); i++) {
                    double angle = angle(i, SHARD_COUNT, -tick * (0.11 + progress * 0.18));
                    double shardRadius = 1.35 - eased * 0.35;
                    double shardY = 1.18 + Math.sin(tick * 0.20 + i) * 0.45;
                    Location loc = base.clone().add(Math.cos(angle) * shardRadius, shardY, Math.sin(angle) * shardRadius);

                    shards.get(i).teleport(loc);
                    shards.get(i).setRotation((float) Math.toDegrees(angle) - tick * 4.2f, (float) (34 + Math.sin(tick * 0.13 + i) * 10));
                }

                double corePulse = 0.88 + Math.sin(tick * 0.48) * 0.16 + progress * 0.28;
                setBlockScale(core, (float) corePulse);
                setBlockScale(halo, (float) (corePulse * 1.9));

                spawnHelix(world, base.clone().add(0, 0.25, 0), tick, radius * 0.55, 2.65, Particle.ELECTRIC_SPARK);
                world.spawnParticle(Particle.PORTAL, base.clone().add(0, 1.55, 0), 18, 0.35, 0.35, 0.35, 0.30);

                if (tick % 6 == 0) {
                    float pitch = 1.75f - (float) progress * 0.55f;
                    world.playSound(base, Sound.BLOCK_NOTE_BLOCK_PLING, 0.13f, Math.max(0.85f, pitch));
                }

                if (tick == ORBIT_END) {
                    world.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.16f, 1.55f);
                    world.playSound(base, Sound.BLOCK_END_PORTAL_SPAWN, 0.24f, 1.35f);
                }
            }

            private void playImplode() {
                double progress = clamp((tick - ORBIT_END) / (double) (IMPLODE_END - ORBIT_END));
                double pull = easeInOut(progress);

                for (int i = 0; i < ghosts.size(); i++) {
                    ItemDisplay ghost = ghosts.get(i);
                    double angle = angle(i, GHOST_COUNT, tick * 0.42);
                    double radius = (1.05 * (1.0 - pull)) + 0.06;
                    double y = 1.85 - pull * 0.34 + Math.sin(tick * 0.20 + i) * 0.04;
                    ghost.teleport(base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
                    ghost.setRotation((float) Math.toDegrees(angle) + tick * 14f, 0f);
                    setItemScale(ghost, (float) (0.42 * (1.0 - pull)));
                }

                for (int i = 0; i < shards.size(); i++) {
                    double angle = angle(i, SHARD_COUNT, tick * 0.22);
                    double radius = 1.0 + progress * 0.25;
                    double y = 1.15 + Math.sin(tick * 0.40 + i) * 0.20;
                    Location loc = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);

                    shards.get(i).teleport(loc);
                    setBlockScale(shards.get(i), (float) (0.62 + progress * 0.16));
                }

                double shake = progress * 0.035;
                Location coreLoc = base.clone().add((Math.random() - 0.5) * shake, 1.48 + (Math.random() - 0.5) * shake, (Math.random() - 0.5) * shake);
                core.teleport(coreLoc);
                halo.teleport(coreLoc);
                setBlockScale(core, (float) (1.10 + progress * 0.70));
                setBlockScale(halo, (float) (2.10 - progress * 0.65));

                world.spawnParticle(Particle.REVERSE_PORTAL, base.clone().add(0, 1.55, 0), 28, 0.65, 0.45, 0.65, 0.18);
                spawnDragonBreath(world, base.clone().add(0, 1.55, 0), 12, 0.38, 0.26, 0.38, 0.02);

                if (tick % 5 == 0) {
                    world.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.12f, 0.75f + (float) progress * 0.45f);
                }
            }

            private void playReveal() {
                int localTick = tick - IMPLODE_END;
                double progress = clamp(localTick / (double) (REVEAL_END - IMPLODE_END));
                double eased = easeOutBack(progress);

                for (ItemDisplay ghost : ghosts) {
                    setItemScale(ghost, 0f);
                }

                if (localTick == 1) {
                    world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1.55, 0), 3, 0.30, 0.15, 0.30, 0.0);
                    world.spawnParticle(Particle.FIREWORK, base.clone().add(0, 1.55, 0), 35, 0.40, 0.25, 0.40, 0.06);
                    world.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.24f, 1.65f);
                    world.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.22f, 1.25f);
                }

                double y = 1.25 + eased * 1.45 + Math.sin(localTick * 0.18) * 0.05;
                Location rewardLoc = base.clone().add(0, y, 0);
                rewardDisplay.teleport(rewardLoc);
                rewardDisplay.setRotation(rewardYaw, 0f);
                setItemScale(rewardDisplay, (float) (0.12 + eased * 0.95));

                double shardFade = Math.max(0.0, 1.0 - progress);
                for (int i = 0; i < shards.size(); i++) {
                    double angle = angle(i, SHARD_COUNT, tick * 0.16);
                    double radius = 1.18 + progress * 0.70;
                    double shardY = 1.20 + progress * 0.85 + Math.sin(tick * 0.22 + i) * 0.22;
                    shards.get(i).teleport(base.clone().add(Math.cos(angle) * radius, shardY, Math.sin(angle) * radius));
                    setBlockScale(shards.get(i), (float) (0.64 * shardFade));
                }

                setBlockScale(core, (float) (1.45 * shardFade));
                setBlockScale(halo, (float) (1.65 * shardFade));

                if (localTick == 10) {
                    setLabel(label, reward, rewardVisual);
                }

                spawnRewardAura(world, rewardLoc, localTick);
                world.spawnParticle(Particle.END_ROD, rewardLoc, 5, 0.12, 0.12, 0.12, 0.02);
                world.spawnParticle(Particle.ENCHANT, rewardLoc, 8, 0.22, 0.18, 0.22, 0.30);

                if (localTick % 8 == 0) {
                    world.playSound(rewardLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.16f, 1.25f + (float) progress * 0.55f);
                }
            }

            private void updateGhostOrbit(List<ItemDisplay> displays, double scaleProgress, double radius, double y, double rotation, boolean fadeIn) {
                for (int i = 0; i < displays.size(); i++) {
                    double angle = angle(i, displays.size(), rotation);
                    double wave = Math.sin(tick * 0.16 + i * 0.8) * 0.18;
                    ItemDisplay display = displays.get(i);

                    display.teleport(base.clone().add(Math.cos(angle) * radius, y + wave, Math.sin(angle) * radius));
                    display.setRotation((float) Math.toDegrees(angle) + tick * 4.5f, 0f);

                    double activePulse = 1.0 + Math.max(0.0, Math.sin(tick * 0.22 + i * 1.7)) * 0.12;
                    float scale = (float) ((fadeIn ? scaleProgress : 1.0) * 0.40 * activePulse);
                    setItemScale(display, scale);

                    if (i % 2 == 0) {
                        world.spawnParticle(Particle.END_ROD, display.getLocation(), 1, 0.03, 0.03, 0.03, 0.0);
                    }
                }
            }

            private void cleanup() {
                for (BlockDisplay shard : shards) safeRemove(shard);
                for (ItemDisplay ghost : ghosts) safeRemove(ghost);
                safeRemove(core);
                safeRemove(halo);
                safeRemove(rewardDisplay);
                safeRemove(label);

                for (BlockDisplay shard : shards) untrack(shard);
                for (ItemDisplay ghost : ghosts) untrack(ghost);
                untrack(core);
                untrack(halo);
                untrack(rewardDisplay);
                untrack(label);
                if (taskHolder[0] != null) untrack(taskHolder[0]);

                onFinish.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        track(taskHolder[0]);
    }

    private static List<ItemStack> buildGhostVisuals(CaseDefinition def, ItemStack rewardVisual) {
        List<ItemStack> visuals = new ArrayList<>();
        if (def.animationItems() != null) {
            for (ItemStack item : def.animationItems()) {
                if (item != null) visuals.add(item.clone());
                if (visuals.size() >= GHOST_COUNT - 1) break;
            }
        }
        visuals.add(rewardVisual.clone());
        while (visuals.size() < GHOST_COUNT) {
            visuals.add(rewardVisual.clone());
        }
        return visuals;
    }

    private ItemStack buildRewardVisual(Reward reward, CaseDefinition def) {
        return resolveRewardVisual(reward, def);
    }

    private static ItemStack findMatchingAnimationItem(Reward reward, CaseDefinition def) {
        String rewardName = normalizeName(reward.displayName());
        String groupName = normalizeName(reward.lpGroup());
        String nodeName = normalizeName(reward.lpNode());

        for (ItemStack item : def.animationItems()) {
            if (item == null) continue;

            String itemName = normalizeName(getDisplayName(item));
            if (matchesName(itemName, rewardName) || matchesName(itemName, groupName) || matchesName(itemName, nodeName)) {
                return item.clone();
            }
        }

        return null;
    }

    private static String getDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return item.getType().name();
    }

    private static boolean matchesName(String itemName, String rewardName) {
        if (itemName.length() < 2 || rewardName.length() < 2) {
            return false;
        }
        return itemName.equals(rewardName) || itemName.contains(rewardName) || rewardName.contains(itemName);
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String colored = ChatColor.translateAlternateColorCodes('&', value);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null) {
            stripped = colored;
        }
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private void setLabel(TextDisplay textDisplay, Reward reward, ItemStack visual) {
        textDisplay.setText(
                "§x§4§2§9§F§9§1§l«Разлом выдал награду»\n" +
                        resolveRewardName(reward, visual)
        );
    }

    private static void setBlockScale(BlockDisplay display, float scale) {
        float half = scale / 2f;
        org.bukkit.util.Transformation tf = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(-half, -half, -half),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
        ));
    }

    private static void setItemScale(ItemDisplay display, float scale) {
        org.bukkit.util.Transformation tf = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
        ));
    }

    private static void spawnGroundSigil(World world, Location center, double radius, double rotation) {
        for (int i = 0; i < 28; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / 28.0;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            Particle particle = i % 2 == 0 ? Particle.ENCHANT : Particle.ELECTRIC_SPARK;
            world.spawnParticle(particle, x, center.getY(), z, 1, 0, 0, 0, 0.0);
        }
    }

    private static void spawnHelix(World world, Location base, int tick, double radius, double height, Particle particle) {
        for (int i = 0; i < 18; i++) {
            double progress = i / 17.0;
            double angle = tick * 0.16 + progress * Math.PI * 5.0;
            double x = base.getX() + Math.cos(angle) * radius * (0.35 + progress * 0.65);
            double y = base.getY() + progress * height;
            double z = base.getZ() + Math.sin(angle) * radius * (0.35 + progress * 0.65);
            world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0.0);
        }
    }

    private static void spawnRewardAura(World world, Location rewardLoc, int tick) {
        double radius = 0.50 + Math.sin(tick * 0.18) * 0.06;
        for (int i = 0; i < 14; i++) {
            double angle = (Math.PI * 2.0 * i) / 14.0 + tick * 0.13;
            double x = rewardLoc.getX() + Math.cos(angle) * radius;
            double y = rewardLoc.getY() + Math.sin(angle * 2.0 + tick * 0.16) * 0.12;
            double z = rewardLoc.getZ() + Math.sin(angle) * radius;
            world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0.0);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0.01);
            }
        }
    }

    private static void spawnFinalBurst(World world, Location loc) {
        world.spawnParticle(Particle.END_ROD, loc, 65, 0.35, 0.24, 0.35, 0.07);
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 90, 0.48, 0.32, 0.48, 0.08);
        world.spawnParticle(Particle.ENCHANT, loc, 110, 0.55, 0.40, 0.55, 0.42);
        world.spawnParticle(Particle.FIREWORK, loc, 34, 0.32, 0.26, 0.32, 0.04);
        spawnDragonBreath(world, loc, 26, 0.34, 0.18, 0.34, 0.03);
    }

    private static void spawnDragonBreath(World world, Location loc, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            world.spawnParticle(Particle.DRAGON_BREATH, loc, count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException noDataRejected) {
            try {
                world.spawnParticle(Particle.DRAGON_BREATH, loc, count, offsetX, offsetY, offsetZ, extra, 1.0F);
            } catch (IllegalArgumentException dataRejected) {
                world.spawnParticle(Particle.END_ROD, loc, count, offsetX, offsetY, offsetZ, extra);
            }
        }
    }

    private static double angle(int index, int total, double rotation) {
        return rotation + (Math.PI * 2.0 * index) / total;
    }

    private static double easeOutBack(double value) {
        double x = clamp(value);
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
    }

    private static double easeInOut(double value) {
        double x = clamp(value);
        return x < 0.5 ? 2.0 * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 2.0) / 2.0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void safeRemove(Entity entity) {
        try {
            if (entity != null && !entity.isDead()) entity.remove();
        } catch (Exception ignored) {
        }
    }
}
