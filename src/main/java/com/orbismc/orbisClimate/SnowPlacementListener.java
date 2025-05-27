package com.orbismc.orbisClimate;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Listener to prevent unwanted snow placement while keeping visual effects
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
     * Prevent snow blocks from forming naturally during our snow/blizzard weather
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockForm(BlockFormEvent event) {
        if (!preventSnowPlacement && !preventIceFormation) {
            return; // Feature disabled
        }

        Material newType = event.getNewState().getType();

        // Prevent snow layer placement during our managed weather
        if (preventSnowPlacement && newType == Material.SNOW) {
            WeatherForecast.WeatherType currentWeather = plugin.getWeatherForecast().getCurrentWeather(event.getBlock().getWorld());

            if (currentWeather == WeatherForecast.WeatherType.SNOW ||
                    currentWeather == WeatherForecast.WeatherType.BLIZZARD) {

                event.setCancelled(true);

                if (plugin.getConfig().getBoolean("debug.log_snow_prevention", false)) {
                    plugin.getLogger().info("Prevented snow placement at " +
                            event.getBlock().getLocation() + " during " + currentWeather.getDisplayName());
                }
            }
        }

        // Optionally prevent ice formation as well
        if (preventIceFormation && newType == Material.ICE) {
            WeatherForecast.WeatherType currentWeather = plugin.getWeatherForecast().getCurrentWeather(event.getBlock().getWorld());

            if (currentWeather == WeatherForecast.WeatherType.SNOW ||
                    currentWeather == WeatherForecast.WeatherType.BLIZZARD) {

                event.setCancelled(true);

                if (plugin.getConfig().getBoolean("debug.log_snow_prevention", false)) {
                    plugin.getLogger().info("Prevented ice formation at " +
                            event.getBlock().getLocation() + " during " + currentWeather.getDisplayName());
                }
            }
        }
    }

    /**
     * Additional protection: prevent vanilla weather from interfering
     * This catches any vanilla weather changes that might slip through
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWeatherChange(WeatherChangeEvent event) {
        // Only intervene if our weather system is active
        WeatherForecast.WeatherType ourWeather = plugin.getWeatherForecast().getCurrentWeather(event.getWorld());

        // If we're managing snow/blizzard weather, ensure vanilla doesn't interfere
        if (ourWeather == WeatherForecast.WeatherType.SNOW ||
                ourWeather == WeatherForecast.WeatherType.BLIZZARD) {

            // If vanilla is trying to start a storm during our snow weather, cancel it
            if (event.toWeatherState()) {
                event.setCancelled(true);

                if (plugin.getConfig().getBoolean("debug.log_weather_interference", false)) {
                    plugin.getLogger().info("Prevented vanilla weather interference during " +
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