package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegenerateCommand extends BaseSubCommand {

    public RegenerateCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.weather", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        try {
            plugin.getWeatherForecast().regenerateForecast(player.getWorld());
            player.sendMessage(ChatColor.GREEN + "Weather forecast regenerated for " +
                    player.getWorld().getName() + "!");

            // Show brief info about the new forecast
            player.sendMessage(ChatColor.GRAY + "Use '/climate forecast' to view the new forecast.");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error regenerating forecast: " + e.getMessage());
            plugin.getLogger().severe("Forecast regeneration failed: " + e.getMessage());
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Regenerate the weather forecast for the current world";
    }

    @Override
    public String getUsage() {
        return "/climate regenerate";
    }
}