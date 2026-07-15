package ru.privatenull.cases.model;

import org.bukkit.Material;
import ru.privatenull.util.MaterialCompat;

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
            MaterialCompat.first("ECHO_SHARD", "NETHER_STAR")
    ),
    FORTUNE_RING(
            "§x§F§F§D§D§5§5§lКруг фортуны",
            "§7Над кейсом появляется светящийся круг,\n§7предметы крутятся по орбите и замедляются,\n§7пока настоящая награда не выходит в центр.",
            MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR")
    ),
    PILLAGER_RAID(
            "§x§F§F§8§4§4§4§lОсада разбойников",
            "§7Четыре разбойника окружают бочку,\n§7по очереди ломают её топорами,\n§7а из осколков появляется награда.",
            Material.IRON_AXE
    ),
    AQUARIUM(
            "&#4FB6FF&lВодяная капсула",
            "&7Кейс превращается в стеклянную капсулу с сердцем моря.\n&7Из воды плавно появляется награда.",
            Material.HEART_OF_THE_SEA
    ),
    MOB_HUNT(
            "&#E05252&lВыбор моба",
            "&7Зомби и скелеты стоят вокруг кейса.\n&7Выбери одного моба и ударь его — остальные\n&7погибнут, а из выбранного выпадет награда.",
            Material.ROTTEN_FLESH
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
        return this == MOB_HUNT ? "&#E05252&lОхота на стражей" : displayName;
    }
    public String description() { return description; }
    public Material icon() { return icon; }
}
