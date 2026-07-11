package ru.privatenull.cases.view;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.model.CaseDefinition;

public interface CaseView {

    ItemStack buildOpenButton(Player player, CaseDefinition definition);

    ItemStack buildAnimationButton(Player player, CaseDefinition definition);

    ItemStack buildPreviewButton(CaseDefinition definition);

    void fill(Inventory inventory, Player player, CaseDefinition definition);

    void open(Player player, CaseDefinition definition);
}
