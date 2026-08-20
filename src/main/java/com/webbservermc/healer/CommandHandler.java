package com.webbservermc.griefheal;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class CommandHandler implements CommandExecutor {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("griefheal.admin")) {
            sender.sendMessage(MM.deserialize("<red>You do not have permission to use this command.</red>"));
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                GriefHeal.getInstance().reloadPluginConfig();
                sender.sendMessage(MM.deserialize("<green>[GriefHeal] Configuration and entity cache reloaded.</green>"));
                return true;
            } else if (args[0].equalsIgnoreCase("now")) {
                GriefHeal.getInstance().processAll();
                sender.sendMessage(MM.deserialize("<green>[GriefHeal] Immediate restoration triggered for all active craters.</green>"));
                return true;
            }
        }

        sender.sendMessage(MM.deserialize("<dark_gray>=== <green>GriefHeal Commands</green> <dark_gray>==="));
        sender.sendMessage(MM.deserialize("<gray>/gh reload <white>- Reloads config.yml and caching tables"));
        sender.sendMessage(MM.deserialize("<gray>/gh now <white>- Forces all active restorations to complete immediately"));

        return true;
    }
}
