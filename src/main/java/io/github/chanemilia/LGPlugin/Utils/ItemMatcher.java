package io.github.chanemilia.LGPlugin.Utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class ItemMatcher {

    public static boolean checkNbt(ItemStack item, Map<?, ?> nbt) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        for (Map.Entry<?, ?> entry : nbt.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            if (key.equalsIgnoreCase("custom_name") || key.equalsIgnoreCase("name")) {
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

                if (!fullComponentString.contains(expectedValue)) {
                    return false;
                }
            }
        }

        return true;
    }
}