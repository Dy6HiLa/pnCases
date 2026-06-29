package ru.privatenull.hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.List;

public record HologramSpec(
        String name,
        String caseName,
        HologramType type,
        Location location,
        List<String> lines,
        ItemStack item,
        Material block,
        Display.Billboard billboard,
        TextDisplay.TextAlignment textAlignment,
        Boolean textShadow,
        Boolean seeThrough,
        Color background,
        Integer textUpdateInterval,
        Float shadowRadius,
        Float shadowStrength,
        Integer visibilityDistance,
        Integer interpolationDuration,
        Vector3f scale,
        Vector3f translation,
        Display.Brightness brightness
) {
}
