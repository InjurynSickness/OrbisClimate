package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.api.SeasonsAPI;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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

    // NEW: Weather state management per world
    public static class WorldWeatherState {
        private WeatherType currentWeather;
        private WeatherType targetWeather;
        private long weatherStartTime;
        private long weatherDuration;
        private boolean weatherLocked;
        private long lastWeatherUpdate;

        public WorldWeatherState() {
            this.currentWeather = WeatherType.CLEAR;
            this.targetWeather = WeatherType.CLEAR;
            this.weatherStartTime = System.currentTimeMillis();
            this.weatherDuration = 0;
            this.weatherLocked = false;
            this.lastWeatherUpdate = 0;
        }

        // Getters and setters
        public WeatherType getCurrentWeather() { return currentWeather; }
        public void setCurrentWeather(WeatherType weather) { this.currentWeather = weather; }
        public WeatherType getTargetWeather() { return targetWeather; }
        public void setTargetWeather(WeatherType weather) { this.targetWeather = weather; }
        public long getWeatherStartTime() { return weatherStartTime; }
        public void setWeatherStartTime(long time) { this.weatherStartTime = time; }
        public long getWeatherDuration() { return weatherDuration; }
        public void setWeatherDuration(long duration) { this.weatherDuration = duration; }
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
    private BukkitTask weatherMaintenanceTask;

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

        // NEW: Start weather maintenance task
        startWeatherMaintenanceTask();
    }

    // NEW: Weather maintenance task to prevent weather from stopping unexpectedly
    private void startWeatherMaintenanceTask() {
        weatherMaintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                maintainWorldWeather(world);
            }
        }, 0L, 100L); // Every 5 seconds
    }

    private void maintainWorldWeather(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state == null) {
            state = new WorldWeatherState();
            worldWeatherStates.put(world, state);
        }

        long currentTime = System.currentTimeMillis();
        WeatherType currentWeather = state.getCurrentWeather();

        // Check if weather should still be active
        if (currentWeather != WeatherType.CLEAR && currentWeather != WeatherType.SANDSTORM) {
            boolean shouldHaveRain = currentWeather.getRainIntensity() > 0;
            boolean shouldHaveThunder = currentWeather.getThunderIntensity() > 0;

            // Force weather to stay if it's not matching what it should be
            if (world.hasStorm() != shouldHaveRain || world.isThundering() != shouldHaveThunder) {
                plugin.getLogger().info("Correcting weather for world " + world.getName() + 
                    " - Expected: " + currentWeather.getDisplayName() + 
                    " | Storm: " + shouldHaveRain + " | Thunder: " + shouldHaveThunder);
                
                applyWeatherToWorld(world, currentWeather, true);
            }

            // Extend weather duration if needed
            if (world.getWeatherDuration() < 200) { // Less than 10 seconds remaining
                int newDuration = calculateWeatherDuration(currentWeather);
                world.setWeatherDuration(newDuration);
                if (shouldHaveThunder && world.getThunderDuration() < 200) {
                    world.setThunderDuration(newDuration);
                }
            }
        }

        state.setLastWeatherUpdate(currentTime);
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

    // NEW: Improved weather application system
    private void updateCurrentWeather(World world) {
        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        int currentHour = getCurrentHour(world);
        WeatherType targetWeather = forecast.getCurrentWeather(currentHour);

        WorldWeatherState state = worldWeatherStates.computeIfAbsent(world, k -> new WorldWeatherState());

        // Check if weather should change
        if (state.getTargetWeather() != targetWeather) {
            changeWeather(world, state, targetWeather);
        }

        // Ensure current weather is properly applied
        applyWeatherToWorld(world, state.getCurrentWeather(), false);
    }

    private void changeWeather(World world, WorldWeatherState state, WeatherType newWeather) {
        plugin.getLogger().info("Weather changing in " + world.getName() + " from " + 
            state.getCurrentWeather().getDisplayName() + " to " + newWeather.getDisplayName());

        state.setTargetWeather(newWeather);
        state.setCurrentWeather(newWeather);
        state.setWeatherStartTime(System.currentTimeMillis());
        
        // Calculate how long this weather should last
        long duration = calculateWeatherDurationMillis(newWeather);
        state.setWeatherDuration(duration);

        // Apply the weather immediately
        applyWeatherToWorld(world, newWeather, true);

        // Notify players
        notifyPlayersOfWeatherChange(world, newWeather);
    }

    private void applyWeatherToWorld(World world, WeatherType weather, boolean force) {
        boolean shouldRain = weather.getRainIntensity() > 0;
        boolean shouldThunder = weather.getThunderIntensity() > 0;

        // Handle snow vs rain
        if (weather == WeatherType.SNOW || weather == WeatherType.BLIZZARD) {
            shouldRain = true; // Snow still uses the storm system
        }

        // Only update if needed or forced
        if (force || world.hasStorm() != shouldRain || world.isThundering() != shouldThunder) {
            world.setStorm(shouldRain);
            world.setThundering(shouldThunder);

            // Set appropriate duration
            int duration = calculateWeatherDuration(weather);
            
            if (shouldRain) {
                world.setWeatherDuration(duration);
            }
            
            if (shouldThunder) {
                world.setThunderDuration(duration);
            }

            plugin.getLogger().info("Applied weather " + weather.getDisplayName() + 
                " to " + world.getName() + " - Storm: " + shouldRain + 
                " | Thunder: " + shouldThunder + " | Duration: " + (duration / 20) + "s");
        }
    }

    private int calculateWeatherDuration(WeatherType weather) {
        // Base duration in ticks (20 ticks = 1 second)
        int baseDuration;
        
        switch (weather) {
            case CLEAR:
                baseDuration = 12000; // 10 minutes
                break;
            case LIGHT_RAIN:
                baseDuration = 6000;  // 5 minutes
                break;
            case HEAVY_RAIN:
                baseDuration = 9600;  // 8 minutes
                break;
            case THUNDERSTORM:
                baseDuration = 7200;  // 6 minutes
                break;
            case SNOW:
                baseDuration = 8400;  // 7 minutes
                break;
            case BLIZZARD:
                baseDuration = 4800;  // 4 minutes
                break;
            case SANDSTORM:
                baseDuration = 3600;  // 3 minutes
                break;
            default:
                baseDuration = 6000;  // 5 minutes default
                break;
        }

        // Add some randomness (±25%)
        int variation = (int) (baseDuration * 0.25);
        return baseDuration + random.nextInt(variation * 2) - variation;
    }

    private long calculateWeatherDurationMillis(WeatherType weather) {
        return calculateWeatherDuration(weather) * 50L; // Convert ticks to milliseconds
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
        }
        updateCurrentWeather(world);
    }

    // NEW: Manual weather override (for admins)
    public void setWeather(World world, WeatherType weather, int durationMinutes) {
        WorldWeatherState state = worldWeatherStates.computeIfAbsent(world, k -> new WorldWeatherState());
        
        state.setCurrentWeather(weather);
        state.setTargetWeather(weather);
        state.setWeatherLocked(true);
        state.setWeatherDuration(durationMinutes * 60 * 1000L); // Convert to milliseconds
        state.setWeatherStartTime(System.currentTimeMillis());
        
        applyWeatherToWorld(world, weather, true);
        
        plugin.getLogger().info("Manual weather override: " + world.getName() + 
            " set to " + weather.getDisplayName() + " for " + durationMinutes + " minutes");
    }

    public void clearWeatherLock(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null) {
            state.setWeatherLocked(false);
            plugin.getLogger().info("Weather lock cleared for " + world.getName());
        }
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

    // NEW: Shutdown method
    public void shutdown() {
        if (weatherMaintenanceTask != null) {
            weatherMaintenanceTask.cancel();
        }
        worldWeatherStates.clear();
        worldForecasts.clear();
        lastCheckedDate.clear();
    }
}