package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import io.github.chanemilia.LGPlugin.Utils.ItemMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CombatLogListener implements Listener {

    private final LGPlugin plugin;
    private final NamespacedKey durabilityKey;

    private static final double MIN_DAMAGE = 1.0;

    private final Map<UUID, UUID> combatPairs = new HashMap<>();
    private final Map<UUID, Integer> timers = new HashMap<>();
    private final Map<UUID, BukkitRunnable> tasks = new HashMap<>();

    public CombatLogListener(LGPlugin plugin) {
        this.plugin = plugin;
        this.durabilityKey = new NamespacedKey(plugin, "original_elytra_durability");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!timers.containsKey(player.getUniqueId())) return;

        InventoryType type = event.getInventory().getType();
        boolean cancelled = false;

        if (type == InventoryType.ENDER_CHEST) {
            if (plugin.getConfig().getBoolean("combatlog.disable-echest", false)) {
                cancelled = true;
            }
        } else if (type == InventoryType.SHULKER_BOX) {
            if (plugin.getConfig().getBoolean("combatlog.disable-shulkers", false)) {
                cancelled = true;
            }
        } else if (isGeneralContainer(type)) {
            if (plugin.getConfig().getBoolean("combatlog.disable-containers", false)) {
                cancelled = true;
            }
        }

        if (cancelled) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot open this in combat!", NamedTextColor.RED));
        }
    }

    private boolean isGeneralContainer(InventoryType type) {
        return switch (type) {
            case CHEST, BARREL, HOPPER, DISPENSER, DROPPER, FURNACE, BLAST_FURNACE, SMOKER, BREWING, CRAFTER -> true;
            default -> false;
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.getFinalDamage() <= MIN_DAMAGE) return;

        tagPlayers(victim, attacker);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isProjectileItem(item.getType())) {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (isProjectileItem(off.getType())) {
                item = off;
            }
        }

        tryApplyCooldown(player, item);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        tryApplyCooldown(event.getPlayer(), event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        tryApplyCooldown(event.getPlayer(), event.getItem());
    }

    private boolean isProjectileItem(Material mat) {
        return mat == Material.ENDER_PEARL || mat == Material.WIND_CHARGE ||
                mat == Material.TRIDENT || mat == Material.SNOWBALL ||
                mat == Material.EGG || mat == Material.SPLASH_POTION ||
                mat == Material.LINGERING_POTION;
    }

    private void tryApplyCooldown(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        List<Map<?, ?>> rules = plugin.getConfig().getMapList("combatlog.cooldowns");
        Material mat = item.getType();
        int maxDuration = -1;

        for (Map<?, ?> rule : rules) {
            String matName = (String) rule.get("material");
            if (matName == null || !matName.equals(mat.name())) continue;

            Object globalObj = rule.get("global");
            boolean global = (globalObj instanceof Boolean) ? (Boolean) globalObj : false;

            boolean inCombat = timers.containsKey(player.getUniqueId());

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
            if (!player.hasCooldown(mat) || player.getCooldown(mat) < maxDuration) {
                player.setCooldown(mat, maxDuration);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.isGliding() && timers.containsKey(player.getUniqueId())) {
            if (plugin.getConfig().getBoolean("combatlog.disable-elytra", false)) {
                event.setCancelled(true);
            }
        }
    }

    private void tagPlayers(Player p1, Player p2) {
        setCombat(p1.getUniqueId(), p2.getUniqueId());
        setCombat(p2.getUniqueId(), p1.getUniqueId());
    }

    private void setCombat(UUID player, UUID opponent) {
        combatPairs.put(player, opponent);
        int combatTime = plugin.getConfig().getInt("combatlog.timer", 30);
        timers.put(player, combatTime);

        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            if (plugin.getConfig().getBoolean("combatlog.disable-elytra", false)) {
                if (p.isGliding()) p.setGliding(false);
                breakElytra(p);
            }
        }

        if (tasks.containsKey(player)) {
            tasks.get(player).cancel();
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                int time = timers.getOrDefault(player, 0);
                Player p = Bukkit.getPlayer(player);

                if (p == null || time <= 0) {
                    clearCombat(player);
                    cancel();
                    return;
                }

                p.sendActionBar(
                        Component.text("Combat: ", NamedTextColor.RED)
                                .append(Component.text(time + "s", NamedTextColor.RED))
                );

                if (time % 20 == 0 && plugin.getConfig().getBoolean("combatlog.disable-elytra", false)) {
                    breakElytra(p);
                }

                timers.put(player, time - 1);
            }
        };

        task.runTaskTimer(plugin, 0L, 20L);
        tasks.put(player, task);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID dead = event.getEntity().getUniqueId();
        UUID opponent = combatPairs.get(dead);

        clearCombat(dead);
        if (opponent != null) {
            clearCombat(opponent);
        }

        for (ItemStack drop : event.getDrops()) {
            restoreItemStack(drop);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restoreElytra(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handleCombatLogout(event.getPlayer());
        restoreElytra(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        handleCombatLogout(event.getPlayer());
        restoreElytra(event.getPlayer());
    }

    private void handleCombatLogout(Player player) {
        UUID uuid = player.getUniqueId();
        if (!timers.containsKey(uuid)) return;

        UUID opponentUUID = combatPairs.get(uuid);
        Player opponent = opponentUUID != null ? Bukkit.getPlayer(opponentUUID) : null;

        String playerName = player.getName();
        String opponentName = opponent != null ? opponent.getName() : "someone";
        String message = playerName + " logged out of combat while fighting " + opponentName + " and died";

        Bukkit.broadcast(Component.text(message, NamedTextColor.RED));

        player.setHealth(0.0);

        clearCombat(uuid);
        if (opponentUUID != null) {
            clearCombat(opponentUUID);
        }
    }

    public void removeAllTags() {
        for (BukkitRunnable task : tasks.values()) {
            task.cancel();
        }

        for (UUID uuid : timers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) restoreElytra(p);
        }

        tasks.clear();
        timers.clear();
        combatPairs.clear();
    }

    private void clearCombat(UUID player) {
        timers.remove(player);
        combatPairs.remove(player);

        Player p = Bukkit.getPlayer(player);
        if (p != null) restoreElytra(p);

        if (tasks.containsKey(player)) {
            tasks.get(player).cancel();
            tasks.remove(player);
        }
    }

    private void breakElytra(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.ELYTRA) continue;

            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof Damageable damageable)) continue;

            PersistentDataContainer container = meta.getPersistentDataContainer();

            if (!container.has(durabilityKey, PersistentDataType.INTEGER)) {
                container.set(durabilityKey, PersistentDataType.INTEGER, damageable.getDamage());
            }

            if (damageable.getDamage() < 431) {
                damageable.setDamage(431);
                item.setItemMeta(meta);
            }
        }
    }

    private void restoreElytra(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            restoreItemStack(item);
        }
    }

    private void restoreItemStack(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA) return;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(durabilityKey, PersistentDataType.INTEGER)) {
            Integer originalDamage = container.get(durabilityKey, PersistentDataType.INTEGER);
            if (originalDamage != null) {
                damageable.setDamage(originalDamage);
                container.remove(durabilityKey);
                item.setItemMeta(meta);
            }
        }
    }
}