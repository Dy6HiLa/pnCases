package ru.privatenull.cases.animation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.util.ParticleCompat;
import ru.privatenull.util.ServerCompatibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** A choice animation: stationary mobs are options and one hit selects the reward source. */
public final class MobHuntAnimation extends CaseAnimation implements Listener {
    private static final int MOB_COUNT = 6;
    private static final int TIMEOUT_TICKS = 20 * 90;
    private static final double MAX_DISTANCE_SQUARED = 12.0 * 12.0;

    private final Map<UUID, Session> sessionsByPlayer = new HashMap<>();
    private final Map<UUID, Session> sessionsByMob = new HashMap<>();
    private final Map<UUID, Session> sessionsByProjectile = new HashMap<>();

    public MobHuntAnimation(PnCasesPlugin plugin) {
        super(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void play(Player player, CaseDefinition definition, Reward reward, Location base, Runnable onFinish) {
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        Session previous = sessionsByPlayer.remove(player.getUniqueId());
        if (previous != null) cleanup(previous, false);

        Session session = new Session(player, base.clone(), definition, reward, onFinish);
        sessionsByPlayer.put(player.getUniqueId(), session);

        for (int index = 0; index < MOB_COUNT; index++) {
            double angle = Math.PI * 2.0 * index / MOB_COUNT;
            Location spawn = findStandingLocation(base, angle, 3.4);
            Mob mob = spawnGuardian(world, spawn, index);
            session.mobs.add(mob);
            session.spawnLocations.put(mob.getUniqueId(), spawn.clone());
            sessionsByMob.put(mob.getUniqueId(), session);
            track(mob);
        }

        player.sendMessage(plugin.getMessages().get("mob-hunt-start"));
        world.playSound(base, Sound.ENTITY_ZOMBIE_AMBIENT, 0.65f, 0.82f);
        world.playSound(base, Sound.ENTITY_SKELETON_AMBIENT, 0.55f, 1.08f);
        ParticleCompat.spawn(world, base.clone().add(0.0, 1.0, 0.0),
                new String[]{"SMOKE", "SMOKE_NORMAL", "CLOUD"}, 36, 2.4, 0.7, 2.4, 0.03);

        session.monitorTask = new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (session.completed || session.revealing) {
                    cancel();
                    return;
                }
                elapsed += 10;
                if (!player.isOnline() || player.isDead()) {
                    beginReveal(session, session.base.clone().add(0.0, 1.0, 0.0), true);
                    return;
                }

                for (Mob mob : new ArrayList<>(session.mobs)) {
                    if (!mob.isValid() || mob.isDead()) {
                        forgetMob(session, mob);
                        continue;
                    }
                    if (mob.getWorld() != session.base.getWorld()
                            || mob.getLocation().distanceSquared(session.base) > MAX_DISTANCE_SQUARED) {
                        Location spawn = session.spawnLocations.getOrDefault(mob.getUniqueId(), session.base);
                        mob.teleport(spawn);
                    }
                }

                if (session.mobs.isEmpty()) {
                    beginReveal(session, session.base.clone().add(0.0, 1.0, 0.0), false);
                } else if (elapsed >= TIMEOUT_TICKS) {
                    player.sendMessage(plugin.getMessages().get("mob-hunt-timeout"));
                    beginReveal(session, session.base.clone().add(0.0, 1.0, 0.0), true);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
        track(session.monitorTask);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Session victimSession = sessionsByMob.get(event.getEntity().getUniqueId());
        if (victimSession != null) {
            if (!(event instanceof EntityDamageByEntityEvent byEntity)
                    || !isOwnerAttack(byEntity.getDamager(), victimSession)) {
                event.setCancelled(true);
            } else {
                event.setDamage(1000.0);
            }
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        Session attackerSession = sessionForAttacker(byEntity.getDamager());
        if (attackerSession == null) return;
        if (!(event.getEntity() instanceof Player victim)
                || !victim.getUniqueId().equals(attackerSession.playerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Session session = sessionsByMob.get(event.getEntity().getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (sessionsByMob.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Session session = sessionsByMob.get(event.getEntity().getUniqueId());
        if (session == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        forgetMob(session, (Mob) event.getEntity());

        if (session.selectionMade) return;
        session.selectionMade = true;
        Location selectedLocation = event.getEntity().getLocation().add(0.0, 0.55, 0.0);
        session.player.playSound(session.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.35f);
        startDeathSequence(session, selectedLocation);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (!(shooter instanceof Entity entity)) return;
        Session session = sessionsByMob.get(entity.getUniqueId());
        if (session == null) return;
        sessionsByProjectile.put(projectile.getUniqueId(), session);
        session.projectiles.add(projectile);
        track(projectile);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Session session = sessionsByProjectile.remove(projectile.getUniqueId());
        if (session == null) return;
        session.projectiles.remove(projectile);
        untrack(projectile);
    }

    @Override
    public void cancelAll() {
        for (Session session : new HashSet<>(sessionsByPlayer.values())) cleanup(session, false);
        HandlerList.unregisterAll(this);
        super.cancelAll();
    }

    private Mob spawnGuardian(World world, Location location, int index) {
        boolean zombie = ThreadLocalRandom.current().nextBoolean();
        Mob mob;
        if (zombie) {
            Zombie entity = world.spawn(location, Zombie.class);
            entity.setBaby(false);
            mob = entity;
        } else {
            mob = world.spawn(location, Skeleton.class);
        }
        mob.setCustomName((zombie ? "§cЗомби" : "§fСкелет") + " §8• §7Страж кейса");
        mob.setCustomNameVisible(true);
        mob.setAI(false);
        mob.setSilent(true);
        mob.setCanPickupItems(false);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);

        EntityEquipment equipment = mob.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.LEATHER_HELMET));
            equipment.setHelmetDropChance(0.0f);
            equipment.setItemInMainHandDropChance(0.0f);
            equipment.setItemInOffHandDropChance(0.0f);
            equipment.setChestplateDropChance(0.0f);
            equipment.setLeggingsDropChance(0.0f);
            equipment.setBootsDropChance(0.0f);
        }
        mob.addScoreboardTag("pncases_mob_hunt");
        return mob;
    }

    private Location findStandingLocation(Location center, double angle, double radius) {
        World world = center.getWorld();
        Location candidate = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
        if (world == null) return candidate;
        int x = candidate.getBlockX();
        int z = candidate.getBlockZ();
        int startY = center.getBlockY() + 2;
        for (int y = startY; y >= center.getBlockY() - 3; y--) {
            if (world.getBlockAt(x, y - 1, z).getType().isSolid()
                    && world.getBlockAt(x, y, z).isPassable()
                    && world.getBlockAt(x, y + 1, z).isPassable()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return center.clone().add(Math.cos(angle) * 2.2, 1.0, Math.sin(angle) * 2.2);
    }

    private boolean isOwnerAttack(Entity damager, Session session) {
        if (damager instanceof Player player) return player.getUniqueId().equals(session.playerId);
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId().equals(session.playerId);
        }
        return false;
    }

    private Session sessionForAttacker(Entity damager) {
        Session direct = sessionsByMob.get(damager.getUniqueId());
        if (direct != null) return direct;
        if (damager instanceof Projectile projectile) {
            Session tracked = sessionsByProjectile.get(projectile.getUniqueId());
            if (tracked != null) return tracked;
            if (projectile.getShooter() instanceof Entity shooter) {
                return sessionsByMob.get(shooter.getUniqueId());
            }
        }
        return null;
    }

    private void forgetMob(Session session, Mob mob) {
        session.mobs.remove(mob);
        session.spawnLocations.remove(mob.getUniqueId());
        sessionsByMob.remove(mob.getUniqueId());
        untrack(mob);
    }

    private void startDeathSequence(Session session, Location selectedLocation) {
        cancelTask(session.monitorTask);
        session.monitorTask = null;
        List<Mob> dying = new ArrayList<>(session.mobs);
        dying.sort((first, second) -> Double.compare(
                angleFromCenter(session.base, first.getLocation()),
                angleFromCenter(session.base, second.getLocation())));

        session.deathTask = new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (session.completed || session.revealing) {
                    cancel();
                    return;
                }
                while (index < dying.size()) {
                    Mob mob = dying.get(index++);
                    if (!mob.isValid() || mob.isDead()) continue;
                    Location death = mob.getLocation().add(0.0, 0.9, 0.0);
                    mob.getWorld().playSound(death, mob instanceof Zombie
                            ? Sound.ENTITY_ZOMBIE_DEATH : Sound.ENTITY_SKELETON_DEATH, 0.38f, 0.9f + index * 0.05f);
                    ParticleCompat.spawn(mob.getWorld(), death,
                            new String[]{"SOUL", "SMOKE", "SMOKE_NORMAL"}, 16, 0.30, 0.55, 0.30, 0.035);
                    mob.setHealth(0.0);
                    return;
                }
                cancelTask(session.deathTask);
                session.deathTask = null;
                beginReveal(session, selectedLocation, false);
            }
        }.runTaskTimer(plugin, 7L, 8L);
        track(session.deathTask);
    }

    private static double angleFromCenter(Location center, Location point) {
        double angle = Math.atan2(point.getZ() - center.getZ(), point.getX() - center.getX());
        return angle < 0.0 ? angle + Math.PI * 2.0 : angle;
    }

    private void beginReveal(Session session, Location location, boolean forced) {
        if (session.completed || session.revealing) return;
        session.revealing = true;
        cancelTask(session.monitorTask);
        cancelTask(session.deathTask);
        session.monitorTask = null;
        session.deathTask = null;

        for (Mob mob : new ArrayList<>(session.mobs)) {
            forgetMob(session, mob);
            mob.remove();
        }
        removeProjectiles(session);

        World world = location.getWorld();
        if (world == null) {
            cleanup(session, true);
            return;
        }
        ItemStack visual = resolveRewardVisual(session.reward, session.definition);
        Location frameCenter = location.clone().add(0.0, 0.55, 0.0);
        session.rewardReveal = createRewardReveal(frameCenter, visual, resolveRewardName(session.reward, visual));
        for (Entity entity : session.rewardReveal.entities()) track(entity);
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.65f, forced ? 1.10f : 1.55f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, frameCenter, 30, 0.35, 0.45, 0.35, 0.08);
        session.revealTask = new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick++;
                if (session.completed) {
                    cancel();
                    return;
                }
                session.rewardReveal.tick(frameCenter, tick);

                if (tick % 3 == 0) {
                    ParticleCompat.spawn(world, frameCenter.clone().add(0.0, 0.18, 0.0),
                            new String[]{"END_ROD", "ENCHANT"}, 3, 0.24, 0.30, 0.24, 0.015);
                }
                if (tick == 30) {
                    world.playSound(frameCenter, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.55f);
                    ParticleCompat.spawn(world, frameCenter, new String[]{"TOTEM_OF_UNDYING", "END_ROD"},
                            18, 0.35, 0.45, 0.35, 0.045);
                }
                if (tick >= 65) cleanup(session, true);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        track(session.revealTask);
    }

    private void cleanup(Session session, boolean finish) {
        if (session.completed) return;
        session.completed = true;
        cancelTask(session.monitorTask);
        cancelTask(session.deathTask);
        cancelTask(session.revealTask);
        for (Mob mob : new ArrayList<>(session.mobs)) {
            sessionsByMob.remove(mob.getUniqueId());
            mob.remove();
            untrack(mob);
        }
        session.mobs.clear();
        removeProjectiles(session);
        if (session.rewardReveal != null) {
            for (Entity entity : session.rewardReveal.entities()) untrack(entity);
            session.rewardReveal.remove();
        }
        sessionsByPlayer.remove(session.playerId, session);
        if (finish) session.onFinish.run();
    }

    private void removeProjectiles(Session session) {
        for (Projectile projectile : new ArrayList<>(session.projectiles)) {
            sessionsByProjectile.remove(projectile.getUniqueId());
            projectile.remove();
            untrack(projectile);
        }
        session.projectiles.clear();
    }

    private void cancelTask(BukkitTask task) {
        if (task == null) return;
        task.cancel();
        untrack(task);
    }

    private MobRewardVisual createRewardReveal(Location center, ItemStack item, String name) {
        if (ServerCompatibility.hasDisplayEntities()) {
            try {
                Class<?> type = Class.forName("ru.privatenull.cases.animation.ModernMobRewardVisual");
                Object instance = type.getConstructor(Location.class, ItemStack.class, String.class)
                        .newInstance(center, item, name);
                if (instance instanceof MobRewardVisual visual) return visual;
            } catch (Throwable exception) {
                plugin.getLogger().warning("Could not create modern mob reward animation: " + exception.getMessage());
            }
        }
        return new LegacyMobRewardVisual(center, item, name);
    }

    private static final class Session {
        private final UUID playerId;
        private final Player player;
        private final Location base;
        private final CaseDefinition definition;
        private final Reward reward;
        private final Runnable onFinish;
        private final Set<Mob> mobs = new HashSet<>();
        private final Set<Projectile> projectiles = new HashSet<>();
        private final Map<UUID, Location> spawnLocations = new HashMap<>();
        private BukkitTask monitorTask;
        private BukkitTask deathTask;
        private BukkitTask revealTask;
        private MobRewardVisual rewardReveal;
        private boolean revealing;
        private boolean selectionMade;
        private boolean completed;

        private Session(Player player, Location base, CaseDefinition definition, Reward reward, Runnable onFinish) {
            this.playerId = player.getUniqueId();
            this.player = player;
            this.base = base;
            this.definition = definition;
            this.reward = reward;
            this.onFinish = onFinish;
        }
    }
}
