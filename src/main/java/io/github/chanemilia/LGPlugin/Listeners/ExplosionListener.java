package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

// Thank you so much Woolyenough
public class ExplosionListener implements Listener {

    private final LGPlugin plugin;

    public ExplosionListener(LGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;

        if (event instanceof EntityDamageByEntityEvent ede) {
            if (ede.getDamager() instanceof ExplosiveMinecart) {
                double multiplier = plugin.getConfig().getDouble("explosion-damage.cart", 1.0);
                event.setDamage(event.getDamage() * multiplier);
            } else if (ede.getDamager() instanceof EnderCrystal) {
                double multiplier = plugin.getConfig().getDouble("explosion-damage.crystal", 0.25);
                event.setDamage(event.getDamage() * multiplier);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(EntityDamageByBlockEvent event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof LivingEntity)) return;

        Material damagerType = event.getDamagerBlockState() != null ? event.getDamagerBlockState().getType() : null;

        if (damagerType == null) return;

        double multiplier;

        switch (damagerType) { // I could save a few lines but this saves a few milliseconds soooo
            case WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED, YELLOW_BED,
                 LIME_BED, PINK_BED, GRAY_BED, LIGHT_GRAY_BED, CYAN_BED,
                 PURPLE_BED, BLUE_BED, BROWN_BED, GREEN_BED, RED_BED, BLACK_BED:
                multiplier = plugin.getConfig().getDouble("explosion-damage.bed", 0.25);
                event.setDamage(event.getDamage() * multiplier);
                break;

            case RESPAWN_ANCHOR:
                multiplier = plugin.getConfig().getDouble("explosion-damage.anchor", 0.25);
                event.setDamage(event.getDamage() * multiplier);
                break;

            default:
                break;
        }
    }
}
