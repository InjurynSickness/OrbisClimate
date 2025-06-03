package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.WeatherProgressionManager;
import me.casperge.realisticseasons.calendar.Date;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ForecastCommand extends BaseSubCommand {

    public ForecastCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.forecast", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        WeatherForecast weatherForecast = plugin.getWeatherForecast();
        
        // Check for detailed forecast argument
        boolean showDetailed = args.length > 0 && args[0].equalsIgnoreCase("detailed");
        
        if (showDetailed) {
            return showDetailedForecast(player, weatherForecast);
        } else {
            return showStandardForecast(player, weatherForecast);
        }
    }
    
    private boolean showStandardForecast(Player player, WeatherForecast weatherForecast) {
        WeatherForecast.DetailedForecast forecast = weatherForecast.getForecast(player.getWorld());

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
        } else {
            headerText += " - " + forecast.getForecastId();
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

        // Show current status
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        int currentHour = getCurrentHour(player, weatherForecast);
        
        player.sendMessage(ChatColor.AQUA + "Current Time: " + ChatColor.WHITE + 
            String.format("%02d:00", currentHour));
        player.sendMessage(ChatColor.AQUA + "Current Weather: " + ChatColor.WHITE + 
            currentWeather.getDisplayName());
        
        // Show next transition if any
        WeatherForecast.WeatherType nextWeather = getNextTransition(forecast, currentHour);
        if (nextWeather != null && !nextWeather.equals(currentWeather)) {
            int nextTransitionHour = getNextTransitionHour(forecast, currentHour);
            player.sendMessage(ChatColor.GRAY + "Next change: " + nextWeather.getDisplayName() + 
                " at " + String.format("%02d:00", nextTransitionHour));
        }

        // Show weather progression if active
        if (plugin.getWeatherProgressionManager() != null) {
            WeatherProgressionManager.WeatherProgression progression =
                    plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
            if (progression != WeatherProgressionManager.WeatherProgression.CLEAR) {
                player.sendMessage(ChatColor.AQUA + "Weather Stage: " + ChatColor.WHITE +
                        progression.name().toLowerCase().replace("_", " "));
            }

            if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
                player.sendMessage(ChatColor.WHITE + "❄ Hail is currently falling!");
            }
        }
        
        // Show detailed option
        player.sendMessage(ChatColor.GRAY + "Use '/climate forecast detailed' for hour-by-hour forecast");

        return true;
    }
    
    private boolean showDetailedForecast(Player player, WeatherForecast weatherForecast) {
        WeatherForecast.DetailedForecast forecast = weatherForecast.getForecast(player.getWorld());

        if (forecast == null) {
            player.sendMessage(ChatColor.RED + "No detailed forecast available for this world yet!");
            return true;
        }
        
        // Header
        String headerText = "=== 24-Hour Detailed Forecast";
        if (weatherForecast.isRealisticSeasonsEnabled() && forecast.getDate() != null) {
            Date date = forecast.getDate();
            String dateStr = date.getMonth() + "/" + date.getDay() + "/" + date.getYear();
            headerText += " - " + dateStr;
        } else {
            headerText += " - " + forecast.getForecastId();
        }
        headerText += " ===";
        
        player.sendMessage(ChatColor.GOLD + headerText);
        
        int currentHour = getCurrentHour(player, weatherForecast);
        
        // Show hour-by-hour forecast in groups
        player.sendMessage(ChatColor.YELLOW + "Hour-by-Hour Forecast:");
        
        StringBuilder line = new StringBuilder();
        for (int hour = 0; hour < 24; hour++) {
            WeatherForecast.WeatherType weather = forecast.getWeatherForHour(hour);
            boolean isTransition = forecast.isTransitionHour(hour);
            boolean isCurrent = (hour == currentHour);
            
            // Color coding
            String hourColor = isCurrent ? ChatColor.GREEN.toString() : 
                              isTransition ? ChatColor.YELLOW.toString() : 
                              ChatColor.WHITE.toString();
            
            String marker = isCurrent ? "►" : isTransition ? "•" : " ";
            
            line.append(hourColor).append(String.format("%s%02d:%s", marker, hour, 
                getWeatherSymbol(weather))).append(" ");
            
            // Break line every 6 hours for readability
            if ((hour + 1) % 6 == 0) {
                player.sendMessage(line.toString());
                line = new StringBuilder();
            }
        }
        
        if (line.length() > 0) {
            player.sendMessage(line.toString());
        }
        
        // Legend
        player.sendMessage(ChatColor.GRAY + "Legend: " + ChatColor.GREEN + "► Current" + 
            ChatColor.GRAY + " | " + ChatColor.YELLOW + "• Transition" + 
            ChatColor.GRAY + " | " + ChatColor.WHITE + "  Regular");
        
        player.sendMessage(ChatColor.GRAY + "Symbols: ☀ Clear | 🌧 Rain | ⛈ Storm | ❄ Snow | 🌪 Blizzard | 🌵 Sand");
        
        // Current status
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        player.sendMessage(ChatColor.AQUA + "Currently: " + ChatColor.WHITE + 
            currentWeather.getDisplayName() + " at " + String.format("%02d:00", currentHour));
        
        return true;
    }
    
    private String getWeatherSymbol(WeatherForecast.WeatherType weather) {
        switch (weather) {
            case CLEAR: return "☀";
            case LIGHT_RAIN: return "🌦";
            case HEAVY_RAIN: return "🌧";
            case THUNDERSTORM: return "⛈";
            case SNOW: return "❄";
            case BLIZZARD: return "🌪";
            case SANDSTORM: return "🌵";
            default: return "?";
        }
    }
    
    private int getCurrentHour(Player player, WeatherForecast weatherForecast) {
        if (weatherForecast.isRealisticSeasonsEnabled()) {
            return plugin.getWeatherForecast().getCurrentHour(player.getWorld());
        } else {
            long timeOfDay = player.getWorld().getTime() % 24000;
            return (int) ((timeOfDay + 6000) / 1000) % 24;
        }
    }
    
    private WeatherForecast.WeatherType getNextTransition(WeatherForecast.DetailedForecast forecast, int currentHour) {
        for (int hour = currentHour + 1; hour < 24; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return forecast.getWeatherForHour(hour);
            }
        }
        // Check next day (hour 0-5)
        for (int hour = 0; hour <= currentHour; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return forecast.getWeatherForHour(hour);
            }
        }
        return null;
    }
    
    private int getNextTransitionHour(WeatherForecast.DetailedForecast forecast, int currentHour) {
        for (int hour = currentHour + 1; hour < 24; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return hour;
            }
        }
        // Check next day
        for (int hour = 0; hour <= currentHour; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return hour;
            }
        }
        return -1;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("detailed");
        }
        return super.getTabCompletions(sender, args);
    }

    @Override
    public String getDescription() {
        return "Show the weather forecast (use 'detailed' for hour-by-hour)";
    }

    @Override
    public String getUsage() {
        return "/climate forecast [detailed]";
    }
}