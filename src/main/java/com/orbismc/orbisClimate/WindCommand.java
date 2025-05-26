package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
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

    // Helper methods to get managers from plugin
    private ClimateZoneManager getClimateZoneManager() {
        return plugin.getClimateZoneManager();
    }

    private TemperatureManager getTemperatureManager() {
        return plugin.getTemperatureManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== OrbisClimate Commands ===");
            sender.sendMessage(ChatColor.YELLOW + "/wind reload " + ChatColor.WHITE + "- Reload configuration");
            sender.sendMessage(ChatColor.YELLOW + "/wind info " + ChatColor.WHITE + "- Show climate information");
            sender.sendMessage(ChatColor.YELLOW + "/wind forecast " + ChatColor.WHITE + "- Show weather forecast");
            sender.sendMessage(ChatColor.YELLOW + "/wind temperature " + ChatColor.WHITE + "- Show temperature info");
            sender.sendMessage(ChatColor.YELLOW + "/wind zone " + ChatColor.WHITE + "- Show climate zone info");
            sender.sendMessage(ChatColor.YELLOW + "/wind regenerate " + ChatColor.WHITE + "- Regenerate today's forecast");
            sender.sendMessage(ChatColor.YELLOW + "/wind status " + ChatColor.WHITE + "- Show integration status");
            sender.sendMessage(ChatColor.YELLOW + "/wind toggle [on|off] " + ChatColor.WHITE + "- Toggle particles on/off");
            sender.sendMessage(ChatColor.YELLOW + "/wind weather " + ChatColor.WHITE + "- Weather control commands (Admin)");
            sender.sendMessage(ChatColor.YELLOW + "/wind debug " + ChatColor.WHITE + "- Show debug information (Admin)");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("orbisclimate.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }

                plugin.reloadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "OrbisClimate configuration reloaded!");
                break;

            case "info":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player player = (Player) sender;
                showClimateInfo(player);
                break;

            case "forecast":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player forecastPlayer = (Player) sender;
                showForecast(forecastPlayer);
                break;

            case "temperature":
            case "temp":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player tempPlayer = (Player) sender;
                showTemperatureInfo(tempPlayer);
                break;

            case "zone":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player zonePlayer = (Player) sender;
                showZoneInfo(zonePlayer);
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

            case "toggle":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player togglePlayer = (Player) sender;
                
                if (args.length < 2) {
                    boolean current = plugin.isPlayerParticlesEnabled(togglePlayer);
                    plugin.setPlayerParticlesEnabled(togglePlayer, !current);
                    togglePlayer.sendMessage(ChatColor.GREEN + "Weather particles " + 
                        (!current ? "enabled" : "disabled") + "!");
                } else {
                    boolean setting = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
                    plugin.setPlayerParticlesEnabled(togglePlayer, setting);
                    togglePlayer.sendMessage(ChatColor.GREEN + "Weather particles " + 
                        (setting ? "enabled" : "disabled") + "!");
                }
                break;

            case "weather":
                if (!sender.hasPermission("orbisclimate.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player weatherPlayer = (Player) sender;
                handleWeatherCommand(weatherPlayer, args);
                break;

            case "debug":
                if (!sender.hasPermission("orbisclimate.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player debugPlayer = (Player) sender;
                showDebugInfo(debugPlayer);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command! Use /wind for help.");
                break;
        }

        return true;
    }

    private void handleWeatherCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.GOLD + "=== Weather Control Commands ===");
            player.sendMessage(ChatColor.YELLOW + "/wind weather set <type> [duration] " + ChatColor.WHITE + "- Set weather");
            player.sendMessage(ChatColor.YELLOW + "/wind weather clear " + ChatColor.WHITE + "- Clear weather locks");
            player.sendMessage(ChatColor.YELLOW + "/wind weather info " + ChatColor.WHITE + "- Show weather info");
            player.sendMessage(ChatColor.WHITE + "Weather types: clear, light_rain, heavy_rain, thunderstorm, snow, blizzard, sandstorm");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "set":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /wind weather set <type> [duration_minutes]");
                    return;
                }
                
                String weatherTypeName = args[2].toLowerCase();
                WeatherForecast.WeatherType weatherType = parseWeatherType(weatherTypeName);
                
                if (weatherType == null) {
                    player.sendMessage(ChatColor.RED + "Invalid weather type! Valid types: clear, light_rain, heavy_rain, thunderstorm, snow, blizzard, sandstorm");
                    return;
                }
                
                int duration = 10; // Default 10 minutes
                if (args.length >= 4) {
                    try {
                        duration = Integer.parseInt(args[3]);
                        if (duration < 1 || duration > 120) {
                            player.sendMessage(ChatColor.RED + "Duration must be between 1 and 120 minutes!");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid duration! Must be a number.");
                        return;
                    }
                }
                
                weatherForecast.setWeather(player.getWorld(), weatherType, duration);
                player.sendMessage(ChatColor.GREEN + "Weather set to " + weatherType.getDisplayName() + 
                    " for " + duration + " minutes in " + player.getWorld().getName());
                break;
                
            case "clear":
                weatherForecast.clearWeatherLock(player.getWorld());
                player.sendMessage(ChatColor.GREEN + "Weather lock cleared for " + player.getWorld().getName() + 
                    ". Weather will now follow the natural forecast.");
                break;
                
            case "info":
                showDetailedWeatherInfo(player);
                break;
                
            default:
                player.sendMessage(ChatColor.RED + "Unknown weather command! Use '/wind weather' for help.");
                break;
        }
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

    private void showDetailedWeatherInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Detailed Weather Information ===");
        
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
        
        // Show active weather systems
        if (plugin.getBlizzardManager().isBlizzardActive(player.getWorld())) {
            player.sendMessage(ChatColor.BLUE + "❄ Blizzard system is active");
        }
        if (plugin.getSandstormManager().isSandstormActive(player.getWorld())) {
            player.sendMessage(ChatColor.YELLOW + "🌪 Sandstorm system is active");
        }
        if (windManager.hasActiveWind(player.getWorld())) {
            player.sendMessage(ChatColor.GRAY + "💨 Wind system is active");
        }
        
        // Weather progression
        if (plugin.getWeatherProgressionManager() != null) {
            WeatherProgressionManager.WeatherProgression progression = 
                plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
            player.sendMessage(ChatColor.AQUA + "Weather Progression: " + ChatColor.WHITE + 
                progression.name().toLowerCase().replace("_", " "));
                
            if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
                player.sendMessage(ChatColor.WHITE + "❄ Hail is currently active");
            }
        }
    }

    private void showClimateInfo(Player player) {
        ClimateZoneManager climateZoneManager = getClimateZoneManager();
        TemperatureManager temperatureManager = getTemperatureManager();
        
        if (climateZoneManager == null || temperatureManager == null) {
            player.sendMessage(ChatColor.RED + "Climate system not fully initialized!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Climate Information ===");

        // Current weather info
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        WeatherForecast.WeatherType zoneWeather = climateZoneManager.getPlayerZoneWeather(player);
        
        player.sendMessage(ChatColor.AQUA + "World Weather: " + ChatColor.WHITE + currentWeather.getDisplayName());
        
        if (zoneWeather != currentWeather) {
            player.sendMessage(ChatColor.AQUA + "Your Zone Weather: " + ChatColor.WHITE + zoneWeather.getDisplayName());
        }

        // Climate zone info
        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);
        player.sendMessage(ChatColor.AQUA + "Climate Zone: " + ChatColor.WHITE + zone.getDisplayName());

        // Temperature info
        double temperature = temperatureManager.getPlayerTemperature(player);
        String tempLevel = temperatureManager.getPlayerTemperatureLevel(player);
        player.sendMessage(ChatColor.AQUA + "Temperature: " + ChatColor.WHITE + 
            String.format("%.1f°C", temperature) + " (" + tempLevel + ")");

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

        // Special conditions
        if (climateZoneManager.isPlayerInDrought(player)) {
            player.sendMessage(ChatColor.RED + "⚠ Drought conditions active in your area!");
        }

        // Wind chances based on current weather
        String windChance = getWindChanceDescription(zoneWeather);
        player.sendMessage(ChatColor.GRAY + "(" + windChance + ")");

        // Indoor/outdoor status
        boolean isIndoors = windManager.isPlayerIndoors(player);
        player.sendMessage(ChatColor.AQUA + "Location: " + ChatColor.WHITE +
                (isIndoors ? "Indoors (protected from weather)" : "Outdoors"));

        // Show particle status
        boolean particlesEnabled = plugin.isPlayerParticlesEnabled(player);
        player.sendMessage(ChatColor.AQUA + "Particles: " + ChatColor.WHITE +
                (particlesEnabled ? "Enabled" : "Disabled") + " (use /wind toggle to change)");

        // Show active effects
        if (!isIndoors) {
            boolean hasActiveWind = windManager.hasActiveWind(player.getWorld());
            player.sendMessage(ChatColor.AQUA + "Wind Status: " + ChatColor.WHITE +
                    (hasActiveWind ? "Active" : "Calm"));

            if (plugin.getWeatherProgressionManager() != null) {
                WeatherProgressionManager.WeatherProgression progression = 
                    plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
                if (progression != WeatherProgressionManager.WeatherProgression.CLEAR) {
                    player.sendMessage(ChatColor.AQUA + "Weather Stage: " + ChatColor.WHITE + 
                        progression.name().toLowerCase().replace("_", " "));
                }
            }
        }
    }

    private void showTemperatureInfo(Player player) {
        TemperatureManager temperatureManager = getTemperatureManager();
        ClimateZoneManager climateZoneManager = getClimateZoneManager();
        
        if (temperatureManager == null || climateZoneManager == null) {
            player.sendMessage(ChatColor.RED + "Temperature system not available!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Temperature Information ===");

        double currentTemp = temperatureManager.getPlayerTemperature(player);
        String tempLevel = temperatureManager.getPlayerTemperatureLevel(player);
        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);

        player.sendMessage(ChatColor.AQUA + "Current Temperature: " + ChatColor.WHITE +
            String.format("%.1f°C (%.1f°F)", currentTemp, (currentTemp * 9/5) + 32));
        
        player.sendMessage(ChatColor.AQUA + "Comfort Level: " + ChatColor.WHITE + tempLevel);

        // Zone temperature range
        player.sendMessage(ChatColor.AQUA + "Zone Range: " + ChatColor.WHITE +
            zone.getMinTemp() + "°C to " + zone.getMaxTemp() + "°C");

        // Temperature effects
        if (temperatureManager.isPlayerTooHot(player)) {
            player.sendMessage(ChatColor.RED + "⚠ You are experiencing heat effects!");
        } else if (temperatureManager.isPlayerTooCold(player)) {
            player.sendMessage(ChatColor.BLUE + "⚠ You are experiencing cold effects!");
        } else {
            player.sendMessage(ChatColor.GREEN + "✓ Temperature is comfortable");
        }

        // Drought bonus
        if (climateZoneManager.isPlayerInDrought(player)) {
            double droughtBonus = plugin.getConfig().getDouble("drought.effects.temperature_bonus", 15.0);
            player.sendMessage(ChatColor.YELLOW + "Drought Heat Bonus: +" + droughtBonus + "°C");
        }
    }

    private void showZoneInfo(Player player) {
        ClimateZoneManager climateZoneManager = getClimateZoneManager();
        
        if (climateZoneManager == null) {
            player.sendMessage(ChatColor.RED + "Climate zone system not available!");
            return;
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
    }

    private void showDebugInfo(Player player) {
        ClimateZoneManager climateZoneManager = getClimateZoneManager();
        TemperatureManager temperatureManager = getTemperatureManager();
        
        player.sendMessage(ChatColor.GOLD + "=== Debug Information ===");

        // Manager status
        player.sendMessage(ChatColor.AQUA + "Managers Loaded:");
        player.sendMessage(ChatColor.WHITE + "  WindManager: " + (windManager != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  ClimateZoneManager: " + (climateZoneManager != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  TemperatureManager: " + (temperatureManager != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  WeatherProgressionManager: " + (plugin.getWeatherProgressionManager() != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  DynamicSoundManager: " + (plugin.getDynamicSoundManager() != null ? "✓" : "✗"));

        if (climateZoneManager != null && temperatureManager != null) {
            // Current values
            player.sendMessage(ChatColor.AQUA + "Current Values:");
            player.sendMessage(ChatColor.WHITE + "  Zone: " + climateZoneManager.getPlayerClimateZone(player));
            player.sendMessage(ChatColor.WHITE + "  Temperature: " + String.format("%.2f°C", temperatureManager.getPlayerTemperature(player)));
            player.sendMessage(ChatColor.WHITE + "  Zone Weather: " + climateZoneManager.getPlayerZoneWeather(player));
            player.sendMessage(ChatColor.WHITE + "  World Weather: " + weatherForecast.getCurrentWeather(player.getWorld()));
            player.sendMessage(ChatColor.WHITE + "  Indoors: " + windManager.isPlayerIndoors(player));
            player.sendMessage(ChatColor.WHITE + "  Drought: " + climateZoneManager.isPlayerInDrought(player));
            player.sendMessage(ChatColor.WHITE + "  Particles Enabled: " + plugin.isPlayerParticlesEnabled(player));
        }

        // Performance info
        player.sendMessage(ChatColor.AQUA + "Performance:");
        player.sendMessage(ChatColor.WHITE + "  Online Players: " + player.getServer().getOnlinePlayers().size());
        player.sendMessage(ChatColor.WHITE + "  World: " + player.getWorld().getName());
        player.sendMessage(ChatColor.WHITE + "  TPS: " + String.format("%.2f", getAverageTPS()));
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

        // Feature status
        sender.sendMessage(ChatColor.AQUA + "Feature Status:");
        sender.sendMessage(ChatColor.WHITE + "  Climate Zones: " + getFeatureStatus("climate_zones"));
        sender.sendMessage(ChatColor.WHITE + "  Temperature System: " + getFeatureStatus("temperature.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Weather Progression: " + getFeatureStatus("weather_progression.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Aurora Effects: " + getFeatureStatus("aurora.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Heat Mirages: " + getFeatureStatus("heat_mirages.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Drought System: " + getFeatureStatus("drought.effects.enabled"));
        sender.sendMessage(ChatColor.WHITE + "  Dynamic Sound System: " + (plugin.getDynamicSoundManager() != null ? "ENABLED" : "DISABLED"));

        // Show world-specific info if player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            showWorldSpecificStatus(player);
        }
    }

    private void showWorldSpecificStatus(Player player) {
        ClimateZoneManager climateZoneManager = getClimateZoneManager();
        
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

    private String getWindChanceDescription(WeatherForecast.WeatherType weather) {
        switch (weather) {
            case THUNDERSTORM:
                return "100% wind chance";
            case HEAVY_RAIN:
            case LIGHT_RAIN:
            case BLIZZARD:
                return "25% wind chance";
            case SNOW:
                return "15% wind chance";
            case SANDSTORM:
                return "High wind chance";
            default:
                return "10% wind chance";
        }
    }

    private String getFeatureStatus(String configPath) {
        return plugin.getConfig().getBoolean(configPath, true) ? "ENABLED" : "DISABLED";
    }

    private double getAverageTPS() {
        // Simple TPS calculation - this is a rough estimate
        try {
            Object server = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
            double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
            return tps[0];
        } catch (Exception e) {
            return 20.0; // Default to 20 if can't get real TPS
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "info", "forecast", "temperature", "zone", "regenerate", "status", "toggle", "weather", "debug");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("toggle")) {
                return Arrays.asList("on", "off");
            } else if (args[0].equalsIgnoreCase("weather")) {
                return Arrays.asList("set", "clear", "info");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("weather") && args[1].equalsIgnoreCase("set")) {
            return Arrays.asList("clear", "light_rain", "heavy_rain", "thunderstorm", "snow", "blizzard", "sandstorm");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("weather") && args[1].equalsIgnoreCase("set")) {
            return Arrays.asList("5", "10", "15", "30", "60");
        }
        return null;
    }
}