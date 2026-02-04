package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public class EnchantListener implements Listener {
    private final LGPlugin plugin;

    public EnchantListener(LGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();

        ConfigurationSection itemConfig = getItemConfig(item.getType());
        if (itemConfig == null) return;

        EnchantmentOffer[] offers = event.getOffers();

        for (int i = 0; i < offers.length; i++) {
            if (offers[i] == null) continue;

            Enchantment offerEnch = offers[i].getEnchantment();
            int offerLevel = offers[i].getEnchantmentLevel();

            int cappedLevel = getCappedLevel(itemConfig, offerEnch, offerLevel);

            if (cappedLevel == 0) {
                offers[i] = null;
            } else if (cappedLevel < offerLevel) {
                offers[i].setEnchantmentLevel(cappedLevel);
            }
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        ConfigurationSection itemConfig = getItemConfig(item.getType());
        if (itemConfig == null) return;

        if (!itemConfig.getBoolean("glint", true)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null) return;

        ConfigurationSection itemConfig = getItemConfig(result.getType());
        if (itemConfig == null) return;

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        boolean changed = false;
        Map<Enchantment, Integer> currentEnchants = new HashMap<>(meta.getEnchants());

        for (Map.Entry<Enchantment, Integer> entry : currentEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int currentLevel = entry.getValue();

            int cappedLevel = getCappedLevel(itemConfig, ench, currentLevel);

            if (cappedLevel == 0) {
                meta.removeEnchant(ench);
                changed = true;
            } else if (cappedLevel < currentLevel) {
                meta.removeEnchant(ench);
                meta.addEnchant(ench, cappedLevel, true);
                changed = true;
            }
        }

        boolean showGlint = itemConfig.getBoolean("glint", true);
        if (!showGlint) {
            if (!meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                changed = true;
            }
        } else {
            if (meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
                changed = true;
            }
        }

        if (changed) {
            result.setItemMeta(meta);
            event.setResult(result);
        }
    }

    private int getCappedLevel(Material material, Enchantment ench, int currentLevel) {
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("restricted-enchantments.items");
        if (itemsSection == null) return currentLevel;

        int specificCap = -1;
        int globalCap = -1;

        ConfigurationSection specificConfig = getSpecificItemConfig(material);
        if (specificConfig != null) {
            specificCap = getLimitFromConfig(specificConfig, ench);
        }

        if (itemsSection.contains("GLOBAL")) {
            ConfigurationSection globalConfig = itemsSection.getConfigurationSection("GLOBAL");
            globalCap = getLimitFromConfig(globalConfig, ench);
        }

        if (specificCap != -1) return Math.min(currentLevel, specificCap);
        if (globalCap != -1) return Math.min(currentLevel, globalCap);

        return currentLevel;
    }

    private int getLimitFromConfig(ConfigurationSection section, Enchantment target) {
        if (section == null) return -1;
        ConfigurationSection enchants = section.getConfigurationSection("enchantments");
        if (enchants == null) return -1;

        for (String key : enchants.getKeys(false)) {
            Enchantment resolved = ItemMatcher.resolveEnchantment(key);

            if (resolved != null && resolved.equals(target)) {
                return enchants.getInt(key);
            }
        }
        return -1;
    }

    private ConfigurationSection getSpecificItemConfig(Material material) {
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("restricted-enchantments.items");
        if (itemsSection == null) return null;

        String materialName = material.name();
        String bestMatch = null;

        for (String key : itemsSection.getKeys(false)) {
            if (key.equalsIgnoreCase("GLOBAL")) continue;

            if (materialName.contains(key)) {
                if (bestMatch == null || key.length() > bestMatch.length()) {
                    bestMatch = key;
                }
            }
        }

        if (bestMatch != null) {
            return itemsSection.getConfigurationSection(bestMatch);
        }

        if (itemsSection.contains("GLOBAL")) {
            return itemsSection.getConfigurationSection("GLOBAL");
        }

        return null;
    }

    private int getCappedLevel(ConfigurationSection itemConfig, Enchantment ench, int currentLevel) {
        ConfigurationSection enchantsConfig = itemConfig.getConfigurationSection("enchantments");
        String enchName = ench.getKey().getKey().toUpperCase();

        if (enchantsConfig == null || !enchantsConfig.contains(enchName)) {
            return currentLevel;
        }

        int configMax = enchantsConfig.getInt(enchName);
        return Math.min(currentLevel, configMax);
    }
}