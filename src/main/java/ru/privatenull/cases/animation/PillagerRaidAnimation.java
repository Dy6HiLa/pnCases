package ru.privatenull.cases.animation;

import org.bukkit.Bukkit;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnCases;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PillagerRaidAnimation extends CaseAnimation {

    private static final int APPROACH_END = 34;
    private static final int STRIKE_END = 126;
    private static final int REVEAL_END = 184;

    public PillagerRaidAnimation(pnCases plugin) {
        super(plugin);
    }

    @Override
    public void play(org.bukkit.entity.Player player, CaseDefinition def, Reward reward, Location base, Runnable onFinish) {
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        // CaseManager passes the centre of the case block, not its lower corner.
        Location center = base.clone();
        Block hiddenBlock = base.getBlock();
        BlockData originalBlockData = hiddenBlock.getBlockData();
        boolean hideBlock = !hiddenBlock.getType().isAir();
        if (hideBlock) {
            hiddenBlock.setType(Material.AIR, false);
        }

        Location caseBlockOrigin = hiddenBlock.getLocation();
        List<BlockDisplay> supports = createWoodenRack(world, caseBlockOrigin);
        BlockDisplay barrel = block(world, caseBlockOrigin, Material.BARREL,
                1.0f, 1.0f, 1.0f, null);
        for (BlockDisplay support : supports) {
            track(support);
        }
        track(barrel);

        List<Pillager> pillagers = spawnPillagers(world, center);
        for (Pillager pillager : pillagers) {
            track(pillager);
        }
        Set<UUID> visualEntityIds = new HashSet<>();
        for (Pillager pillager : pillagers) {
            visualEntityIds.add(pillager.getUniqueId());
        }

        ItemStack rewardVisual = resolveRewardVisual(reward, def);
        ItemDisplay rewardDisplay = item(world, center.clone().add(0.0, 1.15, 0.0), rewardVisual, 0.01f);
        TextDisplay label = text(world, center.clone().add(0.0, 2.85, 0.0));
        track(rewardDisplay);
        track(label);

        List<ItemStack> debrisSource = buildDebrisSource(def, rewardVisual);
        List<FlyingItem> flyingItems = new ArrayList<>();
        Listener[] listenerHolder = new Listener[1];
        listenerHolder[0] = new Listener() {
            @EventHandler
            public void onDamage(EntityDamageEvent event) {
                if (visualEntityIds.contains(event.getEntity().getUniqueId())) {
                    event.setCancelled(true);
                }
            }

            @EventHandler
            public void onInteract(PlayerInteractEntityEvent event) {
                if (visualEntityIds.contains(event.getRightClicked().getUniqueId())) {
                    event.setCancelled(true);
                }
            }

            @EventHandler
            public void onTarget(EntityTargetEvent event) {
                if (visualEntityIds.contains(event.getEntity().getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(listenerHolder[0], plugin);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            int tick;
            boolean broken;
            boolean finished;

            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }
                if (!player.isOnline()) {
                    cleanup();
                    return;
                }

                tick++;
                updatePillagers(pillagers, center, tick);
                updateFlyingItems(flyingItems);

                if (tick == 1) {
                    world.playSound(center, Sound.BLOCK_BARREL_OPEN, 0.28f, 0.75f);
                    world.playSound(center, Sound.ENTITY_PILLAGER_AMBIENT, 0.18f, 0.80f);
                }

                if (tick > APPROACH_END && tick < STRIKE_END && (tick - APPROACH_END) % 6 == 0) {
                    int index = ((tick - APPROACH_END) / 6) % pillagers.size();
                    animateAxeSwing(pillagers.get(index), center, world, index);
                }

                if (tick > APPROACH_END && tick <= STRIKE_END && (tick - APPROACH_END) % 23 == 1) {
                    int index = Math.min(3, (tick - APPROACH_END - 1) / 23);
                    strike(pillagers.get(index), center, index, debrisSource, flyingItems, world);
                }

                if (!broken && tick == STRIKE_END) {
                    broken = true;
                    breakBarrel(barrel, supports, center, world);
                    spawnFlyingItems(center, debrisSource, flyingItems, world);
                }

                if (broken && tick > STRIKE_END) {
                    double progress = Math.min(1.0, (tick - STRIKE_END) / 42.0);
                    float scale = (float) Math.min(1.0, 0.18 + progress * 0.82);
                    rewardDisplay.teleport(center.clone().add(0.0, 1.25 + progress * 0.90, 0.0));
                    rewardDisplay.setRotation((tick * 7.0f) % 360.0f, 0.0f);
                    scale(rewardDisplay, scale);
                    label.teleport(center.clone().add(0.0, 2.85 + progress * 0.35, 0.0));
                    if (tick == STRIKE_END + 8) {
                        label.setText(resolveRewardName(reward, rewardVisual));
                        world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.24f, 1.45f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.55f);
                    }
                    if (tick % 2 == 0) {
                        world.spawnParticle(Particle.END_ROD, rewardDisplay.getLocation(), 3, 0.18, 0.18, 0.18, 0.02);
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, rewardDisplay.getLocation(), 2, 0.22, 0.20, 0.22, 0.04);
                    }
                }

                if (tick >= REVEAL_END) {
                    cleanup();
                }
            }

            private void strike(Pillager pillager, Location target, int index,
                                List<ItemStack> source, List<FlyingItem> items, World currentWorld) {
                if (pillager == null || pillager.isDead()) {
                    return;
                }
                pillager.swingMainHand();
                float pitch = 0.72f + index * 0.08f;
                currentWorld.playSound(target, Sound.ENTITY_PILLAGER_AMBIENT, 0.18f, pitch);
                currentWorld.playSound(target, Sound.BLOCK_WOOD_HIT, 0.36f, 0.72f + index * 0.07f);
                currentWorld.spawnParticle(Particle.BLOCK, target.clone().add(0.0, 0.55, 0.0),
                        14, 0.34, 0.32, 0.34, 0.03, Material.BARREL.createBlockData());
                currentWorld.spawnParticle(Particle.CRIT, target.clone().add(0.0, 1.15, 0.0), 8,
                        0.24, 0.22, 0.24, 0.12);
                currentWorld.spawnParticle(Particle.SWEEP_ATTACK, target.clone().add(0.0, 1.05, 0.0), 1,
                        0.0, 0.0, 0.0, 0.0);
                if (index < source.size()) {
                    spawnSingleItem(target, source.get(index), index, items, currentWorld);
                }
            }

            private void animateAxeSwing(Pillager pillager, Location target, World currentWorld, int index) {
                if (pillager == null || pillager.isDead()) {
                    return;
                }
                pillager.swingMainHand();
                currentWorld.spawnParticle(Particle.CRIT, target.clone().add(0.0, 1.05, 0.0), 3,
                        0.18, 0.18, 0.18, 0.05);
                if (index % 2 == 0) {
                    currentWorld.playSound(target, Sound.BLOCK_WOOD_HIT, 0.10f, 0.82f + index * 0.05f);
                }
            }

            private void cleanup() {
                if (finished) {
                    return;
                }
                finished = true;
                safeRemove(barrel);
                for (BlockDisplay support : supports) {
                    safeRemove(support);
                }
                safeRemove(rewardDisplay);
                safeRemove(label);
                for (Pillager pillager : pillagers) {
                    safeRemove(pillager);
                }
                for (FlyingItem flyingItem : flyingItems) {
                    safeRemove(flyingItem.display);
                }
                if (listenerHolder[0] != null) {
                    HandlerList.unregisterAll(listenerHolder[0]);
                }
                if (hideBlock && hiddenBlock.getType().isAir()) {
                    hiddenBlock.setBlockData(originalBlockData, false);
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

    private List<Pillager> spawnPillagers(World world, Location center) {
        List<Pillager> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            double angle = Math.PI / 4.0 + index * Math.PI / 2.0;
            Location location = center.clone().add(Math.cos(angle) * 2.65, 0.0, Math.sin(angle) * 2.65);
            location = standingLocation(world, location);
            Pillager pillager = (Pillager) world.spawnEntity(location, EntityType.PILLAGER);
            pillager.setAI(false);
            pillager.setGravity(true);
            pillager.setSilent(true);
            pillager.setInvulnerable(true);
            pillager.setCollidable(false);
            pillager.setCanPickupItems(false);
            pillager.setRemoveWhenFarAway(false);
            pillager.setPersistent(false);
            EntityEquipment equipment = pillager.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                equipment.setItemInMainHandDropChance(0.0f);
            }
            face(pillager, center);
            result.add(pillager);
        }
        return result;
    }

    private void updatePillagers(List<Pillager> pillagers, Location center, int tick) {
        double radius = tick <= APPROACH_END
                ? 2.65 - 0.30 * (tick / (double) APPROACH_END)
                : 2.35;
        for (int index = 0; index < pillagers.size(); index++) {
            Pillager pillager = pillagers.get(index);
            if (pillager.isDead()) continue;
            double angle = Math.PI / 4.0 + index * Math.PI / 2.0;
            double bob = tick <= APPROACH_END ? Math.sin(tick * 0.18 + index) * 0.02 : 0.0;
            Location location = center.clone().add(Math.cos(angle) * radius, bob, Math.sin(angle) * radius);
            Location standingLocation = standingLocation(center.getWorld(), location);
            standingLocation.add(0.0, bob, 0.0);
            pillager.teleport(standingLocation);
            face(pillager, center);
        }
    }

    private static Location standingLocation(World world, Location target) {
        if (world == null) {
            return target;
        }

        int x = target.getBlockX();
        int z = target.getBlockZ();
        int highestY = Math.min(world.getMaxHeight() - 2, target.getBlockY() + 1);
        int lowestY = Math.max(world.getMinHeight() + 1, target.getBlockY() - 8);
        for (int y = highestY; y >= lowestY; y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block ground = world.getBlockAt(x, y - 1, z);
            if (feet.isPassable() && head.isPassable() && !ground.isPassable()) {
                return new Location(world, target.getX(), ground.getBoundingBox().getMaxY(), target.getZ(), target.getYaw(), 0.0f);
            }
        }
        return target;
    }

    private void breakBarrel(BlockDisplay barrel, List<BlockDisplay> supports, Location center, World world) {
        safeRemove(barrel);
        for (BlockDisplay support : supports) {
            safeRemove(support);
        }
        world.playSound(center, Sound.BLOCK_BARREL_CLOSE, 0.30f, 0.65f);
        world.playSound(center, Sound.BLOCK_WOOD_BREAK, 0.42f, 0.72f);
        world.spawnParticle(Particle.BLOCK, center.clone().add(0.0, 0.65, 0.0), 44,
                0.50, 0.55, 0.50, 0.08, Material.BARREL.createBlockData());
        world.spawnParticle(Particle.CLOUD, center.clone().add(0.0, 0.55, 0.0), 20,
                0.45, 0.25, 0.45, 0.04);
        world.spawnParticle(Particle.CRIT, center.clone().add(0.0, 0.70, 0.0), 28,
                0.55, 0.40, 0.55, 0.16);
    }

    private void spawnFlyingItems(Location center, List<ItemStack> source, List<FlyingItem> items, World world) {
        for (int index = 0; index < Math.min(8, source.size() + 4); index++) {
            spawnSingleItem(center, source.get(index % source.size()), index, items, world);
        }
    }

    private void spawnSingleItem(Location center, ItemStack source, int index, List<FlyingItem> items, World world) {
        ItemDisplay display = item(world, center.clone().add(0.0, 0.75, 0.0), source, 0.52f);
        double angle = index * Math.PI * 0.72;
        items.add(new FlyingItem(display, Math.cos(angle) * (0.07 + index * 0.005),
                0.13 + (index % 3) * 0.035, Math.sin(angle) * (0.07 + index * 0.005)));
        track(display);
    }

    private void updateFlyingItems(List<FlyingItem> items) {
        for (FlyingItem item : items) {
            if (item.display.isDead()) continue;
            item.vy -= 0.008;
            item.vx *= 0.985;
            item.vz *= 0.985;
            item.display.teleport(item.display.getLocation().add(item.vx, item.vy, item.vz));
            item.display.setRotation((item.display.getLocation().getYaw() + 12.0f) % 360.0f, 0.0f);
        }
    }

    private static List<ItemStack> buildDebrisSource(CaseDefinition def, ItemStack rewardVisual) {
        List<ItemStack> source = new ArrayList<>();
        if (def != null && def.animationItems() != null) {
            for (ItemStack item : def.animationItems()) {
                if (item != null && !item.getType().isAir()) source.add(item.clone());
            }
        }
        if (source.isEmpty()) {
            source.add(new ItemStack(Material.OAK_PLANKS));
            source.add(new ItemStack(Material.STICK));
            source.add(new ItemStack(Material.IRON_INGOT));
        }
        if (rewardVisual != null && !rewardVisual.getType().isAir()) source.add(rewardVisual.clone());
        return source;
    }

    private static List<BlockDisplay> createWoodenRack(World world, Location origin) {
        List<BlockDisplay> supports = new ArrayList<>(6);
        double groundY = findGroundSurface(world, origin);
        double height = origin.getY() - groundY;
        if (height <= 0.05) {
            return supports;
        }

        double[] offsets = {0.12, 0.72};
        for (double x : offsets) {
            for (double z : offsets) {
                addRackLeg(supports, world, origin, x, z, groundY, height);
            }
        }
        supports.add(block(world, origin.clone().add(0.0, -0.16, 0.42), Material.STRIPPED_OAK_LOG,
                1.0f, 0.16f, 0.16f, Axis.X));
        supports.add(block(world, origin.clone().add(0.42, -0.16, 0.0), Material.STRIPPED_OAK_LOG,
                0.16f, 0.16f, 1.0f, Axis.Z));
        return supports;
    }

    private static void addRackLeg(List<BlockDisplay> supports, World world, Location origin,
                                   double x, double z, double groundY, double height) {
        double currentY = groundY;
        double remaining = height;
        while (remaining > 0.001) {
            double segmentHeight = Math.min(1.0, remaining);
            supports.add(block(world, origin.clone().add(x, currentY - origin.getY(), z), Material.STRIPPED_OAK_LOG,
                    0.16f, (float) segmentHeight, 0.16f, Axis.Y));
            currentY += segmentHeight;
            remaining -= segmentHeight;
        }
    }

    private static double findGroundSurface(World world, Location origin) {
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        for (int y = origin.getBlockY() - 1; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.isPassable()) {
                return block.getBoundingBox().getMaxY();
            }
        }
        return origin.getY();
    }

    private static BlockDisplay block(World world, Location location, Material material,
                                      float xScale, float yScale, float zScale, Axis axis) {
        BlockDisplay display = (BlockDisplay) world.spawnEntity(location, EntityType.BLOCK_DISPLAY);
        BlockData data = material.createBlockData();
        if (material == Material.BARREL && data instanceof Directional directional) {
            directional.setFacing(BlockFace.UP);
        }
        if (axis != null && data instanceof Orientable orientable) {
            orientable.setAxis(axis);
        }
        display.setBlock(data);
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(current.getTranslation(), current.getLeftRotation(),
                new Vector3f(xScale, yScale, zScale), current.getRightRotation()));
        return display;
    }

    private static ItemDisplay item(World world, Location location, ItemStack item, float scale) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        display.setItemStack(item.clone());
        display.setBillboard(Display.Billboard.VERTICAL);
        scale(display, scale);
        return display;
    }

    private static TextDisplay text(World world, Location location) {
        TextDisplay display = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setDefaultBackground(false);
        display.setText("");
        return display;
    }

    private static void scale(Display display, float scale) {
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(current.getTranslation(), current.getLeftRotation(),
                new Vector3f(scale, scale, scale), current.getRightRotation()));
    }

    private static void face(Entity entity, Location target) {
        Location location = entity.getLocation();
        float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - location.getZ(), target.getX() - location.getX())) - 90.0f;
        entity.setRotation(yaw, 0.0f);
    }

    private static void safeRemove(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private static final class FlyingItem {
        private final ItemDisplay display;
        private double vx;
        private double vz;
        private double vy;
        private FlyingItem(ItemDisplay display, double vx, double vy, double vz) {
            this.display = display;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }
    }
}
