package io.github.chanemilia.lattePlugin;

import io.github.chanemilia.lattePlugin.Listeners.*;
import io.github.chanemilia.lattePlugin.Commands.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class LattePlugin extends JavaPlugin {

    private CombatLogListener combatLogListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlugin();
        getCommand("lattereload").setExecutor(new ReloadCommand(this));
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
            getServer().getPluginManager().registerEvents(new DeathListener(), this);
        }

        if (getConfig().getBoolean("disabled-effects.enabled", true)) {
            getServer().getPluginManager().registerEvents(new EffectListener(this), this);
        }

        if (getConfig().getBoolean("restricted-enchantments.enabled", true)) {
            getServer().getPluginManager().registerEvents(new EffectListener(this), this);
        }

        if (getConfig().getBoolean("explosion-damage.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
        }

        if (getConfig().getBoolean("item-limits.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ItemLimitListener(this), this);
        }

        if (getConfig().getBoolean("disabled-effects.enabled", true)) {
            getServer().getPluginManager().registerEvents(new EffectListener(this), this);
        }
    }
}