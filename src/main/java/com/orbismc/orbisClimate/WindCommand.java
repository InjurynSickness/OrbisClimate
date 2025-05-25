package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.api.objects.Date;
import me.casperge.realisticseasons.api.objects.Season;
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
    private final WeatherForecast weatherForecast;

    public WindCommand(OrbisClimate plugin, WindManager windManager, WeatherForecast weatherForecast) {
        this.plugin = plugin;
        this.windManager = windManager;
        this.weatherForecast = weatherForecast;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== OrbisClimate Commands ===");
            sender.sendMessage(ChatColor.YELLOW + "/wind reload " + ChatColor.WHITE + "- Reload configuration");
            sender.sendMessage(ChatColor.YELLOW + "/wind info " + ChatColor.WHITE + "- Show wind information");
            sender.sendMessage(ChatColor.YELLOW + "/wind forecast " + ChatColor.WHITE + "- Show weather forecast");
            sender.sendMessage(ChatColor.YELLOW + "/wind regenerate " + ChatColor.WHITE + "- Regenerate today's forecast");
            sender.sendMessage(ChatColor.YELLOW + "/wind status " + ChatColor.WHITE + "- Show integration status");
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

            case "forecast":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player forecastPlayer = (Player) sender;
                showForecast(forecastPlayer);
                break;

            case "regenerate":
                if (!sender.hasPermission("orbisclimate.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player regenPlayer = (Player) sender;
                weatherForecast.regenerateForecast(regenPlayer.getWorld());
                sender.sendMessage(ChatColor.GREEN + "Weather forecast regenerated for " + regenPlayer.getWorld().getName() + "!");
                break;

            case "status":
                showIntegrationStatus(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command! Use /wind for help.");
                break;
        }

        return true;
    }

    private void showWindInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Wind Information ===");

        // Current weather info
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        player.sendMessage(ChatColor.AQUA + "Current Weather: " + ChatColor.WHITE + currentWeather.getDisplayName());

        // Show season information if RealisticSeasons is enabled
        if (weatherForecast.isRealisticSeasonsEnabled()) {
            Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
            Date currentDate = weatherForecast.getCurrentDate(player.getWorld());

            if (currentSeason != null) {
                player.sendMessage(ChatColor.AQUA + "Current Season: " + ChatColor.WHITE +
                        currentSeason.toString().toLowerCase());
            }

            if (currentDate != null) {
                player.sendMessage(ChatColor.AQUA + "Current Date: " + ChatColor.WHITE +
                        currentDate.getMonth() + "/" + currentDate.getDay() + "/" + currentDate.getYear());
            }
        }

        // Wind chances based on current weather
        String windChance;
        if (currentWeather == WeatherForecast.WeatherType.THUNDERSTORM) {
            windChance = "100% wind chance";
        } else if (currentWeather == WeatherForecast.WeatherType.HEAVY_RAIN ||
                currentWeather == WeatherForecast.WeatherType.LIGHT_RAIN ||
                currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
            windChance = "25% wind chance";
        } else if (currentWeather == WeatherForecast.WeatherType.SNOW) {
            windChance = "15% wind chance";
        } else {
            windChance = "10% wind chance";
        }
        player.sendMessage(ChatColor.GRAY + "(" + windChance + ")");

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

    private void showForecast(Player player) {
        WeatherForecast.DailyForecast forecast = weatherForecast.getForecast(player.getWorld());

        if (forecast == null) {
            player.sendMessage(ChatColor.RED + "No forecast available for this world yet!");
            return;
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

        // Show current weather
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        player.sendMessage(ChatColor.AQUA + "Current: " + ChatColor.WHITE + currentWeather.getDisplayName());
    }

    private void showIntegrationStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== OrbisClimate Integration Status ===");

        if (weatherForecast.isRealisticSeasonsEnabled()) {
            sender.sendMessage(ChatColor.GREEN + "✓ RealisticSeasons: " + ChatColor.WHITE + "Connected");
            sender.sendMessage(ChatColor.GRAY + "  Using RealisticSeasons time and seasons for weather generation");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "⚠ RealisticSeasons: " + ChatColor.WHITE + "Not Available");
            sender.sendMessage(ChatColor.GRAY + "  Using vanilla Minecraft time system");
        }

        // Show if player is a player to give world-specific info
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (weatherForecast.isRealisticSeasonsEnabled()) {
                Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
                Date currentDate = weatherForecast.getCurrentDate(player.getWorld());

                sender.sendMessage(ChatColor.AQUA + "World Status:");

                if (currentSeason != null) {
                    sender.sendMessage(ChatColor.WHITE + "  Season: " + currentSeason.toString().toLowerCase());
                }

                if (currentDate != null) {
                    sender.sendMessage(ChatColor.WHITE + "  Date: " +
                            currentDate.getMonth() + "/" + currentDate.getDay() + "/" + currentDate.getYear());
                }

                WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
                sender.sendMessage(ChatColor.WHITE + "  Weather: " + currentWeather.getDisplayName());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "info", "forecast", "regenerate", "status");
        }
        return null;
    }
}