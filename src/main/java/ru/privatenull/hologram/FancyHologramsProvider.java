package ru.privatenull.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.BlockHologramData;
import de.oliver.fancyholograms.api.data.DisplayHologramData;
import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.data.ItemHologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class FancyHologramsProvider implements HologramProvider {

    private final HologramManager manager;

    public FancyHologramsProvider() {
        this.manager = FancyHologramsPlugin.get().getHologramManager();
    }

    @Override
    public boolean isAvailable() {
        return FancyHologramsPlugin.isEnabled() && manager != null;
    }

    @Override
    public void create(HologramSpec spec) {
        if (!isAvailable()) {
            throw new IllegalStateException("FancyHolograms API недоступен");
        }

        remove(spec.name());

        HologramData data = buildData(spec);
        data.setPersistent(false);
        if (spec.visibilityDistance() != null) {
            data.setVisibilityDistance(Math.max(1, spec.visibilityDistance()));
        }

        applyDisplayStyles(data, spec);

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
    }

    @Override
    public void remove(String name) {
        if (manager == null || name == null || name.isBlank()) {
            return;
        }

        manager.getHologram(name).ifPresent(manager::removeHologram);
    }

    private static HologramData buildData(HologramSpec spec) {
        return switch (spec.type()) {
            case ITEM -> buildItemData(spec);
            case BLOCK -> buildBlockData(spec);
            case TEXT -> buildTextData(spec);
        };
    }

    private static TextHologramData buildTextData(HologramSpec spec) {
        TextHologramData data = new TextHologramData(spec.name(), spec.location());
        data.setText(spec.lines());

        if (spec.textShadow() != null) {
            data.setTextShadow(spec.textShadow());
        }
        if (spec.seeThrough() != null) {
            data.setSeeThrough(spec.seeThrough());
        }
        if (spec.textAlignment() != null) {
            data.setTextAlignment(spec.textAlignment());
        }
        if (spec.background() != null) {
            data.setBackground(spec.background());
        }
        if (spec.textUpdateInterval() != null) {
            data.setTextUpdateInterval(Math.max(0, spec.textUpdateInterval()));
        }

        return data;
    }

    private static ItemHologramData buildItemData(HologramSpec spec) {
        ItemHologramData data = new ItemHologramData(spec.name(), spec.location());
        ItemStack item = spec.item() == null ? new ItemStack(Material.CHEST) : spec.item().clone();
        data.setItemStack(item);
        return data;
    }

    private static BlockHologramData buildBlockData(HologramSpec spec) {
        BlockHologramData data = new BlockHologramData(spec.name(), spec.location());
        data.setBlock(spec.block() == null ? Material.CHEST : spec.block());
        return data;
    }

    private static void applyDisplayStyles(HologramData data, HologramSpec spec) {
        if (!(data instanceof DisplayHologramData displayData)) {
            return;
        }

        if (spec.billboard() != null) {
            displayData.setBillboard(spec.billboard());
        }
        if (spec.shadowRadius() != null) {
            displayData.setShadowRadius(spec.shadowRadius());
        }
        if (spec.shadowStrength() != null) {
            displayData.setShadowStrength(spec.shadowStrength());
        }
        if (spec.interpolationDuration() != null) {
            displayData.setInterpolationDuration(Math.max(0, spec.interpolationDuration()));
        }
        if (spec.scale() != null) {
            displayData.setScale(spec.scale());
        }
        if (spec.translation() != null) {
            displayData.setTranslation(spec.translation());
        }
        if (spec.brightness() != null) {
            displayData.setBrightness(spec.brightness());
        }
    }
}
