package ru.privatenull.cases.reward;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.storage.OpenHistoryStorage;
import ru.privatenull.util.ColorUtil;

import java.util.List;
import java.util.Map;

public final class RewardDeliveryService {

    private final PnCasesPlugin plugin;
    private final OpenHistoryStorage history;
    private final RewardPresentationService presentation;

    public RewardDeliveryService(PnCasesPlugin plugin, OpenHistoryStorage history,
                                 RewardPresentationService presentation) {
        this.plugin = plugin;
        this.history = history;
        this.presentation = presentation;
    }

    public void deliver(Player player, CaseDefinition definition, Reward reward) {
        ItemStack visual = presentation.buildDisplayItem(definition, reward);
        String rewardLabel = color(presentation.resolveViewName(reward, visual));
        boolean delivered = switch (reward.type()) {
            case ITEM -> deliverItem(player, reward, rewardLabel);
            case LUCKPERMS -> deliverLuckPerms(player, reward, rewardLabel);
            case VAULT -> deliverVault(player, reward, rewardLabel);
            case PLAYERPOINTS -> deliverPlayerPoints(player, reward, rewardLabel);
        };
        if (!delivered) return;

        if (definition != null) {
            history.add(definition.name(), player.getName(), rewardLabel);
        }
        List<String> broadcast = plugin.getMessages().getList("broadcast",
                "player", player.getName(),
                "case", caseDisplayName(definition),
                "reward", rewardLabel);
        Bukkit.getOnlinePlayers().forEach(online -> broadcast.forEach(online::sendMessage));
    }

    private boolean deliverItem(Player player, Reward reward, String rewardLabel) {
        ItemStack item = reward.item();
        if (item == null) {
            player.sendMessage(plugin.getMessages().getOr("reward.invalid", "reward-invalid"));
            plugin.getLogger().warning("Не удалось выдать ITEM-награду игроку " + player.getName()
                    + ": предмет отсутствует.");
            return false;
        }
        giveToInventoryOrDrop(player, item.clone());
        sendRewardMessage(player, reward, rewardLabel, "", "", "reward-default");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.6f);
        return true;
    }

    private boolean deliverLuckPerms(Player player, Reward reward, String rewardLabel) {
        String subject = player.getUniqueId().toString();
        String duration = reward.lpDuration();
        if (reward.lpGroup() != null && !reward.lpGroup().isBlank()) {
            String command = duration != null && !duration.isBlank()
                    ? "lp user " + subject + " parent addtemp " + reward.lpGroup() + " " + duration
                    : "lp user " + subject + " parent add " + reward.lpGroup();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (reward.lpNode() != null && !reward.lpNode().isBlank()) {
            String command = duration != null && !duration.isBlank()
                    ? "lp user " + subject + " permission settemp " + reward.lpNode() + " true " + duration
                    : "lp user " + subject + " permission set " + reward.lpNode() + " true";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        sendRewardMessage(player, reward, rewardLabel, "", "", "reward-luckperms");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.2f);
        return true;
    }

    private boolean deliverVault(Player player, Reward reward, String rewardLabel) {
        var economy = plugin.getVaultEconomy();
        String amount = presentation.formatVaultAmount(reward.vaultAmount());
        if (economy == null || !economy.isAvailable() || !economy.deposit(player, reward.vaultAmount())) {
            player.sendMessage(plugin.getMessages().getOr("reward.provider-missing", "reward-provider-missing",
                    "provider", "Vault"));
            plugin.getLogger().warning("Не удалось выдать Vault-награду игроку " + player.getName()
                    + ": " + reward.vaultAmount());
            return false;
        }
        sendRewardMessage(player, reward, rewardLabel, amount, "", "reward.vault", "reward-vault");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.7f);
        return true;
    }

    private boolean deliverPlayerPoints(Player player, Reward reward, String rewardLabel) {
        var points = plugin.getPlayerPoints();
        String amount = presentation.formatPlayerPointsAmount(reward.playerPointsAmount());
        if (points == null || !points.isAvailable() || !points.give(player.getUniqueId(), reward.playerPointsAmount())) {
            player.sendMessage(plugin.getMessages().getOr("reward.provider-missing", "reward-provider-missing",
                    "provider", "PlayerPoints"));
            plugin.getLogger().warning("Не удалось выдать PlayerPoints-награду игроку " + player.getName()
                    + ": " + reward.playerPointsAmount());
            return false;
        }
        sendRewardMessage(player, reward, rewardLabel, "", amount,
                "reward.playerpoints", "reward-playerpoints");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.30f, 1.45f);
        return true;
    }

    private void sendRewardMessage(Player player, Reward reward, String rewardLabel,
                                   String moneyAmount, String pointsAmount, String... defaultPaths) {
        String configured = reward.message();
        if (configured != null && !configured.isBlank()) {
            player.sendMessage(formatMessage(configured, rewardLabel, moneyAmount, pointsAmount));
            return;
        }

        if (defaultPaths.length == 1) {
            player.sendMessage(plugin.getMessages().get(defaultPaths[0], "reward", rewardLabel));
        } else {
            player.sendMessage(plugin.getMessages().getOr(defaultPaths[0], defaultPaths[1],
                    "reward", rewardLabel,
                    "amount", !moneyAmount.isBlank() ? moneyAmount : pointsAmount));
        }
    }

    private String formatMessage(String raw, String rewardLabel, String moneyAmount, String pointsAmount) {
        String amount = !moneyAmount.isBlank() ? moneyAmount : pointsAmount;
        return color(raw
                .replace("{reward}", rewardLabel)
                .replace("{amount}", amount)
                .replace("{money}", moneyAmount)
                .replace("{points}", pointsAmount));
    }

    private void giveToInventoryOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    private String caseDisplayName(CaseDefinition definition) {
        if (definition == null) return "кейс";
        if (definition.displayName() != null && !definition.displayName().isBlank()) {
            return color(definition.displayName());
        }
        if (definition.guiTitle() != null && !definition.guiTitle().isBlank()) {
            return color(definition.guiTitle());
        }
        return definition.name();
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }
}
