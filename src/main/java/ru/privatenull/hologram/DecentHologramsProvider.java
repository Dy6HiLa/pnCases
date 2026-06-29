package ru.privatenull.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class DecentHologramsProvider implements HologramProvider {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    @Override
    public void create(HologramSpec spec) {
        if (!isAvailable()) {
            throw new IllegalStateException("DecentHolograms API недоступен");
        }

        remove(spec.name());

        Hologram hologram = switch (spec.type()) {
            case ITEM -> createItemHologram(spec);
            case BLOCK -> createBlockHologram(spec);
            case TEXT -> createTextHologram(spec);
        };

        hologram.setSaveToFile(false);
        hologram.updateAll();
    }

    @Override
    public void remove(String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        try {
            DHAPI.removeHologram(name);
        } catch (Throwable ignored) {
        }
    }

    private static Hologram createTextHologram(HologramSpec spec) {
        return DHAPI.createHologram(spec.name(), spec.location(), false, colorLines(spec.lines()));
    }

    private static Hologram createItemHologram(HologramSpec spec) {
        Hologram hologram = DHAPI.createHologram(spec.name(), spec.location(), false);
        ItemStack item = spec.item() == null ? new ItemStack(Material.CHEST) : spec.item().clone();
        DHAPI.addHologramLine(hologram, item);
        return hologram;
    }

    private static Hologram createBlockHologram(HologramSpec spec) {
        Hologram hologram = DHAPI.createHologram(spec.name(), spec.location(), false);
        DHAPI.addHologramLine(hologram, spec.block() == null ? Material.CHEST : spec.block());
        return hologram;
    }

    private static List<String> colorLines(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(line == null ? "" : line.replace('&', '§'));
        }
        return out;
    }
}
