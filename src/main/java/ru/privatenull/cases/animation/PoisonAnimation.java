package ru.privatenull.cases.animation;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class PoisonAnimation extends CaseAnimation {

    private static final int REQUIRED_HITS = 6;
    private static final int OPENING_TICKS = 42;
    private static final int EXPLODE_CHARGE_TICKS = 28;
    private static final int REVEAL_TICKS = 68;
    private static final int AUTO_BREAK_TICK = 260;

    public PoisonAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void play(Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        Random random = new Random();
        Location cubeBase = base.clone().add(0, 0.15, 0);
        applySlimeFacing(cubeBase, def, player);
        float slimeYaw = cubeBase.getYaw();
        float slimePitch = cubeBase.getPitch();
        ItemStack rewardVisual = buildRewardVisual(reward, def);

        Slime cube = world.spawn(cubeBase, Slime.class, slime -> {
            slime.setAI(false);
            slime.setGravity(false);
            slime.setSilent(true);
            slime.setCollidable(false);
            slime.setCanPickupItems(false);
            slime.setRemoveWhenFarAway(false);
            slime.setPersistent(false);
            slime.setSize(1);
            slime.setHealth(Math.min(slime.getMaxHealth(), 1.0));
            slime.setGlowing(true);
            slime.setCustomNameVisible(false);
        });
        track(cube);

        TextDisplay info = (TextDisplay) world.spawnEntity(base.clone().add(0, 2.85, 0), org.bukkit.entity.EntityType.TEXT_DISPLAY);
        info.setBillboard(Display.Billboard.CENTER);
        info.setSeeThrough(true);
        info.setDefaultBackground(false);
        info.setText(buildStatusText(0));
        track(info);

        ItemDisplay rewardDisplay = (ItemDisplay) world.spawnEntity(base.clone().add(0, 1.1, 0), org.bukkit.entity.EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        setScale(rewardDisplay, 0f);
        track(rewardDisplay);

        List<SlimeGlob> globs = new ArrayList<>();

        final int STATE_OPENING = 0;
        final int STATE_BATTLE = 1;
        final int STATE_CHARGE = 2;
        final int STATE_REVEAL = 3;

        int[] state = {STATE_OPENING};
        int[] tick = {0};
        int[] hits = {0};
        int[] lastHitTick = {-100};
        int[] chargeStartTick = {-1};
        int[] impactTick = {-1};
        float[] rewardYaw = {0f};

        Listener[] listenerHolder = new Listener[1];
        BukkitTask[] taskHolder = new BukkitTask[1];

        UUID ownerId = player.getUniqueId();
        UUID cubeId = cube.getUniqueId();

        listenerHolder[0] = new Listener() {
            @EventHandler
            public void onDamage(EntityDamageEvent event) {
                if (!event.getEntity().getUniqueId().equals(cubeId)) return;

                event.setCancelled(true);

                if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
                if (!(byEntity.getDamager() instanceof Player damager)) return;

                if (!damager.getUniqueId().equals(ownerId)) {
                    damager.sendMessage("§cТы не можешь бить этот куб.");
                    return;
                }

                if (state[0] != STATE_BATTLE) return;
                if (tick[0] - lastHitTick[0] < 5) return;

                lastHitTick[0] = tick[0];
                hits[0]++;

                Location hitLoc = cube.getLocation().clone().add(0, 0.9, 0);
                world.spawnParticle(Particle.ITEM_SLIME, hitLoc, 16, 0.35, 0.22, 0.35, 0.05);
                world.spawnParticle(Particle.ITEM_SLIME, hitLoc, 10, 0.25, 0.18, 0.25, 0.04);
                world.spawnParticle(Particle.WITCH, hitLoc, 8, 0.25, 0.18, 0.25, 0.02);

                world.playSound(cube.getLocation(), Sound.ENTITY_SLIME_HURT, 0.36f, 0.7f + hits[0] * 0.08f);
                world.playSound(cube.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.24f, 1.2f);

                info.setText(buildStatusText(hits[0]));

                if (hits[0] >= REQUIRED_HITS) {
                    state[0] = STATE_CHARGE;
                    chargeStartTick[0] = tick[0];
                    info.setText(
                            "§x§9§6§F§B§8§A«Ядовитый куб»\n" +
                                    " §7- §fКуб перегружен\n" +
                                    " §7- §fСейчас лопнет..."
                    );
                    world.playSound(cube.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.28f, 0.65f);
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(listenerHolder[0], plugin);

        taskHolder[0] = new BukkitRunnable() {
            @Override
            public void run() {
                tick[0]++;

                if (!player.isOnline()) {
                    cleanup();
                    return;
                }

                updateGlobs(world, base, globs);

                if (state[0] == STATE_OPENING) {
                    double progress = tick[0] / (double) OPENING_TICKS;
                    double eased = easeOutBack(Math.min(1.0, progress));

                    int size;
                    if (progress < 0.25) size = 1;
                    else if (progress < 0.35) size = 2;
                    else if (progress < 0.45) size = 3;
                    else size = 4;

                    cube.setSize(size);

                    double bob = Math.sin(tick[0] * 0.22) * 0.05;
                    cube.teleport(oriented(cubeBase.clone().add(0, bob, 0), slimeYaw, slimePitch));

                    world.spawnParticle(Particle.ITEM_SLIME, cube.getLocation().clone().add(0, 0.5, 0), 8, 0.30, 0.15, 0.30, 0.03);
                    world.spawnParticle(Particle.ITEM_SLIME, cube.getLocation().clone().add(0, 0.5, 0), 6, 0.22, 0.10, 0.22, 0.02);

                    spawnGroundRing(world, base.clone().add(0, 0.02, 0), 0.5 + eased * 0.9, tick[0] * 0.08);

                    if (tick[0] % 8 == 0) {
                        world.playSound(cube.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.24f, 0.6f + (float) progress * 0.25f);
                    }

                    if (tick[0] >= OPENING_TICKS) {
                        state[0] = STATE_BATTLE;
                        cube.setSize(4);
                        info.setText(buildStatusText(hits[0]));
                        world.playSound(cube.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.28f, 0.8f);
                    }
                    return;
                }

                if (state[0] == STATE_BATTLE) {
                    cube.setSize(4);

                    double bob = Math.sin(tick[0] * 0.18) * 0.08;
                    cube.teleport(oriented(cubeBase.clone().add(0, bob, 0), slimeYaw, slimePitch));

                    world.spawnParticle(Particle.ITEM_SLIME, cube.getLocation().clone().add(0, 0.75, 0), 5, 0.30, 0.20, 0.30, 0.02);
                    if (tick[0] % 4 == 0) {
                        world.spawnParticle(Particle.WITCH, cube.getLocation().clone().add(0, 0.75, 0), 4, 0.28, 0.18, 0.28, 0.01);
                    }

                    int spitInterval = Math.max(10, 24 - hits[0] * 2);
                    if (tick[0] % spitInterval == 0) {
                        spitSlime(world, cube.getLocation().clone().add(0, 0.95, 0), random, globs);
                        world.playSound(cube.getLocation(), Sound.ENTITY_SLIME_ATTACK, 0.28f, 0.85f);
                    }

                    if (tick[0] >= AUTO_BREAK_TICK) {
                        state[0] = STATE_CHARGE;
                        chargeStartTick[0] = tick[0];
                        info.setText(
                                "§x§9§6§F§B§8§A«Ядовитый куб»\n" +
                                        " §7- §fКуб слишком долго жил\n" +
                                        " §7- §fОн сам взрывается..."
                        );
                    }
                    return;
                }

                if (state[0] == STATE_CHARGE) {
                    int dt = tick[0] - chargeStartTick[0];

                    if (dt < 6) cube.setSize(5);
                    else if (dt < 12) cube.setSize(6);
                    else cube.setSize(6);

                    double shakeX = (random.nextDouble() - 0.5) * 0.08;
                    double shakeZ = (random.nextDouble() - 0.5) * 0.08;
                    cube.teleport(oriented(cubeBase.clone().add(shakeX, 0.05, shakeZ), slimeYaw, slimePitch));

                    Location loc = cube.getLocation().clone().add(0, 1.0, 0);
                    world.spawnParticle(Particle.ITEM_SLIME, loc, 20, 0.45, 0.25, 0.45, 0.05);
                    world.spawnParticle(Particle.ITEM_SLIME, loc, 14, 0.35, 0.18, 0.35, 0.04);
                    world.spawnParticle(Particle.WITCH, loc, 12, 0.30, 0.22, 0.30, 0.02);
                    world.spawnParticle(Particle.SMOKE, loc, 6, 0.22, 0.12, 0.22, 0.01);

                    if (dt % 4 == 0) {
                        world.playSound(cube.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.30f, 0.45f + dt * 0.03f);
                    }

                    if (dt >= EXPLODE_CHARGE_TICKS) {
                        Location explodeLoc = cube.getLocation().clone().add(0, 1.0, 0);

                        for (SlimeGlob glob : globs) {
                            safeRemove(glob.display);
                            untrack(glob.display);
                        }
                        globs.clear();

                        world.spawnParticle(Particle.EXPLOSION, explodeLoc, 4, 0.35, 0.25, 0.35, 0.0);
                        world.spawnParticle(Particle.ITEM_SLIME, explodeLoc, 42, 0.60, 0.35, 0.60, 0.09);
                        world.spawnParticle(Particle.ITEM_SLIME, explodeLoc, 32, 0.50, 0.28, 0.50, 0.06);
                        world.spawnParticle(Particle.WITCH, explodeLoc, 24, 0.40, 0.24, 0.40, 0.03);
                        world.spawnParticle(Particle.SMOKE, explodeLoc, 18, 0.28, 0.16, 0.28, 0.03);

                        world.playSound(cube.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.28f, 1.2f);
                        world.playSound(cube.getLocation(), Sound.BLOCK_SLIME_BLOCK_BREAK, 0.32f, 0.8f);
                        world.playSound(cube.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 0.32f, 0.7f);

                        safeRemove(cube);
                        untrack(cube);

                        impactTick[0] = tick[0];
                        state[0] = STATE_REVEAL;
                        info.setText("");
                    }
                    return;
                }

                if (state[0] == STATE_REVEAL) {
                    int dt = tick[0] - impactTick[0];
                    double progress = Math.min(1.0, dt / (double) REVEAL_TICKS);
                    double eased = easeOutBack(progress);
                    double bob = Math.sin(dt * 0.22) * 0.06 * (1.0 - progress * 0.35);

                    Location rewardLoc = base.clone().add(0, 1.1 + eased * 1.75 + bob, 0);
                    rewardDisplay.teleport(rewardLoc);

                    float scale = (float) (0.2 + eased * 0.95);
                    setScale(rewardDisplay, scale);

                    rewardYaw[0] += 4.8f;
                    if (rewardYaw[0] >= 360f) rewardYaw[0] -= 360f;
                    rewardDisplay.setRotation(rewardYaw[0], 0f);

                    if (dt == 5) {
                        setLabel(info, reward, rewardVisual);
                        info.teleport(base.clone().add(0, 3.45, 0));
                    }

                    spawnRewardAura(world, rewardLoc, dt);

                    if (dt % 8 == 0) {
                        world.playSound(base, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.24f, 0.8f + (float) progress * 0.5f);
                    }

                    if (dt >= REVEAL_TICKS) {
                        world.spawnParticle(Particle.ITEM_SLIME, rewardLoc, 18, 0.25, 0.16, 0.25, 0.03);
                        world.spawnParticle(Particle.ITEM_SLIME, rewardLoc, 14, 0.22, 0.12, 0.22, 0.03);
                        world.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.0f);
                        world.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.25f);
                        cleanup();
                    }
                }
            }

            private void cleanup() {
                for (SlimeGlob glob : globs) {
                    safeRemove(glob.display);
                    untrack(glob.display);
                }
                globs.clear();

                safeRemove(cube);
                safeRemove(rewardDisplay);
                safeRemove(info);

                untrack(cube);
                untrack(rewardDisplay);
                untrack(info);

                if (listenerHolder[0] != null) {
                    HandlerList.unregisterAll(listenerHolder[0]);
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

    private static void spitSlime(World world, Location from, Random random, List<SlimeGlob> globs) {
        int count = 3 + random.nextInt(2);

        world.spawnParticle(Particle.ITEM_SLIME, from, 10, 0.16, 0.12, 0.16, 0.03);
        world.spawnParticle(Particle.ITEM_SLIME, from, 8, 0.12, 0.10, 0.12, 0.02);

        for (int i = 0; i < count; i++) {
            ItemDisplay glob = (ItemDisplay) world.spawnEntity(from, org.bukkit.entity.EntityType.ITEM_DISPLAY);
            glob.setItemStack(new ItemStack(Material.SLIME_BALL));
            glob.setBillboard(Display.Billboard.VERTICAL);
            setScale(glob, 0.28f);

            double angle = random.nextDouble() * Math.PI * 2D;
            double speed = 0.12 + random.nextDouble() * 0.10;

            Vector velocity = new Vector(
                    Math.cos(angle) * speed,
                    0.18 + random.nextDouble() * 0.08,
                    Math.sin(angle) * speed
            );

            globs.add(new SlimeGlob(glob, velocity, 14 + random.nextInt(8)));
        }
    }

    private void updateGlobs(World world, Location base, List<SlimeGlob> globs) {
        Iterator<SlimeGlob> iterator = globs.iterator();

        while (iterator.hasNext()) {
            SlimeGlob glob = iterator.next();
            glob.age++;

            Location current = glob.display.getLocation().clone().add(glob.velocity);
            glob.velocity.multiply(0.96);
            glob.velocity.setY(glob.velocity.getY() - 0.018);

            glob.display.teleport(current);

            float scale = (float) (0.20 + Math.sin(glob.age * 0.30) * 0.03);
            setScale(glob.display, scale);

            world.spawnParticle(Particle.ITEM_SLIME, current, 1, 0.01, 0.01, 0.01, 0.0);

            if (glob.age >= glob.maxAge || current.getY() <= base.getY() + 0.18) {
                world.spawnParticle(Particle.ITEM_SLIME, current, 6, 0.10, 0.06, 0.10, 0.02);
                world.spawnParticle(Particle.ITEM_SLIME, current, 5, 0.08, 0.05, 0.08, 0.02);
                world.playSound(current, Sound.BLOCK_SLIME_BLOCK_BREAK, 0.16f, 1.5f);

                safeRemove(glob.display);
                untrack(glob.display);
                iterator.remove();
            }
        }
    }

    private static void spawnGroundRing(World world, Location center, double radius, double rotation) {
        for (int i = 0; i < 18; i++) {
            double angle = rotation + (Math.PI * 2D * i) / 18D;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            world.spawnParticle(Particle.ITEM_SLIME, x, center.getY(), z, 1, 0, 0, 0, 0.0);
        }
    }

    private static void spawnRewardAura(World world, Location rewardLoc, int tick) {
        double radius = 0.42 + Math.sin(tick * 0.16) * 0.05;

        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2D * i) / 12D + tick * 0.10;
            double x = rewardLoc.getX() + Math.cos(angle) * radius;
            double z = rewardLoc.getZ() + Math.sin(angle) * radius;
            double y = rewardLoc.getY() + Math.sin(angle * 2.0 + tick * 0.12) * 0.10;

            world.spawnParticle(Particle.ITEM_SLIME, x, y, z, 1, 0, 0, 0, 0.0);

            if (i % 3 == 0) {
                world.spawnParticle(Particle.WITCH, x, y, z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    private static String buildStatusText(int hits) {
        int left = Math.max(0, REQUIRED_HITS - hits);
        return "§x§9§6§F§B§8§A«Ядовитый куб»\n" +
                " §8- §fУдаров: §a" + hits + "§7/§a" + REQUIRED_HITS + "\n" +
                " §8- §fОсталось сломать: §x§F§B§C§A§0§8" + left + "\n" +
                " §8- §fБей куб, пока он плюётся слизью";
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
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

    private void applySlimeFacing(Location loc, CaseDefinition def, Player player) {
        ConfigurationSection caseSection = plugin.getCaseManager() == null ? null : plugin.getCaseManager().getCaseSection(def.name());
        ConfigurationSection animation = caseSection == null ? null : caseSection.getConfigurationSection("animation");
        ConfigurationSection poison = animation == null ? null : animation.getConfigurationSection("poison");
        if (poison == null) {
            poison = plugin.getConfig().getConfigurationSection("cases." + def.name() + ".animation.poison");
        }
        if (poison == null) {
            loc.setYaw(0f);
            loc.setPitch(0f);
            return;
        }

        loc.setPitch((float) poison.getDouble("slime-pitch", 0.0));

        if (poison.contains("slime-yaw")) {
            loc.setYaw((float) poison.getDouble("slime-yaw", 0.0));
            return;
        }

        String facing = poison.getString("slime-facing", "SOUTH");
        if (facing == null) facing = "SOUTH";

        switch (facing.trim().toUpperCase(Locale.ROOT)) {
            case "NORTH" -> loc.setYaw(180f);
            case "EAST" -> loc.setYaw(-90f);
            case "WEST" -> loc.setYaw(90f);
            case "PLAYER" -> faceLocation(loc, player.getLocation());
            case "SOUTH" -> loc.setYaw(0f);
            default -> {
                try {
                    loc.setYaw((float) Double.parseDouble(facing));
                } catch (NumberFormatException ignored) {
                    loc.setYaw(0f);
                }
            }
        }
    }

    private static void faceLocation(Location loc, Location target) {
        Vector direction = target.toVector().subtract(loc.toVector());
        if (direction.lengthSquared() < 0.0001) {
            loc.setYaw(0f);
            return;
        }
        loc.setDirection(direction);
    }

    private static Location oriented(Location loc, float yaw, float pitch) {
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        return loc;
    }

    private void setLabel(TextDisplay td, Reward reward, ItemStack visual) {
        td.setText(
                "§x§9§6§F§B§8§A«Награда»\n" +
                        "" + resolveRewardName(reward, visual)
        );
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

    private static ItemStack buildFallbackRewardVisual(Reward reward) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = reward.displayName();
            if (name == null || name.isBlank()) {
                name = reward.lpGroup() != null && !reward.lpGroup().isBlank()
                        ? "&f" + reward.lpGroup()
                        : "&fНаграда";
            }
            meta.setDisplayName(ru.privatenull.util.ColorUtil.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
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
        String colored = ru.privatenull.util.ColorUtil.colorize(value);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null) {
            stripped = colored;
        }
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static void safeRemove(Entity e) {
        try {
            if (e != null && !e.isDead()) e.remove();
        } catch (Exception ignored) {
        }
    }

    private static final class SlimeGlob {
        private final ItemDisplay display;
        private final Vector velocity;
        private final int maxAge;
        private int age;

        private SlimeGlob(ItemDisplay display, Vector velocity, int maxAge) {
            this.display = display;
            this.velocity = velocity;
            this.maxAge = maxAge;
            this.age = 0;
        }
    }
}
