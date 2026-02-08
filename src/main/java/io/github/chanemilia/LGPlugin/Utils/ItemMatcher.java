package io.github.chanemilia.LGPlugin.Utils;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemMatcher {

    public static final Pattern ENCHANT_PATTERN = Pattern.compile("[\"']?([a-z0-9_:]+)[\"']?:(\\d+)");

    private static String cleanKey(String key) {
        if (key == null) return "";
        String cleaned = key.replace("\"", "").replace("'", "").trim().toLowerCase();
        if (cleaned.startsWith("minecraft:")) {
            cleaned = cleaned.substring(10);
        }
        return cleaned;
    }

    public static boolean matchesMaterial(Material material, String configKey) {
        if (material == null || configKey == null) return false;
        String cleanConfig = cleanKey(configKey);
        String matKey = material.getKey().getKey().toLowerCase();
        String matEnum = material.name().toLowerCase();

        if (matKey.equals(cleanConfig)) return true;

        if (matEnum.equals(cleanConfig)) return true;

        if (matKey.contains(cleanConfig)) return true;

        if (matEnum.contains(cleanConfig)) return true;

        return false;
    }

    public static boolean matchesEnchantment(Enchantment enchantment, String configKey) {
        if (enchantment == null || configKey == null) return false;
        String cleanConfig = cleanKey(configKey);
        String enchKey = enchantment.getKey().getKey().toLowerCase();

        if (enchKey.equals(cleanConfig)) return true;
        if (enchKey.contains(cleanConfig)) return true;

        return false;
    }

    @SuppressWarnings("deprecation")
    public static boolean matchesPotion(PotionEffectType type, String configKey) {
        if (type == null || configKey == null) return false;
        String cleanConfig = cleanKey(configKey);
        String typeKey = type.getKey().getKey().toLowerCase();
        String typeName = type.getName().toLowerCase();

        if (typeKey.equals(cleanConfig)) return true;
        if (typeName.equals(cleanConfig)) return true;

        if (typeKey.contains(cleanConfig)) return true;
        if (typeName.contains(cleanConfig)) return true;

        return false;
    }

    @SuppressWarnings("deprecation")
    public static boolean checkNbt(ItemStack item, Map<?, ?> nbt) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        for (Map.Entry<?, ?> entry : nbt.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            if (key.equalsIgnoreCase("enchantments")) {
                if (value instanceof Map) {
                    if (!checkEnchantsMap(meta, (Map<?, ?>) value)) return false;
                } else {
                    if (!checkEnchantsString(meta, value.toString())) return false;
                }
                continue;
            }

            if (key.equalsIgnoreCase("name") || key.equalsIgnoreCase("custom_name")) {
                if (!meta.hasDisplayName()) return false;
                if (!meta.getDisplayName().equals(value.toString())) return false;
                continue;
            }

            if (key.equalsIgnoreCase("custom_model_data") || key.equalsIgnoreCase("custommodeldata")) {
                if (!meta.hasCustomModelData()) return false;
                int required = (value instanceof Number) ? ((Number) value).intValue() : 0;
                if (meta.getCustomModelData() != required) return false;
                continue;
            }

            String fullComponentString = meta.getAsString();
            String searchKey = key.toLowerCase();
            if (!searchKey.contains(":")) searchKey = "minecraft:" + searchKey;

            String expectedValue = value.toString();
            String exactSnippet = searchKey + "=" + expectedValue;
            String quotedSnippet = searchKey + "=\"" + expectedValue + "\"";
            String singleQuotedSnippet = searchKey + "='" + expectedValue + "'";

            if (!fullComponentString.contains(exactSnippet) &&
                    !fullComponentString.contains(quotedSnippet) &&
                    !fullComponentString.contains(singleQuotedSnippet)) {

                if (!fullComponentString.contains(expectedValue)) return false;
            }
        }
        return true;
    }

    private static boolean checkEnchantsMap(ItemMeta meta, Map<?, ?> enchants) {
        for (Map.Entry<?, ?> entry : enchants.entrySet()) {
            String key = entry.getKey().toString();
            Object val = entry.getValue();
            int level = (val instanceof Number) ? ((Number) val).intValue() : 1;

            Enchantment ench = resolveEnchantment(key);
            if (ench == null) return false;
            if (!meta.hasEnchant(ench)) return false;
            if (meta.getEnchantLevel(ench) < level) return false;
        }
        return true;
    }

    private static boolean checkEnchantsString(ItemMeta meta, String enchantString) {
        String[] parts = enchantString.split(",");
        for (String part : parts) {
            Matcher matcher = ENCHANT_PATTERN.matcher(part.trim());
            if (matcher.find()) {
                String keyName = matcher.group(1);
                int level = Integer.parseInt(matcher.group(2));

                Enchantment ench = resolveEnchantment(keyName);
                if (ench == null) return false;
                if (!meta.hasEnchant(ench)) return false;
                if (meta.getEnchantLevel(ench) < level) return false;
            }
        }
        return true;
    }

    public static Material resolveMaterial(String name) {
        if (name == null) return null;
        String clean = cleanKey(name);

        Material mat = Material.matchMaterial(clean);
        if (mat != null) return mat;

        try {
            return Material.valueOf(clean.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        return Material.matchMaterial("minecraft:" + clean);
    }

    @SuppressWarnings("deprecation")
    public static Enchantment resolveEnchantment(String key) {
        if (key == null) return null;
        String clean = cleanKey(key);

        NamespacedKey nsKey = NamespacedKey.minecraft(clean);
        Enchantment ench = Registry.ENCHANTMENT.get(nsKey);
        if (ench != null) return ench;

        try {
            return Enchantment.getByName(clean.toUpperCase());
        } catch (Exception ignored) {}

        return null;
    }

    @SuppressWarnings("deprecation")
    public static PotionEffectType resolvePotionEffectType(String key) {
        if (key == null) return null;
        String clean = cleanKey(key);

        NamespacedKey nsKey;
        if (clean.contains(":")) {
            nsKey = NamespacedKey.fromString(clean);
        } else {
            nsKey = NamespacedKey.minecraft(clean);
        }

        if (nsKey != null) {
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(nsKey);
            if (type != null) return type;
        }

        try {
            return PotionEffectType.getByName(clean.toUpperCase());
        } catch (Exception ignored) {}

        return null;
    }
}