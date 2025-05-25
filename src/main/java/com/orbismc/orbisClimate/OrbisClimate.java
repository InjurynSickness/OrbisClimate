package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;

public class OrbisClimate extends JavaPlugin {

    private WindManager windManager;
    private Random random;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize random
        random = new Random();

        // Initialize wind manager
        windManager = new WindManager(this, random);

        // Register commands
        WindCommand windCommand = new WindCommand(this, windManager);
        getCommand("wind").setExecutor(windCommand);
        getCommand("wind").setTabCompleter(windCommand);

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
}