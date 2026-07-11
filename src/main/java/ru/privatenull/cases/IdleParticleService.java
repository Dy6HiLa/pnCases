package ru.privatenull.cases;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.util.VisualEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IdleParticleService {

    private static final String DISPLAY_TAG = "pncases_idle_showcase";

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, VisualEntity> displays = new HashMap<>();

    private org.bukkit.scheduler.BukkitTask task;
    private long tick;

    public IdleParticleService(PnCasesPlugin plugin, CaseManager caseManager) {
        this.plugin = plugin;
        this.caseManager = caseManager;
    }

    public void syncCases(Collection<CaseDefinition> definitions) {
        removeDisplays();
        entries.clear();

        for (CaseDefinition definition : definitions) {
            IdleParticleSettings settings = definition.idleParticles();
            if (settings == null || !settings.enabled()) {
                continue;
            }

            ItemStack displayItem = displayItem(definition);
            for (Location location : definition.blockLocations()) {
                if (location != null && location.getWorld() != null) {
                    entries.add(new Entry(
                            entryKey(location),
                            definition.name(),
                            location.clone(),
                            settings,
                            displayItem.clone()
                    ));
                }
            }
        }

        if (entries.isEmpty()) {
            stop();
            return;
        }

        if (task == null || task.isCancelled()) {
            tick = 0L;
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    public void shutdown() {
        entries.clear();
        removeDisplays();
        stop();
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tick++;
        for (Entry entry : entries) {
            Location block = entry.blockLocation();
            World world = block.getWorld();
            if (world == null) {
                removeDisplay(entry.key());
                continue;
            }

            IdleParticleSettings settings = entry.settings();
            Location base = block.clone().add(0.5, 0.08, 0.5);
            if (caseManager.isCaseBlockBusy(block) || !hasViewer(world, base, settings.viewDistance())) {
                removeDisplay(entry.key());
                continue;
            }

            VisualEntity display = ensureDisplay(entry, base);
            Location displayLocation = updateDisplay(display, base, settings);

            if (settings.effectsEnabled() && tick % settings.intervalTicks() == 0) {
                spawnPattern(world, base, displayLocation, settings);
            }
        }
    }

    private VisualEntity ensureDisplay(Entry entry, Location base) {
        VisualEntity current = displays.get(entry.key());
        if (current != null && current.isValid() && !current.isDead()) {
            return current;
        }

        VisualEntity display = VisualEntity.item(displayLocation(base, entry.settings(), 0.0), entry.displayItem().clone());
        display.addScoreboardTag(DISPLAY_TAG);
        setScale(display, displayScale(entry.settings()));
        displays.put(entry.key(), display);
        return display;
    }

    private Location updateDisplay(VisualEntity display, Location base, IdleParticleSettings settings) {
        double bob = Math.sin(tick * 0.075 + settings.speed() * 4.0) * 0.055;
        Location location = displayLocation(base, settings, bob);
        float yaw = (float) ((tick * settings.speed() * 34.0) % 360.0);
        float pitch = (float) (Math.sin(tick * 0.035) * 4.0);
        display.teleport(location);
        display.setRotation(yaw, pitch);
        setScale(display, displayScale(settings));
        return location;
    }

    private Location displayLocation(Location base, IdleParticleSettings settings, double bob) {
        double y = 0.98 + Math.min(0.65, settings.height() * 0.28) + bob;
        return base.clone().add(0.0, y, 0.0);
    }

    private float displayScale(IdleParticleSettings settings) {
        return switch (settings.style()) {
            case CROWN -> 0.86f;
            case DOUBLE_ORBIT -> 0.82f;
            default -> 0.78f;
        };
    }

    private void spawnPattern(World world, Location base, Location displayLocation, IdleParticleSettings settings) {
        double rotation = tick * settings.speed();
        switch (settings.style()) {
            case HORIZONTAL_RING -> {
                drawRuneRing(world, base, settings, rotation, 8, settings.theme().primary(), 0.18);
                drawHalo(world, base, settings, -rotation * 0.62, 6, settings.theme().secondary());
                drawShowcaseSpark(world, displayLocation, settings, 8);
            }
            case VERTICAL_SPIRAL -> {
                drawTwinHelix(world, base, settings, rotation, 3);
                drawBeacon(world, base, settings, rotation);
                drawShowcaseSpark(world, displayLocation, settings, 7);
            }
            case DOUBLE_ORBIT -> {
                drawVerticalOrbit(world, base, settings, rotation, 0.0);
                drawVerticalOrbit(world, base, settings, -rotation * 0.72, Math.PI / 2.0);
                drawRuneRing(world, base, settings, rotation * 0.55, 6, settings.theme().secondary(), 0.22);
                drawShowcaseSpark(world, displayLocation, settings, 9);
            }
            case CROWN -> {
                drawCrown(world, base, settings, rotation);
                drawHalo(world, base, settings, -rotation * 0.45, 7, settings.theme().secondary());
                drawShowcaseSpark(world, displayLocation, settings, 6);
            }
            case AURORA -> {
                drawTwinHelix(world, base, settings, rotation, 2);
                drawRuneRing(world, base, settings, rotation * 0.72, 7, settings.theme().primary(), 0.16);
                drawAuroraNeedles(world, base, settings, rotation);
                drawShowcaseSpark(world, displayLocation, settings, 8);
            }
        }

        if (tick % Math.max(18, settings.intervalTicks() * 8L) == 0) {
            drawPulse(world, base, settings, rotation);
        }
    }

    private void drawRuneRing(
            World world,
            Location base,
            IdleParticleSettings settings,
            double rotation,
            int points,
            Particle particle,
            double y
    ) {
        double radius = settings.radius();
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / points;
            double lift = Math.sin(rotation * 1.2 + i * 0.8) * 0.018;
            Location loc = base.clone().add(Math.cos(angle) * radius, y + lift, Math.sin(angle) * radius);
            spawn(world, particle, loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawHalo(World world, Location base, IdleParticleSettings settings, double rotation, int points, Particle particle) {
        double radius = settings.radius() * 0.58;
        double y = 0.92 + Math.min(0.65, settings.height() * 0.24);
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / points;
            Location loc = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            spawn(world, particle, loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawTwinHelix(World world, Location base, IdleParticleSettings settings, double rotation, int layers) {
        double height = Math.max(0.65, settings.height());
        double radius = settings.radius() * 0.52;
        for (int strand = 0; strand < 2; strand++) {
            for (int i = 0; i < layers; i++) {
                double progress = ((tick * 0.025) + (double) i / layers) % 1.0;
                double angle = rotation + progress * Math.PI * 2.0 + strand * Math.PI;
                Location loc = base.clone().add(
                        Math.cos(angle) * radius,
                        0.22 + progress * height,
                        Math.sin(angle) * radius
                );
                spawn(world, strand == 0 ? settings.theme().primary() : settings.theme().secondary(), loc, 1,
                        0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void drawVerticalOrbit(World world, Location base, IdleParticleSettings settings, double rotation, double planeRotation) {
        double radius = settings.radius() * 0.64;
        double centerY = 0.86 + Math.min(0.55, settings.height() * 0.18);
        for (int i = 0; i < 5; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / 5.0;
            double horizontal = Math.cos(angle) * radius;
            double x = Math.cos(planeRotation) * horizontal;
            double z = Math.sin(planeRotation) * horizontal;
            Location loc = base.clone().add(x, centerY + Math.sin(angle) * 0.58, z);
            spawn(world, i % 2 == 0 ? settings.theme().primary() : settings.theme().secondary(), loc, 1,
                    0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawBeacon(World world, Location base, IdleParticleSettings settings, double rotation) {
        double height = Math.max(0.9, settings.height());
        for (int i = 0; i < 4; i++) {
            double progress = ((tick * 0.018) + i * 0.25) % 1.0;
            double twist = rotation * 0.7 + progress * Math.PI * 2.0;
            Location loc = base.clone().add(
                    Math.cos(twist) * settings.radius() * 0.18,
                    0.24 + progress * height,
                    Math.sin(twist) * settings.radius() * 0.18
            );
            spawn(world, settings.theme().secondary(), loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawCrown(World world, Location base, IdleParticleSettings settings, double rotation) {
        int points = 8;
        double radius = settings.radius() * 0.68;
        double y = 1.08 + Math.min(0.65, settings.height() * 0.26);
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / points;
            double spike = i % 2 == 0 ? 0.22 : 0.06;
            Location loc = base.clone().add(Math.cos(angle) * radius, y + spike, Math.sin(angle) * radius);
            spawn(world, i % 2 == 0 ? settings.theme().secondary() : settings.theme().primary(), loc, 1,
                    0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawAuroraNeedles(World world, Location base, IdleParticleSettings settings, double rotation) {
        for (int i = 0; i < 3; i++) {
            double phase = rotation * 0.55 + i * (Math.PI * 2.0 / 3.0);
            double radius = settings.radius() * (0.38 + i * 0.09);
            Location loc = base.clone().add(
                    Math.cos(phase) * radius,
                    0.55 + Math.sin(rotation + i) * 0.18,
                    Math.sin(phase) * radius
            );
            spawn(world, settings.theme().secondary(), loc, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    private void drawShowcaseSpark(World world, Location displayLocation, IdleParticleSettings settings, int everyTicks) {
        if (tick % everyTicks != 0) {
            return;
        }

        double angle = tick * settings.speed() * 1.7;
        for (int i = 0; i < 2; i++) {
            double side = angle + i * Math.PI;
            Location loc = displayLocation.clone().add(Math.cos(side) * 0.32, 0.02, Math.sin(side) * 0.32);
            spawn(world, settings.theme().secondary(), loc, 1, 0.015, 0.015, 0.015, 0.0);
        }
    }

    private void drawPulse(World world, Location base, IdleParticleSettings settings, double rotation) {
        int points = 10;
        double radius = settings.radius() * 0.92;
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / points;
            Location loc = base.clone().add(Math.cos(angle) * radius, 0.30, Math.sin(angle) * radius);
            spawn(world, settings.theme().secondary(), loc, 1, 0.015, 0.015, 0.015, 0.0);
        }
    }

    private boolean hasViewer(World world, Location base, double viewDistance) {
        double maxDistanceSquared = viewDistance * viewDistance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(base) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void removeDisplays() {
        for (VisualEntity display : displays.values()) {
            safeRemove(display);
        }
        displays.clear();
    }

    private void removeDisplay(String key) {
        VisualEntity display = displays.remove(key);
        safeRemove(display);
    }

    private void safeRemove(VisualEntity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void spawn(World world, Particle particle, Location loc, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException ignored) {
            world.spawnParticle(Particle.END_ROD, loc, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    private ItemStack displayItem(CaseDefinition definition) {
        ItemStack configured = definition.idleParticles().displayItem();
        if (configured != null && !configured.getType().isAir()) {
            configured.setAmount(1);
            return configured;
        }
        if (definition.openButton() != null && !definition.openButton().getType().isAir()) {
            return definition.openButton().clone();
        }
        if (definition.animationItems() != null) {
            for (ItemStack item : definition.animationItems()) {
                if (item != null && !item.getType().isAir()) {
                    return item.clone();
                }
            }
        }
        return new ItemStack(Material.CHEST);
    }

    private static void setScale(VisualEntity display, float scale) {
        display.setScale(scale);
    }

    private static String entryKey(Location location) {
        UUID worldId = location.getWorld().getUID();
        return worldId + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record Entry(
            String key,
            String caseName,
            Location blockLocation,
            IdleParticleSettings settings,
            ItemStack displayItem
    ) {
    }
}
