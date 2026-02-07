package io.github.chanemilia.LGPlugin.Listeners;

import io.github.chanemilia.LGPlugin.LGPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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
            PotionEffectType configuredType = getPotionEffectType(effectName);

            if (configuredType != null && configuredType.equals(type)) {
                int maxAmplifier = effectsConfig.getInt(effectName) + 1;

                if (newEffect.getAmplifier() > maxAmplifier) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private PotionEffectType getPotionEffectType(String effectName) {
        try {
            NamespacedKey key;
            if (effectName.contains(":")) {
                key = NamespacedKey.fromString(effectName);
            } else {
                // If no namespace provided, assume minecraft namespace
                key = NamespacedKey.minecraft(effectName.toLowerCase());
            }

            if (key != null) {
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(key);
                if (type != null) {
                    return type;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid NamespacedKey format, will try enum fallback
        }

        try {
            return PotionEffectType.getByName(effectName);
        } catch (IllegalArgumentException ignored) {
            // Not found, perhaps I'll add config error logs in the future
        }

        return null;
    }
}