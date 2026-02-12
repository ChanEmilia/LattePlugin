package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import io.github.chanemilia.LGPlugin.Utils.ItemMatcher;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
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
        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i] == null) continue;

            int cappedLevel = getCappedLevel(event.getItem().getType(), offers[i].getEnchantment(), offers[i].getEnchantmentLevel());

            if (cappedLevel == 0) {
                offers[i] = null;
            } else if (cappedLevel < offers[i].getEnchantmentLevel()) {
                offers[i].setEnchantmentLevel(cappedLevel);
            }
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        ConfigurationSection itemConfig = getEffectiveConfig(event.getItem().getType());

        if (itemConfig != null) {
            boolean showGlint = itemConfig.getBoolean("glint", true);
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta != null) {
                handleGlint(meta, itemConfig, event.getEnchantsToAdd().keySet());
                event.getItem().setItemMeta(meta);
            }
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null) return;

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        boolean changed = false;
        Map<Enchantment, Integer> currentEnchants = new HashMap<>(meta.getEnchants());

        for (Map.Entry<Enchantment, Integer> entry : currentEnchants.entrySet()) {
            int cappedLevel = getCappedLevel(result.getType(), entry.getKey(), entry.getValue());

            if (cappedLevel == 0) {
                meta.removeEnchant(entry.getKey());
                changed = true;
            } else if (cappedLevel < entry.getValue()) {
                meta.removeEnchant(entry.getKey());
                meta.addEnchant(entry.getKey(), cappedLevel, true);
                changed = true;
            }
        }

        ConfigurationSection itemConfig = getSpecificItemConfig(result.getType());

        if (itemConfig != null && meta.hasEnchants()) {
            boolean showGlint = itemConfig.getBoolean("glint", true);

            Boolean currentOverride = meta.getEnchantmentGlintOverride();

            if (!showGlint) {
                if (currentOverride) {
                    meta.setEnchantmentGlintOverride(false);
                    changed = true;
                }
            } else {
                meta.setEnchantmentGlintOverride(null);
                changed = true;
            }
        }

        if (changed) {
            result.setItemMeta(meta);
            event.setResult(result);
        }
    }

    private boolean handleGlint(ItemMeta meta, ConfigurationSection itemConfig, Set<Enchantment> currentEnchants) {
        boolean configShowGlint = itemConfig.getBoolean("glint", true);
        Boolean desiredOverride = null;

        if (!configShowGlint) {
            desiredOverride = false;

            ConfigurationSection restrictedEnchants = itemConfig.getConfigurationSection("enchantments");
            if (restrictedEnchants != null && currentEnchants != null) {
                for (Enchantment ench : currentEnchants) {
                    if (isEnchantmentListed(restrictedEnchants, ench)) {
                        desiredOverride = null;
                        break;
                    }
                }
            }
        }

        if (!Objects.equals(meta.getEnchantmentGlintOverride(), desiredOverride)) {
            meta.setEnchantmentGlintOverride(desiredOverride);
            return true;
        }
        return false;
    }

    private boolean isEnchantmentListed(ConfigurationSection enchantsConfig, Enchantment ench) {
        for (String key : enchantsConfig.getKeys(false)) {
            if (ItemMatcher.matchesEnchantment(ench, key)) {
                return true;
            }
        }
        return false;
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

            if (ItemMatcher.matchesEnchantment(target, key)) {
                return enchants.getInt(key);
            }
        }
        return -1;
    }

    private ConfigurationSection getSpecificItemConfig(Material material) {
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("restricted-enchantments.items");
        if (itemsSection == null) return null;

        String bestMatch = null;

        for (String key : itemsSection.getKeys(false)) {
            if (key.equalsIgnoreCase("GLOBAL")) continue;

            if (ItemMatcher.matchesMaterial(material, key)) {
                if (bestMatch == null || key.length() > bestMatch.length()) {
                    bestMatch = key;
                }
            }
        }

        if (bestMatch != null) {
            return itemsSection.getConfigurationSection(bestMatch);
        }
        return null;
    }

    private ConfigurationSection getEffectiveConfig(Material material) {
        ConfigurationSection specific = getSpecificItemConfig(material);
        if (specific != null) return specific;

        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("restricted-enchantments.items");
        if (itemsSection != null && itemsSection.contains("GLOBAL")) {
            return itemsSection.getConfigurationSection("GLOBAL");
        }
        return null;
    }
}