package ru.privatenull.util;

import ru.privatenull.pnlibrary.text.ColorUtil;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemFactory {

    private ItemFactory() {
    }

    private static String c(String s) {
        return ColorUtil.colorize(s);
    }

    public static ItemStack fromSection(ConfigurationSection sec) {
        if (sec == null) return null;

        ItemStack exact = deserializeItem(sec.getString("item_data", sec.getString("item-data")));
        if (exact != null) {
            return exact;
        }

        String base64 = normalizeBase64(sec.getString("base64"));
        if (base64 == null) {
            base64 = normalizeMaterialBase64(sec.getString("material"));
        }
        if (base64 != null) {
            ItemStack skull = SkullUtil.fromBase64(base64, c(sec.getString("name", "&fItem")));
            applyMeta(skull, sec.getString("name"), sec.getStringList("lore"), sec.getConfigurationSection("enchantments"));
            return skull;
        }

        Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
        if (mat == null) mat = Material.STONE;
        ItemStack it = new ItemStack(mat, Math.max(1, sec.getInt("amount", 1)));
        applyMeta(it, sec.getString("name"), sec.getStringList("lore"), sec.getConfigurationSection("enchantments"));
        return it;
    }

    public static ItemStack fromMap(Map<?, ?> map) {
        Object exactRaw = map.containsKey("item_data") ? map.get("item_data") : map.get("item-data");
        ItemStack exact = deserializeItem(asString(exactRaw, null));
        if (exact != null) {
            return exact;
        }

        String base64 = normalizeBase64(asString(map.get("base64"), null));
        if (base64 == null) {
            base64 = normalizeBase64(asString(map.get("texture"), null));
        }
        if (base64 == null) {
            base64 = normalizeMaterialBase64(asString(map.get("material"), null));
        }
        ItemStack it;

        if (base64 != null) {
            it = SkullUtil.fromBase64(base64, c(asString(map.get("name"), "&fItem")));
        } else {
            Material mat = Material.matchMaterial(asString(map.get("material"), "STONE"));
            if (mat == null) mat = Material.STONE;
            int amount = asInt(map.get("amount"), 1);
            it = new ItemStack(mat, Math.max(1, amount));
        }

        String name = asString(map.get("name"), null);
        List<String> lore = new ArrayList<>();
        Object loreObj = map.get("lore");
        if (loreObj instanceof List<?> list) {
            for (Object o : list) if (o instanceof String s) lore.add(c(s));
        }

        Map<String, Integer> ench = new HashMap<>();
        Object enchObj = map.get("enchantments");
        if (enchObj instanceof Map<?, ?> emap) {
            for (var e : emap.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                String k = String.valueOf(e.getKey());
                int v = asInt(e.getValue(), 1);
                ench.put(k, v);
            }
        }

        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(c(name));
            if (!lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }

        for (var e : ench.entrySet()) {
            Enchantment en = Enchantment.getByKey(NamespacedKey.minecraft(e.getKey().toLowerCase()));
            if (en != null) it.addUnsafeEnchantment(en, e.getValue());
        }

        return it;
    }

    public static void writeItem(ConfigurationSection parent, String key, ItemStack source) {
        parent.set(key, null);
        ConfigurationSection section = parent.createSection(key);
        ItemStack item = source.clone();
        item.setAmount(Math.max(1, item.getAmount()));

        section.set("material", item.getType().name());
        if (item.getAmount() > 1) {
            section.set("amount", item.getAmount());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.hasDisplayName()) {
            section.set("name", meta.getDisplayName());
        }
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            section.set("lore", meta.getLore());
        }
        if (!meta.getEnchants().isEmpty()) {
            ConfigurationSection enchantments = section.createSection("enchantments");
            meta.getEnchants().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getKey().getKey()))
                    .forEach(entry -> enchantments.set(entry.getKey().getKey().getKey(), entry.getValue()));
        }
    }

    public static void writeExactItem(ConfigurationSection parent, String key, ItemStack source) {
        ItemStack item = source.clone();
        item.setAmount(1);
        writeItem(parent, key, item);

        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) return;
        try {
            section.set("item_data", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        } catch (IllegalArgumentException ex) {
            section.set("item_data", null);
        }
    }

    public static boolean isRealItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private static void applyMeta(ItemStack it, String name, List<String> lore, ConfigurationSection enchSec) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isBlank()) meta.setDisplayName(c(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String s : lore) colored.add(c(s));
                meta.setLore(colored);
            }
            it.setItemMeta(meta);
        }

        if (enchSec != null) {
            for (String key : enchSec.getKeys(false)) {
                int lvl = enchSec.getInt(key, 1);
                Enchantment en = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase()));
                if (en != null) it.addUnsafeEnchantment(en, lvl);
            }
        }
    }

    private static String asString(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static String normalizeBase64(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("base64-")) {
            normalized = normalized.substring("base64-".length()).trim();
        } else if (lower.startsWith("base64:")) {
            normalized = normalized.substring("base64:".length()).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeMaterialBase64(String material) {
        if (material == null || material.isBlank()) return null;
        String normalized = material.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("base64-") && !lower.startsWith("base64:")) {
            return null;
        }
        return normalizeBase64(normalized);
    }

    private static ItemStack deserializeItem(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
            return item == null || item.getType().isAir() ? null : item;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}
