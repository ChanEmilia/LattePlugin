package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import io.github.chanemilia.LGPlugin.Utils.ItemMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
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
            cancelled = plugin.getConfig().getBoolean("combatlog.disable-echest", false);
        } else if (type == InventoryType.SHULKER_BOX) {
            cancelled = plugin.getConfig().getBoolean("combatlog.disable-shulkers", false);
        } else if (isGeneralContainer(type)) {
            cancelled = plugin.getConfig().getBoolean("combatlog.disable-containers", false);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (isChargeable(item.getType())) return;

        final ItemStack finalItem = item.clone();
        Bukkit.getScheduler().runTask(plugin, () -> tryApplyCooldown(event.getPlayer(), finalItem));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        Projectile projectile = event.getEntity();
        ItemStack item = null;

        if (projectile instanceof EnderPearl) item = new ItemStack(Material.ENDER_PEARL);
        else if (projectile instanceof Snowball) item = new ItemStack(Material.SNOWBALL);
        else if (projectile instanceof Egg) item = new ItemStack(Material.EGG);
        else if (projectile instanceof Trident trident) {
            item = trident.getItemStack();
        }
        else if (projectile instanceof ThrownPotion potion) item = potion.getItem();
        else if (projectile.getType().name().equals("WIND_CHARGE")) {
            Material windMat = ItemMatcher.resolveMaterial("WIND_CHARGE");
            if (windMat != null) item = new ItemStack(windMat);
        }

        if (item == null) {
            item = player.getInventory().getItemInMainHand();
            if (!isProjectileItem(item.getType())) {
                item = player.getInventory().getItemInOffHand();
                if (!isProjectileItem(item.getType())) item = null;
            }
        }

        if (item != null) {
            final ItemStack finalItem = item;
            Bukkit.getScheduler().runTask(plugin, () -> tryApplyCooldown(player, finalItem));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        final ItemStack item = event.getItem().clone();
        Bukkit.getScheduler().runTask(plugin, () -> tryApplyCooldown(event.getPlayer(), item));
    }

    @EventHandler(ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        final ItemStack item = event.getItem().clone();
        Bukkit.getScheduler().runTask(plugin, () -> tryApplyCooldown(event.getPlayer(), item));
    }

    private boolean isChargeable(Material mat) {
        return mat.isEdible() ||
                mat == Material.BOW ||
                mat == Material.CROSSBOW ||
                mat == Material.SHIELD ||
                mat == Material.TRIDENT ||
                mat == Material.GOAT_HORN ||
                mat == Material.SPYGLASS;
    }

    private boolean isProjectileItem(Material mat) {
        String name = mat.name();
        return name.contains("PEARL") || name.contains("CHARGE") ||
                name.contains("TRIDENT") || name.contains("SNOWBALL") ||
                name.contains("EGG") || name.contains("POTION");
    }

    private void tryApplyCooldown(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        Material mat = item.getType();
        List<Map<?, ?>> rules = plugin.getConfig().getMapList("combatlog.cooldowns");
        int maxDuration = -1;

        for (Map<?, ?> rule : rules) {
            String configMatName = (String) rule.get("material");
            if (configMatName == null) continue;

            Material ruleMat = ItemMatcher.resolveMaterial(configMatName);
            if (ruleMat == null || ruleMat != mat) continue;

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
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() != Material.AIR) {
            tryApplyCooldown(attacker, weapon);
        }

        if (event.getFinalDamage() <= MIN_DAMAGE) return;
        tagPlayers(victim, attacker);
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
        if (tasks.containsKey(player)) tasks.get(player).cancel();

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
                p.sendActionBar(Component.text("Combat: ", NamedTextColor.RED).append(Component.text(time + "s", NamedTextColor.RED)));
                if (plugin.getConfig().getBoolean("combatlog.disable-elytra", false)) {
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
        if (opponent != null) clearCombat(opponent);

        for (ItemStack drop : event.getDrops()) {
            restoreItemStack(drop);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        restoreItemStack(event.getItemDrop().getItemStack());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { restoreElytra(event.getPlayer()); }

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
        if (opponentUUID != null) clearCombat(opponentUUID);
    }

    public void removeAllTags() {
        for (BukkitRunnable task : tasks.values()) task.cancel();
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