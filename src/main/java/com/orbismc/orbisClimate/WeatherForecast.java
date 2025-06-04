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

    // Enhanced forecast that includes exact timing and transitions
    public static class DetailedForecast {
        private final Date date;
        private final Season season;
        private final Map<Integer, WeatherType> hourlyWeather; // Hour -> Weather mapping
        private final Map<Integer, Boolean> transitionHours; // Hours where transitions occur
        private final String forecastId;

        public DetailedForecast(Date date, Season season) {
            this.date = date;
            this.season = season;
            this.hourlyWeather = new HashMap<>();
            this.transitionHours = new HashMap<>();
            this.forecastId = generateForecastId(date);
        }

        private String generateForecastId(Date date) {
            if (date != null) {
                return date.getYear() + "-" + date.getMonth() + "-" + date.getDay();
            }
            return "vanilla-" + System.currentTimeMillis() / 86400000; // Day number
        }

        public void setWeatherForHour(int hour, WeatherType weather) {
            hourlyWeather.put(hour, weather);
        }

        public void setTransitionHour(int hour, boolean isTransition) {
            transitionHours.put(hour, isTransition);
        }

        public WeatherType getWeatherForHour(int hour) {
            return hourlyWeather.getOrDefault(hour, WeatherType.CLEAR);
        }

        public boolean isTransitionHour(int hour) {
            return transitionHours.getOrDefault(hour, false);
        }

        public Date getDate() { return date; }
        public Season getSeason() { return season; }
        public String getForecastId() { return forecastId; }

        // Legacy compatibility methods
        public WeatherType getMorningWeather() { return getWeatherForHour(9); }
        public WeatherType getAfternoonWeather() { return getWeatherForHour(15); }
        public WeatherType getEveningWeather() { return getWeatherForHour(21); }
        public WeatherType getNightWeather() { return getWeatherForHour(3); }

        public WeatherType getCurrentWeather(int hour) {
            return getWeatherForHour(hour);
        }
    }

    // Simplified world weather state - just tracks what the forecast says
    public static class WorldWeatherState {
        private WeatherType currentWeather;
        private WeatherType lastAppliedWeather;
        private boolean weatherLocked;
        private long lockExpirationTime;
        private String activeForecastId;
        private int lastProcessedHour;

        public WorldWeatherState() {
            this.currentWeather = WeatherType.CLEAR;
            this.lastAppliedWeather = null;
            this.weatherLocked = false;
            this.lockExpirationTime = 0;
            this.activeForecastId = null;
            this.lastProcessedHour = -1;
        }

        // Getters and setters
        public WeatherType getCurrentWeather() { return currentWeather; }
        public void setCurrentWeather(WeatherType weather) { this.currentWeather = weather; }
        public WeatherType getLastAppliedWeather() { return lastAppliedWeather; }
        public void setLastAppliedWeather(WeatherType weather) { this.lastAppliedWeather = weather; }
        public boolean isWeatherLocked() { return weatherLocked && System.currentTimeMillis() < lockExpirationTime; }
        public void setWeatherLocked(boolean locked, long durationMs) { 
            this.weatherLocked = locked; 
            this.lockExpirationTime = System.currentTimeMillis() + durationMs;
        }
        public void clearWeatherLock() { 
            this.weatherLocked = false; 
            this.lockExpirationTime = 0; 
        }
        public String getActiveForecastId() { return activeForecastId; }
        public void setActiveForecastId(String forecastId) { this.activeForecastId = forecastId; }
        public int getLastProcessedHour() { return lastProcessedHour; }
        public void setLastProcessedHour(int hour) { this.lastProcessedHour = hour; }
    }

    private final OrbisClimate plugin;
    private final Random random;
    private final Map<World, DetailedForecast> worldForecasts = new HashMap<>();
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
                plugin.getLogger().info("RealisticSeasons integration enabled - forecast will follow RealisticSeasons time!");
            } catch (Exception e) {
                plugin.getLogger().warning("RealisticSeasons is installed but API is not available: " + e.getMessage());
                realisticSeasonsEnabled = false;
            }
        } else {
            plugin.getLogger().info("RealisticSeasons not found, using vanilla time system for forecast");
            realisticSeasonsEnabled = false;
        }
    }

    public void checkAndUpdateForecast(World world) {
        WorldWeatherState state = worldWeatherStates.computeIfAbsent(world, k -> new WorldWeatherState());
        
        // Check if forecast needs regeneration
        boolean needsNewForecast = false;
        String currentForecastId = generateCurrentForecastId(world);
        
        DetailedForecast currentForecast = worldForecasts.get(world);
        if (currentForecast == null || !currentForecastId.equals(currentForecast.getForecastId())) {
            needsNewForecast = true;
        }

        // Generate new forecast if needed
        if (needsNewForecast) {
            generateDetailedForecast(world);
            announceDailyForecast(world);
        }

        // Apply weather strictly according to forecast
        applyForecastWeather(world, state);
    }

    private String generateCurrentForecastId(World world) {
        if (realisticSeasonsEnabled) {
            Date date = seasonsAPI.getDate(world);
            return date.getYear() + "-" + date.getMonth() + "-" + date.getDay();
        } else {
            long currentDay = world.getFullTime() / 24000;
            return "vanilla-" + currentDay;
        }
    }

    private void generateDetailedForecast(World world) {
        Date currentDate = null;
        Season currentSeason = null;

        if (realisticSeasonsEnabled) {
            currentDate = seasonsAPI.getDate(world);
            currentSeason = seasonsAPI.getSeason(world);
        }

        DetailedForecast forecast = new DetailedForecast(currentDate, currentSeason);
        
        // Generate hour-by-hour weather for the entire day
        WeatherType[] periodWeather = generatePeriodWeather(currentSeason);
        
        // Map periods to hours with transitions
        mapPeriodsToHours(forecast, periodWeather);
        
        worldForecasts.put(world, forecast);

        String dateStr = realisticSeasonsEnabled && currentDate != null ?
                currentDate.getMonth() + "/" + currentDate.getDay() + "/" + currentDate.getYear() :
                generateCurrentForecastId(world);
        String seasonStr = realisticSeasonsEnabled && currentSeason != null ? 
                currentSeason.toString() : "N/A";
        
        plugin.getLogger().info("Generated detailed forecast for " + world.getName() + 
            " (" + dateStr + ", " + seasonStr + ")");
        
        if (plugin.getConfig().getBoolean("debug.log_weather_transitions", false)) {
            logDetailedForecast(world, forecast);
        }
    }

    private WeatherType[] generatePeriodWeather(Season season) {
        // Generate 4 period weather types with better continuity
        WeatherType[] periods = new WeatherType[4]; // Morning, Afternoon, Evening, Night
        
        // Morning weather
        periods[0] = generateWeatherType(season, null, 0.0);
        
        // Afternoon weather (high continuity from morning)
        periods[1] = generateWeatherType(season, periods[0], 0.7);
        
        // Evening weather (medium continuity from afternoon)
        periods[2] = generateWeatherType(season, periods[1], 0.5);
        
        // Night weather (lower continuity, but consider morning for next day)
        periods[3] = generateWeatherType(season, periods[2], 0.3);
        
        return periods;
    }

    private void mapPeriodsToHours(DetailedForecast forecast, WeatherType[] periodWeather) {
        // Map each hour to a weather type and mark transition hours
        
        // Night (0-5): Use night weather
        for (int hour = 0; hour <= 5; hour++) {
            forecast.setWeatherForHour(hour, periodWeather[3]); // Night
        }
        
        // Morning transition (6): Transition from night to morning
        forecast.setWeatherForHour(6, periodWeather[0]); // Morning
        forecast.setTransitionHour(6, !periodWeather[3].equals(periodWeather[0]));
        
        // Morning (7-11): Morning weather
        for (int hour = 7; hour <= 11; hour++) {
            forecast.setWeatherForHour(hour, periodWeather[0]); // Morning
        }
        
        // Afternoon transition (12): Transition from morning to afternoon
        forecast.setWeatherForHour(12, periodWeather[1]); // Afternoon
        forecast.setTransitionHour(12, !periodWeather[0].equals(periodWeather[1]));
        
        // Afternoon (13-17): Afternoon weather
        for (int hour = 13; hour <= 17; hour++) {
            forecast.setWeatherForHour(hour, periodWeather[1]); // Afternoon
        }
        
        // Evening transition (18): Transition from afternoon to evening
        forecast.setWeatherForHour(18, periodWeather[2]); // Evening
        forecast.setTransitionHour(18, !periodWeather[1].equals(periodWeather[2]));
        
        // Evening (19-23): Evening weather
        for (int hour = 19; hour <= 23; hour++) {
            forecast.setWeatherForHour(hour, periodWeather[2]); // Evening
        }
        
        // Note: Night transition happens at hour 0 of next day
    }

    private void logDetailedForecast(World world, DetailedForecast forecast) {
        plugin.getLogger().info("Detailed 24-hour forecast for " + world.getName() + ":");
        for (int hour = 0; hour < 24; hour++) {
            WeatherType weather = forecast.getWeatherForHour(hour);
            boolean isTransition = forecast.isTransitionHour(hour);
            String transitionMarker = isTransition ? " [TRANSITION]" : "";
            plugin.getLogger().info("  " + String.format("%02d", hour) + ":00 - " + 
                weather.getDisplayName() + transitionMarker);
        }
    }

    private void applyForecastWeather(World world, WorldWeatherState state) {
        // Check if weather is manually locked
        if (state.isWeatherLocked()) {
            return;
        }

        DetailedForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        int currentHour = getCurrentHour(world);
        
        // Get the weather that the forecast says should be active right now
        WeatherType forecastWeather = forecast.getWeatherForHour(currentHour);
        boolean isTransitionHour = forecast.isTransitionHour(currentHour);
        
        // Check if hour has changed or weather needs to be updated
        boolean hourChanged = state.getLastProcessedHour() != currentHour;
        boolean weatherChanged = !forecastWeather.equals(state.getCurrentWeather());
        
        if (hourChanged || weatherChanged) {
            state.setLastProcessedHour(currentHour);
            
            if (weatherChanged) {
                // Weather is changing according to forecast
                WeatherType previousWeather = state.getCurrentWeather();
                state.setCurrentWeather(forecastWeather);
                
                if (plugin.getConfig().getBoolean("debug.log_weather_transitions", false)) {
                    String transitionInfo = isTransitionHour ? " (forecast transition hour)" : "";
                    plugin.getLogger().info("Forecast weather change in " + world.getName() + 
                        " at hour " + currentHour + ": " + previousWeather.getDisplayName() + 
                        " -> " + forecastWeather.getDisplayName() + transitionInfo);
                }
                
                // Notify players if this is a transition hour
                if (isTransitionHour) {
                    notifyPlayersOfWeatherTransition(world, previousWeather, forecastWeather);
                }
            }
        }

        // Apply the current forecast weather to the world
        if (!forecastWeather.equals(state.getLastAppliedWeather())) {
            applyWeatherToWorld(world, forecastWeather);
            state.setLastAppliedWeather(forecastWeather);
        }
    }

    private WeatherType generateWeatherType(Season season, WeatherType previousWeather, double continuityChance) {
        // Check continuity first
        if (previousWeather != null && random.nextDouble() < continuityChance) {
            return previousWeather;
        }

        // Generate new weather based on season
        double roll = random.nextDouble() * 100.0;

        // Season-based weather probabilities
        double clearChance = 45.0;
        double lightPrecipChance = 25.0;
        double heavyPrecipChance = 15.0;
        double stormChance = 10.0;
        double specialChance = 5.0; // Snow/sandstorm

        if (season != null) {
            switch (season) {
                case WINTER:
                    clearChance = 25.0;
                    lightPrecipChance = 30.0;
                    heavyPrecipChance = 25.0;
                    stormChance = 10.0;
                    specialChance = 10.0;
                    break;
                case SPRING:
                    clearChance = 40.0;
                    lightPrecipChance = 35.0;
                    heavyPrecipChance = 15.0;
                    stormChance = 8.0;
                    specialChance = 2.0;
                    break;
                case SUMMER:
                    clearChance = 55.0;
                    lightPrecipChance = 15.0;
                    heavyPrecipChance = 10.0;
                    stormChance = 15.0;
                    specialChance = 5.0;
                    break;
                case FALL:
                    clearChance = 35.0;
                    lightPrecipChance = 35.0;
                    heavyPrecipChance = 20.0;
                    stormChance = 8.0;
                    specialChance = 2.0;
                    break;
            }
        }

        double cumulative = 0;

        cumulative += clearChance;
        if (roll < cumulative) {
            return WeatherType.CLEAR;
        }

        cumulative += lightPrecipChance;
        if (roll < cumulative) {
            if (season == Season.WINTER && random.nextDouble() < 0.6) {
                return WeatherType.SNOW;
            }
            return WeatherType.LIGHT_RAIN;
        }

        cumulative += heavyPrecipChance;
        if (roll < cumulative) {
            if (season == Season.WINTER && random.nextDouble() < 0.4) {
                return WeatherType.BLIZZARD;
            }
            return WeatherType.HEAVY_RAIN;
        }

        cumulative += stormChance;
        if (roll < cumulative) {
            if (season == Season.WINTER && random.nextDouble() < 0.5) {
                return WeatherType.BLIZZARD;
            }
            return WeatherType.THUNDERSTORM;
        }

        // Special weather
        if (season == Season.SUMMER && random.nextDouble() < 0.7) {
            return WeatherType.SANDSTORM;
        }
        return WeatherType.SNOW;
    }

    private void applyWeatherToWorld(World world, WeatherType weather) {
        boolean shouldRain = weather.getRainIntensity() > 0;
        boolean shouldThunder = weather.getThunderIntensity() > 0;

        // For snow/blizzard, use particle effects only (no vanilla snow placement)
        if (weather == WeatherType.SNOW || weather == WeatherType.BLIZZARD) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
            world.setThunderDuration(0);
        } else {
            world.setStorm(shouldRain);
            world.setThundering(shouldThunder);
            
            if (shouldRain) {
                world.setWeatherDuration(Integer.MAX_VALUE);
            } else {
                world.setWeatherDuration(0);
            }
            
            if (shouldThunder) {
                world.setThunderDuration(Integer.MAX_VALUE);
            } else {
                world.setThunderDuration(0);
            }
        }

        if (plugin.getConfig().getBoolean("debug.log_weather_applications", false)) {
            plugin.getLogger().info("Applied forecast weather " + weather.getDisplayName() + 
                " to " + world.getName() + " - Storm: " + shouldRain + " | Thunder: " + shouldThunder);
        }
    }

    private void notifyPlayersOfWeatherTransition(World world, WeatherType from, WeatherType to) {
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

    public int getCurrentHour(World world) {
        if (realisticSeasonsEnabled) {
            return seasonsAPI.getHours(world);
        } else {
            // Convert Minecraft time to hour (0-23)
            long timeOfDay = world.getTime() % 24000;
            return (int) ((timeOfDay + 6000) / 1000) % 24;
        }
    }

    private void announceDailyForecast(World world) {
        DetailedForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        String dateStr = forecast.getForecastId();
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

    // NEW: Check if current hour is a transition hour for progression system
    public boolean isTransitionHour(World world) {
        DetailedForecast forecast = worldForecasts.get(world);
        if (forecast == null) return false;
        
        int currentHour = getCurrentHour(world);
        return forecast.isTransitionHour(currentHour);
    }

    // NEW: Get upcoming weather for progression pre-warnings
    public WeatherType getUpcomingWeather(World world, int hoursAhead) {
        DetailedForecast forecast = worldForecasts.get(world);
        if (forecast == null) return WeatherType.CLEAR;
        
        int currentHour = getCurrentHour(world);
        int futureHour = (currentHour + hoursAhead) % 24;
        return forecast.getWeatherForHour(futureHour);
    }

    // NEW: Get next transition hour for progression system
    public int getNextTransitionHour(World world) {
        DetailedForecast forecast = worldForecasts.get(world);
        if (forecast == null) return -1;
        
        int currentHour = getCurrentHour(world);
        
        // Look ahead for next transition
        for (int hour = currentHour + 1; hour < 24; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return hour;
            }
        }
        
        // Check next day (wrap around)
        for (int hour = 0; hour <= currentHour; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return hour;
            }
        }
        
        return -1; // No transitions found
    }

    // NEW: Get weather type at next transition for progression system
    public WeatherType getNextTransitionWeather(World world) {
        int nextTransitionHour = getNextTransitionHour(world);
        if (nextTransitionHour == -1) return null;
        
        DetailedForecast forecast = worldForecasts.get(world);
        if (forecast == null) return null;
        
        return forecast.getWeatherForHour(nextTransitionHour);
    }

    // NEW: Get hours until next transition for progression warnings
    public int getHoursUntilNextTransition(World world) {
        int nextTransitionHour = getNextTransitionHour(world);
        if (nextTransitionHour == -1) return -1;
        
        int currentHour = getCurrentHour(world);
        
        if (nextTransitionHour > currentHour) {
            return nextTransitionHour - currentHour;
        } else {
            // Next day
            return (24 - currentHour) + nextTransitionHour;
        }
    }

    // Public interface methods
    public DetailedForecast getForecast(World world) {
        return worldForecasts.get(world);
    }
    
    // Backward compatibility method for DailyForecast interface
    public DailyForecast getDailyForecast(World world) {
        DetailedForecast detailed = worldForecasts.get(world);
        if (detailed == null) return null;
        return new DailyForecast(detailed);
    }
    
    // Wrapper class for backward compatibility
    public static class DailyForecast {
        private final DetailedForecast detailed;
        
        public DailyForecast(DetailedForecast detailed) {
            this.detailed = detailed;
        }
        
        public WeatherType getMorningWeather() { return detailed.getMorningWeather(); }
        public WeatherType getAfternoonWeather() { return detailed.getAfternoonWeather(); }
        public WeatherType getEveningWeather() { return detailed.getEveningWeather(); }
        public WeatherType getNightWeather() { return detailed.getNightWeather(); }
        public Date getDate() { return detailed.getDate(); }
        public Season getSeason() { return detailed.getSeason(); }
        
        public WeatherType getCurrentWeather(int hour) {
            return detailed.getCurrentWeather(hour);
        }
    }

    public WeatherType getCurrentWeather(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null) {
            return state.getCurrentWeather();
        }

        DetailedForecast forecast = worldForecasts.get(world);
        if (forecast == null) return WeatherType.CLEAR;
        return forecast.getWeatherForHour(getCurrentHour(world));
    }

    public void regenerateForecast(World world) {
        plugin.getLogger().info("Regenerating forecast for " + world.getName() + " (forced)");
        generateDetailedForecast(world);
        announceDailyForecast(world);
        
        // Reset state to force immediate application
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null) {
            state.setLastProcessedHour(-1);
            state.setLastAppliedWeather(null);
        }
    }

    // Manual weather override (for admins) - overrides forecast temporarily
    public void setWeather(World world, WeatherType weather, int durationMinutes) {
        WorldWeatherState state = worldWeatherStates.computeIfAbsent(world, k -> new WorldWeatherState());
        
        state.setCurrentWeather(weather);
        state.setWeatherLocked(true, durationMinutes * 60L * 1000L);
        state.setLastAppliedWeather(null);
        
        applyWeatherToWorld(world, weather);
        state.setLastAppliedWeather(weather);
        
        plugin.getLogger().info("Manual weather override: " + world.getName() + 
            " set to " + weather.getDisplayName() + " for " + durationMinutes + " minutes (overriding forecast)");
    }

    public void clearWeatherLock(World world) {
        WorldWeatherState state = worldWeatherStates.get(world);
        if (state != null) {
            state.clearWeatherLock();
            state.setLastAppliedWeather(null);
            state.setLastProcessedHour(-1); // Force re-evaluation
            plugin.getLogger().info("Weather lock cleared for " + world.getName() + " - returning to forecast");
        }
    }

    // Integration getters
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

    // Debug and monitoring
    public String getDetailedWeatherInfo(World world) {
        DetailedForecast forecast = worldForecasts.get(world);
        WorldWeatherState state = worldWeatherStates.get(world);
        
        if (forecast == null || state == null) {
            return "No forecast data available for " + world.getName();
        }
        
        int currentHour = getCurrentHour(world);
        WeatherType forecastWeather = forecast.getWeatherForHour(currentHour);
        boolean isTransitionHour = forecast.isTransitionHour(currentHour);
        
        StringBuilder info = new StringBuilder();
        info.append("=== Weather Debug for ").append(world.getName()).append(" ===\n");
        info.append("Current Hour: ").append(currentHour).append(":00\n");
        info.append("Forecast ID: ").append(forecast.getForecastId()).append("\n");
        info.append("Current Weather: ").append(state.getCurrentWeather().getDisplayName()).append("\n");
        info.append("Forecast Weather: ").append(forecastWeather.getDisplayName()).append("\n");
        info.append("Is Transition Hour: ").append(isTransitionHour).append("\n");
        info.append("Weather Locked: ").append(state.isWeatherLocked()).append("\n");
        info.append("Last Applied: ").append(state.getLastAppliedWeather() != null ? 
            state.getLastAppliedWeather().getDisplayName() : "None").append("\n");
        
        return info.toString();
    }

    public void shutdown() {
        worldWeatherStates.clear();
        worldForecasts.clear();
        
        if (plugin.getConfig().getBoolean("weather_control.restore_vanilla_on_shutdown", true)) {
            for (World world : Bukkit.getWorlds()) {
                world.setWeatherDuration(0);
                world.setThunderDuration(0);
            }
        }
    }
}