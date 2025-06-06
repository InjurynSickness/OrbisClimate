package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.WeatherProgressionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class WeatherCommand extends BaseSubCommand {

    public WeatherCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.weather", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        if (args.length == 0) {
            showWeatherHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                return handleSetWeather(player, args);
            case "clear":
                return handleClearWeather(player);
            case "info":
                return handleWeatherInfo(player);
            case "progression":
                return handleProgressionInfo(player);
            default:
                player.sendMessage(ChatColor.RED + "Unknown weather command! Use '/climate weather' for help.");
                return true;
        }
    }

    private void showWeatherHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Weather Control Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/climate weather set <type> [duration] " + ChatColor.WHITE + "- Set weather");
        player.sendMessage(ChatColor.YELLOW + "/climate weather clear " + ChatColor.WHITE + "- Clear weather locks");
        player.sendMessage(ChatColor.YELLOW + "/climate weather info " + ChatColor.WHITE + "- Show weather info");
        player.sendMessage(ChatColor.YELLOW + "/climate weather progression " + ChatColor.WHITE + "- Show progression info");
        player.sendMessage(ChatColor.WHITE + "Weather types: clear, light_rain, heavy_rain, thunderstorm, snow, blizzard, sandstorm");
    }

    private boolean handleSetWeather(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /climate weather set <type> [duration_minutes]");
            return true;
        }

        String weatherTypeName = args[1].toLowerCase();
        WeatherForecast.WeatherType weatherType = parseWeatherType(weatherTypeName);

        if (weatherType == null) {
            player.sendMessage(ChatColor.RED + "Invalid weather type! Valid types: clear, light_rain, heavy_rain, thunderstorm, snow, blizzard, sandstorm");
            return true;
        }

        int duration = 10; // Default 10 minutes
        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
                if (duration < 1 || duration > 120) {
                    player.sendMessage(ChatColor.RED + "Duration must be between 1 and 120 minutes!");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid duration! Must be a number.");
                return true;
            }
        }

        plugin.getWeatherForecast().setWeather(player.getWorld(), weatherType, duration);
        player.sendMessage(ChatColor.GREEN + "Weather set to " + weatherType.getDisplayName() +
            " for " + duration + " minutes in " + player.getWorld().getName());

        return true;
    }

    private boolean handleClearWeather(Player player) {
        plugin.getWeatherForecast().clearWeatherLock(player.getWorld());
        player.sendMessage(ChatColor.GREEN + "Weather lock cleared for " + player.getWorld().getName() +
            ". Weather will now follow the natural forecast.");
        return true;
    }

    private boolean handleWeatherInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Detailed Weather Information ===");

        WeatherForecast weatherForecast = plugin.getWeatherForecast();

        // Current weather state
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        player.sendMessage(ChatColor.AQUA + "Current Weather: " + ChatColor.WHITE + currentWeather.getDisplayName());

        // Minecraft weather state
        player.sendMessage(ChatColor.AQUA + "MC Storm: " + ChatColor.WHITE + player.getWorld().hasStorm());
        player.sendMessage(ChatColor.AQUA + "MC Thunder: " + ChatColor.WHITE + player.getWorld().isThundering());
        player.sendMessage(ChatColor.AQUA + "MC Weather Duration: " + ChatColor.WHITE +
            (player.getWorld().getWeatherDuration() / 20) + " seconds");

        if (player.getWorld().isThundering()) {
            player.sendMessage(ChatColor.AQUA + "MC Thunder Duration: " + ChatColor.WHITE +
                (player.getWorld().getThunderDuration() / 20) + " seconds");
        }

        // Time information
        long time = player.getWorld().getTime();
        int hour = (int) (((time + 6000) % 24000) / 1000);
        player.sendMessage(ChatColor.AQUA + "Current Hour: " + ChatColor.WHITE + hour + ":00");
        
        // NEW: Show forecast transition status
        if (weatherForecast.isTransitionHour(player.getWorld())) {
            player.sendMessage(ChatColor.YELLOW + "⚡ Current hour is a forecast transition hour");
        }

        // Show active weather systems
        if (plugin.getBlizzardManager().isBlizzardActive(player.getWorld())) {
            player.sendMessage(ChatColor.BLUE + "❄ Blizzard system is active");
        }
        if (plugin.getSandstormManager().isSandstormActive(player.getWorld())) {
            player.sendMessage(ChatColor.YELLOW + "🌪 Sandstorm system is active");
        }
        if (plugin.getWindManager().hasActiveWind(player.getWorld())) {
            player.sendMessage(ChatColor.GRAY + "💨 Wind system is active");
        }

        // NEW: Weather progression (enhanced)
        if (plugin.getWeatherProgressionManager() != null) {
            WeatherProgressionManager.WeatherProgression progression =
                plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
            player.sendMessage(ChatColor.AQUA + "Weather Progression: " + ChatColor.WHITE +
                progression.name().toLowerCase().replace("_", " "));

            if (plugin.getWeatherProgressionManager().isInTransition(player.getWorld())) {
                player.sendMessage(ChatColor.YELLOW + "⚡ Progression transition active");
            }

            if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
                player.sendMessage(ChatColor.WHITE + "❄ Hail is currently active");
            }
            
            // Show upcoming transitions
            int hoursUntil = weatherForecast.getHoursUntilNextTransition(player.getWorld());
            if (hoursUntil != -1 && hoursUntil <= 3) {
                WeatherForecast.WeatherType nextWeather = weatherForecast.getNextTransitionWeather(player.getWorld());
                if (nextWeather != null) {
                    String timeDesc = hoursUntil == 0 ? "this hour" : "in " + hoursUntil + " hour(s)";
                    player.sendMessage(ChatColor.GRAY + "Next transition: " + nextWeather.getDisplayName() + " " + timeDesc);
                }
            }
        }

        return true;
    }

    private boolean handleProgressionInfo(Player player) {
        if (plugin.getWeatherProgressionManager() == null) {
            player.sendMessage(ChatColor.RED + "Weather progression system not available!");
            return true;
        }
        
        player.sendMessage(ChatColor.GOLD + "=== Weather Progression Information ===");
        
        WeatherProgressionManager.WeatherProgression progression = 
            plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
        player.sendMessage(ChatColor.AQUA + "Current Stage: " + ChatColor.WHITE + 
            progression.name().toLowerCase().replace("_", " "));
        
        boolean inTransition = plugin.getWeatherProgressionManager().isInTransition(player.getWorld());
        if (inTransition) {
            player.sendMessage(ChatColor.YELLOW + "Status: Weather is transitioning");
        }
        
        boolean hailActive = plugin.getWeatherProgressionManager().isHailActive(player.getWorld());
        if (hailActive) {
            player.sendMessage(ChatColor.WHITE + "❄ Hail is currently active");
        }
        
        // Show forecast integration status
        WeatherForecast.DetailedForecast forecast = plugin.getWeatherForecast().getForecast(player.getWorld());
        if (forecast != null) {
            int currentHour = plugin.getWeatherForecast().getCurrentHour(player.getWorld());
            boolean isTransitionHour = forecast.isTransitionHour(currentHour);
            
            player.sendMessage(ChatColor.AQUA + "Forecast Integration:");
            player.sendMessage(ChatColor.WHITE + "  Current hour is transition: " + isTransitionHour);
            
            // Show next forecast transition
            int hoursUntil = plugin.getWeatherForecast().getHoursUntilNextTransition(player.getWorld());
            if (hoursUntil != -1) {
                WeatherForecast.WeatherType nextWeather = plugin.getWeatherForecast().getNextTransitionWeather(player.getWorld());
                
                String timeDesc = hoursUntil == 0 ? "this hour" : 
                                 hoursUntil == 1 ? "next hour" : 
                                 "in " + hoursUntil + " hours";
                
                player.sendMessage(ChatColor.WHITE + "  Next forecast transition: " + 
                    nextWeather.getDisplayName() + " " + timeDesc);
            }
        }
        
        // Show progression configuration status
        player.sendMessage(ChatColor.AQUA + "Configuration:");
        player.sendMessage(ChatColor.WHITE + "  Enhanced Transitions: " + 
            plugin.getConfig().getBoolean("weather_progression.enhanced_transitions.enabled", true));
        player.sendMessage(ChatColor.WHITE + "  Pre-storm Effects: " + 
            plugin.getConfig().getBoolean("weather_progression.pre_storm_effects.enabled", true));
        player.sendMessage(ChatColor.WHITE + "  Hail Effects: " + 
            plugin.getConfig().getBoolean("weather_progression.active_weather_effects.hail.enabled", true));
        player.sendMessage(ChatColor.WHITE + "  Forecast Integration: " + 
            plugin.getConfig().getBoolean("weather_progression.forecast_integration.use_forecast_transitions", true));
        
        return true;
    }

    private WeatherForecast.WeatherType parseWeatherType(String typeName) {
        switch (typeName.toLowerCase()) {
            case "clear":
                return WeatherForecast.WeatherType.CLEAR;
            case "light_rain":
            case "lightrain":
            case "light":
                return WeatherForecast.WeatherType.LIGHT_RAIN;
            case "heavy_rain":
            case "heavyrain":
            case "heavy":
            case "rain":
                return WeatherForecast.WeatherType.HEAVY_RAIN;
            case "thunderstorm":
            case "thunder":
            case "storm":
                return WeatherForecast.WeatherType.THUNDERSTORM;
            case "snow":
                return WeatherForecast.WeatherType.SNOW;
            case "blizzard":
                return WeatherForecast.WeatherType.BLIZZARD;
            case "sandstorm":
            case "sand":
                return WeatherForecast.WeatherType.SANDSTORM;
            default:
                return null;
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "clear", "info", "progression");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("clear", "light_rain", "heavy_rain", "thunderstorm", "snow", "blizzard", "sandstorm");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("5", "10", "15", "30", "60");
        }
        return super.getTabCompletions(sender, args);
    }

    @Override
    public String getDescription() {
        return "Control and monitor weather systems";
    }

    @Override
    public String getUsage() {
        return "/climate weather <set|clear|info|progression>";
    }
}