package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OrbisClimate extends JavaPlugin implements Listener {

    private WindManager windManager;
    private WeatherForecast weatherForecast;
    private BlizzardManager blizzardManager;
    private SandstormManager sandstormManager;
    private ClimateZoneManager climateZoneManager;
    private TemperatureManager temperatureManager;
    private WeatherProgressionManager weatherProgressionManager;
    private DynamicSoundManager dynamicSoundManager;
    private PerformanceMonitor performanceMonitor;
    private SnowPlacementListener snowPlacementListener;
    private Random random;

    // Player particle preferences
    private final Map<Player, Boolean> playerParticleSettings = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Starting OrbisClimate plugin initialization...");

        try {
            // Save default config
            saveDefaultConfig();
            getLogger().info("✓ Config saved successfully");

            // NEW: Validate weather progression configuration
            validateWeatherProgressionConfig();

            // Initialize random
            random = new Random();
            getLogger().info("✓ Random initialized");

            // DISABLE VANILLA WEATHER SYSTEM FIRST
            disableVanillaWeather();

            // Initialize performance monitor early
            getLogger().info("Initializing performance monitor...");
            performanceMonitor = new PerformanceMonitor(this);
            getLogger().info("✓ Performance monitor initialized");

            // Initialize weather forecast system
            getLogger().info("Initializing weather forecast system...");
            weatherForecast = new WeatherForecast(this);
            getLogger().info("✓ Weather forecast system initialized");

            // Initialize wind manager with performance monitor
            getLogger().info("Initializing wind manager...");
            windManager = new WindManager(this, random, weatherForecast);
            getLogger().info("✓ Wind manager initialized");

            // Initialize climate zone manager
            getLogger().info("Initializing climate zone manager...");
            climateZoneManager = new ClimateZoneManager(this, weatherForecast, windManager);
            getLogger().info("✓ Climate zone manager initialized");

            // Initialize temperature manager
            getLogger().info("Initializing temperature manager...");
            temperatureManager = new TemperatureManager(this, weatherForecast, climateZoneManager, windManager);
            getLogger().info("✓ Temperature manager initialized");

            // IMPORTANT: Initialize weather progression manager AFTER weather forecast
            getLogger().info("Initializing weather progression manager...");
            weatherProgressionManager = new WeatherProgressionManager(this, weatherForecast, climateZoneManager);
            getLogger().info("✓ Weather progression manager initialized");

            // Initialize blizzard manager
            getLogger().info("Initializing blizzard manager...");
            blizzardManager = new BlizzardManager(this, weatherForecast, windManager);
            getLogger().info("✓ Blizzard manager initialized");

            // Initialize sandstorm manager
            getLogger().info("Initializing sandstorm manager...");
            sandstormManager = new SandstormManager(this, weatherForecast, windManager);
            getLogger().info("✓ Sandstorm manager initialized");

            // Initialize dynamic sound manager
            getLogger().info("Initializing dynamic sound manager...");
            dynamicSoundManager = new DynamicSoundManager(this);
            getLogger().info("✓ Dynamic sound manager initialized");

            // Initialize snow placement prevention listener
            getLogger().info("Initializing snow placement prevention...");
            snowPlacementListener = new SnowPlacementListener(this);
            getLogger().info("✓ Snow placement prevention initialized");

            // Register event listeners
            getServer().getPluginManager().registerEvents(this, this);
            getServer().getPluginManager().registerEvents(snowPlacementListener, this);
            getLogger().info("✓ Event listeners registered");

            // Register commands
            getLogger().info("Registering commands...");
            ClimateCommand climateCommand = new ClimateCommand(this);

            // Register main command with aliases
            if (getCommand("climate") != null) {
                getCommand("climate").setExecutor(climateCommand);
                getCommand("climate").setTabCompleter(climateCommand);
                getLogger().info("✓ Climate command registered successfully");
            } else {
                getLogger().severe("✗ Failed to register climate command - command not found in plugin.yml!");
            }

            // Also register aliases if they exist
            if (getCommand("wind") != null) {
                getCommand("wind").setExecutor(climateCommand);
                getCommand("wind").setTabCompleter(climateCommand);
                getLogger().info("✓ Wind command alias registered successfully");
            } else {
                getLogger().info("Wind command alias not found in plugin.yml - skipping");
            }

            if (getCommand("weather") != null) {
                getCommand("weather").setExecutor(climateCommand);
                getCommand("weather").setTabCompleter(climateCommand);
                getLogger().info("✓ Weather command alias registered successfully");
            } else {
                getLogger().info("Weather command alias not found in plugin.yml - skipping");
            }

            // Start main weather system task with performance optimization
            getLogger().info("Starting weather system tasks...");
            startWeatherSystemTasks();
            getLogger().info("✓ Weather system tasks started");

            getLogger().info("OrbisClimate has been enabled successfully!");

            // Print integration status
            if (weatherForecast.isRealisticSeasonsEnabled()) {
                getLogger().info("✓ RealisticSeasons integration: ENABLED");
            } else {
                getLogger().warning("⚠ RealisticSeasons integration: DISABLED (using vanilla time)");
            }

            // Print feature status and performance info
            printFeatureStatus();
            printPerformanceInfo();

        } catch (Exception e) {
            getLogger().severe("✗ Failed to enable OrbisClimate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * NEW: Validate weather progression configuration
     */
    private void validateWeatherProgressionConfig() {
        // Ensure progression is configured correctly with forecast system
        boolean progressionEnabled = getConfig().getBoolean("weather_progression.enabled", true);
        boolean enhancedTransitions = getConfig().getBoolean("weather_progression.enhanced_transitions.enabled", true);
        boolean forecastIntegration = getConfig().getBoolean("weather_progression.forecast_integration.use_forecast_transitions", true);
        
        if (progressionEnabled && !forecastIntegration) {
            getLogger().warning("Weather progression is enabled but forecast integration is disabled!");
            getLogger().warning("This may cause conflicts. Consider enabling forecast integration for best results.");
        }
        
        if (enhancedTransitions && !progressionEnabled) {
            getLogger().warning("Enhanced transitions are enabled but weather progression is disabled!");
            getLogger().warning("Enhanced transitions require weather progression to be enabled.");
        }
        
        getLogger().info("Weather progression configuration validated.");
    }

    /**
     * UPDATED: Start weather system tasks with proper forecast priority
     */
    private void startWeatherSystemTasks() {
        // Main weather task - OPTIMIZED: Use configurable interval with default of 2 minutes
        int weatherUpdateInterval = getConfig().getInt("weather.update_interval_ticks", 2400);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                // Check performance before running intensive tasks
                if (performanceMonitor != null && performanceMonitor.shouldSkipEffects(null)) {
                    return; // Skip this cycle if performance is poor
                }

                // IMPORTANT: Update weather forecast for all worlds FIRST
                Bukkit.getWorlds().forEach(world -> {
                    try {
                        // The forecast system is the single source of truth
                        weatherForecast.checkAndUpdateForecast(world);
                        
                        // The progression manager will automatically detect forecast changes
                        // No need to manually coordinate - it listens for changes
                        
                    } catch (Exception e) {
                        getLogger().warning("Error updating forecast for world " + world.getName() + ": " + e.getMessage());
                    }
                });

                // Check for weather events AFTER forecast updates (less frequently for better performance)
                if (System.currentTimeMillis() % 4 == 0) { // Every 4th cycle
                    blizzardManager.checkForBlizzards();
                    sandstormManager.checkForSandstorms();
                }
            } catch (Exception e) {
                getLogger().severe("Error in weather system task: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0L, weatherUpdateInterval);

        // Player cache cleanup task - less frequent for better performance
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                // Clear player zone cache occasionally to ensure accuracy
                climateZoneManager.clearPlayerCache();

                // Clean up performance monitor data
                if (performanceMonitor != null) {
                    // Remove offline players from monitoring
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (!player.isOnline()) {
                            performanceMonitor.cleanupPlayer(player);
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().severe("Error in cache cleanup task: " + e.getMessage());
                e.printStackTrace();
            }
        }, 12000L, 12000L); // Every 10 minutes instead of 5
    }

    /**
     * Completely disable vanilla Minecraft weather system
     */
    private void disableVanillaWeather() {
        if (!getConfig().getBoolean("weather_control.disable_vanilla_weather", true)) {
            getLogger().info("Vanilla weather disabling is disabled in config");
            return;
        }

        getLogger().info("Disabling vanilla weather system...");

        // Disable weather for all worlds immediately
        for (World world : Bukkit.getWorlds()) {
            disableWeatherForWorld(world);
        }

        // Create a task to prevent vanilla weather from ever starting
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                // Prevent vanilla weather from starting
                if (world.hasStorm() && !isOurWeatherActive(world)) {
                    world.setStorm(false);
                    world.setWeatherDuration(Integer.MAX_VALUE); // Prevent vanilla weather
                }
                if (world.isThundering() && !isOurWeatherActive(world)) {
                    world.setThundering(false);
                    world.setThunderDuration(Integer.MAX_VALUE);
                }
            }
        }, 0L, 400L); // Check every 20 seconds

        getLogger().info("✓ Vanilla weather system disabled");
    }

    /**
     * Disable weather for a specific world
     */
    private void disableWeatherForWorld(World world) {
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE); // Max value prevents vanilla weather
        world.setThunderDuration(Integer.MAX_VALUE);
    }

    /**
     * Check if our weather system is controlling this world's weather
     */
    private boolean isOurWeatherActive(World world) {
        if (weatherForecast == null) return false;

        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
        return currentWeather != null &&
               (currentWeather.getRainIntensity() > 0 || currentWeather.getThunderIntensity() > 0);
    }

    private void printPerformanceInfo() {
        getLogger().info("=== Performance Configuration ===");

        boolean adaptiveQuality = getConfig().getBoolean("performance.particles.adaptive_quality", true);
        getLogger().info("Adaptive Quality: " + (adaptiveQuality ? "ENABLED" : "DISABLED"));

        if (performanceMonitor != null) {
            double currentTPS = performanceMonitor.getCurrentTPS();
            getLogger().info("Current TPS: " + String.format("%.2f", currentTPS));

            boolean isPerformanceMode = performanceMonitor.isPerformanceMode();
            getLogger().info("Performance Mode: " + (isPerformanceMode ? "ACTIVE" : "INACTIVE"));

            if (isPerformanceMode) {
                getLogger().warning("⚠ Starting in performance mode due to server conditions");
            }
        }

        int maxParticles = getConfig().getInt("performance.particles.max_particles_per_player", 100);
        getLogger().info("Max Particles Per Player: " + maxParticles);

        boolean useBatching = getConfig().getBoolean("performance.particles.use_batch_processing", true);
        getLogger().info("Batch Processing: " + (useBatching ? "ENABLED" : "DISABLED"));

        // OPTIMIZED: Print weather update interval
        int weatherUpdateInterval = getConfig().getInt("weather.update_interval_ticks", 2400);
        getLogger().info("Weather Update Interval: " + weatherUpdateInterval + " ticks (" + (weatherUpdateInterval / 20) + " seconds)");
    }

    /**
     * UPDATED: Print feature status with progression info
     */
    private void printFeatureStatus() {
        getLogger().info("=== Feature Status ===");
        getLogger().info("Wind System: " + (getConfig().getBoolean("wind.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Temperature System: " + (getConfig().getBoolean("temperature.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Climate Zones: " + (climateZoneManager != null ? "ENABLED" : "DISABLED"));
        
        // UPDATED: Weather progression status
        boolean progressionEnabled = getConfig().getBoolean("weather_progression.enabled", true);
        boolean enhancedTransitions = getConfig().getBoolean("weather_progression.enhanced_transitions.enabled", true);
        boolean forecastIntegration = getConfig().getBoolean("weather_progression.forecast_integration.use_forecast_transitions", true);
        
        getLogger().info("Weather Progression: " + (progressionEnabled ? "ENABLED" : "DISABLED"));
        if (progressionEnabled) {
            getLogger().info("  Enhanced Transitions: " + (enhancedTransitions ? "ENABLED" : "DISABLED"));
            getLogger().info("  Forecast Integration: " + (forecastIntegration ? "ENABLED" : "DISABLED"));
        }
        
        getLogger().info("Aurora Effects: " + (getConfig().getBoolean("aurora.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Heat Mirages: " + (getConfig().getBoolean("heat_mirages.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Drought System: " + (getConfig().getBoolean("drought.effects.enabled", true) ? "ENABLED" : "DISABLED"));
        
        // UPDATED: Lightning and hail from progression system
        getLogger().info("Lightning Warnings: " + (getConfig().getBoolean("weather_progression.pre_storm_effects.lightning_warnings.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Hail Effects: " + (getConfig().getBoolean("weather_progression.active_weather_effects.hail.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Dynamic Sound System: " + (dynamicSoundManager != null ? "ENABLED" : "DISABLED"));
        getLogger().info("Performance Monitoring: " + (performanceMonitor != null ? "ENABLED" : "DISABLED"));
        getLogger().info("Vanilla Weather: " + (getConfig().getBoolean("weather_control.disable_vanilla_weather", true) ? "DISABLED" : "ENABLED"));
        getLogger().info("Snow Placement Prevention: " + (getConfig().getBoolean("weather_control.prevent_snow_placement", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Weather Optimization: " + (getConfig().getBoolean("weather.skip_unchanged", true) ? "ENABLED" : "DISABLED"));
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling OrbisClimate...");

        try {
            // Re-enable vanilla weather if configured to do so
            if (getConfig().getBoolean("weather_control.restore_vanilla_on_shutdown", true)) {
                getLogger().info("Restoring vanilla weather system...");
                for (World world : Bukkit.getWorlds()) {
                    world.setWeatherDuration(0); // Allow vanilla weather to resume
                    world.setThunderDuration(0);
                }
                getLogger().info("✓ Vanilla weather system restored");
            }

            // Cancel all tasks first to prevent new operations
            Bukkit.getScheduler().cancelTasks(this);
            getLogger().info("✓ All scheduled tasks cancelled");

            // Shutdown performance monitor first
            if (performanceMonitor != null) {
                performanceMonitor.shutdown();
                getLogger().info("✓ Performance monitor shut down");
            }

            // Then shutdown managers in reverse dependency order
            if (dynamicSoundManager != null) {
                dynamicSoundManager.shutdown();
                getLogger().info("✓ Dynamic sound manager shut down");
            }
            if (weatherProgressionManager != null) {
                weatherProgressionManager.shutdown();
                getLogger().info("✓ Weather progression manager shut down");
            }
            if (temperatureManager != null) {
                temperatureManager.shutdown();
                getLogger().info("✓ Temperature manager shut down");
            }
            if (climateZoneManager != null) {
                climateZoneManager.shutdown();
                getLogger().info("✓ Climate zone manager shut down");
            }
            if (sandstormManager != null) {
                sandstormManager.shutdown();
                getLogger().info("✓ Sandstorm manager shut down");
            }
            if (blizzardManager != null) {
                blizzardManager.shutdown();
                getLogger().info("✓ Blizzard manager shut down");
            }
            if (windManager != null) {
                windManager.shutdown();
                getLogger().info("✓ Wind manager shut down");
            }
            if (weatherForecast != null) {
                weatherForecast.shutdown();
                getLogger().info("✓ Weather forecast shut down");
            }

            // Clear all data structures
            playerParticleSettings.clear();

            // Force garbage collection to clean up
            System.gc();

        } catch (Exception e) {
            getLogger().severe("Error shutting down managers: " + e.getMessage());
            e.printStackTrace();
        }

        getLogger().info("OrbisClimate has been disabled!");
    }

    // Event handlers for player management
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (temperatureManager != null) {
            temperatureManager.addPlayer(player);
        }

        // Clear zone cache for this player to ensure fresh detection
        if (climateZoneManager != null) {
            climateZoneManager.clearPlayerCache(player);
        }

        // Initialize default particle settings
        playerParticleSettings.put(player, true);

        // Check server performance and notify if in performance mode
        if (performanceMonitor != null && performanceMonitor.isPerformanceMode()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && player.hasPermission("orbisclimate.notifications")) {
                    player.sendMessage("§6[OrbisClimate] §7Server is in performance mode - some effects may be reduced");
                }
            }, 60L); // 3 seconds after join
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (temperatureManager != null) {
            temperatureManager.removePlayer(player);
        }

        // Clean up player cache
        if (climateZoneManager != null) {
            climateZoneManager.clearPlayerCache(player);
        }

        // Clean up particle settings
        playerParticleSettings.remove(player);

        // Clean up wind manager cache
        if (windManager != null) {
            windManager.clearPlayerCache(player);
        }

        // Clean up performance monitor data
        if (performanceMonitor != null) {
            performanceMonitor.cleanupPlayer(player);
        }
    }

    // Configuration reload method
    public void reloadConfiguration() {
        reloadConfig();

        // Reload performance monitor first
        if (performanceMonitor != null) {
            performanceMonitor.reloadConfig();
        }

        if (windManager != null) {
            windManager.reloadConfig();
        }
        if (blizzardManager != null) {
            blizzardManager.reloadConfig();
        }
        if (sandstormManager != null) {
            sandstormManager.reloadConfig();
        }
        if (climateZoneManager != null) {
            climateZoneManager.reloadConfig();
        }
        if (temperatureManager != null) {
            temperatureManager.reloadConfig();
        }
        if (weatherProgressionManager != null) {
            weatherProgressionManager.reloadConfig();
        }
        if (dynamicSoundManager != null) {
            dynamicSoundManager.reloadConfig();
        }
        if (snowPlacementListener != null) {
            snowPlacementListener.reloadConfig();
        }

        getLogger().info("Configuration reloaded for all managers!");

        // Print updated performance info
        printPerformanceInfo();
    }

    // Performance-aware particle setting methods
    public boolean isPlayerParticlesEnabled(Player player) {
        if (performanceMonitor != null && performanceMonitor.shouldSkipEffects(player)) {
            return false; // Override user setting if performance is critical
        }
        return playerParticleSettings.getOrDefault(player, true);
    }

    public void setPlayerParticlesEnabled(Player player, boolean enabled) {
        playerParticleSettings.put(player, enabled);

        // Notify about performance mode if applicable
        if (enabled && performanceMonitor != null && performanceMonitor.isPerformanceMode()) {
            player.sendMessage("§6[OrbisClimate] §7Note: Server is in performance mode - effects may still be reduced");
        }
    }

    // Getters for managers
    public Random getRandom() {
        return random;
    }

    public WeatherForecast getWeatherForecast() {
        return weatherForecast;
    }

    public BlizzardManager getBlizzardManager() {
        return blizzardManager;
    }

    public SandstormManager getSandstormManager() {
        return sandstormManager;
    }

    public WindManager getWindManager() {
        return windManager;
    }

    public ClimateZoneManager getClimateZoneManager() {
        return climateZoneManager;
    }

    public TemperatureManager getTemperatureManager() {
        return temperatureManager;
    }

    public WeatherProgressionManager getWeatherProgressionManager() {
        return weatherProgressionManager;
    }

    public DynamicSoundManager getDynamicSoundManager() {
        return dynamicSoundManager;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public String getPerformanceReport() {
        if (performanceMonitor != null) {
            return performanceMonitor.getPerformanceReport();
        }
        return "§cPerformance monitoring not available";
    }
}