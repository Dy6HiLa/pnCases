package ru.privatenull.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

public final class ParticleCompat {

    private ParticleCompat() {
    }

    public static Particle first(String... names) {
        if (names != null) {
            for (String name : names) {
                Particle particle = byName(name);
                if (particle != null) {
                    return particle;
                }
            }
        }

        Particle fallback = byName("END_ROD");
        if (fallback != null) {
            return fallback;
        }

        Particle[] values = Particle.values();
        return values.length == 0 ? null : values[0];
    }

    public static void spawn(World world, Location location, String[] names, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        Particle particle = first(names);
        spawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void spawn(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || location == null || particle == null) {
            return;
        }
        try {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException ignored) {
            Particle fallback = first("END_ROD", "CLOUD");
            if (fallback != null && fallback != particle) {
                try {
                    world.spawnParticle(fallback, location, count, offsetX, offsetY, offsetZ, extra);
                } catch (IllegalArgumentException ignoredAgain) {
                }
            }
        }
    }

    public static void spawnBlock(World world, Location location, int count,
                                  double offsetX, double offsetY, double offsetZ, double extra,
                                  Material material) {
        if (world == null || location == null) {
            return;
        }

        Particle particle = first("BLOCK", "BLOCK_CRACK", "BLOCK_DUST");
        Material safeMaterial = material == null || material.isAir() ? Material.STONE : material;
        try {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, safeMaterial.createBlockData());
        } catch (Throwable ignored) {
            spawn(world, first("CLOUD", "END_ROD"), location, Math.max(1, count / 3), offsetX, offsetY, offsetZ, extra);
        }
    }

    private static Particle byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
