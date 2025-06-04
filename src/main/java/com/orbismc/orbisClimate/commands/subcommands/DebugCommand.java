package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DebugCommand extends BaseSubCommand {

    public DebugCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.debug", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();
        TemperatureManager temperatureManager = plugin.getTemperatureManager();
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();

        player.sendMessage(ChatColor.GOLD + "=== Debug Information ===");

        // Manager status
        player.sendMessage(ChatColor.AQUA + "Managers Loaded:");
        player.sendMessage(ChatColor.WHITE + "  WindManager: " + (plugin.getWindManager() != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  ClimateZoneManager: " + (climateZoneManager != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  TemperatureManager: " + (temperatureManager != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  WeatherProgressionManager: " + (plugin.getWeatherProgressionManager() != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  DynamicSoundManager: " + (plugin.getDynamicSoundManager() != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  PerformanceMonitor: " + (monitor != null ? "✓" : "✗"));

        if (climateZoneManager != null && temperatureManager != null) {
            // Current values
            player.sendMessage(ChatColor.AQUA + "Current Values:");
            player.sendMessage(ChatColor.WHITE + "  Zone: " + climateZoneManager.getPlayerClimateZone(player));
            player.sendMessage(ChatColor.WHITE + "  Temperature: " + String.format("%.2f°C", temperatureManager.getPlayerTemperature(player)));
            player.sendMessage(ChatColor.WHITE + "  Zone Weather: " + climateZoneManager.getPlayerZoneWeather(player));
            player.sendMessage(ChatColor.WHITE + "  World Weather: " + plugin.getWeatherForecast().getCurrentWeather(player.getWorld()));
            player.sendMessage(ChatColor.WHITE + "  Indoors: " + plugin.getWindManager().isPlayerIndoors(player));
            player.sendMessage(ChatColor.WHITE + "  Drought: " + climateZoneManager.isPlayerInDrought(player));
            player.sendMessage(ChatColor.WHITE + "  Particles Enabled: " + plugin.isPlayerParticlesEnabled(player));
        }

        // NEW: Weather progression debug info
        if (plugin.getWeatherProgressionManager() != null) {
            player.sendMessage(ChatColor.AQUA + "Weather Progression:");
            
            WeatherProgressionManager.WeatherProgression progression = 
                plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
            player.sendMessage(ChatColor.WHITE + "  Current Stage: " + progression.name());
            
            boolean inTransition = plugin.getWeatherProgressionManager().isInTransition(player.getWorld());
            player.sendMessage(ChatColor.WHITE + "  In Transition: " + inTransition);
            
            boolean hailActive = plugin.getWeatherProgressionManager().isHailActive(player.getWorld());
            player.sendMessage(ChatColor.WHITE + "  Hail Active: " + hailActive);
            
            // Show forecast integration info
            WeatherForecast.DetailedForecast forecast = plugin.getWeatherForecast().getForecast(player.getWorld());
            if (forecast != null) {
                int currentHour = plugin.getWeatherForecast().getCurrentHour(player.getWorld());
                boolean isTransitionHour = forecast.isTransitionHour(currentHour);
                player.sendMessage(ChatColor.WHITE + "  Forecast Transition Hour: " + isTransitionHour);
                
                int hoursUntil = plugin.getWeatherForecast().getHoursUntilNextTransition(player.getWorld());
                if (hoursUntil != -1) {
                    WeatherForecast.WeatherType nextWeather = plugin.getWeatherForecast().getNextTransitionWeather(player.getWorld());
                    player.sendMessage(ChatColor.WHITE + "  Next Transition: " + 
                        (nextWeather != null ? nextWeather.getDisplayName() : "Unknown") + " in " + hoursUntil + "h");
                }
            }
            
            // Show progression configuration
            player.sendMessage(ChatColor.WHITE + "  Enhanced Transitions: " + 
                plugin.getConfig().getBoolean("weather_progression.enhanced_transitions.enabled", true));
            player.sendMessage(ChatColor.WHITE + "  Forecast Integration: " + 
                plugin.getConfig().getBoolean("weather_progression.forecast_integration.use_forecast_transitions", true));
        }

        // Performance info
        if (monitor != null) {
            player.sendMessage(ChatColor.AQUA + "Performance:");
            player.sendMessage(ChatColor.WHITE + "  TPS: " + String.format("%.2f", monitor.getCurrentTPS()));
            player.sendMessage(ChatColor.WHITE + "  Performance Mode: " + (monitor.isPerformanceMode() ? "ACTIVE" : "INACTIVE"));
            player.sendMessage(ChatColor.WHITE + "  Effect Multiplier: " + String.format("%.2fx", monitor.getPerformanceMultiplier()));

            if (monitor.shouldSkipEffects(player)) {
                player.sendMessage(ChatColor.RED + "  ⚠ Effects are being skipped for performance");
            }
        }

        // System info
        player.sendMessage(ChatColor.AQUA + "System:");
        player.sendMessage(ChatColor.WHITE + "  Online Players: " + player.getServer().getOnlinePlayers().size());
        player.sendMessage(ChatColor.WHITE + "  World: " + player.getWorld().getName());

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        double memoryPercent = (double) usedMemory / maxMemory * 100;

        player.sendMessage(ChatColor.WHITE + "  Memory: " + usedMemory + "MB/" + maxMemory + "MB (" +
                String.format("%.1f%%", memoryPercent) + ")");

        // NEW: Show debug logging status
        if (plugin.getConfig().getBoolean("debug.weather_progression.include_in_debug_commands", true)) {
            player.sendMessage(ChatColor.AQUA + "Debug Logging:");
            player.sendMessage(ChatColor.WHITE + "  Weather Transitions: " + 
                plugin.getConfig().getBoolean("debug.log_weather_transitions", false));
            player.sendMessage(ChatColor.WHITE + "  Progression Changes: " + 
                plugin.getConfig().getBoolean("debug.weather_progression.log_progression_changes", false));
            player.sendMessage(ChatColor.WHITE + "  Forecast Triggers: " + 
                plugin.getConfig().getBoolean("debug.weather_progression.log_forecast_triggers", false));
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Show detailed debug information";
    }

    @Override
    public String getUsage() {
        return "/climate debug";
    }
}