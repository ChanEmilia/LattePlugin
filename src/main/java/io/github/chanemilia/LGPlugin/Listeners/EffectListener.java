package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import io.github.chanemilia.LGPlugin.Utils.ItemMatcher;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class EffectListener implements Listener {

    private final LGPlugin plugin;

    public EffectListener(LGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        PotionEffect newEffect = event.getNewEffect();
        if (newEffect == null) return;

        PotionEffectType type = newEffect.getType();
        ConfigurationSection effectsConfig = plugin.getConfig().getConfigurationSection("disabled-potions.effects");

        if (effectsConfig == null) return;

        for (String effectName : effectsConfig.getKeys(false)) {
            if (ItemMatcher.matchesPotion(type, effectName)) {
                int maxAmplifier = effectsConfig.getInt(effectName);

                if (newEffect.getAmplifier() > maxAmplifier) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }
}