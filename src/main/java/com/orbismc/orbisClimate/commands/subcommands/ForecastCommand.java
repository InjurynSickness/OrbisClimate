package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.WeatherProgressionManager;
import me.casperge.realisticseasons.calendar.Date;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ForecastCommand extends BaseSubCommand {

    public ForecastCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.forecast", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        WeatherForecast weatherForecast = plugin.getWeatherForecast();
        WeatherForecast.DailyForecast forecast = weatherForecast.getForecast(player.getWorld());

        if (forecast == null) {
            player.sendMessage(ChatColor.RED + "No forecast available for this world yet!");
            return true;
        }

        // Build header with date and season info
        String headerText = "=== Weather Forecast";

        if (weatherForecast.isRealisticSeasonsEnabled() && forecast.getDate() != null) {
            Date date = forecast.getDate();
            String dateStr = date.getMonth() + "/" + date.getDay() + "/" + date.getYear();
            headerText += " - " + dateStr;

            if (forecast.getSeason() != null) {
                headerText += " (" + forecast.getSeason().toString().toLowerCase() + ")";
            }
        }

        headerText += " ===";
        player.sendMessage(ChatColor.GOLD + headerText);

        // Show forecast periods
        player.sendMessage(ChatColor.YELLOW + "Morning (6AM-12PM): " + ChatColor.WHITE +
                forecast.getMorningWeather().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Afternoon (12PM-6PM): " + ChatColor.WHITE +
                forecast.getAfternoonWeather().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Evening (6PM-12AM): " + ChatColor.WHITE +
                forecast.getEveningWeather().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Night (12AM-6AM): " + ChatColor.WHITE +
                forecast.getNightWeather().getDisplayName());

        // Show current weather and progression
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        player.sendMessage(ChatColor.AQUA + "Current: " + ChatColor.WHITE + currentWeather.getDisplayName());

        if (plugin.getWeatherProgressionManager() != null) {
            WeatherProgressionManager.WeatherProgression progression =
                    plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
            if (progression != WeatherProgressionManager.WeatherProgression.CLEAR) {
                player.sendMessage(ChatColor.AQUA + "Progression: " + ChatColor.WHITE +
                        progression.name().toLowerCase().replace("_", " "));
            }

            if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
                player.sendMessage(ChatColor.WHITE + "❄ Hail is currently falling!");
            }
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Show the daily weather forecast";
    }

    @Override
    public String getUsage() {
        return "/climate forecast";
    }
}