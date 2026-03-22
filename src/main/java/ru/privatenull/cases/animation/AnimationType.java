package ru.privatenull.cases.animation;

import org.bukkit.Material;

public enum AnimationType {

    ANVIL(
            "§x§F§F§A§A§0§0§lНаковальня",
            "§7С неба рушится огромная наковальня,\n§7дробит кейс и разлетается обломками —\n§7из-под удара вырывается твоя награда.",
            Material.ANVIL
    ),
    DYNAMITE(
            "§x§F§F§5§5§2§2§lДинамит",
            "§7Динамит летит по параболе и врезается\n§7в кейс. Взрыв разносит всё вокруг:\n§7дебрис, порох, дым — и твоя награда.",
            Material.TNT
    ),
    PORTAL(
            "§x§2§2§0§0§C§C§lЧёрная дыра",
            "§7Предметы разлетаются во все стороны.\n§7Затем чёрная дыра засасывает всё\n§7обратно — и выпускает твою награду.",
            Material.ENDER_EYE
    );

    private final String displayName;
    private final String description;
    private final Material icon;

    AnimationType(String displayName, String description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Material icon() {
        return icon;
    }
}