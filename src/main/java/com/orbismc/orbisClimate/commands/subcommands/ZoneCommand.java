package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.ClimateZoneManager;
import com.orbismc.orbisClimate.WeatherForecast;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ZoneCommand extends BaseSubCommand {

    public ZoneCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.info", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();

        if (climateZoneManager == null) {
            player.sendMessage(ChatColor.RED + "Climate zone system not available!");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "=== Climate Zone Information ===");

        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);
        player.sendMessage(ChatColor.AQUA + "Current Zone: " + ChatColor.WHITE + zone.getDisplayName());

        // Zone characteristics
        player.sendMessage(ChatColor.AQUA + "Temperature Range: " + ChatColor.WHITE +
                zone.getMinTemp() + "°C to " + zone.getMaxTemp() + "°C");

        // Zone-specific weather
        WeatherForecast.WeatherType zoneWeather = climateZoneManager.getPlayerZoneWeather(player);
        player.sendMessage(ChatColor.AQUA + "Zone Weather: " + ChatColor.WHITE + zoneWeather.getDisplayName());

        // Special zone effects
        switch (zone) {
            case ARCTIC:
                player.sendMessage(ChatColor.AQUA + "Zone Effects: " + ChatColor.WHITE +
                        "Aurora at night, Wind-blown snow, Extreme cold");
                break;
            case DESERT:
                player.sendMessage(ChatColor.AQUA + "Zone Effects: " + ChatColor.WHITE +
                        "Heat mirages, Drought conditions, Sandstorms");
                if (climateZoneManager.isPlayerInDrought(player)) {
                    player.sendMessage(ChatColor.RED + "⚠ Drought active - increased heat and effects!");
                }
                break;
            case TEMPERATE:
                player.sendMessage(ChatColor.AQUA + "Zone Effects: " + ChatColor.WHITE +
                        "Seasonal variation, Hurricane potential, Moderate climate");
                break;
        }

        // Position info
        player.sendMessage(ChatColor.GRAY + "Location: " +
                player.getLocation().getBlockX() + ", " +
                player.getLocation().getBlockY() + ", " +
                player.getLocation().getBlockZ());
        player.sendMessage(ChatColor.GRAY + "Biome: " +
                player.getLocation().getBlock().getBiome().name().toLowerCase().replace("_", " "));

        return true;
    }

    @Override
    public String getDescription() {
        return "Show detailed climate zone information";
    }

    @Override
    public String getUsage() {
        return "/climate zone";
    }
}