package com.orbismc.orbisClimate;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class WindCommand implements CommandExecutor, TabCompleter {

    private final OrbisClimate plugin;
    private final WindManager windManager;

    public WindCommand(OrbisClimate plugin, WindManager windManager) {
        this.plugin = plugin;
        this.windManager = windManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== OrbisClimate Commands ===");
            sender.sendMessage(ChatColor.YELLOW + "/wind reload " + ChatColor.WHITE + "- Reload configuration");
            sender.sendMessage(ChatColor.YELLOW + "/wind info " + ChatColor.WHITE + "- Show wind information");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("orbisclimate.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }

                plugin.reloadConfig();
                windManager.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Wind configuration reloaded!");
                break;

            case "info":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player player = (Player) sender;
                showWindInfo(player);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command! Use /wind for help.");
                break;
        }

        return true;
    }

    private void showWindInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Wind Information ===");

        // Weather info
        if (player.getWorld().isThundering()) {
            player.sendMessage(ChatColor.AQUA + "Weather: " + ChatColor.WHITE + "Thunderstorm " + ChatColor.GRAY + "(100% wind)");
        } else if (player.getWorld().hasStorm()) {
            player.sendMessage(ChatColor.AQUA + "Weather: " + ChatColor.WHITE + "Rain " + ChatColor.GRAY + "(25% wind)");
        } else {
            player.sendMessage(ChatColor.AQUA + "Weather: " + ChatColor.WHITE + "Clear " + ChatColor.GRAY + "(10% wind)");
        }

        // Indoor/outdoor status
        boolean isIndoors = windManager.isPlayerIndoors(player);
        player.sendMessage(ChatColor.AQUA + "Location: " + ChatColor.WHITE +
                (isIndoors ? "Indoors (no wind effects)" : "Outdoors"));

        // Show active wind status if outdoors
        if (!isIndoors) {
            boolean hasActiveWind = windManager.hasActiveWind(player.getWorld());
            player.sendMessage(ChatColor.AQUA + "Wind Status: " + ChatColor.WHITE +
                    (hasActiveWind ? "Active" : "Calm"));
        }

        // Configuration info
        int heightCheck = plugin.getConfig().getInt("wind.interior_height_distance", 50);
        player.sendMessage(ChatColor.AQUA + "Ceiling Check: " + ChatColor.WHITE + heightCheck + " blocks");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "info");
        }
        return null;
    }
}