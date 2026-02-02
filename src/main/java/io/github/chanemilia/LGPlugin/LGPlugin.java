package io.github.chanemilia.LGPlugin;

import io.github.chanemilia.LGPlugin.Listeners.*;
import io.github.chanemilia.LGPlugin.Commands.MainCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class LGPlugin extends JavaPlugin {

    private CombatLogListener combatLogListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlugin();

        PluginCommand command = getCommand("lgplugin");
        if (command != null) {
            command.setExecutor(new MainCommand(this));
        }

        getLogger().info("Good job Nina Iseri your plugin works!");
    }

    @Override
    public void onDisable() {
        if (combatLogListener != null) {
            combatLogListener.removeAllTags();
        }
    }

    public void reload() {
        if (combatLogListener != null) {
            combatLogListener.removeAllTags();
        }
        HandlerList.unregisterAll(this);
        reloadConfig();
        loadPlugin();
    }

    private void loadPlugin() {
        combatLogListener = new CombatLogListener(this);
        if (getConfig().getBoolean("combatlog.enabled", true)) {
            getServer().getPluginManager().registerEvents(combatLogListener, this);
        }

        if (getConfig().getBoolean("death-drops.enabled", true)) {
            getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        }

        if (getConfig().getBoolean("disabled-potions.enabled", true)) {
            getServer().getPluginManager().registerEvents(new EffectListener(this), this);
        }

        if (getConfig().getBoolean("restricted-enchantments.enabled", true)) {
            getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        }

        if (getConfig().getBoolean("explosion-damage.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
        }

        if (getConfig().getBoolean("item-limits.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ItemLimitListener(this), this);
        }
    }
}