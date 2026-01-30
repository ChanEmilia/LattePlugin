package io.github.chanemilia.LGPlugin.Commands;

import io.github.chanemilia.LGPlugin.LGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ReloadCommand implements CommandExecutor {

    private final LattePlugin plugin;

    public ReloadCommand(LattePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("legacyground.reload")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        plugin.reload();
        sender.sendMessage(Component.text("Configuration reloaded successfully.", NamedTextColor.GREEN));
        return true;
    }
}