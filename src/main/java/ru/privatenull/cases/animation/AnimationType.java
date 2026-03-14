package ru.privatenull.cases.animation;

import org.bukkit.Material;

public enum AnimationType {

    ANVIL(
            "§x§F§F§A§A§0§0§lНаковальня",
            "§7Наковальня падает с высоты\n§7и выбивает награду под ударом.",
            Material.ANVIL
    ),
    DYNAMITE(
            "§x§F§F§5§5§2§2§lДинамит",
            "§7Динамит взрывается, разбрасывает\n§7порох и показывает награду.",
            Material.TNT
    ),
    PORTAL(
            "§x§9§9§0§0§F§F§lПортал",
            "§7Пурпурные столбы окружают кейс,\n§7из портала вырывается награда.",
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