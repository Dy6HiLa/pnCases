package ru.privatenull.gui.machine;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;

public final class MachineGuiListener implements Listener {

    private final CaseManager caseManager;
    private final MachineMainScreen mainScreen;
    private final MachineActionController actions;
    private final MachineTextEditor textEditor;

    public MachineGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;

        MachineCaseState state = new MachineCaseState(caseManager);
        MachineItemFactory itemFactory = new MachineItemFactory(caseManager, state);
        MachineConfigEditor configEditor = new MachineConfigEditor(caseManager);
        MachineLayoutEditor layoutEditor = new MachineLayoutEditor(caseManager, state, itemFactory, configEditor);

        this.mainScreen = new MachineMainScreen(caseManager, state, itemFactory);
        MachineTogglesScreen togglesScreen = new MachineTogglesScreen(caseManager, state, itemFactory);
        MachineHologramScreen hologramScreen = new MachineHologramScreen(caseManager, state, itemFactory);
        MachinePurchaseScreen purchaseScreen = new MachinePurchaseScreen(caseManager, state, itemFactory);
        MachineMenuScreen menuScreen = new MachineMenuScreen(caseManager, itemFactory);
        MachineAnimationScreen animationScreen = new MachineAnimationScreen(caseManager, itemFactory);
        MachineShowcaseScreen showcaseScreen = new MachineShowcaseScreen(caseManager, itemFactory);
        this.textEditor = new MachineTextEditor(caseManager, state, configEditor, mainScreen);
        this.actions = new MachineActionController(
                caseManager,
                state,
                configEditor,
                layoutEditor,
                mainScreen,
                togglesScreen,
                hologramScreen,
                purchaseScreen,
                menuScreen,
                animationScreen,
                showcaseScreen,
                textEditor
        );
    }

    public void openMain(Player player, String caseName) {
        mainScreen.open(player, caseName);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MachineGuiHolder holder)) return;

        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) {
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
        if (slot < 0) return;

        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (definition == null) {
            player.closeInventory();
            player.sendMessage(caseManager.getPlugin().getMessages().get(
                    "machine-case-unloaded", "case", holder.caseName()));
            return;
        }
        actions.handle(holder.type(), player, event, definition, slot);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        textEditor.handleChat(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        textEditor.cancel(event.getPlayer());
    }
}
