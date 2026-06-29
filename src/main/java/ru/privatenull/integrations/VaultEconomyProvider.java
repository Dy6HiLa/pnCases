package ru.privatenull.integrations;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class VaultEconomyProvider {

    private static final DecimalFormat FALLBACK_FORMAT;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        FALLBACK_FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    private final JavaPlugin plugin;
    private Economy economy;

    public VaultEconomyProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public boolean isAvailable() {
        return getEconomy() != null;
    }

    public boolean deposit(Player player, double amount) {
        Economy current = getEconomy();
        if (current == null || amount <= 0.0) {
            return false;
        }

        EconomyResponse response = current.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    public String format(double amount) {
        Economy current = getEconomy();
        if (current != null) {
            try {
                return current.format(amount);
            } catch (Throwable ignored) {
            }
        }
        return "$" + FALLBACK_FORMAT.format(amount);
    }

    private Economy getEconomy() {
        if (economy == null) {
            refresh();
        }
        return economy;
    }

    private void refresh() {
        try {
            RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            economy = registration == null ? null : registration.getProvider();
        } catch (Throwable ignored) {
            economy = null;
        }
    }
}
