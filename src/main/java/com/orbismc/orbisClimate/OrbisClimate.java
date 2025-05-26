package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
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
    private Random random;

    // NEW: Player particle preferences
    private final Map<Player, Boolean> playerParticleSettings = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Starting OrbisClimate plugin initialization...");

        try {
            // Save default config
            saveDefaultConfig();
            getLogger().info("✓ Config saved successfully");

            // Initialize random
            random = new Random();
            getLogger().info("✓ Random initialized");

            // Initialize weather forecast system
            getLogger().info("Initializing weather forecast system...");
            weatherForecast = new WeatherForecast(this);
            getLogger().info("✓ Weather forecast system initialized");

            // Initialize wind manager
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

            // Initialize weather progression manager
            getLogger().info("Initializing weather progression manager...");
            weatherProgressionManager = new WeatherProgressionManager(this, weatherForecast, climateZoneManager);
            getLogger().info("✓ Weather progression manager initialized");

            // Initialize blizzard manager
            getLogger().info("Initializing blizzard manager...");
            blizzardManager = new BlizzardManager(this, weatherForecast);
            getLogger().info("✓ Blizzard manager initialized");

            // Initialize sandstorm manager
            getLogger().info("Initializing sandstorm manager...");
            sandstormManager = new SandstormManager(this, weatherForecast, windManager);
            getLogger().info("✓ Sandstorm manager initialized");

            // NEW: Initialize dynamic sound manager
            getLogger().info("Initializing dynamic sound manager...");
            dynamicSoundManager = new DynamicSoundManager(this);
            getLogger().info("✓ Dynamic sound manager initialized");

            // Register event listeners
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("✓ Event listeners registered");

            // Register commands
            getLogger().info("Registering commands...");
            WindCommand windCommand = new WindCommand(this, windManager, weatherForecast);

            if (getCommand("wind") != null) {
                getCommand("wind").setExecutor(windCommand);
                getCommand("wind").setTabCompleter(windCommand);
                getLogger().info("✓ Wind command registered successfully");
            } else {
                getLogger().severe("✗ Failed to register wind command - command not found in plugin.yml!");
            }

            // Start main weather system task
            getLogger().info("Starting weather system tasks...");
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    // Update weather forecast for all worlds
                    Bukkit.getWorlds().forEach(world -> {
                        weatherForecast.checkAndUpdateForecast(world);
                    });

                    // Check for weather events
                    blizzardManager.checkForBlizzards();
                    sandstormManager.checkForSandstorms();
                } catch (Exception e) {
                    getLogger().severe("Error in weather system task: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 0L, 1200L); // Every minute (1200 ticks)

            // Start player cache cleanup task (every 5 minutes)
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    // Clear player zone cache occasionally to ensure accuracy
                    climateZoneManager.clearPlayerCache();
                } catch (Exception e) {
                    getLogger().severe("Error in cache cleanup task: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 6000L, 6000L); // Every 5 minutes

            getLogger().info("✓ Weather system tasks started");

            getLogger().info("OrbisClimate has been enabled successfully!");

            // Print integration status
            if (weatherForecast.isRealisticSeasonsEnabled()) {
                getLogger().info("✓ RealisticSeasons integration: ENABLED");
            } else {
                getLogger().warning("⚠ RealisticSeasons integration: DISABLED (using vanilla time)");
            }

            // Print feature status
            printFeatureStatus();

        } catch (Exception e) {
            getLogger().severe("✗ Failed to enable OrbisClimate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void printFeatureStatus() {
        getLogger().info("=== Feature Status ===");
        getLogger().info("Wind System: " + (getConfig().getBoolean("wind.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Temperature System: " + (getConfig().getBoolean("temperature.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Climate Zones: " + (climateZoneManager != null ? "ENABLED" : "DISABLED"));
        getLogger().info("Weather Progression: " + (getConfig().getBoolean("weather_progression.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Aurora Effects: " + (getConfig().getBoolean("aurora.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Heat Mirages: " + (getConfig().getBoolean("heat_mirages.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Drought System: " + (getConfig().getBoolean("drought.effects.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Lightning Warnings: " + (getConfig().getBoolean("weather_progression.lightning_warnings.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Hail Effects: " + (getConfig().getBoolean("weather_progression.hail.enabled", true) ? "ENABLED" : "DISABLED"));
        getLogger().info("Dynamic Sound System: " + (dynamicSoundManager != null ? "ENABLED" : "DISABLED"));
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling OrbisClimate...");

        try {
            if (windManager != null) {
                windManager.shutdown();
                getLogger().info("✓ Wind manager shut down");
            }
            if (blizzardManager != null) {
                blizzardManager.shutdown();
                getLogger().info("✓ Blizzard manager shut down");
            }
            if (sandstormManager != null) {
                sandstormManager.shutdown();
                getLogger().info("✓ Sandstorm manager shut down");
            }
            if (climateZoneManager != null) {
                climateZoneManager.shutdown();
                getLogger().info("✓ Climate zone manager shut down");
            }
            if (temperatureManager != null) {
                temperatureManager.shutdown();
                getLogger().info("✓ Temperature manager shut down");
            }
            if (weatherProgressionManager != null) {
                weatherProgressionManager.shutdown();
                getLogger().info("✓ Weather progression manager shut down");
            }
            if (dynamicSoundManager != null) {
                dynamicSoundManager.shutdown();
                getLogger().info("✓ Dynamic sound manager shut down");
            }
        } catch (Exception e) {
            getLogger().severe("Error shutting down managers: " + e.getMessage());
            e.printStackTrace();
        }

        getLogger().info("OrbisClimate has been disabled!");
    }

    // Event handlers for player management
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (temperatureManager != null) {
            temperatureManager.addPlayer(event.getPlayer());
        }

        // Clear zone cache for this player to ensure fresh detection
        if (climateZoneManager != null) {
            climateZoneManager.clearPlayerCache(event.getPlayer());
        }

        // Initialize default particle settings
        playerParticleSettings.put(event.getPlayer(), true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (temperatureManager != null) {
            temperatureManager.removePlayer(event.getPlayer());
        }

        // Clean up player cache
        if (climateZoneManager != null) {
            climateZoneManager.clearPlayerCache(event.getPlayer());
        }

        // Clean up particle settings
        playerParticleSettings.remove(event.getPlayer());
    }

    // Configuration reload method
    public void reloadConfiguration() {
        reloadConfig();

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

        getLogger().info("Configuration reloaded for all managers!");
    }

    // NEW: Player particle preference methods
    public boolean isPlayerParticlesEnabled(Player player) {
        return playerParticleSettings.getOrDefault(player, true);
    }

    public void setPlayerParticlesEnabled(Player player, boolean enabled) {
        playerParticleSettings.put(player, enabled);
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
}