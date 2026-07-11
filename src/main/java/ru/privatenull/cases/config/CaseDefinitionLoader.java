package ru.privatenull.cases.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.cases.reward.RewardPresentationService;
import ru.privatenull.util.ItemFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static ru.privatenull.config.ConfigValues.bool;
import static ru.privatenull.config.ConfigValues.decimal;
import static ru.privatenull.config.ConfigValues.integer;
import static ru.privatenull.config.ConfigValues.string;

public final class CaseDefinitionLoader {

    private final CaseBlockCodec blockCodec;
    private final CaseViewConfigParser viewParser = new CaseViewConfigParser();
    private final RewardConfigParser rewardParser;

    public CaseDefinitionLoader(CaseBlockCodec blockCodec, RewardPresentationService presentation) {
        this.blockCodec = blockCodec;
        this.rewardParser = new RewardConfigParser(presentation);
    }

    public CaseDefinition load(String caseName, ConfigurationSection section) {
        String displayName = section.getString(
                "display-name",
                section.getString("display_name", humanizeCaseName(caseName))
        );
        List<Location> blocks = blockCodec.readLoadedLocations(section);

        ConfigurationSection gui = section.getConfigurationSection("gui");
        String title = gui == null ? "&8Case" : gui.getString("title", "&8Case");
        ItemStack openButton = ItemFactory.fromSection(gui == null ? null : gui.getConfigurationSection("open-item"));
        if (openButton == null) openButton = new ItemStack(Material.CHEST);
        CaseGuiLayout layout = viewParser.parseLayout(gui);
        IdleParticleSettings idleParticles = viewParser.parseIdleParticles(section.getConfigurationSection("idle-particles"));

        Cost cost = parseCost(section.getConfigurationSection("cost"));
        Animation animation = parseAnimation(section.getConfigurationSection("animation"));
        List<Reward> rewards = rewardParser.parse(section);

        return new CaseDefinition(
                caseName.toLowerCase(Locale.ROOT),
                displayName,
                blocks,
                title,
                openButton,
                layout,
                idleParticles,
                cost.type(),
                cost.amount(),
                cost.keyId(),
                cost.buyXpLevels(),
                animation.durationTicks(),
                animation.cycleEveryTicks(),
                animation.riseBlocks(),
                animation.spinDegreesPerTick(),
                animation.fixed(),
                animation.items(),
                rewards
        );
    }

    private Cost parseCost(ConfigurationSection section) {
        String rawType = section == null ? "NONE" : section.getString("type", "NONE");
        if (rawType == null) rawType = "NONE";
        CaseDefinition.CostType type;
        try {
            type = CaseDefinition.CostType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            type = CaseDefinition.CostType.NONE;
        }

        int amount = section == null ? 0 : section.getInt("amount", 0);
        String keyId = section == null ? null : section.getString("key");
        if (keyId != null) keyId = keyId.toLowerCase(Locale.ROOT);

        int buyXpLevels = 0;
        if (section != null && bool(section, true,
                "buy_xp_enabled", "buy-xp-enabled", "buy_key_with_xp", "buy-key-with-xp")) {
            buyXpLevels = section.getInt("buy_xp_levels", 0);
            if (buyXpLevels <= 0) buyXpLevels = section.getInt("buy-xp-levels", 0);
        }
        return new Cost(type, amount, keyId, buyXpLevels);
    }

    private Animation parseAnimation(ConfigurationSection section) {
        int duration = integer(section, 80, "duration_ticks", "duration-ticks");
        int cycleEvery = integer(section, 2, "cycle_every_ticks", "cycle-every-ticks");
        double rise = decimal(section, 1.2, "rise_blocks", "rise-blocks");
        float spin = (float) decimal(section, 18.0, "spin_degrees_per_tick", "spin-degrees-per-tick");

        List<ItemStack> items = new ArrayList<>();
        if (section != null && section.isList("items")) {
            for (Object raw : section.getList("items", List.of())) {
                if (raw instanceof Map<?, ?> map) items.add(ItemFactory.fromMap(map));
            }
        }
        items.removeIf(Objects::isNull);
        if (items.isEmpty()) items.add(new ItemStack(Material.SLIME_BALL));

        return new Animation(duration, cycleEvery, rise, spin, fixedAnimation(section), items);
    }

    private AnimationType fixedAnimation(ConfigurationSection section) {
        String raw = string(section, null,
                "fixed", "fixed_animation", "fixed-animation", "case_animation", "case-animation", "type");
        if (raw == null || raw.isBlank()) return null;

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (List.of("PLAYER", "PLAYERS", "CHOICE", "SELECT", "NONE", "FALSE", "OFF").contains(normalized)) {
            return null;
        }
        try {
            return AnimationType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String humanizeCaseName(String caseName) {
        String normalized = caseName == null ? "" : caseName.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "items" -> "&bКейс с ресурсами";
            case "money" -> "&6Кейс с монетами";
            case "playerpoints", "points" -> "&bКейс с поинтами";
            case "luckperms", "donate" -> "&6Донат кейс";
            default -> caseName == null || caseName.isBlank() ? "&fКейс" : "&f" + caseName.replace('_', ' ');
        };
    }

    private record Cost(CaseDefinition.CostType type, int amount, String keyId, int buyXpLevels) {
    }

    private record Animation(
            int durationTicks,
            int cycleEveryTicks,
            double riseBlocks,
            float spinDegreesPerTick,
            AnimationType fixed,
            List<ItemStack> items
    ) {
    }
}
