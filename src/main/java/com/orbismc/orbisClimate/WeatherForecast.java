package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.api.SeasonsAPI;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WeatherForecast {

    public enum WeatherType {
        CLEAR("Clear", 0, 0),
        LIGHT_RAIN("Light Rain", 1, 0),
        HEAVY_RAIN("Heavy Rain", 2, 0),
        THUNDERSTORM("Thunderstorm", 2, 1),
        SNOW("Snow", 1, 0),
        BLIZZARD("Blizzard", 2, 0),
        SANDSTORM("Sandstorm", 0, 0);

        private final String displayName;
        private final int rainIntensity;
        private final int thunderIntensity;

        WeatherType(String displayName, int rainIntensity, int thunderIntensity) {
            this.displayName = displayName;
            this.rainIntensity = rainIntensity;
            this.thunderIntensity = thunderIntensity;
        }

        public String getDisplayName() { return displayName; }
        public int getRainIntensity() { return rainIntensity; }
        public int getThunderIntensity() { return thunderIntensity; }
    }

    public static class DailyForecast {
        private final WeatherType morningWeather;
        private final WeatherType afternoonWeather;
        private final WeatherType eveningWeather;
        private final WeatherType nightWeather;
        private final Date date;
        private final Season season;

        public DailyForecast(Date date, Season season, WeatherType morning, WeatherType afternoon,
                             WeatherType evening, WeatherType night) {
            this.date = date;
            this.season = season;
            this.morningWeather = morning;
            this.afternoonWeather = afternoon;
            this.eveningWeather = evening;
            this.nightWeather = night;
        }

        public WeatherType getCurrentWeather(int hour) {
            if (hour >= 6 && hour < 12) {
                return morningWeather;
            } else if (hour >= 12 && hour < 18) {
                return afternoonWeather;
            } else if (hour >= 18 && hour < 24) {
                return eveningWeather;
            } else {
                return nightWeather;
            }
        }

        public Date getDate() { return date; }
        public Season getSeason() { return season; }
        public WeatherType getMorningWeather() { return morningWeather; }
        public WeatherType getAfternoonWeather() { return afternoonWeather; }
        public WeatherType getEveningWeather() { return eveningWeather; }
        public WeatherType getNightWeather() { return nightWeather; }
    }

    // Weather state management per world
    public static class WorldWeatherState {
        private WeatherType currentWeather;
        private WeatherType targetWeather;
        private WeatherType lastAppliedWeather;
        private long weatherStartTime;
        private boolean weatherLocked;
        private long lastWeatherUpdate;

        public WorldWeatherState() {
            this.currentWeather = WeatherType.CLEAR;
            this.targetWeather = WeatherType.CLEAR;
            this.lastAppliedWeather = null;
            this.weatherStartTime = System.currentTimeMillis();
            this.weatherLocked = false;
            this.lastWeatherUpdate = 0;
        }

        // Getters and setters
        public WeatherType getCurrentWeather() { return currentWeather; }
        public void setCurrentWeather(WeatherType weather) { this.currentWeather = weather; }
        public WeatherType getTargetWeather() { return targetWeather; }
        public void setTargetWeather(WeatherType weather) { this.targetWeather = weather; }
        public WeatherType getLastAppliedWeather() { return lastAppliedWeather; }
        public void setLastAppliedWeather(WeatherType weather) { this.lastAppliedWeather = weather; }
        public long getWeatherStartTime() { return weatherStartTime; }
        public void setWeatherStartTime(long time) { this.weatherStartTime = time; }
        public boolean isWeatherLocked() { return weatherLocked; }
        public void setWeatherLocked(boolean locked) { this.weatherLocked = locked; }
        public long getLastWeatherUpdate() { return lastWeatherUpdate; }
        public void setLastWeatherUpdate(long time) { this.lastWeatherUpdate = time; }
    }

    private final OrbisClimate plugin;
    private final Random random;
    private final Map<World, DailyForecast> worldForecasts = new HashMap<>();
    private final Map<World, String> lastCheckedDate = new HashMap<>();
    private final Map<World, WorldWeatherState> worldWeatherStates = new HashMap<>();
    private SeasonsAPI seasonsAPI;
    private boolean realisticSeasonsEnabled = false;

    public WeatherForecast(OrbisClimate plugin) {
        this.plugin = plugin;
        this.random = new Random();

        // Check if RealisticSeasons is available
        if (Bukkit.getPluginManager().getPlugin("RealisticSeasons") != null) {
            try {
                seasonsAPI = SeasonsAPI.getInstance();
                realisticSeasonsEnabled = true;
                plugin.getLogger().info("RealisticSeasons integration enabled!");
            } catch (Exception e) {
                plugin.getLogger().warning("RealisticSeasons is installed but API is not available: " + e.getMessage());
                realisticSeasonsEnabled = false;
            }
        } else {
            plugin.getLogger().info("RealisticSeasons not found, using vanilla time system");
            realisticSeasonsEnabled = false;
        }
    }

    public void checkAndUpdateForecast(World world) {
        String currentDateKey = getCurrentDateKey(world);
        String lastDateKey = lastCheckedDate.get(world);

        // Check if we need to generate a new forecast
        if (lastDateKey == null || !currentDateKey.equals(lastDateKey)) {
            generateDailyForecast(world);
            lastCheckedDate.put(world, currentDateKey);

            if (lastDateKey != null) {
                announceDailyForecast(world);
            }
        }

        // Apply current weather based on forecast
        updateCurrentWeather(world);
    }

    private String getCurrentDateKey(World world) {
        if (realisticSeasonsEnabled) {
            Date date = seasonsAPI.getDate(world);
            return date.getYear() + "-" + date.getMonth() + "-" + date.getDay();
        } else {
            long currentDay = world.getFullTime() / 24000;
            return String.valueOf(currentDay);
        }
    }

    private void generateDailyForecast(World world) {
        Date currentDate = null;
        Season currentSeason = null;

        if (realisticSeasonsEnabled) {
            currentDate = seasonsAPI.getDate(world);
            currentSeason = seasonsAPI.getSeason(world);
        }

        // Generate weather for each time period
        WeatherType morning = generateWeatherType(currentSeason);
        WeatherType afternoon = generateWeatherType(currentSeason, morning);
        WeatherType evening = generateWeatherType(currentSeason, afternoon);
        WeatherType night = generateWeatherType(currentSeason, evening);

        DailyForecast forecast = new DailyForecast(currentDate, currentSeason, morning, afternoon, evening, night);
        worldForecasts.put(world, forecast);

        String dateStr = realisticSeasonsEnabled ?
                currentDate.getMonth() + "/" + currentDate.getDay() + "/" + currentDate.getYear() :
                getCurrentDateKey(world);
        String seasonStr = realisticSeasonsEnabled ? currentSeason.toString() : "N/A";
    }

    private WeatherType generateWeatherType(Season season) {
        return generateWeatherType(season, null);
    }

    private WeatherType generateWeatherType(Season season, WeatherType previousWeather) {
        double roll = random.nextDouble() * 100.0;

        double clearChance = 40.0;
        double lightPrecipChance = 30.0;
        double heavyPrecipChance = 20.0;
        double stormChance = 10.0;
        double sandstormChance = 5.0;

        if (season != null) {
            switch (season) {
                case WINTER:
                    clearChance = 20.0;
                    lightPrecipChance = 40.0;
                    heavyPrecipChance = 30.0;
                    stormChance = 10.0;
                    sandstormChance = 2.0;
                    break;
                case SPRING:
                    clearChance = 35.0;
                    lightPrecipChance = 35.0;
                    heavyPrecipChance = 20.0;
                    stormChance = 10.0;
                    sandstormChance = 5.0;
                    break;
                case SUMMER:
                    clearChance = 50.0;
                    lightPrecipChance = 20.0;
                    heavyPrecipChance = 10.0;
                    stormChance = 10.0;
                    sandstormChance = 15.0;
                    break;
                case FALL:
                    clearChance = 30.0;
                    lightPrecipChance = 40.0;
                    heavyPrecipChance = 25.0;
                    stormChance = 5.0;
                    sandstormChance = 3.0;
                    break;
            }
        }

        // Add continuity with previous weather
        if (previousWeather != null && random.nextDouble() < 0.3) {
            return previousWeather;
        }

        double cumulative = 0;

        cumulative += clearChance;
        if (roll < cumulative) {
            return WeatherType.CLEAR;
        }

        cumulative += lightPrecipChance;
        if (roll < cumulative) {
            if (season == Season.WINTER && random.nextDouble() < 0.7) {
                return WeatherType.SNOW;
            } else if (season == Season.SUMMER && random.nextDouble() < 0.3) {
                return WeatherType.SANDSTORM;
            }
            return WeatherType.LIGHT_RAIN;
        }

        cumulative += heavyPrecipChance;
        if (roll < cumulative) {
            if (season == Season.WINTER && random.nextDouble() < 0.4) {
                return WeatherType.BLIZZARD;
            } else if (season == Season.SUMMER && random.nextDouble() < 0.2) {
                return WeatherType.SANDSTORM;
            }
            return WeatherType.HEAVY_RAIN;
        }

        cumulative += stormChance;
        if (roll < cumulative) {
            if (season == Season.WINTER && random.nextDouble() < 0.7) {
                return WeatherType.BLIZZARD;
            }
            return WeatherType.THUNDERSTORM;
        }

        return WeatherType.SANDSTORM;
    }

    // OPTIMIZED: Modified weather application to prevent redundant API calls
    private void updateCurrentWeather(World world) {
        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        // Check if weather is manually locked by admin
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null && state.isWeatherLocked()) {
            // Don't change weather if it's been manually set
            return;
        }

        int currentHour = getCurrentHour(world);
        WeatherType targetWeather = forecast.getCurrentWeather(currentHour);

        if (state == null) {
            state = new WorldWeatherState();
            worldWeatherStates.put(world, state);
        }

        // Check if weather should change
        if (state.getTargetWeather() != targetWeather) {
            changeWeather(world, state, targetWeather);
        }

        // OPTIMIZATION: Only apply weather if it has actually changed
        WeatherType currentWeather = state.getCurrentWeather();
        if (currentWeather != state.getLastAppliedWeather()) {
            applyWeatherToWorld(world, currentWeather);
            state.setLastAppliedWeather(currentWeather);
            
            if (plugin.getConfig().getBoolean("debug.log_weather_optimizations", false)) {
                plugin.getLogger().info("Applied weather " + currentWeather.getDisplayName() + 
                    " to " + world.getName() + " (was " + 
                    (state.getLastAppliedWeather() != null ? state.getLastAppliedWeather().getDisplayName() : "null") + ")");
            }
        }
    }

    private void changeWeather(World world, WorldWeatherState state, WeatherType newWeather) {
        if (plugin.getConfig().getBoolean("debug.log_weather_transitions", false)) {
            plugin.getLogger().info("Weather changing in " + world.getName() + " from " + 
                state.getCurrentWeather().getDisplayName() + " to " + newWeather.getDisplayName());
        }

        state.setTargetWeather(newWeather);
        state.setCurrentWeather(newWeather);
        state.setWeatherStartTime(System.currentTimeMillis());

        // Apply the weather immediately and mark as applied
        applyWeatherToWorld(world, newWeather);
        state.setLastAppliedWeather(newWeather);

        // Notify players
        notifyPlayersOfWeatherChange(world, newWeather);
    }

    // UPDATED: Modified to prevent snow placement while keeping visual effects
    private void applyWeatherToWorld(World world, WeatherType weather) {
        boolean shouldRain = weather.getRainIntensity() > 0;
        boolean shouldThunder = weather.getThunderIntensity() > 0;

        // IMPORTANT: For snow/blizzard, we DON'T want to use the storm system
        // because that places actual snow blocks. Instead, we handle this purely
        // through our particle effects in BlizzardManager
        if (weather == WeatherType.SNOW || weather == WeatherType.BLIZZARD) {
            // Force clear weather in Minecraft but let our particle systems handle the visuals
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE); // Prevent vanilla weather
            world.setThunderDuration(0);
        } else {
            // Handle rain and thunder normally
            world.setStorm(shouldRain);
            world.setThundering(shouldThunder);
            
            if (shouldRain) {
                world.setWeatherDuration(Integer.MAX_VALUE); // We control duration, not vanilla
            } else {
                world.setWeatherDuration(0);
            }
            
            if (shouldThunder) {
                world.setThunderDuration(Integer.MAX_VALUE); // We control duration, not vanilla
            } else {
                world.setThunderDuration(0);
            }
        }

        // Optional debug logging
        if (plugin.getConfig().getBoolean("debug.log_weather_applications", false)) {
            if (weather == WeatherType.SNOW || weather == WeatherType.BLIZZARD) {
                plugin.getLogger().info("Set weather " + weather.getDisplayName() + 
                    " for " + world.getName() + " - Using particle effects only (no snow blocks)");
            } else {
                plugin.getLogger().info("Set weather " + weather.getDisplayName() + 
                    " for " + world.getName() + " - Storm: " + shouldRain + " | Thunder: " + shouldThunder);
            }
        }
    }

    private void notifyPlayersOfWeatherChange(World world, WeatherType newWeather) {
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§6[OrbisClimate] §7Weather changing to: §f" + newWeather.getDisplayName());
            }
        }
    }

    private int getCurrentHour(World world) {
        if (realisticSeasonsEnabled) {
            return seasonsAPI.getHours(world);
        } else {
            long timeOfDay = world.getTime() % 24000;
            return (int) ((timeOfDay + 6000) / 1000) % 24;
        }
    }

    private void announceDailyForecast(World world) {
        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        String dateStr = "Day " + getCurrentDateKey(world);
        String seasonStr = "";

        if (realisticSeasonsEnabled && forecast.getDate() != null) {
            Date date = forecast.getDate();
            dateStr = date.getMonth() + "/" + date.getDay() + "/" + date.getYear();
            seasonStr = " (" + forecast.getSeason().toString().toLowerCase() + ")";
        }

        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§6[OrbisClimate] §3Daily Weather Forecast - " + dateStr + seasonStr);
                player.sendMessage("§7Morning (6AM-12PM): §f" + forecast.getMorningWeather().getDisplayName());
                player.sendMessage("§7Afternoon (12PM-6PM): §f" + forecast.getAfternoonWeather().getDisplayName());
                player.sendMessage("§7Evening (6PM-12AM): §f" + forecast.getEveningWeather().getDisplayName());
                player.sendMessage("§7Night (12AM-6AM): §f" + forecast.getNightWeather().getDisplayName());
            }
        }
    }

    public DailyForecast getForecast(World world) {
        return worldForecasts.get(world);
    }

    public WeatherType getCurrentWeather(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null) {
            return state.getCurrentWeather();
        }

        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return WeatherType.CLEAR;
        return forecast.getCurrentWeather(getCurrentHour(world));
    }

    public void regenerateForecast(World world) {
        generateDailyForecast(world);
        announceDailyForecast(world);
        
        // Force weather update
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null) {
            state.setTargetWeather(WeatherType.CLEAR); // Force recalculation
            state.setLastAppliedWeather(null); // Force reapplication
        }
        updateCurrentWeather(world);
    }

    // Manual weather override (for admins)
    public void setWeather(World world, WeatherType weather, int durationMinutes) {
        WorldWeatherState state = worldWeatherStates.computeIfAbsent(world, k -> new WorldWeatherState());
        
        state.setCurrentWeather(weather);
        state.setTargetWeather(weather);
        state.setWeatherLocked(true);
        state.setWeatherStartTime(System.currentTimeMillis());
        state.setLastAppliedWeather(null); // Force reapplication
        
        applyWeatherToWorld(world, weather);
        state.setLastAppliedWeather(weather);
        
        plugin.getLogger().info("Manual weather override: " + world.getName() + 
            " set to " + weather.getDisplayName() + " for " + durationMinutes + " minutes");

        // Schedule unlock after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state.setWeatherLocked(false);
            plugin.getLogger().info("Weather lock expired for " + world.getName());
        }, durationMinutes * 60L * 20L); // Convert minutes to ticks
    }

    public void clearWeatherLock(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null) {
            state.setWeatherLocked(false);
            state.setLastAppliedWeather(null); // Force weather recalculation
            plugin.getLogger().info("Weather lock cleared for " + world.getName());
        }
    }

    // Performance monitoring methods
    public boolean hasWeatherChanged(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state == null) return true; // No state = needs initial setup
        
        return state.getCurrentWeather() != state.getLastAppliedWeather();
    }

    public String getWeatherDebugInfo(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state == null) return "No weather state for " + world.getName();
        
        return String.format("World: %s | Current: %s | Last Applied: %s | Locked: %s", 
            world.getName(),
            state.getCurrentWeather().getDisplayName(),
            state.getLastAppliedWeather() != null ? state.getLastAppliedWeather().getDisplayName() : "None",
            state.isWeatherLocked());
    }

    public boolean isRealisticSeasonsEnabled() {
        return realisticSeasonsEnabled;
    }

    public Season getCurrentSeason(World world) {
        if (realisticSeasonsEnabled) {
            return seasonsAPI.getSeason(world);
        }
        return null;
    }

    public Date getCurrentDate(World world) {
        if (realisticSeasonsEnabled) {
            return seasonsAPI.getDate(world);
        }
        return null;
    }

    // Shutdown method - re-enable vanilla weather
    public void shutdown() {
        worldWeatherStates.clear();
        worldForecasts.clear();
        lastCheckedDate.clear();
        
        // Re-enable vanilla weather when plugin shuts down
        if (plugin.getConfig().getBoolean("weather_control.restore_vanilla_on_shutdown", true)) {
            for (World world : Bukkit.getWorlds()) {
                world.setWeatherDuration(0); // Allow vanilla weather to resume
                world.setThunderDuration(0);
            }
        }
    }
}