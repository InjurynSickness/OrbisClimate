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

/**
 * REDESIGNED WeatherProgressionManager that works WITH the forecast system
 * instead of against it. This adds visual/audio enhancements to forecast weather
 * without trying to control the actual weather state.
 */
public class WeatherProgressionManager {

    public enum WeatherProgression {
        CLEAR,
        PRE_STORM,      // Visual warnings before storms
        ACTIVE_WEATHER, // Weather is active according to forecast
        POST_STORM,     // Clearing effects after storms
        TRANSITION      // During forecast transitions
    }

    public static class WorldProgressionData {
        private WeatherProgression currentProgression;
        private WeatherForecast.WeatherType lastKnownWeather;
        private WeatherForecast.WeatherType targetWeather;
        private long progressionStartTime;
        private long nextProgressionCheck;
        private boolean hailActive;
        private long hailStartTime;
        private int lightningWarningCount;
        private long lastLightningWarning;
        private boolean inTransition;
        private long transitionStartTime;

        public WorldProgressionData() {
            this.currentProgression = WeatherProgression.CLEAR;
            this.lastKnownWeather = WeatherForecast.WeatherType.CLEAR;
            this.targetWeather = WeatherForecast.WeatherType.CLEAR;
            this.progressionStartTime = System.currentTimeMillis();
            this.nextProgressionCheck = 0;
            this.hailActive = false;
            this.hailStartTime = 0;
            this.lightningWarningCount = 0;
            this.lastLightningWarning = 0;
            this.inTransition = false;
            this.transitionStartTime = 0;
        }

        // Getters and setters
        public WeatherProgression getCurrentProgression() { return currentProgression; }
        public void setCurrentProgression(WeatherProgression progression) { this.currentProgression = progression; }
        public WeatherForecast.WeatherType getLastKnownWeather() { return lastKnownWeather; }
        public void setLastKnownWeather(WeatherForecast.WeatherType weather) { this.lastKnownWeather = weather; }
        public WeatherForecast.WeatherType getTargetWeather() { return targetWeather; }
        public void setTargetWeather(WeatherForecast.WeatherType weather) { this.targetWeather = weather; }
        public long getProgressionStartTime() { return progressionStartTime; }
        public void setProgressionStartTime(long time) { this.progressionStartTime = time; }
        public long getNextProgressionCheck() { return nextProgressionCheck; }
        public void setNextProgressionCheck(long time) { this.nextProgressionCheck = time; }
        public boolean isHailActive() { return hailActive; }
        public void setHailActive(boolean active) { this.hailActive = active; }
        public long getHailStartTime() { return hailStartTime; }
        public void setHailStartTime(long time) { this.hailStartTime = time; }
        public int getLightningWarningCount() { return lightningWarningCount; }
        public void setLightningWarningCount(int count) { this.lightningWarningCount = count; }
        public long getLastLightningWarning() { return lastLightningWarning; }
        public void setLastLightningWarning(long time) { this.lastLightningWarning = time; }
        public boolean isInTransition() { return inTransition; }
        public void setInTransition(boolean transition) { this.inTransition = transition; }
        public long getTransitionStartTime() { return transitionStartTime; }
        public void setTransitionStartTime(long time) { this.transitionStartTime = time; }
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
    private boolean enhancedTransitions;
    private int transitionWarningMinutes;

    // Runtime data
    private final Map<World, WorldProgressionData> worldProgressionData = new HashMap<>();
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
        enhancedTransitions = plugin.getConfig().getBoolean("weather_progression.enhanced_transitions", true);
        transitionWarningMinutes = plugin.getConfig().getInt("weather_progression.transition_warning_minutes", 2);
    }

    private void initializeWorldData() {
        for (World world : Bukkit.getWorlds()) {
            worldProgressionData.put(world, new WorldProgressionData());
        }
    }

    private void startProgressionTask() {
        if (!progressiveWeatherEnabled) return;

        progressionTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                updateWeatherProgression(world);
                processProgressionEffects(world);
            }
        }, 0L, 20L); // Update every second
    }

    /**
     * NEW: Main progression update that follows forecast instead of controlling it
     */
    private void updateWeatherProgression(World world) {
        WorldProgressionData data = worldProgressionData.get(world);
        if (data == null) return;

        // Get current weather from forecast (single source of truth)
        WeatherForecast.WeatherType currentForecastWeather = weatherForecast.getCurrentWeather(world);
        WeatherForecast.WeatherType lastKnownWeather = data.getLastKnownWeather();
        
        long currentTime = System.currentTimeMillis();

        // Check if forecast weather has changed (transition detection)
        if (currentForecastWeather != lastKnownWeather) {
            handleForecastWeatherChange(world, data, lastKnownWeather, currentForecastWeather);
        }

        // Update progression based on current forecast weather
        updateProgressionForCurrentWeather(world, data, currentForecastWeather, currentTime);

        // Handle ongoing effects
        handleOngoingEffects(world, data, currentTime);

        // Update our tracking
        data.setLastKnownWeather(currentForecastWeather);
    }

    /**
     * Handle when the forecast weather changes - this is where we add transition effects
     */
    private void handleForecastWeatherChange(World world, WorldProgressionData data, 
                                           WeatherForecast.WeatherType from, WeatherForecast.WeatherType to) {
        
        if (plugin.getConfig().getBoolean("debug.log_weather_transitions", false)) {
            plugin.getLogger().info("Progression Manager detected forecast change in " + world.getName() + 
                ": " + from.getDisplayName() + " -> " + to.getDisplayName());
        }

        // Start transition effects if enabled
        if (enhancedTransitions) {
            startTransitionEffects(world, data, from, to);
        }

        // Determine new progression based on the forecast weather
        WeatherProgression newProgression = determineProgressionForWeather(to);
        data.setCurrentProgression(newProgression);
        data.setProgressionStartTime(System.currentTimeMillis());

        // Start special effects for certain weather types
        if (to == WeatherForecast.WeatherType.THUNDERSTORM && lightningWarningsEnabled) {
            // Reset lightning warnings for new storm
            data.setLightningWarningCount(0);
            data.setLastLightningWarning(System.currentTimeMillis());
        }

        // Check for hail during rain
        if (hailEnabled && isRainWeather(to) && random.nextDouble() < hailChanceDuringRain) {
            startHail(world, data);
        }

        // Notify players
        notifyPlayersOfWeatherChange(world, from, to);
    }

    /**
     * NEW: Determine what progression stage we should be in based on forecast weather
     */
    private WeatherProgression determineProgressionForWeather(WeatherForecast.WeatherType weather) {
        switch (weather) {
            case CLEAR:
                return WeatherProgression.CLEAR;
            case LIGHT_RAIN:
            case HEAVY_RAIN:
            case SNOW:
                return WeatherProgression.ACTIVE_WEATHER;
            case THUNDERSTORM:
            case BLIZZARD:
            case SANDSTORM:
                return WeatherProgression.ACTIVE_WEATHER;
            default:
                return WeatherProgression.CLEAR;
        }
    }

    /**
     * NEW: Update progression effects based on current forecast weather
     */
    private void updateProgressionForCurrentWeather(World world, WorldProgressionData data, 
                                                   WeatherForecast.WeatherType currentWeather, long currentTime) {
        
        // Check if we need to advance within the current weather type
        if (currentTime >= data.getNextProgressionCheck()) {
            
            switch (data.getCurrentProgression()) {
                case CLEAR:
                    // Check if we should show pre-storm effects before next weather change
                    checkForUpcomingWeatherTransition(world, data);
                    break;
                    
                case ACTIVE_WEATHER:
                    // Weather is active, check for intensity changes or special effects
                    updateActiveWeatherEffects(world, data, currentWeather);
                    break;
                    
                case TRANSITION:
                    // Handle ongoing transitions
                    updateTransitionEffects(world, data);
                    break;
            }
            
            // Schedule next check
            data.setNextProgressionCheck(currentTime + 10000); // Check every 10 seconds
        }
    }

    /**
     * NEW: Check if we should show warning effects for upcoming weather changes
     */
    private void checkForUpcomingWeatherTransition(World world, WorldProgressionData data) {
        // Get detailed forecast to check for upcoming transitions
        WeatherForecast.DetailedForecast forecast = weatherForecast.getForecast(world);
        if (forecast == null) return;

        int currentHour = weatherForecast.getCurrentHour(world);
        
        // Look ahead for upcoming transitions
        for (int lookAhead = 1; lookAhead <= 3; lookAhead++) { // Look 1-3 hours ahead
            int futureHour = (currentHour + lookAhead) % 24;
            
            if (forecast.isTransitionHour(futureHour)) {
                WeatherForecast.WeatherType upcomingWeather = forecast.getWeatherForHour(futureHour);
                
                // Show pre-storm effects if a storm is coming
                if (isStormWeather(upcomingWeather) && data.getCurrentProgression() == WeatherProgression.CLEAR) {
                    data.setCurrentProgression(WeatherProgression.PRE_STORM);
                    data.setTargetWeather(upcomingWeather);
                    notifyPlayersOfUpcomingWeather(world, upcomingWeather, lookAhead);
                    break;
                }
            }
        }
    }

    /**
     * Start transition effects between weather types
     */
    private void startTransitionEffects(World world, WorldProgressionData data, 
                                      WeatherForecast.WeatherType from, WeatherForecast.WeatherType to) {
        data.setInTransition(true);
        data.setTransitionStartTime(System.currentTimeMillis());
        data.setCurrentProgression(WeatherProgression.TRANSITION);
        
        // Schedule transition completion
        long transitionDuration = getTransitionDuration(from, to) * 1000L; // Convert to milliseconds
        data.setNextProgressionCheck(System.currentTimeMillis() + transitionDuration);
    }

    /**
     * Get transition duration based on weather types
     */
    private long getTransitionDuration(WeatherForecast.WeatherType from, WeatherForecast.WeatherType to) {
        // Quick transitions
        if ((from == WeatherForecast.WeatherType.CLEAR && to == WeatherForecast.WeatherType.LIGHT_RAIN) ||
            (from == WeatherForecast.WeatherType.LIGHT_RAIN && to == WeatherForecast.WeatherType.CLEAR)) {
            return plugin.getConfig().getLong("weather_progression.transitions.quick_change", 30);
        }
        
        // Medium transitions
        if ((from == WeatherForecast.WeatherType.LIGHT_RAIN && to == WeatherForecast.WeatherType.HEAVY_RAIN) ||
            (from == WeatherForecast.WeatherType.HEAVY_RAIN && to == WeatherForecast.WeatherType.LIGHT_RAIN)) {
            return plugin.getConfig().getLong("weather_progression.transitions.medium_change", 90);
        }
        
        // Slow transitions (storms)
        return plugin.getConfig().getLong("weather_progression.transitions.slow_change", 180);
    }

    /**
     * Process ongoing effects for current progression
     */
    private void processProgressionEffects(World world) {
        WorldProgressionData data = worldProgressionData.get(world);
        if (data == null) return;

        WeatherProgression progression = data.getCurrentProgression();

        switch (progression) {
            case PRE_STORM:
                processPreStormEffects(world, data);
                break;
            case ACTIVE_WEATHER:
                processActiveWeatherEffects(world, data);
                break;
            case TRANSITION:
                processTransitionEffects(world, data);
                break;
        }
    }

    /**
     * Handle ongoing effects during active weather
     */
    private void handleOngoingEffects(World world, WorldProgressionData data, long currentTime) {
        // Handle hail timing
        if (data.isHailActive() &&
                currentTime - data.getHailStartTime() > (hailDurationMinutes * 60 * 1000)) {
            stopHail(world, data);
        }
    }

    /**
     * Update effects during active weather
     */
    private void updateActiveWeatherEffects(World world, WorldProgressionData data, WeatherForecast.WeatherType weather) {
        // Add intensity variations or special effects during active weather
        switch (weather) {
            case THUNDERSTORM:
                // Continue lightning warnings during storm
                processThunderstormEffects(world, data);
                break;
            case HEAVY_RAIN:
                // Check for hail if not already active
                if (!data.isHailActive() && hailEnabled && random.nextDouble() < (hailChanceDuringRain * 0.1)) {
                    startHail(world, data);
                }
                break;
        }
    }

    /**
     * Update transition effects
     */
    private void updateTransitionEffects(World world, WorldProgressionData data) {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime >= data.getNextProgressionCheck()) {
            // Transition complete
            data.setInTransition(false);
            WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
            data.setCurrentProgression(determineProgressionForWeather(currentWeather));
        }
    }

    /**
     * Process pre-storm effects (unchanged from original)
     */
    private void processPreStormEffects(World world, WorldProgressionData data) {
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

    /**
     * Process active weather effects
     */
    private void processActiveWeatherEffects(World world, WorldProgressionData data) {
        if (data.isHailActive()) {
            processHailEffects(world);
        }
    }

    /**
     * Process transition effects
     */
    private void processTransitionEffects(World world, WorldProgressionData data) {
        // Add visual effects during transitions
        if (random.nextInt(50) == 0) { // 2% chance per second
            createTransitionEffects(world, data);
        }
    }

    /**
     * Process thunderstorm effects (unchanged from original)
     */
    private void processThunderstormEffects(World world, WorldProgressionData data) {
        // Enhanced lightning effects during storms
        if (random.nextInt(200) == 0) { // Every ~10 seconds on average
            createEnhancedLightningEffects(world);
        }
    }

    /**
     * Create transition effects between weather types
     */
    private void createTransitionEffects(World world, WorldProgressionData data) {
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
        
        for (Player player : world.getPlayers()) {
            if (random.nextInt(3) != 0) continue; // Not every player every time

            Location loc = player.getLocation();
            
            // Create swirling particles to indicate change
            for (int i = 0; i < 10; i++) {
                double angle = (System.currentTimeMillis() / 100.0 + i * 36) % 360;
                double radians = Math.toRadians(angle);
                double radius = 5 + Math.sin(System.currentTimeMillis() / 1000.0) * 2;
                
                Location particleLoc = loc.clone().add(
                    Math.cos(radians) * radius,
                    10 + Math.sin(System.currentTimeMillis() / 800.0 + i) * 3,
                    Math.sin(radians) * radius
                );
                
                Color transitionColor = getWeatherColor(currentWeather);
                Particle.DustOptions dustOptions = new Particle.DustOptions(transitionColor, 1.5f);
                
                player.spawnParticle(Particle.DUST, particleLoc, 1, 0.1, 0.1, 0.1, 0, dustOptions);
            }
        }
    }

    /**
     * Get color associated with weather type for effects
     */
    private Color getWeatherColor(WeatherForecast.WeatherType weather) {
        switch (weather) {
            case CLEAR:
                return Color.fromRGB(255, 255, 200); // Light yellow
            case LIGHT_RAIN:
            case HEAVY_RAIN:
                return Color.fromRGB(100, 150, 200); // Light blue
            case THUNDERSTORM:
                return Color.fromRGB(80, 80, 120); // Dark blue
            case SNOW:
            case BLIZZARD:
                return Color.fromRGB(255, 255, 255); // White
            case SANDSTORM:
                return Color.fromRGB(200, 150, 100); // Sandy brown
            default:
                return Color.fromRGB(150, 150, 150); // Gray
        }
    }

    // Helper methods
    private boolean isStormWeather(WeatherForecast.WeatherType weather) {
        return weather == WeatherForecast.WeatherType.THUNDERSTORM ||
               weather == WeatherForecast.WeatherType.BLIZZARD ||
               weather == WeatherForecast.WeatherType.SANDSTORM;
    }

    private boolean isRainWeather(WeatherForecast.WeatherType weather) {
        return weather == WeatherForecast.WeatherType.LIGHT_RAIN ||
               weather == WeatherForecast.WeatherType.HEAVY_RAIN ||
               weather == WeatherForecast.WeatherType.THUNDERSTORM;
    }

    // Notification methods
    private void notifyPlayersOfWeatherChange(World world, WeatherForecast.WeatherType from, WeatherForecast.WeatherType to) {
        if (!plugin.getConfig().getBoolean("notifications.weather_transition_notifications", true)) {
            return;
        }
        
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§6[OrbisClimate] §7Weather changing: §f" + 
                    from.getDisplayName() + " §7→ §f" + to.getDisplayName());
            }
        }
    }

    private void notifyPlayersOfUpcomingWeather(World world, WeatherForecast.WeatherType upcoming, int hoursAhead) {
        String timeDesc = hoursAhead == 1 ? "within the hour" : "in " + hoursAhead + " hours";
        
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§6[OrbisClimate] §7" + upcoming.getDisplayName() + 
                    " approaching " + timeDesc + "...");
            }
        }
    }

    // Original effect methods (unchanged)
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

    private void startHail(World world, WorldProgressionData data) {
        data.setHailActive(true);
        data.setHailStartTime(System.currentTimeMillis());

        // Notify players
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§f§l❄ Hail begins to fall from the stormy sky!");
            }
        }
    }

    private void stopHail(World world, WorldProgressionData data) {
        data.setHailActive(false);

        // Notify players
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§7§l❄ The hail subsides, returning to rain.");
            }
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

    private void createHailEffects(Player player) {
        Location loc = player.getLocation();

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

    // Public getters (updated to reflect new system)
    public WeatherProgression getWorldProgression(World world) {
        WorldProgressionData data = worldProgressionData.get(world);
        return data != null ? data.getCurrentProgression() : WeatherProgression.CLEAR;
    }

    public boolean isHailActive(World world) {
        WorldProgressionData data = worldProgressionData.get(world);
        return data != null && data.isHailActive();
    }
    
    public boolean isInTransition(World world) {
        WorldProgressionData data = worldProgressionData.get(world);
        return data != null && data.isInTransition();
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
        worldProgressionData.clear();
    }
}