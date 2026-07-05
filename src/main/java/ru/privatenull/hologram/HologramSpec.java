package ru.privatenull.hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record HologramSpec(
        String name,
        String caseName,
        HologramType type,
        Location location,
        List<String> lines,
        ItemStack item,
        Material block,
        String billboard,
        String textAlignment,
        Boolean textShadow,
        Boolean seeThrough,
        Color background,
        Integer textUpdateInterval,
        Float shadowRadius,
        Float shadowStrength,
        Integer visibilityDistance,
        Integer interpolationDuration,
        HologramVector scale,
        HologramVector translation,
        HologramBrightness brightness
) {
    public record HologramVector(float x, float y, float z) {
    }

    public record HologramBrightness(int block, int sky) {
    }
}
