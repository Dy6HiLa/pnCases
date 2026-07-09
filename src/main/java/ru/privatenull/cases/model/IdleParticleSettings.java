package ru.privatenull.cases.model;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.util.MaterialCompat;
import ru.privatenull.util.ParticleCompat;

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
        AURORA("Сияние", MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR")),
        HORIZONTAL_RING("Горизонтальное кольцо", Material.ENDER_EYE),
        VERTICAL_SPIRAL("Вертикальная спираль", Material.BLAZE_ROD),
        DOUBLE_ORBIT("Двойная орбита", MaterialCompat.first("RECOVERY_COMPASS", "COMPASS")),
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
        MAGIC("Магия", MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR"), new String[]{"END_ROD"}, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"}),
        PORTAL("Портал", Material.ENDER_EYE, new String[]{"PORTAL"}, new String[]{"REVERSE_PORTAL", "PORTAL"}),
        ELECTRIC("Искры", MaterialCompat.first("LIGHTNING_ROD", "BLAZE_ROD"), new String[]{"ELECTRIC_SPARK", "END_ROD"}, new String[]{"END_ROD"}),
        FIRE("Пламя", Material.BLAZE_POWDER, new String[]{"FLAME"}, new String[]{"LAVA"}),
        TOXIC("Яд", Material.SLIME_BALL, new String[]{"WITCH", "SPELL_WITCH"}, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"});

        private final String displayName;
        private final Material icon;
        private final String[] primary;
        private final String[] secondary;

        Theme(String displayName, Material icon, String[] primary, String[] secondary) {
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
            return ParticleCompat.first(primary);
        }

        public Particle secondary() {
            return ParticleCompat.first(secondary);
        }
    }
}
