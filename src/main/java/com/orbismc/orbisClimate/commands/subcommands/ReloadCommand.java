package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends BaseSubCommand {

    public ReloadCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.reload", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading OrbisClimate configuration...");

        try {
            plugin.reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "OrbisClimate configuration reloaded successfully!");

            // Show performance impact of reload
            if (plugin.getPerformanceMonitor() != null) {
                double tps = plugin.getPerformanceMonitor().getCurrentTPS();
                sender.sendMessage(ChatColor.GRAY + "Current TPS: " + String.format("%.2f", tps));
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
            plugin.getLogger().severe("Configuration reload failed: " + e.getMessage());
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Reload the plugin configuration";
    }

    @Override
    public String getUsage() {
        return "/climate reload";
    }
}