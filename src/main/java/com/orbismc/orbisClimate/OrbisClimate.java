package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;

public class OrbisClimate extends JavaPlugin {

    private WindManager windManager;
    private WeatherForecast weatherForecast;
    private Random random;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize random
        random = new Random();

        // Initialize weather forecast system
        weatherForecast = new WeatherForecast(this);

        // Initialize wind manager
        windManager = new WindManager(this, random, weatherForecast);

        // Register commands
        WindCommand windCommand = new WindCommand(this, windManager, weatherForecast);
        getCommand("wind").setExecutor(windCommand);
        getCommand("wind").setTabCompleter(windCommand);

        // Start weather forecast task (check every minute for time changes)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Bukkit.getWorlds().forEach(world -> weatherForecast.checkAndUpdateForecast(world));
        }, 0L, 1200L); // Every minute (1200 ticks)

        getLogger().info("OrbisClimate has been enabled!");
    }

    @Override
    public void onDisable() {
        if (windManager != null) {
            windManager.shutdown();
        }
        getLogger().info("OrbisClimate has been disabled!");
    }

    public Random getRandom() {
        return random;
    }

    public WeatherForecast getWeatherForecast() {
        return weatherForecast;
    }
}