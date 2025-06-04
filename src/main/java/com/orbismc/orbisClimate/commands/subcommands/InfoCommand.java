package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.*;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InfoCommand extends BaseSubCommand {

    public InfoCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.info", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();
        TemperatureManager temperatureManager = plugin.getTemperatureManager();
        WeatherForecast weatherForecast = plugin.getWeatherForecast();
        WindManager windManager = plugin.getWindManager();

        if (climateZoneManager == null || temperatureManager == null) {
            player.sendMessage(ChatColor.RED + "Climate system not fully initialized!");
            return true;
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
                (particlesEnabled ? "Enabled" : "Disabled") + " (use /climate toggle to change)");

        // Show active effects
        if (!isIndoors) {
            boolean hasActiveWind = windManager.hasActiveWind(player.getWorld());
            player.sendMessage(ChatColor.AQUA + "Wind Status: " + ChatColor.WHITE +
                    (hasActiveWind ? "Active" : "Calm"));

            // NEW: Show weather progression information
            if (plugin.getWeatherProgressionManager() != null) {
                WeatherProgressionManager.WeatherProgression progression =
                        plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
                if (progression != WeatherProgressionManager.WeatherProgression.CLEAR) {
                    player.sendMessage(ChatColor.AQUA + "Weather Stage: " + ChatColor.WHITE +
                            progression.name().toLowerCase().replace("_", " "));
                }
                
                // Show if in transition
                if (plugin.getWeatherProgressionManager().isInTransition(player.getWorld())) {
                    player.sendMessage(ChatColor.YELLOW + "⚡ Weather is transitioning...");
                }
                
                // Show special effects
                if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
                    player.sendMessage(ChatColor.WHITE + "❄ Hail is currently falling!");
                }
                
                // Show upcoming weather transitions
                if (weatherForecast.getHoursUntilNextTransition(player.getWorld()) != -1) {
                    int hoursUntil = weatherForecast.getHoursUntilNextTransition(player.getWorld());
                    WeatherForecast.WeatherType nextWeather = weatherForecast.getNextTransitionWeather(player.getWorld());
                    if (nextWeather != null && hoursUntil <= 3) { // Only show if within 3 hours
                        String timeDesc = hoursUntil == 0 ? "soon" : "in " + hoursUntil + " hour(s)";
                        player.sendMessage(ChatColor.GRAY + "Next: " + nextWeather.getDisplayName() + " " + timeDesc);
                    }
                }
            }
        }

        // Add performance context
        showPerformanceContext(player);

        return true;
    }

    private void showPerformanceContext(Player player) {
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) return;

        if (monitor.isPerformanceMode()) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Server is in performance mode - some effects may be reduced");
        }

        if (monitor.shouldSkipEffects(player)) {
            player.sendMessage(ChatColor.RED + "⚠ Effects are currently disabled for you due to performance");
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

    @Override
    public String getDescription() {
        return "Show detailed climate information for your location";
    }

    @Override
    public String getUsage() {
        return "/climate info";
    }
}