package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import io.github.chanemilia.LGPlugin.Utils.ItemMatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class CooldownListener implements Listener {

    private final LGPlugin plugin;
    private final CombatLogListener combatLog;

    public CooldownListener(LGPlugin plugin, CombatLogListener combatLog) {
        this.plugin = plugin;
        this.combatLog = combatLog;
    }

    // Consumables, apply the cooldown after the item is consumed
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        final ItemStack item = event.getItem().clone();
        Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(event.getPlayer(), item));
    }

    // Projectiles, apply after the item is used
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        Projectile projectile = event.getEntity();
        ItemStack itemStack = null;

        switch (projectile) {
            case Trident trident -> itemStack = trident.getItemStack();
            case ThrownPotion potion -> itemStack = potion.getItem();
            case EnderPearl enderPearl -> itemStack = new ItemStack(Material.ENDER_PEARL);
            case Snowball snowball -> itemStack = new ItemStack(Material.SNOWBALL);
            case Egg egg -> itemStack = new ItemStack(Material.EGG);
            case WindCharge windCharge -> itemStack = new ItemStack(Material.WIND_CHARGE);
            case Firework firework -> itemStack = new ItemStack(Material.FIREWORK_ROCKET);
            default -> {
            }
        }

        if (itemStack != null) {
            final ItemStack finalItem = itemStack;
            Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(player, finalItem));
        }
    }

    // Riptide specifically
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        final ItemStack item = event.getItem().clone();
        Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(event.getPlayer(), item));
    }

    // Chargeable weapons, apply the cooldown once the item is finished charging and used
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final ItemStack bow = event.getBow() != null ? event.getBow().clone() : null;
        if (bow != null) {
            Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(player, bow));
        }
    }

    // Utility Items
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.hasItem()) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        Material type = item.getType();

        // Filter for specific utility items to avoid conflict with other events
        if (isUtilityItem(type)) {
            final ItemStack finalItem = item.clone();
            Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(event.getPlayer(), finalItem));
        }
    }

    // Blocks, apply the cooldown after the item is consumed
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        final ItemStack item = event.getItemInHand().clone();
        Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(event.getPlayer(), item));
    }

    // Weapons, apply cooldown after hitting any entity
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeaponHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;

        final ItemStack finalWeapon = weapon.clone();
        Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(player, finalWeapon));
    }

    // Shield, apply cooldown after it is disabled
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShieldDisable(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // This event fires when shield is damaged
        if (!victim.isBlocking()) return;

        ItemStack activeItem = victim.getActiveItem(); // The item they are blocking with
        if (activeItem.getType() != Material.SHIELD) return;

        final ItemStack shield = activeItem.clone();

        // Apply cooldown after shield is disabled
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.getCooldown(Material.SHIELD) > 0) {
                checkAndApplyCooldown(victim, shield);
            }
        });
    }

    // Totem of Undying, apply cooldown after one is used and disable its functionality
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        EquipmentSlot totemSlot = null;
        ItemStack totemStack = null;

        if (player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) {
            totemSlot = EquipmentSlot.HAND;
            totemStack = player.getInventory().getItemInMainHand();
        } else if (player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            totemSlot = EquipmentSlot.OFF_HAND;
            totemStack = player.getInventory().getItemInOffHand();
        }

        if (totemSlot == null) return;

        if (player.hasCooldown(Material.TOTEM_OF_UNDYING)) {
            event.setCancelled(true);
            return;
        }

        if (!event.isCancelled()) {
            ItemStack finalTotem = totemStack.clone();
            Bukkit.getScheduler().runTask(plugin, () -> checkAndApplyCooldown(player, finalTotem));
        }
    }

    // Like who would even
    private boolean isUtilityItem(Material mat) {
        return mat == Material.GOAT_HORN ||
                mat == Material.SPYGLASS ||
                mat == Material.FISHING_ROD ||
                mat == Material.FLINT_AND_STEEL ||
                mat == Material.SHEARS;
    }

    private void checkAndApplyCooldown(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        Material mat = item.getType();

        List<Map<?, ?>> rules = plugin.getConfig().getMapList("cooldowns");
        int maxDuration = -1;

        for (Map<?, ?> rule : rules) {
            String configMatName = (String) rule.get("material");
            if (configMatName == null) continue;

            if (!ItemMatcher.matchesMaterial(mat, configMatName)) continue;

            Object globalObj = rule.get("global");
            boolean global = (globalObj instanceof Boolean) ? (Boolean) globalObj : false;
            boolean inCombat = combatLog.isInCombat(player.getUniqueId());

            if (!global && !inCombat) continue;

            if (rule.containsKey("nbt")) {
                Map<?, ?> nbtRules = (Map<?, ?>) rule.get("nbt");
                if (!ItemMatcher.checkNbt(item, nbtRules)) continue;
            }

            Object durationObj = rule.get("duration");
            int duration = (durationObj instanceof Integer) ? (Integer) durationObj : 0;

            if (duration > maxDuration) {
                maxDuration = duration;
            }
        }

        if (maxDuration > 0) {
            int current = player.getCooldown(mat);
            if (current < maxDuration) {
                player.setCooldown(mat, maxDuration);
            }
        }
    }
}