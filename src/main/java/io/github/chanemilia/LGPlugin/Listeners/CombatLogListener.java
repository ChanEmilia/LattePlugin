package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatLogListener implements Listener {

    private final LGPlugin plugin;
    private final NamespacedKey durabilityKey;

    private static final int COMBAT_TIME = 30;
    private static final double MIN_DAMAGE = 1.0; // To stop player fists from triggering combat tag which is annoying

    private final Map<UUID, UUID> combatPairs = new HashMap<>();
    private final Map<UUID, Integer> timers = new HashMap<>();
    private final Map<UUID, BukkitRunnable> tasks = new HashMap<>();

    public CombatLogListener(LGPlugin plugin) {
        this.plugin = plugin;
        this.durabilityKey = new NamespacedKey(plugin, "original_elytra_durability");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.getFinalDamage() <= MIN_DAMAGE) return;

        tagPlayers(victim, attacker);
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        if (event.getItem() == null) return;

        Player player = event.getPlayer();
        Material mat = event.getItem().getType();

        ConfigurationSection cooldowns = plugin.getConfig().getConfigurationSection("combatlog.cooldowns");
        if (cooldowns != null && cooldowns.contains(mat.name())) {
            ConfigurationSection itemConfig = cooldowns.getConfigurationSection(mat.name());
            if (itemConfig != null) {
                boolean global = itemConfig.getBoolean("global", true);
                int duration = itemConfig.getInt("duration", 0);

                if (global || timers.containsKey(player.getUniqueId())) {
                    if (!player.hasCooldown(mat)) {
                        player.setCooldown(mat, duration);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;

        if (event.isGliding() && timers.containsKey(player.getUniqueId())) {
            if (plugin.getConfig().getBoolean("combatlog.disable-elytra", false)) {
                event.setCancelled(true);
            }
        }
    }

    private void tagPlayers(Player p1, Player p2) {
        setCombat(p1.getUniqueId(), p2.getUniqueId());
        setCombat(p2.getUniqueId(), p1.getUniqueId());

        if (plugin.getConfig().getBoolean("combatlog.disable-elytra", false)) {
            if (p1.isGliding()) p1.setGliding(false);
            if (p2.isGliding()) p2.setGliding(false);
        }
    }

    private void setCombat(UUID player, UUID opponent) {
        combatPairs.put(player, opponent);
        timers.put(player, COMBAT_TIME);

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
        tasks.clear();
        timers.clear();
        combatPairs.clear();
    }

    private void clearCombat(UUID player) {
        timers.remove(player);
        combatPairs.remove(player);
        if (tasks.containsKey(player)) {
            tasks.get(player).cancel();
            tasks.remove(player);
        }
    }
}