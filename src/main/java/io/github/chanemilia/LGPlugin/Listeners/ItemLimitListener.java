package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import io.github.chanemilia.LGPlugin.Utils.ItemMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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
import java.util.function.Consumer;

public class ItemLimitListener implements Listener {
    private final LGPlugin plugin;
    private final Set<UUID> encumberedPlayers = new HashSet<>();
    private final List<PotionEffect> encumbranceEffects = new ArrayList<>();

    private final List<LimitRule> directLimits = new ArrayList<>();
    private final List<GroupRule> groupLimits = new ArrayList<>();

    public ItemLimitListener(LGPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); // Tick every second
    }

    private void tick() {
        reloadConfiguration();

        for (Player player : Bukkit.getOnlinePlayers()) {
            checkLimits(player);
        }
    }

    private void reloadConfiguration() {
        encumbranceEffects.clear();
        ConfigurationSection effectsSection = plugin.getConfig().getConfigurationSection("item-limits.effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key.toLowerCase()));
                if (type == null) continue;

                ConfigurationSection eff = effectsSection.getConfigurationSection(key);
                if (eff == null) continue;

                int duration = eff.getInt("duration", 40);
                int amplifier = eff.getInt("amplifier", 0);
                encumbranceEffects.add(new PotionEffect(type, duration, amplifier));
            }
        }

        directLimits.clear();
        List<Map<?, ?>> limits = plugin.getConfig().getMapList("item-limits.limits");
        for (Map<?, ?> map : limits) {
            String matName = (String) map.get("material");

            Object limitObj = map.get("limit");
            int limit = (limitObj instanceof Number) ? ((Number) limitObj).intValue() : 64;

            Map<?, ?> nbt = (Map<?, ?>) map.get("nbt");

            if (matName != null) {
                directLimits.add(new LimitRule(matName, limit, nbt));
            }
        }

        groupLimits.clear();
        ConfigurationSection groupsSection = plugin.getConfig().getConfigurationSection("item-limits.groups");
        if (groupsSection != null) {
            for (String groupKey : groupsSection.getKeys(false)) {
                ConfigurationSection group = groupsSection.getConfigurationSection(groupKey);
                if (group == null) continue;

                int limit = group.getInt("limit");
                List<WeightedItem> items = new ArrayList<>();

                List<Map<?, ?>> groupItems = group.getMapList("items");
                for (Map<?, ?> itemMap : groupItems) {
                    String matName = (String) itemMap.get("material");

                    Object weightObj = itemMap.get("weight");
                    int weight = (weightObj instanceof Number) ? ((Number) weightObj).intValue() : 1;

                    Map<?, ?> nbt = (Map<?, ?>) itemMap.get("nbt");

                    if (matName != null) {
                        items.add(new WeightedItem(matName, weight, nbt));
                    }
                }

                groupLimits.add(new GroupRule(groupKey, limit, items));
            }
        }
    }

    private void checkLimits(Player player) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("item-limits");
        if (config == null) return;

        boolean scanBundles = config.getBoolean("scan-bundles", true);
        boolean scanShulkers = config.getBoolean("scan-shulkers", false);
        boolean scanEchest = config.getBoolean("scan-echest", false);

        Map<LimitRule, Integer> currentDirectCounts = new HashMap<>();
        Map<GroupRule, Integer> currentGroupCounts = new HashMap<>();

        Consumer<ItemStack> itemScanner = item -> {
            if (item == null || item.getType() == Material.AIR) return;

            // Check Direct Limits
            for (LimitRule rule : directLimits) {
                if (rule.matches(item)) {
                    currentDirectCounts.merge(rule, item.getAmount(), Integer::sum);
                }
            }

            // Check Group Limits
            for (GroupRule group : groupLimits) {
                int weight = group.getWeight(item);
                if (weight > 0) {
                    currentGroupCounts.merge(group, item.getAmount() * weight, Integer::sum);
                }
            }
        };

        scanInventory(player.getInventory(), itemScanner, scanBundles, scanShulkers);
        if (scanEchest) {
            scanInventory(player.getEnderChest(), itemScanner, scanBundles, scanShulkers);
        }
        itemScanner.accept(player.getItemOnCursor());

        boolean isOverLimit = false;

        for (Map.Entry<LimitRule, Integer> entry : currentDirectCounts.entrySet()) {
            if (entry.getValue() > entry.getKey().limit) {
                isOverLimit = true;
                break;
            }
        }

        if (!isOverLimit) {
            for (Map.Entry<GroupRule, Integer> entry : currentGroupCounts.entrySet()) {
                if (entry.getValue() > entry.getKey().limit) {
                    isOverLimit = true;
                    break;
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

    private void scanInventory(Inventory inv, Consumer<ItemStack> consumer, boolean scanBundles, boolean scanShulkers) {
        for (ItemStack item : inv.getContents()) {
            scanItemRecursive(item, consumer, scanBundles, scanShulkers);
        }
    }

    private void scanItemRecursive(ItemStack item, Consumer<ItemStack> consumer, boolean scanBundles, boolean scanShulkers) {
        if (item == null || item.getType() == Material.AIR) return;

        consumer.accept(item);

        if (scanBundles && item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
            for (ItemStack content : bundleMeta.getItems()) {
                scanItemRecursive(content, consumer, scanBundles, scanShulkers);
            }
        } else if (scanShulkers && item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta blockMeta) {
            if (blockMeta.getBlockState() instanceof ShulkerBox shulker) {
                for (ItemStack content : shulker.getInventory().getContents()) {
                    scanItemRecursive(content, consumer, scanBundles, scanShulkers);
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

    private static class LimitRule {
        final String material;
        final int limit;
        final Map<?, ?> nbt;

        LimitRule(String material, int limit, Map<?, ?> nbt) {
            this.material = material;
            this.limit = limit;
            this.nbt = nbt;
        }

        boolean matches(ItemStack item) {
            if (!item.getType().name().equals(material)) return false;
            return nbt == null || ItemMatcher.checkNbt(item, nbt);
        }
    }

    private static class GroupRule {
        final String name;
        final int limit;
        final List<WeightedItem> items;

        GroupRule(String name, int limit, List<WeightedItem> items) {
            this.name = name;
            this.limit = limit;
            this.items = items;
        }

        int getWeight(ItemStack item) {
            for (WeightedItem weightedItem : items) {
                if (weightedItem.matches(item)) {
                    return weightedItem.weight;
                }
            }
            return 0;
        }
    }

    private record WeightedItem(String material, int weight, Map<?, ?> nbt) {

        boolean matches(ItemStack item) {
            if (!item.getType().name().equals(material)) return false;
            return nbt == null || ItemMatcher.checkNbt(item, nbt);
        }
    }
}