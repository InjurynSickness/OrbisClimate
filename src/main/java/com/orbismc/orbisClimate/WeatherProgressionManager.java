package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Color;

import java.util.*;

public class WeatherProgressionManager {

    public enum WeatherProgression {
        CLEAR,
        PRE_STORM,      // Lightning warnings, building clouds
        LIGHT_RAIN,
        HEAVY_RAIN,
        THUNDERSTORM,
        POST_STORM      // Clearing up
    }

    public static class WorldWeatherData {
        private WeatherProgression currentProgression;
        private WeatherForecast.WeatherType targetWeather;
        private long progressionStartTime;
        private long nextProgressionTime;
        private boolean hailActive;
        private long hailStartTime;
        private int lightningWarningCount;
        private long lastLightningWarning;

        public WorldWeatherData() {
            this.currentProgression = WeatherProgression.CLEAR;
            this.targetWeather = WeatherForecast.WeatherType.CLEAR;
            this.progressionStartTime = System.currentTimeMillis();
            this.nextProgressionTime = 0;
            this.hailActive = false;
            this.hailStartTime = 0;
            this.lightningWarningCount = 0;
            this.lastLightningWarning = 0;
        }

        // Getters and setters
        public WeatherProgression getCurrentProgression() { return currentProgression; }
        public void setCurrentProgression(WeatherProgression progression) { this.currentProgression = progression; }
        public WeatherForecast.WeatherType getTargetWeather() { return targetWeather; }
        public void setTargetWeather(WeatherForecast.WeatherType weather) { this.targetWeather = weather; }
        public long getProgressionStartTime() { return progressionStartTime; }
        public void setProgressionStartTime(long time) { this.progressionStartTime = time; }
        public long getNextProgressionTime() { return nextProgressionTime; }
        public void setNextProgressionTime(long time) { this.nextProgressionTime = time; }
        public boolean isHailActive() { return hailActive; }
        public void setHailActive(boolean active) { this.hailActive = active; }
        public long getHailStartTime() { return hailStartTime; }
        public void setHailStartTime(long time) { this.hailStartTime = time; }
        public int getLightningWarningCount() { return lightningWarningCount; }
        public void setLightningWarningCount(int count) { this.lightningWarningCount = count; }
        public long getLastLightningWarning() { return lastLightningWarning; }
        public void setLastLightningWarning(long time) { this.lastLightningWarning = time; }
    }

    private final OrbisClimate plugin;
    private final WeatherForecast weatherForecast;
    private final ClimateZoneManager climateZoneManager;
    private final Random random;

    // Configuration
    private boolean progressiveWeatherEnabled;
    private boolean lightningWarningsEnabled;
    private boolean hailEnabled;
    private int preStormDurationMinutes;
    private int lightningWarningIntervalSeconds;
    private double hailChanceDuringRain;
    private int hailDurationMinutes;

    // Runtime data
    private final Map<World, WorldWeatherData> worldWeatherData = new HashMap<>();
    private BukkitTask progressionTask;

    public WeatherProgressionManager(OrbisClimate plugin, WeatherForecast weatherForecast,
                                     ClimateZoneManager climateZoneManager) {
        this.plugin = plugin;
        this.weatherForecast = weatherForecast;
        this.climateZoneManager = climateZoneManager;
        this.random = new Random();

        loadConfiguration();
        initializeWorldData();
        startProgressionTask();
    }

    private void loadConfiguration() {
        progressiveWeatherEnabled = plugin.getConfig().getBoolean("weather_progression.enabled", true);
        lightningWarningsEnabled = plugin.getConfig().getBoolean("weather_progression.lightning_warnings.enabled", true);
        hailEnabled = plugin.getConfig().getBoolean("weather_progression.hail.enabled", true);
        preStormDurationMinutes = plugin.getConfig().getInt("weather_progression.pre_storm_duration_minutes", 5);
        lightningWarningIntervalSeconds = plugin.getConfig().getInt("weather_progression.lightning_warnings.interval_seconds", 30);
        hailChanceDuringRain = plugin.getConfig().getDouble("weather_progression.hail.chance_during_rain", 0.3);
        hailDurationMinutes = plugin.getConfig().getInt("weather_progression.hail.duration_minutes", 3);
    }

    private void initializeWorldData() {
        for (World world : Bukkit.getWorlds()) {
            worldWeatherData.put(world, new WorldWeatherData());
        }
    }

    private void startProgressionTask() {
        if (!progressiveWeatherEnabled) return;

        progressionTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                updateWeatherProgression(world);
                processWeatherEffects(world);
            }
        }, 0L, 20L); // Update every second
    }

    private void updateWeatherProgression(World world) {
        WorldWeatherData data = worldWeatherData.get(world);
        if (data == null) return;

        WeatherForecast.WeatherType currentTarget = weatherForecast.getCurrentWeather(world);
        long currentTime = System.currentTimeMillis();

        // Check if target weather has changed
        if (currentTarget != data.getTargetWeather()) {
            startWeatherTransition(world, data, currentTarget);
        }

        // Check if it's time for next progression step
        if (currentTime >= data.getNextProgressionTime()) {
            progressWeatherStep(world, data);
        }

        // Handle hail timing
        if (data.isHailActive() &&
                currentTime - data.getHailStartTime() > (hailDurationMinutes * 60 * 1000)) {
            stopHail(world, data);
        }
    }

    private void startWeatherTransition(World world, WorldWeatherData data, WeatherForecast.WeatherType newTarget) {
        data.setTargetWeather(newTarget);
        data.setProgressionStartTime(System.currentTimeMillis());

        // Determine starting progression based on current and target weather
        WeatherProgression startProgression = determineStartProgression(data.getCurrentProgression(), newTarget);
        data.setCurrentProgression(startProgression);

        // Schedule next progression step
        scheduleNextProgression(data, startProgression, newTarget);

        // Start lightning warnings if transitioning to thunderstorm
        if (newTarget == WeatherForecast.WeatherType.THUNDERSTORM && lightningWarningsEnabled) {
            data.setLightningWarningCount(0);
            data.setLastLightningWarning(System.currentTimeMillis());
        }
    }

    private WeatherProgression determineStartProgression(WeatherProgression current, WeatherForecast.WeatherType target) {
        // If we're going to a storm, start with pre-storm
        if (target == WeatherForecast.WeatherType.THUNDERSTORM ||
                target == WeatherForecast.WeatherType.HEAVY_RAIN) {
            return WeatherProgression.PRE_STORM;
        }

        // If going to light rain from clear, go directly
        if (target == WeatherForecast.WeatherType.LIGHT_RAIN && current == WeatherProgression.CLEAR) {
            return WeatherProgression.LIGHT_RAIN;
        }

        // If clearing up, go to post-storm first
        if (target == WeatherForecast.WeatherType.CLEAR &&
                (current == WeatherProgression.THUNDERSTORM || current == WeatherProgression.HEAVY_RAIN)) {
            return WeatherProgression.POST_STORM;
        }

        return WeatherProgression.CLEAR;
    }

    private void scheduleNextProgression(WorldWeatherData data, WeatherProgression current, WeatherForecast.WeatherType target) {
        long delay = 0;

        switch (current) {
            case PRE_STORM:
                delay = preStormDurationMinutes * 60 * 1000; // Convert to milliseconds
                break;
            case LIGHT_RAIN:
                if (target == WeatherForecast.WeatherType.HEAVY_RAIN || target == WeatherForecast.WeatherType.THUNDERSTORM) {
                    delay = 3 * 60 * 1000; // 3 minutes before intensifying
                }
                break;
            case HEAVY_RAIN:
                if (target == WeatherForecast.WeatherType.THUNDERSTORM) {
                    delay = 2 * 60 * 1000; // 2 minutes before thunder starts
                }
                break;
            case POST_STORM:
                delay = 2 * 60 * 1000; // 2 minutes to fully clear
                break;
        }

        data.setNextProgressionTime(System.currentTimeMillis() + delay);
    }

    private void progressWeatherStep(World world, WorldWeatherData data) {
        WeatherProgression current = data.getCurrentProgression();
        WeatherForecast.WeatherType target = data.getTargetWeather();

        WeatherProgression next = getNextProgression(current, target);
        data.setCurrentProgression(next);

        // Apply world weather changes
        applyProgressionToWorld(world, next);

        // Schedule next step if needed
        if (next != WeatherProgression.CLEAR && next != WeatherProgression.THUNDERSTORM) {
            scheduleNextProgression(data, next, target);
        }

        // Check for hail during rain
        if (hailEnabled && (next == WeatherProgression.HEAVY_RAIN) &&
                random.nextDouble() < hailChanceDuringRain) {
            startHail(world, data);
        }

        // Notify players of weather changes
        notifyPlayersOfProgression(world, next);
    }

    private WeatherProgression getNextProgression(WeatherProgression current, WeatherForecast.WeatherType target) {
        switch (current) {
            case PRE_STORM:
                return WeatherProgression.LIGHT_RAIN;
            case LIGHT_RAIN:
                if (target == WeatherForecast.WeatherType.HEAVY_RAIN || target == WeatherForecast.WeatherType.THUNDERSTORM) {
                    return WeatherProgression.HEAVY_RAIN;
                }
                return WeatherProgression.CLEAR;
            case HEAVY_RAIN:
                if (target == WeatherForecast.WeatherType.THUNDERSTORM) {
                    return WeatherProgression.THUNDERSTORM;
                }
                return WeatherProgression.POST_STORM;
            case THUNDERSTORM:
                return WeatherProgression.POST_STORM;
            case POST_STORM:
                return WeatherProgression.CLEAR;
            default:
                return WeatherProgression.CLEAR;
        }
    }

    private void applyProgressionToWorld(World world, WeatherProgression progression) {
        switch (progression) {
            case CLEAR:
                world.setStorm(false);
                world.setThundering(false);
                break;
            case PRE_STORM:
                // Don't change world weather yet, just visual effects
                break;
            case LIGHT_RAIN:
                world.setStorm(true);
                world.setThundering(false);
                break;
            case HEAVY_RAIN:
                world.setStorm(true);
                world.setThundering(false);
                break;
            case THUNDERSTORM:
                world.setStorm(true);
                world.setThundering(true);
                break;
            case POST_STORM:
                world.setStorm(false);
                world.setThundering(false);
                break;
        }
    }

    private void processWeatherEffects(World world) {
        WorldWeatherData data = worldWeatherData.get(world);
        if (data == null) return;

        WeatherProgression progression = data.getCurrentProgression();

        switch (progression) {
            case PRE_STORM:
                processPreStormEffects(world, data);
                break;
            case HEAVY_RAIN:
                if (data.isHailActive()) {
                    processHailEffects(world);
                }
                break;
            case THUNDERSTORM:
                processThunderstormEffects(world);
                break;
        }
    }

    private void processPreStormEffects(World world, WorldWeatherData data) {
        long currentTime = System.currentTimeMillis();

        // Lightning warnings
        if (lightningWarningsEnabled &&
                currentTime - data.getLastLightningWarning() > (lightningWarningIntervalSeconds * 1000)) {

            createLightningWarning(world);
            data.setLastLightningWarning(currentTime);
            data.setLightningWarningCount(data.getLightningWarningCount() + 1);
        }

        // Building cloud effects
        if (random.nextInt(100) == 0) { // 1% chance per second
            createBuildingCloudEffects(world);
        }
    }

    private void processHailEffects(World world) {
        for (Player player : world.getPlayers()) {
            ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);

            // Hail is more common in temperate zones
            if (zone == ClimateZoneManager.ClimateZone.TEMPERATE && random.nextInt(20) == 0) {
                createHailEffects(player);
            }
        }
    }

    private void processThunderstormEffects(World world) {
        // Enhanced lightning effects during storms
        if (random.nextInt(200) == 0) { // Every ~10 seconds on average
            createEnhancedLightningEffects(world);
        }
    }

    private void createLightningWarning(World world) {
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();

            // Create distant lightning flash
            for (int i = 0; i < 5; i++) {
                Location lightningLoc = loc.clone().add(
                        (random.nextDouble() - 0.5) * 100, // Far away
                        20 + random.nextDouble() * 30,     // High in sky
                        (random.nextDouble() - 0.5) * 100
                );

                // White flash particles
                player.spawnParticle(Particle.ELECTRIC_SPARK, lightningLoc, 20,
                        2, 2, 2, 0.1);
                player.spawnParticle(Particle.FLASH, lightningLoc, 1,
                        0, 0, 0, 0);
            }

            // Distant thunder sound
            player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 0.8f);

            // Warning message
            if (player.hasPermission("orbisclimate.notifications") && random.nextInt(3) == 0) {
                player.sendMessage("§8§l⚡ Lightning flickers in the distance... A storm approaches.");
            }
        }
    }

    private void createBuildingCloudEffects(World world) {
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation().add(0, 25, 0);

            // Dark cloud particles gathering
            for (int i = 0; i < 15; i++) {
                Location cloudLoc = loc.clone().add(
                        (random.nextDouble() - 0.5) * 30,
                        random.nextDouble() * 10,
                        (random.nextDouble() - 0.5) * 30
                );

                // Dark gray cloud particles
                Particle.DustOptions darkCloud = new Particle.DustOptions(
                        Color.fromRGB(64, 64, 64), 2.0f
                );

                player.spawnParticle(Particle.DUST, cloudLoc, 1,
                        0.5, 0.2, 0.5, 0, darkCloud);
            }
        }
    }

    private void startHail(World world, WorldWeatherData data) {
        data.setHailActive(true);
        data.setHailStartTime(System.currentTimeMillis());

        // Notify players
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§f§l❄ Hail begins to fall from the stormy sky!");
            }
        }
    }

    private void stopHail(World world, WorldWeatherData data) {
        data.setHailActive(false);

        // Notify players
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§7§l❄ The hail subsides, returning to rain.");
            }
        }
    }

    private void createHailEffects(Player player) {
        Location loc = player.getLocation();

        // Don't show hail if player is indoors
        if (climateZoneManager != null) {
            // You could add indoor check here if needed
        }

        // Create hail particles falling around player
        for (int i = 0; i < 10; i++) {
            Location hailLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 15,
                    8 + random.nextDouble() * 5,
                    (random.nextDouble() - 0.5) * 15
            );

            // White/ice particles for hail
            player.spawnParticle(Particle.WHITE_ASH, hailLoc, 1,
                    0, -1, 0, 0.5);
            player.spawnParticle(Particle.CLOUD, hailLoc, 1,
                    0.1, 0, 0.1, 0.02);
        }

        // Hail impact sounds occasionally
        if (random.nextInt(40) == 0) {
            player.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.2f, 1.5f);
        }
    }

    private void createEnhancedLightningEffects(World world) {
        for (Player player : world.getPlayers()) {
            if (random.nextInt(3) != 0) continue; // Not every player every time

            Location loc = player.getLocation();
            Location lightningLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 50,
                    15 + random.nextDouble() * 20,
                    (random.nextDouble() - 0.5) * 50
            );

            // Bright lightning flash
            player.spawnParticle(Particle.ELECTRIC_SPARK, lightningLoc, 30,
                    3, 5, 3, 0.2);
            player.spawnParticle(Particle.FLASH, lightningLoc, 1,
                    0, 0, 0, 0);

            // Thunder sound with delay based on distance
            double distance = loc.distance(lightningLoc);
            long delay = (long) (distance / 340.0 * 20); // Speed of sound delay in ticks

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                        0.7f, 0.9f + random.nextFloat() * 0.2f);
            }, Math.max(1, delay));
        }
    }

    private void notifyPlayersOfProgression(World world, WeatherProgression progression) {
        String message = getProgressionMessage(progression);
        if (message == null) return;

        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage(message);
            }
        }
    }

    private String getProgressionMessage(WeatherProgression progression) {
        switch (progression) {
            case PRE_STORM:
                return "§7§lClouds gather overhead as a storm approaches...";
            case LIGHT_RAIN:
                return "§9§l☔ Light rain begins to fall.";
            case HEAVY_RAIN:
                return "§1§l☔ The rain intensifies into a heavy downpour!";
            case THUNDERSTORM:
                return "§5§l⚡ Thunder rumbles as the storm reaches its peak!";
            case POST_STORM:
                return "§7§lThe storm begins to weaken and move on...";
            case CLEAR:
                return "§6§l☀ The skies clear as the storm passes.";
            default:
                return null;
        }
    }

    // Public getters
    public WeatherProgression getWorldProgression(World world) {
        WorldWeatherData data = worldWeatherData.get(world);
        return data != null ? data.getCurrentProgression() : WeatherProgression.CLEAR;
    }

    public boolean isHailActive(World world) {
        WorldWeatherData data = worldWeatherData.get(world);
        return data != null && data.isHailActive();
    }

    // Configuration reload
    public void reloadConfig() {
        loadConfiguration();
    }

    // Shutdown
    public void shutdown() {
        if (progressionTask != null) {
            progressionTask.cancel();
        }
        worldWeatherData.clear();
    }
}