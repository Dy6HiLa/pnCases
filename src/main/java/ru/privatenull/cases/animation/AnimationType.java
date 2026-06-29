package ru.privatenull.cases.animation;

import org.bukkit.Material;

public enum AnimationType {

    ANVIL(
            "§x§F§F§A§A§0§0§lНаковальня",
            "§7С неба падает огромная наковальня,\n§7дробит кейс и выбивает награду\n§7из-под сильного удара.",
            Material.ANVIL
    ),
    DYNAMITE(
            "§x§F§F§5§5§2§2§lДинамит",
            "§7Динамит летит по дуге и врезается\n§7в кейс. Взрыв, дым, огонь\n§7и твоя награда.",
            Material.TNT
    ),
    PORTAL(
            "§x§2§2§0§0§C§C§lЧёрная дыра",
            "§7Предметы разлетаются вокруг кейса.\n§7Затем чёрная дыра засасывает всё\n§7и выпускает твою награду.",
            Material.ENDER_EYE
    ),
    POISON(
            "§x§6§5§E§A§6§A§lОтравление",
            "§7Кейс окутывает ядовитый туман,\n§7появляется куб слизи,\n§7а после взрыва выходит награда.",
            Material.SLIME_BALL
    ),
    CAULDRON(
            "§x§4§2§9§F§9§1§lАстральный разлом",
            "§7Вокруг кейса собираются обсидиановые осколки,\n§7открывается разлом, предметы уходят в орбиту,\n§7а настоящая награда вырывается из портала.",
            Material.ECHO_SHARD
    );

    private final String displayName;
    private final String description;
    private final Material icon;

    AnimationType(String displayName, String description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public Material icon() { return icon; }
}
