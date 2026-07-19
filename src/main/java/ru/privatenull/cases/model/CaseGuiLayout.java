package ru.privatenull.cases.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CaseGuiLayout(
        int size,
        int openSlot,
        int animationSlot,
        List<Integer> historySlots,
        List<Integer> decorSlots,
        ItemStack decorItem,
        ItemStack animationItem,
        ItemStack emptyHistoryItem
) {

    public static final int DEFAULT_SIZE = 54;
    public static final int DEFAULT_OPEN_SLOT = 22;
    public static final int DEFAULT_ANIMATION_SLOT = 49;

    public static final List<Integer> DEFAULT_HISTORY_SLOTS = List.of(45, 46, 47, 48, 51, 52, 53);
    public static final List<Integer> DEFAULT_DECOR_SLOTS = List.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    );

    public static CaseGuiLayout defaults() {
        return new CaseGuiLayout(
                DEFAULT_SIZE,
                DEFAULT_OPEN_SLOT,
                DEFAULT_ANIMATION_SLOT,
                DEFAULT_HISTORY_SLOTS,
                DEFAULT_DECOR_SLOTS,
                new ItemStack(Material.GRAY_STAINED_GLASS_PANE),
                null,
                new ItemStack(Material.BARRIER)
        );
    }
}
