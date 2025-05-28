package com.orbismc.orbisClimate;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Listener to prevent unwanted snow placement while keeping visual effects
 * Works alongside RealisticSeasons to prevent all snow/ice formation
 */
public class SnowPlacementListener implements Listener {

    private final OrbisClimate plugin;
    private boolean preventSnowPlacement;
    private boolean preventIceFormation;

    public SnowPlacementListener(OrbisClimate plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        preventSnowPlacement = plugin.getConfig().getBoolean("weather_control.prevent_snow_placement", true);
        preventIceFormation = plugin.getConfig().getBoolean("weather_control.prevent_ice_formation", false);
    }

    /**
     * HIGH PRIORITY: Run AFTER RealisticSeasons but BEFORE other plugins
     * This ensures we catch anything RealisticSeasons might miss
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockForm(BlockFormEvent event) {
        if (!preventSnowPlacement && !preventIceFormation) {
            return; // Feature disabled
        }

        // Skip if already cancelled by RealisticSeasons
        if (event.isCancelled()) {
            return;
        }

        Material newType = event.getNewState().getType();

        // Prevent all snow-related blocks during our managed weather OR always if configured
        if (preventSnowPlacement && isSnowMaterial(newType)) {
            // Check if we're managing snow weather OR if we want to prevent all snow
            boolean preventAll = plugin.getConfig().getBoolean("weather_control.prevent_all_snow", false);
            
            if (preventAll) {
                // Prevent ALL snow formation regardless of weather
                event.setCancelled(true);
                logPrevention(newType, event, "all weather conditions");
            } else {
                // Only prevent during our snow/blizzard weather
                WeatherForecast.WeatherType currentWeather = plugin.getWeatherForecast().getCurrentWeather(event.getBlock().getWorld());
                
                if (currentWeather == WeatherForecast.WeatherType.SNOW ||
                        currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
                    event.setCancelled(true);
                    logPrevention(newType, event, currentWeather.getDisplayName());
                }
            }
        }

        // Prevent ice formation during our weather OR always if configured
        if (preventIceFormation && isIceMaterial(newType)) {
            boolean preventAll = plugin.getConfig().getBoolean("weather_control.prevent_all_ice", false);
            
            if (preventAll) {
                // Prevent ALL ice formation regardless of weather
                event.setCancelled(true);
                logPrevention(newType, event, "all conditions");
            } else {
                // Only prevent during our cold weather
                WeatherForecast.WeatherType currentWeather = plugin.getWeatherForecast().getCurrentWeather(event.getBlock().getWorld());
                
                if (currentWeather == WeatherForecast.WeatherType.SNOW ||
                        currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
                    event.setCancelled(true);
                    logPrevention(newType, event, currentWeather.getDisplayName());
                }
            }
        }
    }

    /**
     * Check if material is snow-related
     */
    private boolean isSnowMaterial(Material material) {
        switch (material) {
            case SNOW:          // Snow layers (the main culprit!)
            case SNOW_BLOCK:    // Full snow blocks
            case POWDER_SNOW:   // 1.17+ powder snow
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if material is ice-related
     */
    private boolean isIceMaterial(Material material) {
        switch (material) {
            case ICE:
            case PACKED_ICE:
            case BLUE_ICE:
            case FROSTED_ICE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Log prevention with details
     */
    private void logPrevention(Material material, BlockFormEvent event, String reason) {
        if (plugin.getConfig().getBoolean("debug.log_snow_prevention", false)) {
            plugin.getLogger().info("Prevented " + material.name().toLowerCase() + " formation at " +
                    event.getBlock().getLocation() + " during " + reason);
        }
    }

    /**
     * LOWEST PRIORITY: Run after all other weather plugins to override their decisions
     */
    @EventHandler(priority = EventPriority.LOWEST) 
    public void onWeatherChangeOverride(WeatherChangeEvent event) {
        // Only intervene if we want to completely control weather
        if (!plugin.getConfig().getBoolean("weather_control.override_all_weather", false)) {
            return;
        }

        WeatherForecast.WeatherType ourWeather = plugin.getWeatherForecast().getCurrentWeather(event.getWorld());

        // If we're managing snow/blizzard weather, ensure vanilla doesn't interfere
        if (ourWeather == WeatherForecast.WeatherType.SNOW ||
                ourWeather == WeatherForecast.WeatherType.BLIZZARD) {

            // If vanilla is trying to start a storm during our snow weather, cancel it
            if (event.toWeatherState()) {
                event.setCancelled(true);

                if (plugin.getConfig().getBoolean("debug.log_weather_interference", false)) {
                    plugin.getLogger().info("Overrode vanilla weather interference during " +
                            ourWeather.getDisplayName() + " in " + event.getWorld().getName());
                }
            }
        }
    }

    /**
     * Reload configuration
     */
    public void reloadConfig() {
        loadConfig();
    }
}