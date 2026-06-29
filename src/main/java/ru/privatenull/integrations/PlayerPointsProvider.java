package ru.privatenull.integrations;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;

import java.util.UUID;

public final class PlayerPointsProvider {

    private PlayerPointsAPI api;

    public PlayerPointsProvider() {
        refresh();
    }

    public boolean isAvailable() {
        return getApi() != null;
    }

    public boolean give(UUID uuid, int amount) {
        PlayerPointsAPI current = getApi();
        if (current == null || amount <= 0) {
            return false;
        }
        return current.give(uuid, amount);
    }

    public String format(int amount) {
        PlayerPointsAPI current = getApi();
        if (current == null) {
            return amount + " поинтов";
        }

        try {
            return amount + " " + current.getCurrencyName(amount);
        } catch (Throwable ignored) {
            return amount + " поинтов";
        }
    }

    private PlayerPointsAPI getApi() {
        if (api == null) {
            refresh();
        }
        return api;
    }

    private void refresh() {
        try {
            PlayerPoints playerPoints = PlayerPoints.getInstance();
            api = playerPoints == null ? null : playerPoints.getAPI();
        } catch (Throwable ignored) {
            api = null;
        }
    }
}
