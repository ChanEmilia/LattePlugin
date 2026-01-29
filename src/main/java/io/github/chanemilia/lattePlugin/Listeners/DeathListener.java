package io.github.chanemilia.lattePlugin.Listeners;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class DeathListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()));
        Component name = Component.text(player.getName() + "'s Head")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        Player killer = player.getKiller();
        if (killer != null) {
            List<Component> lore = new ArrayList<>();
            Component loreLine = Component.text("Killed by " + killer.getName())
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
            lore.add(loreLine);
            meta.lore(lore);
        }

        head.setItemMeta(meta);

        event.getDrops().add(head);
    }
}