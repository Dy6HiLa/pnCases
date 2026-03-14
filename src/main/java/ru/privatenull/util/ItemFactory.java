package ru.privatenull.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemFactory {

    private static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static ItemStack fromSection(ConfigurationSection sec) {
        if (sec == null) return null;

        String base64 = sec.getString("base64");
        if (base64 != null && !base64.isBlank()) {
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
        Object b64 = map.get("base64");
        ItemStack it;

        if (b64 instanceof String s && !s.isBlank()) {
            it = SkullUtil.fromBase64(s, c(asString(map.get("name"), "&fItem")));
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

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}