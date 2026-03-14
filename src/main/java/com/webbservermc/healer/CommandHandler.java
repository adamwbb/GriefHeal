package com.webbservermc.griefheal;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandHandler implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("griefheal.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                GriefHeal.getInstance().reloadConfig();
                sender.sendMessage("§a[GriefHeal] Config reloaded.");
                return true;
            } else if (args[0].equalsIgnoreCase("now")) {
                GriefHeal.getInstance().processAll();
                sender.sendMessage("§a[GriefHeal] Manual restoration triggered.");
                return true;
            }
        }
        
        sender.sendMessage("§8=== §aGriefHeal Commands §8===");
        sender.sendMessage("§7/gh reload §f- Reloads the config.yml");
        sender.sendMessage("§7/gh now §f- Forces all pending restorations to finish immediately");
        
        return true;
    }
}