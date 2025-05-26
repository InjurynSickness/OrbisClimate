package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.ClimateZoneManager;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatusCommand extends BaseSubCommand {

    public StatusCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.use", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== OrbisClimate Integration Status ===");

        WeatherForecast weatherForecast = plugin.getWeatherForecast();

        if (weatherForecast.isRealisticSeasonsEnabled()) {
            sender.sendMessage(ChatColor.GREEN + "✓ RealisticSeasons: " + ChatColor.WHITE + "Connected");
            sender.sendMessage(ChatColor.GRAY + "  Using RealisticSeasons time and seasons for weather generation");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "⚠ RealisticSeasons: " + ChatColor.WHITE + "Not Available");
            sender.sendMessage(ChatColor.GRAY + "  Using vanilla Minecraft time system");
        }

        // Feature status
        sender.sendMessage(ChatColor.AQUA + "Feature Status:");
        sender.sendMessage(ChatColor.WHITE + "  Climate Zones: " + getFeatureStatus("climate_zones"));
        sender.sendMessage(ChatColor.WHITE + "  Temperature System: " + getFeatureStatus("temperature.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Weather Progression: " + getFeatureStatus("weather_progression.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Aurora Effects: " + getFeatureStatus("aurora.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Heat Mirages: " + getFeatureStatus("heat_mirages.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Drought System: " + getFeatureStatus("drought.effects.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Dynamic Sound System: " + (plugin.getDynamicSoundManager() != null ? "ENABLED" : "DISABLED"));
        sender.sendMessage(ChatColor.WHITE + "  Performance Monitoring: " + (plugin.getPerformanceMonitor() != null ? "ENABLED" : "DISABLED"));

        // Show world-specific info if player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            showWorldSpecificStatus(player);
        }

        return true;
    }

    private void showWorldSpecificStatus(Player player) {
        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();
        WeatherForecast weatherForecast = plugin.getWeatherForecast();

        player.sendMessage(ChatColor.AQUA + "World Status (" + player.getWorld().getName() + "):");

        if (weatherForecast.isRealisticSeasonsEnabled()) {
            Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
            Date currentDate = weatherForecast.getCurrentDate(player.getWorld());

            if (currentSeason != null) {
                player.sendMessage(ChatColor.WHITE + "  Season: " + currentSeason.toString().toLowerCase());
            }

            if (currentDate != null) {
                player.sendMessage(ChatColor.WHITE + "  Date: " +
                        currentDate.getMonth() + "/" + currentDate.getDay() + "/" + currentDate.getYear());
            }
        }

        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        player.sendMessage(ChatColor.WHITE + "  Weather: " + currentWeather.getDisplayName());

        if (climateZoneManager != null) {
            ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);
            player.sendMessage(ChatColor.WHITE + "  Your Zone: " + zone.getDisplayName());
        }
    }

    private String getFeatureStatus(String configPath) {
        return plugin.getConfig().getBoolean(configPath, true) ? "ENABLED" : "DISABLED";
    }

    @Override
    public String getDescription() {
        return "Show plugin integration and feature status";
    }

    @Override
    public String getUsage() {
        return "/climate status";
    }
}
