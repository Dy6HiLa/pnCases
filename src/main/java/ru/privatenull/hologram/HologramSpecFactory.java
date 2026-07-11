package ru.privatenull.hologram;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.util.ColorUtil;
import ru.privatenull.util.ItemFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HologramSpecFactory {

    HologramSpec build(CaseDefinition definition, ConfigurationSection config, Location blockLocation) {
        return new HologramSpec(
                name(definition, blockLocation),
                definition.name(),
                type(config),
                location(blockLocation, config),
                lines(definition, config, blockLocation),
                item(config),
                material(config.getString("block", config.getString("block_material", "CHEST")), Material.CHEST),
                enumText(config.getString("billboard", "CENTER"), "CENTER", "FIXED", "VERTICAL", "HORIZONTAL", "CENTER"),
                enumText(config.getString("text_alignment", "CENTER"), "CENTER", "LEFT", "RIGHT", "CENTER"),
                optionalBoolean(config, "text_shadow"),
                optionalBoolean(config, "see_through"),
                config.contains("background") ? color(config.getString("background", "")) : null,
                config.contains("text_update_interval") ? Math.max(0, config.getInt("text_update_interval", 0)) : null,
                config.contains("shadow_radius") ? (float) config.getDouble("shadow_radius") : null,
                config.contains("shadow_strength") ? (float) config.getDouble("shadow_strength") : null,
                config.contains("visibility_distance") ? Math.max(1, config.getInt("visibility_distance")) : null,
                config.contains("interpolation_duration") ? Math.max(0, config.getInt("interpolation_duration")) : null,
                vector(config, "scale"),
                vector(config, "translation"),
                brightness(config)
        );
    }

    List<Location> blockLocations(CaseDefinition definition) {
        if (definition == null) return List.of();
        if (!definition.blockLocations().isEmpty()) return definition.blockLocations();
        return definition.blockLocation() == null ? List.of() : List.of(definition.blockLocation());
    }

    String name(CaseDefinition definition, Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "world" : world.getName();
        return prefix(definition)
                + '_' + sanitize(worldName)
                + '_' + location.getBlockX()
                + '_' + location.getBlockY()
                + '_' + location.getBlockZ();
    }

    String prefix(CaseDefinition definition) {
        return "pncases_" + sanitize(definition.name());
    }

    String legacyName(CaseDefinition definition) {
        return "pncases_" + definition.name();
    }

    private HologramType type(ConfigurationSection config) {
        String raw = config.getString("type", "TEXT");
        try {
            return HologramType.valueOf(raw == null ? "TEXT" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HologramType.TEXT;
        }
    }

    private Location location(Location block, ConfigurationSection config) {
        Location result = block.clone().add(0.5, 0.0, 0.5);
        double x = config.getDouble("x", 0.0);
        double y = config.contains("height") ? config.getDouble("height") : config.getDouble("y", 1.8);
        double z = config.getDouble("z", 0.0);
        ConfigurationSection offset = config.getConfigurationSection("offset");
        if (offset != null) {
            x = offset.getDouble("x", x);
            y = offset.contains("height") ? offset.getDouble("height") : offset.getDouble("y", y);
            z = offset.getDouble("z", z);
        }
        return result.add(x, y, z);
    }

    private List<String> lines(CaseDefinition definition, ConfigurationSection config, Location block) {
        List<String> configured = config.getStringList("lines");
        if (configured.isEmpty()) {
            String line = config.getString("line");
            configured = line == null || line.isBlank() ? List.of("&e" + definition.name()) : List.of(line);
        }
        List<String> result = new ArrayList<>(configured.size());
        for (String line : configured) result.add(placeholders(line, definition, block));
        return result;
    }

    private ItemStack item(ConfigurationSection config) {
        ItemStack configured = ItemFactory.fromSection(config.getConfigurationSection("item"));
        return configured != null
                ? configured
                : new ItemStack(material(config.getString("material", "CHEST"), Material.CHEST));
    }

    private HologramSpec.HologramBrightness brightness(ConfigurationSection config) {
        ConfigurationSection section = config.getConfigurationSection("brightness");
        if (section == null || (!section.contains("block") && !section.contains("sky"))) return null;
        return new HologramSpec.HologramBrightness(
                clamp(section.getInt("block", 15), 0, 15),
                clamp(section.getInt("sky", 15), 0, 15)
        );
    }

    private HologramSpec.HologramVector vector(ConfigurationSection root, String key) {
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section != null) {
            if (!section.contains("x") && !section.contains("y") && !section.contains("z")) return null;
            return new HologramSpec.HologramVector(
                    (float) section.getDouble("x", 0.0),
                    (float) section.getDouble("y", 0.0),
                    (float) section.getDouble("z", 0.0)
            );
        }
        List<?> values = root.getList(key);
        if (values == null || values.size() < 3) return null;
        try {
            return new HologramSpec.HologramVector(
                    Float.parseFloat(String.valueOf(values.get(0))),
                    Float.parseFloat(String.valueOf(values.get(1))),
                    Float.parseFloat(String.valueOf(values.get(2)))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String placeholders(String value, CaseDefinition definition, Location block) {
        if (value == null) return "";
        String guiRaw = definition.guiTitle() == null ? "" : definition.guiTitle();
        String guiPlain = ChatColor.stripColor(ColorUtil.colorize(guiRaw));
        World world = block.getWorld();
        return value
                .replace("{case}", definition.name())
                .replace("{gui_title}", guiRaw)
                .replace("{gui_title_plain}", guiPlain == null ? "" : guiPlain)
                .replace("{world}", world == null ? "world" : world.getName())
                .replace("{x}", String.valueOf(block.getBlockX()))
                .replace("{y}", String.valueOf(block.getBlockY()))
                .replace("{z}", String.valueOf(block.getBlockZ()));
    }

    private String enumText(String raw, String fallback, String... supported) {
        String normalized = raw == null ? fallback : raw.toUpperCase(Locale.ROOT);
        for (String value : supported) {
            if (value.equals(normalized)) return normalized;
        }
        return fallback;
    }

    private Boolean optionalBoolean(ConfigurationSection config, String key) {
        return config.contains(key) ? config.getBoolean(key) : null;
    }

    private Material material(String raw, Material fallback) {
        Material result = raw == null ? null : Material.matchMaterial(raw);
        return result == null ? fallback : result;
    }

    private Color color(String value) {
        if (value == null) return null;
        String raw = value.trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("null")) return null;
        try {
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            if (hex.matches("^[0-9a-fA-F]{6}$")) return Color.fromRGB(Integer.parseInt(hex, 16));
            String[] parts = raw.split("[,; ]+");
            if (parts.length >= 3) {
                return Color.fromRGB(
                        clamp(Integer.parseInt(parts[0]), 0, 255),
                        clamp(Integer.parseInt(parts[1]), 0, 255),
                        clamp(Integer.parseInt(parts[2]), 0, 255)
                );
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private String sanitize(String value) {
        return (value == null ? "case" : value).replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
