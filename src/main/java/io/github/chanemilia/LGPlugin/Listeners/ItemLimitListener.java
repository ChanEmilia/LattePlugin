package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.*;

public class ItemLimitListener implements Listener {
    private final LGPlugin plugin;
    private final Set<UUID> encumberedPlayers = new HashSet<>();
    private final List<PotionEffect> encumbranceEffects = new ArrayList<>();

    public ItemLimitListener(LGPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); // Tick every second
    }

    private void tick() {
        reloadEffects();

        for (Player player : Bukkit.getOnlinePlayers()) {
            checkLimits(player);
        }
    }

    private void reloadEffects() {
        encumbranceEffects.clear();
        ConfigurationSection effectsSection = plugin.getConfig().getConfigurationSection("item-limits.effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                PotionEffectType type = PotionEffectType.getByName(key);
                if (type == null) continue;

                ConfigurationSection eff = effectsSection.getConfigurationSection(key);
                int duration = eff.getInt("duration", 40);
                int amplifier = eff.getInt("amplifier", 0);
                encumbranceEffects.add(new PotionEffect(type, duration, amplifier));
            }
        }
    }

    private void checkLimits(Player player) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("item-limits");
        if (config == null) return;

        boolean scanBundles = config.getBoolean("scan-bundles", true);
        boolean scanShulkers = config.getBoolean("scan-shulkers", false);
        boolean scanEchest = config.getBoolean("scan-echest", false);

        // Count items
        Map<Material, Integer> itemCounts = new HashMap<>();

        // Main Inventory
        countInventory(player.getInventory(), itemCounts, scanBundles, scanShulkers);

        // Ender Chest
        if (scanEchest) {
            countInventory(player.getEnderChest(), itemCounts, scanBundles, scanShulkers);
        }

        // Cursor
        ItemStack cursor = player.getItemOnCursor();
        addItemCount(cursor, itemCounts, scanBundles, scanShulkers);


        boolean isOverLimit = false;

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                Material mat = Material.getMaterial(key);
                if (mat == null) continue;

                int limit = itemsSection.getInt(key);
                int current = itemCounts.getOrDefault(mat, 0);

                if (current > limit) {
                    isOverLimit = true;
                    break;
                }
            }
        }

        if (!isOverLimit) {
            ConfigurationSection groupsSection = config.getConfigurationSection("groups");
            if (groupsSection != null) {
                for (String groupKey : groupsSection.getKeys(false)) {
                    ConfigurationSection group = groupsSection.getConfigurationSection(groupKey);
                    if (group == null) continue;

                    int limit = group.getInt("limit");
                    ConfigurationSection groupItems = group.getConfigurationSection("items");

                    int groupTotal = 0;
                    if (groupItems != null) {
                        for (String itemKey : groupItems.getKeys(false)) {
                            Material mat = Material.getMaterial(itemKey);
                            if (mat == null) continue;

                            int weight = groupItems.getInt(itemKey, 1);
                            int count = itemCounts.getOrDefault(mat, 0);
                            groupTotal += (count * weight);
                        }
                    }

                    if (groupTotal > limit) {
                        isOverLimit = true;
                        break;
                    }
                }
            }
        }

        if (isOverLimit) {
            encumberedPlayers.add(player.getUniqueId());
            applyEncumbrance(player, config);
        } else {
            encumberedPlayers.remove(player.getUniqueId());
        }
    }

    private void countInventory(Inventory inv, Map<Material, Integer> counts, boolean scanBundles, boolean scanShulkers) {
        for (ItemStack item : inv.getContents()) {
            addItemCount(item, counts, scanBundles, scanShulkers);
        }
    }

    private void addItemCount(ItemStack item, Map<Material, Integer> counts, boolean scanBundles, boolean scanShulkers) {
        if (item == null || item.getType() == Material.AIR) return;

        counts.merge(item.getType(), item.getAmount(), Integer::sum);

        if (scanBundles && item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
            for (ItemStack content : bundleMeta.getItems()) {
                addItemCount(content, counts, scanBundles, scanShulkers);
            }
        } else if (scanShulkers && item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta blockMeta) {
            if (blockMeta.getBlockState() instanceof ShulkerBox shulker) {
                for (ItemStack content : shulker.getInventory().getContents()) {
                    addItemCount(content, counts, scanBundles, scanShulkers); // Recurse, should be pretty efficient
                }
            }
        }
    }

    private void applyEncumbrance(Player player, ConfigurationSection config) {
        for (PotionEffect effect : encumbranceEffects) {
            player.addPotionEffect(effect);
        }

        String msgType = config.getString("message-type", "ACTION_BAR").toUpperCase();
        String text = config.getString("text", "You are overencumbered!");
        String subtitle = config.getString("subtitle", "");

        if (msgType.equals("TITLE")) {
            player.showTitle(Title.title(
                    Component.text(text, NamedTextColor.RED),
                    Component.text(subtitle, NamedTextColor.RED),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ofMillis(500))
            ));
        } else {
            player.sendActionBar(Component.text(text, NamedTextColor.RED));
        }
    }
}