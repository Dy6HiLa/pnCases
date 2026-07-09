package ru.privatenull.util;

import org.bukkit.Material;

public final class MaterialCompat {

    private MaterialCompat() {
    }

    public static Material first(String... names) {
        if (names != null) {
            for (String name : names) {
                Material material = match(name);
                if (material != null) {
                    return material;
                }
            }
        }
        return Material.STONE;
    }

    private static Material match(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Material.matchMaterial(name.trim());
    }
}
