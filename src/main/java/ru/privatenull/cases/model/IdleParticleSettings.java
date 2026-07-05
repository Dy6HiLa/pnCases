package ru.privatenull.cases.model;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public record IdleParticleSettings(
        boolean enabled,
        boolean effectsEnabled,
        Style style,
        Theme theme,
        int intervalTicks,
        double radius,
        double height,
        double speed,
        double viewDistance,
        ItemStack displayItem
) {

    public IdleParticleSettings {
        displayItem = displayItem == null || displayItem.getType().isAir() ? null : displayItem.clone();
    }

    public static IdleParticleSettings defaults() {
        return new IdleParticleSettings(true, true, Style.AURORA, Theme.MAGIC, 2, 0.85, 1.35, 0.14, 28.0, null);
    }

    @Override
    public ItemStack displayItem() {
        return displayItem == null ? null : displayItem.clone();
    }

    public enum Style {
        AURORA("Сияние", Material.AMETHYST_SHARD),
        HORIZONTAL_RING("Горизонтальное кольцо", Material.ENDER_EYE),
        VERTICAL_SPIRAL("Вертикальная спираль", Material.BREEZE_ROD),
        DOUBLE_ORBIT("Двойная орбита", Material.RECOVERY_COMPASS),
        CROWN("Корона", Material.NETHER_STAR);

        private final String displayName;
        private final Material icon;

        Style(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String displayName() {
            return displayName;
        }

        public Material icon() {
            return icon;
        }
    }

    public enum Theme {
        MAGIC("Магия", Material.AMETHYST_SHARD, Particle.END_ROD, Particle.ENCHANT),
        PORTAL("Портал", Material.ENDER_EYE, Particle.PORTAL, Particle.REVERSE_PORTAL),
        ELECTRIC("Искры", Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK, Particle.END_ROD),
        FIRE("Пламя", Material.BLAZE_POWDER, Particle.FLAME, Particle.LAVA),
        TOXIC("Яд", Material.SLIME_BALL, Particle.WITCH, Particle.ENCHANT);

        private final String displayName;
        private final Material icon;
        private final Particle primary;
        private final Particle secondary;

        Theme(String displayName, Material icon, Particle primary, Particle secondary) {
            this.displayName = displayName;
            this.icon = icon;
            this.primary = primary;
            this.secondary = secondary;
        }

        public String displayName() {
            return displayName;
        }

        public Material icon() {
            return icon;
        }

        public Particle primary() {
            return primary;
        }

        public Particle secondary() {
            return secondary;
        }
    }
}
