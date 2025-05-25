package com.orbismc.orbisClimate;

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
        THUNDERSTORM("Thunderstorm", 2, 1);

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
        private final long dayNumber;

        public DailyForecast(long dayNumber, WeatherType morning, WeatherType afternoon,
                             WeatherType evening, WeatherType night) {
            this.dayNumber = dayNumber;
            this.morningWeather = morning;
            this.afternoonWeather = afternoon;
            this.eveningWeather = evening;
            this.nightWeather = night;
        }

        public WeatherType getCurrentWeather(long worldTime) {
            long timeOfDay = worldTime % 24000;

            if (timeOfDay >= 0 && timeOfDay < 6000) {        // 6AM - 12PM (Morning)
                return morningWeather;
            } else if (timeOfDay >= 6000 && timeOfDay < 12000) { // 12PM - 6PM (Afternoon)
                return afternoonWeather;
            } else if (timeOfDay >= 12000 && timeOfDay < 18000) { // 6PM - 12AM (Evening)
                return eveningWeather;
            } else {                                            // 12AM - 6AM (Night)
                return nightWeather;
            }
        }

        public long getDayNumber() { return dayNumber; }
        public WeatherType getMorningWeather() { return morningWeather; }
        public WeatherType getAfternoonWeather() { return afternoonWeather; }
        public WeatherType getEveningWeather() { return eveningWeather; }
        public WeatherType getNightWeather() { return nightWeather; }
    }

    private final OrbisClimate plugin;
    private final Random random;
    private final Map<World, DailyForecast> worldForecasts = new HashMap<>();
    private final Map<World, Long> lastCheckedDay = new HashMap<>();

    // Weather pattern chances (can be made configurable)
    private final double clearChance = 40.0;           // 40% chance of clear weather
    private final double lightRainChance = 30.0;       // 30% chance of light rain
    private final double heavyRainChance = 20.0;       // 20% chance of heavy rain
    private final double thunderstormChance = 10.0;    // 10% chance of thunderstorm

    public WeatherForecast(OrbisClimate plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public void checkAndUpdateForecast(World world) {
        long currentDay = world.getFullTime() / 24000; // Get current day number
        Long lastDay = lastCheckedDay.get(world);

        // Check if we need to generate a new forecast (new day or first time)
        if (lastDay == null || currentDay > lastDay) {
            generateDailyForecast(world, currentDay);
            lastCheckedDay.put(world, currentDay);

            // Announce new forecast if it's not the first time
            if (lastDay != null) {
                announceDailyForecast(world);
            }
        }

        // Apply current weather based on forecast
        applyCurrentWeather(world);
    }

    private void generateDailyForecast(World world, long dayNumber) {
        // Generate weather for each time period of the day
        WeatherType morning = generateWeatherType();
        WeatherType afternoon = generateWeatherType(morning); // Slight influence from morning
        WeatherType evening = generateWeatherType(afternoon); // Slight influence from afternoon
        WeatherType night = generateWeatherType(evening); // Slight influence from evening

        DailyForecast forecast = new DailyForecast(dayNumber, morning, afternoon, evening, night);
        worldForecasts.put(world, forecast);

        plugin.getLogger().info("Generated forecast for " + world.getName() + " Day " + dayNumber +
                ": Morning=" + morning.getDisplayName() +
                ", Afternoon=" + afternoon.getDisplayName() +
                ", Evening=" + evening.getDisplayName() +
                ", Night=" + night.getDisplayName());
    }

    private WeatherType generateWeatherType() {
        return generateWeatherType(null);
    }

    private WeatherType generateWeatherType(WeatherType previousWeather) {
        double roll = random.nextDouble() * 100.0;

        // If there's a previous weather type, add some continuity
        if (previousWeather != null) {
            // 30% chance to continue the same weather pattern
            if (random.nextDouble() < 0.3) {
                return previousWeather;
            }

            // Slight bias towards similar weather patterns
            if (previousWeather == WeatherType.CLEAR) {
                roll -= 10; // More likely to stay clear
            } else if (previousWeather == WeatherType.THUNDERSTORM) {
                roll += 20; // Less likely to have consecutive thunderstorms
            }
        }

        if (roll < clearChance) {
            return WeatherType.CLEAR;
        } else if (roll < clearChance + lightRainChance) {
            return WeatherType.LIGHT_RAIN;
        } else if (roll < clearChance + lightRainChance + heavyRainChance) {
            return WeatherType.HEAVY_RAIN;
        } else {
            return WeatherType.THUNDERSTORM;
        }
    }

    private void applyCurrentWeather(World world) {
        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        WeatherType currentWeather = forecast.getCurrentWeather(world.getTime());

        // Apply weather to the world
        boolean shouldRain = currentWeather.getRainIntensity() > 0;
        boolean shouldThunder = currentWeather.getThunderIntensity() > 0;

        // Only change weather if needed to avoid constant updates
        if (world.hasStorm() != shouldRain || world.isThundering() != shouldThunder) {
            world.setStorm(shouldRain);
            world.setThundering(shouldThunder);

            // Set weather duration (until next time period check)
            int ticksUntilNextPeriod = getTicksUntilNextTimePeriod(world.getTime());
            world.setWeatherDuration(ticksUntilNextPeriod);

            if (shouldThunder) {
                world.setThunderDuration(ticksUntilNextPeriod);
            }
        }
    }

    private int getTicksUntilNextTimePeriod(long currentTime) {
        long timeOfDay = currentTime % 24000;

        if (timeOfDay < 6000) {         // Morning -> transition at 12PM (6000)
            return (int) (6000 - timeOfDay);
        } else if (timeOfDay < 12000) { // Afternoon -> transition at 6PM (12000)
            return (int) (12000 - timeOfDay);
        } else if (timeOfDay < 18000) { // Evening -> transition at 12AM (18000)
            return (int) (18000 - timeOfDay);
        } else {                        // Night -> transition at 6AM (24000/0)
            return (int) (24000 - timeOfDay);
        }
    }

    private void announceDailyForecast(World world) {
        DailyForecast forecast = worldForecasts.get(world);
        if (forecast == null) return;

        // Announce to players with notification permission
        for (Player player : world.getPlayers()) {
            if (player.hasPermission("orbisclimate.notifications")) {
                player.sendMessage("§6[OrbisClimate] §3Daily Weather Forecast - Day " + forecast.getDayNumber());
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
        return forecast.getCurrentWeather(world.getTime());
    }

    public void regenerateForecast(World world) {
        long currentDay = world.getFullTime() / 24000;
        generateDailyForecast(world, currentDay);
        announceDailyForecast(world);
        applyCurrentWeather(world);
    }
}