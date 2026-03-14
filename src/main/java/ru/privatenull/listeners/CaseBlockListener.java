package ru.privatenull.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.privatenull.cases.CaseManager;

public class CaseBlockListener implements Listener {

    private final CaseManager caseManager;

    public CaseBlockListener(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK &&
                e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        if (e.getClickedBlock() == null) return;

        var def = caseManager.getCaseByBlock(e.getClickedBlock());
        if (def == null) return;

        e.setCancelled(true);

        var p = e.getPlayer();
        var msg = caseManager.getPlugin().getMessages();

        if (caseManager.isOpening(p.getUniqueId())) {
            p.sendMessage(msg.get("already-opening"));
            return;
        }

        if (caseManager.isCaseBusy(def, p.getUniqueId())) {
            p.sendMessage(msg.get("case-busy"));
            return;
        }

        caseManager.openCaseGui(p, def);
    }
}