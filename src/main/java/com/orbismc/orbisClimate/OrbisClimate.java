package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;

public class OrbisClimate extends JavaPlugin {

    private WindManager windManager;
    private WeatherForecast weatherForecast;
    private BlizzardManager blizzardManager;
    private SandstormManager sandstormManager;
    private Random random;

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

            // Initialize blizzard manager
            getLogger().info("Initializing blizzard manager...");
            blizzardManager = new BlizzardManager(this, weatherForecast);
            getLogger().info("✓ Blizzard manager initialized");

            // Initialize sandstorm manager
            getLogger().info("Initializing sandstorm manager...");
            sandstormManager = new SandstormManager(this, weatherForecast, windManager);
            getLogger().info("✓ Sandstorm manager initialized");

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

            // Start weather forecast task (check every minute for time changes)
            getLogger().info("Starting weather forecast task...");
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    Bukkit.getWorlds().forEach(world -> {
                        weatherForecast.checkAndUpdateForecast(world);
                    });
                    // Check for blizzards and sandstorms every minute
                    blizzardManager.checkForBlizzards();
                    sandstormManager.checkForSandstorms();
                } catch (Exception e) {
                    getLogger().severe("Error in weather forecast task: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 0L, 1200L); // Every minute (1200 ticks)
            getLogger().info("✓ Weather forecast task started");

            getLogger().info("OrbisClimate has been enabled successfully!");

            // Print RealisticSeasons integration status
            if (weatherForecast.isRealisticSeasonsEnabled()) {
                getLogger().info("✓ RealisticSeasons integration: ENABLED");
            } else {
                getLogger().warning("⚠ RealisticSeasons integration: DISABLED (using vanilla time)");
            }

        } catch (Exception e) {
            getLogger().severe("✗ Failed to enable OrbisClimate: " + e.getMessage());
            e.printStackTrace();
            // Don't disable the plugin, but log the error
        }
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
        } catch (Exception e) {
            getLogger().severe("Error shutting down managers: " + e.getMessage());
            e.printStackTrace();
        }

        getLogger().info("OrbisClimate has been disabled!");
    }

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
}