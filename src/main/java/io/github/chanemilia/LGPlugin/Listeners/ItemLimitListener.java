package io.github.chanemilia.lattePlugin.Listeners;

import io.github.chanemilia.lattePlugin.LattePlugin;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.time.Duration;
import java.util.*;

public class ItemLimitListener implements Listener {
    private final LGPlugin plugin;
    private final Set<UUID> encumberedPlayers = new HashSet<>();

    // Encumbrance Settings
    private String encumbranceMessageType = "ACTION_BAR";
    private String encumbranceText = "You are overencumbered and cannot run!";
    private String encumbranceSubtitle = "";
    private final List<PotionEffect> encumbranceEffects = new ArrayList<>();

    private static final long MESSAGE_COOLDOWN_MS = 3000;

    public ItemLimitListener(LattePlugin plugin) {
        this.plugin = plugin;
        loadLimits();
        reloadEncumbranceSettings();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private void reloadEncumbranceSettings() {
        encumbranceEffects.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("item-limits");

        if (section == null) {
            // config-defaults
            encumbranceMessageType = "ACTION_BAR";
            encumbranceText = "You are overencumbered and cannot run!";
            encumbranceEffects.add(new PotionEffect(PotionEffectType.BLINDNESS, 40, 3, false, false, true));
            encumbranceEffects.add(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, false, true));
            encumbranceEffects.add(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1, false, false, true));
            return;
        }

        encumbranceMessageType = section.getString("message-type", "ACTION_BAR").toUpperCase();
        encumbranceText = section.getString("text", "You are overencumbered and cannot run!");
        encumbranceSubtitle = section.getString("subtitle", "");

        if (section.isConfigurationSection("effects")) {
            ConfigurationSection effectsSection = section.getConfigurationSection("effects");
            for (String key : effectsSection.getKeys(false)) {
                ConfigurationSection effectConfig = effectsSection.getConfigurationSection(key);
                if (effectConfig == null) continue;

                PotionEffectType type = PotionEffectType.getByName(key);
                if (type == null) {
                    plugin.getLogger().warning("Invalid encumbrance effect type in config: " + key);
                    continue;
                }

                int duration = effectConfig.getInt("duration", 40);
                int amplifier = effectConfig.getInt("amplifier", 0);
                boolean ambient = effectConfig.getBoolean("ambient", false);
                boolean particles = effectConfig.getBoolean("particles", false);
                boolean icon = effectConfig.getBoolean("icon", true);

                encumbranceEffects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
        }
    }

    private void tick() {
        for (UUID uuid : encumberedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {

                Component msgComp = Component.text(encumbranceText, NamedTextColor.RED);

                if ("TITLE".equals(encumbranceMessageType)) {
                    Component subComp = Component.text(encumbranceSubtitle, NamedTextColor.RED);
                    Title.Times times = Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ofMillis(500));
                    Title title = Title.title(msgComp, subComp, times);
                    player.showTitle(title);
                } else {
                    player.sendActionBar(msgComp);
                }

                // Apply effects
                for (PotionEffect effect : encumbranceEffects) {
                    player.addPotionEffect(effect);
                }
            }
        }
    }

    private void scheduleCheck(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> checkEncumbrance(player));
    }

    private void checkEncumbrance(Player player) {
        if (!player.isOnline()) return;

        boolean isEncumbered = false;

        for (LimitRule rule : limits) {
            int current = getCurrentScore(player, rule);
            int max = rule.getMax();

            if (current > max) {
                isEncumbered = true;
                sendLimitMessage(player, rule.getName(), max);
            }
        }

        if (isEncumbered) {
            encumberedPlayers.add(player.getUniqueId());
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
        } else {
            encumberedPlayers.remove(player.getUniqueId());
        }
    }

    private interface LimitRule {
        String getName();
        int getMax();
        boolean matches(ItemStack item);
        int getWeight(ItemStack item);
    }

    private class MaterialLimitRule implements LimitRule {
        final Material material;
        final int max;

        MaterialLimitRule(Material material, int max) {
            this.material = material;
            this.max = max;
        }

        @Override public String getName() { return material.name(); }
        @Override public int getMax() { return max; }
        @Override public boolean matches(ItemStack item) { return item != null && item.getType() == material; }
        @Override public int getWeight(ItemStack item) { return matches(item) ? 1 : 0; }
    }

    private class PotionLimitRule implements LimitRule {
        final PotionType type;
        final int max;

        PotionLimitRule(PotionType type, int max) {
            this.type = type;
            this.max = max;
        }

        @Override public String getName() { return type.name(); }
        @Override public int getMax() { return max; }
        @Override
        public boolean matches(ItemStack item) {
            if (item == null) return false;
            return (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)
                    && item.getItemMeta() instanceof PotionMeta meta
                    && meta.getBasePotionType() == type;
        }
        @Override public int getWeight(ItemStack item) { return matches(item) ? 1 : 0; }
    }

    private class GroupLimitRule implements LimitRule {
        final String name;
        final int max;
        final Map<Object, Integer> weights;

        GroupLimitRule(String name, int max, Map<Object, Integer> weights) {
            this.name = name;
            this.max = max;
            this.weights = weights;
        }

        @Override public String getName() { return name; }
        @Override public int getMax() { return max; }
        @Override
        public boolean matches(ItemStack item) {
            if (item == null) return false;
            // Check Material
            if (weights.containsKey(item.getType())) return true;
            // Check PotionType
            if ((item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)
                    && item.getItemMeta() instanceof PotionMeta meta) {
                return weights.containsKey(meta.getBasePotionType());
            }
            return false;
        }
        @Override
        public int getWeight(ItemStack item) {
            if (item == null) return 0;
            // Try Material
            Integer w = weights.get(item.getType());
            if (w != null) return w;
            // Try PotionType
            if ((item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)
                    && item.getItemMeta() instanceof PotionMeta meta) {
                w = weights.get(meta.getBasePotionType());
                if (w != null) return w;
            }
            return 0;
        }
    }

    private void loadLimits() {
        limits.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("item-limits");
        if (section == null) return;

        if (section.isConfigurationSection("groups")) {
            ConfigurationSection groups = section.getConfigurationSection("groups");
            for (String groupKey : groups.getKeys(false)) {
                ConfigurationSection group = groups.getConfigurationSection(groupKey);
                if (group == null) continue;
                int max = group.getInt("limit", -1);
                if (max <= 0) continue;

                Map<Object, Integer> weights = new HashMap<>();
                ConfigurationSection items = group.getConfigurationSection("items");
                if (items != null) {
                    for (String itemKey : items.getKeys(false)) {
                        int weight = items.getInt(itemKey, 1);
                        Object keyObj = parseKey(itemKey);
                        if (keyObj != null) {
                            weights.put(keyObj, weight);
                        }
                    }
                }
                if (!weights.isEmpty()) {
                    limits.add(new GroupLimitRule(groupKey, max, weights));
                }
            }
        }

        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase("enabled") || key.equalsIgnoreCase("groups")) continue;
            int max = section.getInt(key, -1);
            if (max < 0) continue;

            Object keyObj = parseKey(key);
            if (keyObj instanceof Material mat) {
                limits.add(new MaterialLimitRule(mat, max));
            } else if (keyObj instanceof PotionType pt) {
                limits.add(new PotionLimitRule(pt, max));
            }
        }
    }

    private Object parseKey(String key) {
        try {
            return Material.valueOf(key);
        } catch (IllegalArgumentException ignored) {}
        try {
            return PotionType.valueOf(key);
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    private int getCurrentScore(Player player, LimitRule rule) {
        int total = 0;
        total += countInventory(player.getInventory().getStorageContents(), rule);
        total += countInventory(player.getInventory().getArmorContents(), rule);
        total += countInventory(new ItemStack[]{player.getInventory().getItemInOffHand()}, rule); // Wrap single item

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.WORKBENCH) {
            total += countInventory(top.getContents(), rule);
        }

        total += countItem(player.getItemOnCursor(), rule);
        return total;
    }

    private int countInventory(ItemStack[] items, LimitRule rule) {
        int total = 0;
        for (ItemStack item : items) {
            total += countItem(item, rule);
        }
        return total;
    }

    private boolean isBundle(Material material) {
        return material == Material.BUNDLE || material.name().endsWith("_BUNDLE");
    }

    private int countItem(ItemStack item, LimitRule rule) {
        if (item == null) return 0;
        int score = 0;

        if (rule.matches(item)) {
            score += item.getAmount() * rule.getWeight(item);
        }

        if (isBundle(item.getType()) && item.hasItemMeta()) {
            if (item.getItemMeta() instanceof BundleMeta bundleMeta) {
                for (ItemStack inner : bundleMeta.getItems()) {
                    score += countItem(inner, rule);
                }
            }
        }
        return score;
    }

    private void sendLimitMessage(Player player, String ruleName, int max) {
        long now = System.currentTimeMillis();
        Map<String, Long> perRule = messageCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        long last = perRule.getOrDefault(ruleName, 0L);
        if (now - last < MESSAGE_COOLDOWN_MS) return;
        perRule.put(ruleName, now);
        player.sendMessage(Component.text("You are only be able to hold a maximum " + max + " (score) of " + ruleName.toLowerCase() + " in your inventory!", NamedTextColor.RED));
    }

    private int calculateAllowed(Player player, ItemStack item) {
        if (item == null) return Integer.MAX_VALUE;
        int minAllowed = Integer.MAX_VALUE;

        for (LimitRule rule : limits) {
            if (rule.matches(item)) {
                int current = getCurrentScore(player, rule);
                int max = rule.getMax();
                int weight = rule.getWeight(item);

                if (current >= max) return 0;

                int remainingScore = max - current;
                int allowedForThisRule = remainingScore / weight;

                if (allowedForThisRule < minAllowed) {
                    minAllowed = allowedForThisRule;
                }
            }
        }
        return minAllowed;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();

        int allowed = calculateAllowed(player, stack);

        if (allowed <= 0) {
            event.setCancelled(true);
            scheduleCheck(player); // Check just in case
        } else if (allowed < stack.getAmount()) {
            event.setCancelled(true);

            ItemStack toGive = stack.clone();
            toGive.setAmount(allowed);

            HashMap<Integer, ItemStack> inventoryLeftovers = player.getInventory().addItem(toGive);

            int actuallyTaken = allowed;
            if (!inventoryLeftovers.isEmpty()) {
                int notTaken = 0;
                for (ItemStack vals : inventoryLeftovers.values()) {
                    notTaken += vals.getAmount();
                }
                actuallyTaken = allowed - notTaken;
            }

            if (actuallyTaken > 0) {
                ItemStack newStack = stack.clone();
                newStack.setAmount(stack.getAmount() - actuallyTaken);
                event.getItem().setItemStack(newStack);

                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.0f);
            }
            scheduleCheck(player);
        } else {
            scheduleCheck(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        encumberedPlayers.remove(event.getPlayer().getUniqueId());
        messageCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        if (encumberedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && encumberedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}