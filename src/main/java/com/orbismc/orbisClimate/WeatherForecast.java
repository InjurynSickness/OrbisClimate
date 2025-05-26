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
        BLIZZARD("Blizzard", 2, 0);

        private final String displayName;
        private final int rainIntensity; // 0 = no rain, 1 = light, 2 = heavy
        private final int thunderIntensity; // 0 = no thunder, 1 = thunder

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
        private final WeatherType morningWeather;    // 6AM - 12PM
        private final WeatherType afternoonWeather;  // 12PM - 6PM
        private final WeatherType eveningWeather;    // 6PM - 12AM
        private final WeatherType nightWeather;      // 12AM - 6AM
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
            if (hour >= 6 && hour < 12) {        // 6AM - 12PM (Morning)
                return morningWeather;
            } else if (hour >= 12 && hour < 18) { // 12PM - 6PM (Afternoon)
                return afternoonWeather;
            } else if (hour >= 18 && hour < 24) { // 6PM - 12AM (Evening)
                return eveningWeather;
            } else {                              // 12AM - 6AM (Night)
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

    private final OrbisClimate plugin;
    private final Random random;
    private final Map<World, DailyForecast> worldForecasts = new HashMap<>();
    private final Map<World, String> lastCheckedDate = new HashMap<>();
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

        // Check if we need to generate a new forecast (new day or first time)
        if (lastDateKey == null || !currentDateKey.equals(lastDateKey)) {
            generateDailyForecast(world);
            lastCheckedDate.put(world, currentDateKey);

            // Announce new forecast if it's not the first time
            if (lastDateKey != null) {
                announceDailyForecast(world);
            }
        }

        // Apply current weather based on forecast
        applyCurrentWeather(world);
    }

    private String getCurrentDateKey(World world) {
        if (realisticSeasonsEnabled) {
            Date date = seasonsAPI.getDate(world);
            return date.getYear() + "-" + date.getMonth() + "-" + date.getDay();
        } else {
            // Fallback to vanilla time
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

        // Generate weather for each time period of the day
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

        plugin.getLogger().info("Generated forecast for " + world.getName() +
                " (" + dateStr + ", " + seasonStr + "): " +
                "Morning=" + morning.getDisplayName() +
                ", Afternoon=" + afternoon.getDisplayName() +
                ", Evening=" + evening.getDisplayName() +
                ", Night=" + night.getDisplayName());
    }

    private WeatherType generateWeatherType(Season season) {
        return generateWeatherType(season, null);
    }

    private WeatherType generateWeatherType(Season season, WeatherType previousWeather) {
        double roll = random.nextDouble() * 100.0;

        // Base weather chances
        double clearChance = 40.0;
        double lightPrecipChance = 30.0;
        double heavyPrecipChance = 20.0;
        double stormChance = 10.0;

        // Modify chances based on season (if RealisticSeasons is available)
        if (season != null) {
            switch (season) {
                case WINTER:
                    clearChance = 20.0;        // Less clear weather in winter
                    lightPrecipChance = 40.0;  // More light precipitation
                    heavyPrecipChance = 30.0;  // More heavy precipitation
                    stormChance = 10.0;
                    break;
                case SPRING:
                    clearChance = 35.0;
                    lightPrecipChance = 35.0;  // Moderate rain in spring
                    heavyPrecipChance = 20.0;
                    stormChance = 10.0;
                    break;
                case SUMMER:
                    clearChance = 60.0;        // More clear weather in summer
                    lightPrecipChance = 20.0;
                    heavyPrecipChance = 10.0;
                    stormChance = 10.0;        // But still thunderstorms
                    break;
                case FALL: // Note: RealisticSeasons uses FALL, not AUTUMN
                    clearChance = 30.0;
                    lightPrecipChance = 40.0;  // Lots of rain in autumn
                    heavyPrecipChance = 25.0;
                    stormChance = 5.0;         // Less thunderstorms
                    break;
            }
        }

        // Add continuity with previous weather
        if (previousWeather != null && random.nextDouble() < 0.3) {
            return previousWeather; // 30% chance to continue same weather
        }

        // Generate weather type
        if (roll < clearChance) {
            return WeatherType.CLEAR;
        } else if (roll < clearChance + lightPrecipChance) {
            // Choose between rain and snow based on season
            if (season == Season.WINTER && random.nextDouble() < 0.7) {
                return WeatherType.SNOW;
            }
            return WeatherType.LIGHT_RAIN;
        } else if (roll < clearChance + lightPrecipChance + heavyPrecipChance) {
            // Choose between heavy rain and blizzard based on season
            if (season == Season.WINTER && random.nextDouble() < 0.4) {
                return WeatherType.BLIZZARD;
            }
            return WeatherType.HEAVY_RAIN;
        } else {
            // Thunderstorms are rare in winter
            if (season == Season.WINTER && random.nextDouble() < 0.7) {
                return WeatherType.BLIZZARD;
            }
            return WeatherType.THUNDERSTORM;
        }
    }

    private void applyCurrentWeather(World world) {
        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        int currentHour = getCurrentHour(world);
        WeatherType currentWeather = forecast.getCurrentWeather(currentHour);

        // Apply weather to the world
        boolean shouldRain = currentWeather.getRainIntensity() > 0;
        boolean shouldThunder = currentWeather.getThunderIntensity() > 0;

        // Handle snow vs rain based on weather type
        if (currentWeather == WeatherType.SNOW || currentWeather == WeatherType.BLIZZARD) {
            // For snow, we still set storm=true but the biome temperature will determine if it's snow
            shouldRain = true;
        }

        // Only change weather if needed to avoid constant updates
        if (world.hasStorm() != shouldRain || world.isThundering() != shouldThunder) {
            world.setStorm(shouldRain);
            world.setThundering(shouldThunder);

            // Set weather duration (until next time period check)
            int ticksUntilNextPeriod = getTicksUntilNextTimePeriod(currentHour);
            world.setWeatherDuration(ticksUntilNextPeriod);

            if (shouldThunder) {
                world.setThunderDuration(ticksUntilNextPeriod);
            }
        }
    }

    private int getCurrentHour(World world) {
        if (realisticSeasonsEnabled) {
            return seasonsAPI.getHours(world);
        } else {
            // Fallback to vanilla time conversion
            long timeOfDay = world.getTime() % 24000;
            return (int) ((timeOfDay + 6000) / 1000) % 24; // Convert MC time to 24-hour format
        }
    }

    private int getTicksUntilNextTimePeriod(int currentHour) {
        int nextTransitionHour;

        if (currentHour < 6) {
            nextTransitionHour = 6;  // Next transition at 6AM
        } else if (currentHour < 12) {
            nextTransitionHour = 12; // Next transition at 12PM
        } else if (currentHour < 18) {
            nextTransitionHour = 18; // Next transition at 6PM
        } else {
            nextTransitionHour = 24; // Next transition at 12AM (next day)
        }

        int hoursUntilTransition = (nextTransitionHour - currentHour) % 24;

        if (realisticSeasonsEnabled) {
            // RealisticSeasons uses real-time or custom time scale
            // For now, assume 1 hour = 1200 ticks (1 minute real time)
            // This should be configurable based on RealisticSeasons settings
            return hoursUntilTransition * 1200;
        } else {
            // Vanilla MC: 1 hour = 1000 ticks
            return hoursUntilTransition * 1000;
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

        // Announce to players with notification permission
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
        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return WeatherType.CLEAR;
        return forecast.getCurrentWeather(getCurrentHour(world));
    }

    public void regenerateForecast(World world) {
        generateDailyForecast(world);
        announceDailyForecast(world);
        applyCurrentWeather(world);
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
}