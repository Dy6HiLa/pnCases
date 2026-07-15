package ru.privatenull.cases.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ru.privatenull.config.ConfigValues.integer;

public final class CaseBlockCodec {

    public List<Location> readLoadedLocations(ConfigurationSection caseSection) {
        List<Location> locations = new ArrayList<>();
        for (Map<String, Object> block : readConfiguredBlocks(caseSection)) {
            World world = Bukkit.getWorld(String.valueOf(block.get("world")));
            if (world == null) continue;

            Location location = new Location(
                    world,
                    integer(block.get("x"), 0),
                    integer(block.get("y"), 0),
                    integer(block.get("z"), 0)
            );
            if (locations.stream().noneMatch(existing -> key(existing).equals(key(location)))) {
                locations.add(location);
            }
        }
        return locations;
    }

    public List<Map<String, Object>> readConfiguredBlocks(ConfigurationSection caseSection) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (caseSection == null) return blocks;

        addBlock(blocks, fromSection(caseSection.getConfigurationSection("block")));
        for (Object raw : caseSection.getList("blocks", Collections.emptyList())) {
            addBlock(blocks, fromObject(raw));
        }
        return blocks;
    }

    public void addBlock(List<Map<String, Object>> blocks, Map<String, Object> candidate) {
        Map<String, Object> normalized = normalize(candidate);
        if (normalized == null) return;

        String candidateKey = key(normalized);
        if (blocks.stream().noneMatch(existing -> key(existing).equals(candidateKey))) {
            blocks.add(normalized);
        }
    }

    private Map<String, Object> fromSection(ConfigurationSection section) {
        if (section == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("world", section.getString("world", ""));
        result.put("x", section.get("x"));
        result.put("y", section.get("y"));
        result.put("z", section.get("z"));
        return result;
    }

    private Map<String, Object> fromObject(Object raw) {
        if (raw instanceof ConfigurationSection section) return fromSection(section);
        if (!(raw instanceof Map<?, ?> map)) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("world", map.get("world"));
        result.put("x", map.get("x"));
        result.put("y", map.get("y"));
        result.put("z", map.get("z"));
        return result;
    }

    private Map<String, Object> normalize(Map<String, Object> raw) {
        if (raw == null) return null;
        String world = raw.get("world") == null ? "" : String.valueOf(raw.get("world")).trim();
        if (world.isBlank() || !raw.containsKey("x") || !raw.containsKey("y") || !raw.containsKey("z")) return null;
        Integer x = coordinate(raw.get("x"));
        Integer y = coordinate(raw.get("y"));
        Integer z = coordinate(raw.get("z"));
        if (x == null || y == null || z == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("world", world);
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        return result;
    }

    private Integer coordinate(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String key(Map<String, Object> block) {
        return String.valueOf(block.get("world")).toLowerCase(Locale.ROOT)
                + ':' + integer(block.get("x"), 0)
                + ':' + integer(block.get("y"), 0)
                + ':' + integer(block.get("z"), 0);
    }

    private String key(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT)
                + ':' + location.getBlockX()
                + ':' + location.getBlockY()
                + ':' + location.getBlockZ();
    }
}
