package ru.privatenull.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.PnCasesPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public final class HologramService {

    private final PnCasesPlugin plugin;
    private final Set<String> externalNames = new HashSet<>();
    private final HologramProviderResolver providerResolver;
    private final HologramSpecFactory specFactory;

    private HologramProvider provider;
    private boolean missingProviderWarningLogged;

    public HologramService(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.providerResolver = new HologramProviderResolver(plugin);
        this.specFactory = new HologramSpecFactory();
        reloadProvider();
    }

    public void shutdown() {
        clearManaged();
        provider = null;
    }

    public void clearManaged() {
        clearNativeDisplays();

        HologramProvider currentProvider = provider;
        for (String name : new ArrayList<>(externalNames)) {
            removeExternal(name, currentProvider);
        }

        externalNames.clear();
    }

    public void syncCases(Collection<CaseDefinition> defs) {
        reloadProvider();
        clearManaged();

        for (CaseDefinition def : defs) {
            try {
                if (specFactory.blockLocations(def).isEmpty()) {
                    continue;
                }
                ConfigurationSection config = getHologramSection(def);
                if (config == null || !config.getBoolean("enabled", false)) {
                    continue;
                }
                for (Location location : specFactory.blockLocations(def)) {
                    createCaseHologram(def, config, location);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Holograms: ошибка создания голограммы для кейса '" + def.name() + "': " + t.getMessage());
            }
        }
    }

    /** Rebuilds holograms for one edited case only. */
    public void refreshCase(CaseDefinition previous, CaseDefinition updated) {
        if (previous != null) {
            hideCase(previous);
        }
        if (updated != null) {
            showCase(updated);
        }
    }

    public void hideCase(CaseDefinition def) {
        reloadProvider();
        if (def == null) return;

        String prefix = specFactory.prefix(def);
        for (String name : new ArrayList<>(externalNames)) {
            if (name.equals(specFactory.legacyName(def)) || name.startsWith(prefix + "_")) {
                removeExternal(name, provider);
                externalNames.remove(name);
            }
        }
        removeExternal(specFactory.legacyName(def), provider);
        clearNativeDisplays(def.name());
    }

    public void hideCase(CaseDefinition def, Location blockLocation) {
        reloadProvider();
        if (def == null || blockLocation == null) return;

        String name = specFactory.name(def, blockLocation);
        removeExternal(name, provider);
        externalNames.remove(name);
        clearNativeDisplays(def, blockLocation);
    }

    public void showCase(CaseDefinition def) {
        reloadProvider();
        if (def == null || specFactory.blockLocations(def).isEmpty()) return;

        try {
            ConfigurationSection config = getHologramSection(def);
            if (config == null || !config.getBoolean("enabled", false)) {
                return;
            }
            for (Location location : specFactory.blockLocations(def)) {
                createCaseHologram(def, config, location);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Holograms: ошибка showCase для '" + def.name() + "': " + t.getMessage());
        }
    }

    public void showCase(CaseDefinition def, Location blockLocation) {
        reloadProvider();
        if (def == null || blockLocation == null) return;

        try {
            ConfigurationSection config = getHologramSection(def);
            if (config == null || !config.getBoolean("enabled", false)) {
                return;
            }
            createCaseHologram(def, config, blockLocation);
        } catch (Throwable t) {
            plugin.getLogger().warning("Holograms: showCase error for '" + def.name() + "': " + t.getMessage());
        }
    }

    private void createCaseHologram(CaseDefinition def, ConfigurationSection config, Location blockLocation) {
        HologramSpec spec = specFactory.build(def, config, blockLocation);
        removeExternal(spec.name(), provider);
        externalNames.remove(spec.name());

        HologramProvider currentProvider = provider;
        if (currentProvider == null) {
            logMissingProviderOnce();
            return;
        }

        try {
            if (!currentProvider.isAvailable()) {
                throw new IllegalStateException("провайдер выключен");
            }
            currentProvider.create(spec);
            externalNames.add(spec.name());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Hologram provider failed while creating " + spec.name(), t);
            provider = null;
            logMissingProviderOnce();
        }
    }

    private void removeExternal(String name, HologramProvider currentProvider) {
        if (name == null || name.isBlank()) {
            return;
        }

        if (currentProvider != null) {
            try {
                currentProvider.remove(name);
            } catch (Throwable failure) {
                logProviderFailure("remove " + name, failure);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            HologramProvider fancy = providerResolver.fancy();
            if (fancy != null && fancy != currentProvider) {
                try {
                    fancy.remove(name);
                } catch (Throwable failure) {
                    logProviderFailure("remove FancyHolograms entry " + name, failure);
                }
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            HologramProvider decent = providerResolver.decent();
            if (decent != null && decent != currentProvider) {
                try {
                    decent.remove(name);
                } catch (Throwable failure) {
                    logProviderFailure("remove DecentHolograms entry " + name, failure);
                }
            }
        }
    }

    private void clearNativeDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("pncases_hologram")) {
                    entity.remove();
                }
            }
        }
    }

    private void clearNativeDisplays(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            return;
        }

        String caseTag = "pncases_" + caseName;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Set<String> tags = entity.getScoreboardTags();
                if (tags.contains("pncases_hologram") && tags.contains(caseTag)) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Removes only the legacy native display belonging to one physical case
     * block. Older builds tagged native displays by case id only, so the
     * configured hologram position is used to distinguish multiple bindings.
     */
    private void clearNativeDisplays(CaseDefinition definition, Location blockLocation) {
        if (definition == null || blockLocation == null || blockLocation.getWorld() == null) return;

        Location expected = blockLocation.clone().add(0.5, 1.8, 0.5);
        try {
            ConfigurationSection config = getHologramSection(definition);
            if (config != null) {
                expected = specFactory.build(definition, config, blockLocation).location();
            }
        } catch (RuntimeException ignored) {
            // The default legacy position remains safe for cleanup.
        }

        String caseTag = "pncases_" + definition.name();
        World world = blockLocation.getWorld();
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            Set<String> tags = entity.getScoreboardTags();
            if (!tags.contains("pncases_hologram") || !tags.contains(caseTag)) continue;
            Location location = entity.getLocation();
            if (sameHologramPosition(
                    location.getX(), location.getY(), location.getZ(),
                    expected.getX(), expected.getY(), expected.getZ())) {
                entity.remove();
            }
        }
    }

    static boolean sameHologramPosition(
            double x,
            double y,
            double z,
            double expectedX,
            double expectedY,
            double expectedZ
    ) {
        return Math.abs(x - expectedX) <= 0.25
                && Math.abs(y - expectedY) <= 0.25
                && Math.abs(z - expectedZ) <= 0.25;
    }

    private void reloadProvider() {
        if (provider != null) {
            try {
                if (provider.isAvailable()) {
                    return;
                }
            } catch (Throwable failure) {
                logProviderFailure("check provider availability", failure);
                provider = null;
            }
        }

        provider = providerResolver.select();
        if (provider != null) {
            missingProviderWarningLogged = false;
        }
    }

    private void logMissingProviderOnce() {
        if (missingProviderWarningLogged) {
            return;
        }
        missingProviderWarningLogged = true;
        plugin.getLogger().warning("Голограммы: FancyHolograms или DecentHolograms не найдены. Голограммы кейсов отключены.");
    }

    private ConfigurationSection getHologramSection(CaseDefinition def) {
        ConfigurationSection config = plugin.getCaseManager() == null
                ? null
                : plugin.getCaseManager().getCaseSection(def.name());
        if (config == null) {
            config = plugin.getConfig().getConfigurationSection("cases." + def.name());
        }
        return config == null ? null : config.getConfigurationSection("hologram");
    }

    private void logProviderFailure(String action, Throwable failure) {
        plugin.getLogger().log(Level.FINE, "Holograms: failed to " + action, failure);
    }

}
