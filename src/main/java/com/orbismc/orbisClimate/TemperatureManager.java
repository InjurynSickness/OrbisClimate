package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.api.SeasonsAPI;
import me.casperge.realisticseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TemperatureManager {

    public static class TemperatureLevel {
        public static final int SEVERE_COLD = -25;
        public static final int COLD = -10;
        public static final int MILD_COLD = 0;
        public static final int COMFORTABLE_MIN = 10;
        public static final int COMFORTABLE_MAX = 25;
        public static final int MILD_HEAT = 30;
        public static final int HOT = 40;
        public static final int SEVERE_HEAT = 50;
    }

    public static class PlayerTemperatureData {
        private double currentTemperature;
        private double targetTemperature;
        private long lastUpdateTime;
        private long lastEffectTime;
        private boolean isIndoors;

        public PlayerTemperatureData() {
            this.currentTemperature = 20.0; // Start at comfortable temperature
            this.targetTemperature = 20.0;
            this.lastUpdateTime = System.currentTimeMillis();
            this.lastEffectTime = 0;
            this.isIndoors = false;
        }

        // Getters and setters
        public double getCurrentTemperature() { return currentTemperature; }
        public void setCurrentTemperature(double temp) { this.currentTemperature = temp; }
        public double getTargetTemperature() { return targetTemperature; }
        public void setTargetTemperature(double temp) { this.targetTemperature = temp; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long time) { this.lastUpdateTime = time; }
        public long getLastEffectTime() { return lastEffectTime; }
        public void setLastEffectTime(long time) { this.lastEffectTime = time; }
        public boolean isIndoors() { return isIndoors; }
        public void setIndoors(boolean indoors) { this.isIndoors = indoors; }
    }

    private final OrbisClimate plugin;
    private final WeatherForecast weatherForecast;
    private final ClimateZoneManager climateZoneManager;
    private final WindManager windManager;
    private SeasonsAPI seasonsAPI;
    private boolean realisticSeasonsEnabled;

    // Configuration
    private boolean temperatureEnabled;
    private Map<ClimateZoneManager.ClimateZone, Double> baseTemperatures;
    private Map<String, List<PotionEffect>> temperatureEffects;
    private int effectCooldownTicks;

    // Runtime data
    private final Map<Player, PlayerTemperatureData> playerTemperatureData = new HashMap<>();
    private BukkitTask temperatureTask;

    public TemperatureManager(OrbisClimate plugin, WeatherForecast weatherForecast,
                              ClimateZoneManager climateZoneManager, WindManager windManager) {
        this.plugin = plugin;
        this.weatherForecast = weatherForecast;
        this.climateZoneManager = climateZoneManager;
        this.windManager = windManager;

        // Check for RealisticSeasons
        if (Bukkit.getPluginManager().getPlugin("RealisticSeasons") != null) {
            try {
                seasonsAPI = SeasonsAPI.getInstance();
                realisticSeasonsEnabled = true;
            } catch (Exception e) {
                realisticSeasonsEnabled = false;
            }
        }

        loadConfiguration();
        startTemperatureTask();
    }

    private void loadConfiguration() {
        temperatureEnabled = plugin.getConfig().getBoolean("temperature.enabled", true);
        effectCooldownTicks = 1200; // 60 seconds between effects

        // Load base temperatures
        baseTemperatures = new HashMap<>();
        baseTemperatures.put(ClimateZoneManager.ClimateZone.ARCTIC,
                plugin.getConfig().getDouble("temperature.base_temperatures.arctic", -15.0));
        baseTemperatures.put(ClimateZoneManager.ClimateZone.TEMPERATE,
                plugin.getConfig().getDouble("temperature.base_temperatures.temperate", 15.0));
        baseTemperatures.put(ClimateZoneManager.ClimateZone.DESERT,
                plugin.getConfig().getDouble("temperature.base_temperatures.desert", 35.0));

        // Load temperature effects
        loadTemperatureEffects();
    }

    private void loadTemperatureEffects() {
        temperatureEffects = new HashMap<>();

        // Load cold effects
        temperatureEffects.put("mild_cold", parseEffects(
                plugin.getConfig().getStringList("temperature.player_effects.cold.effects.mild_cold")));
        temperatureEffects.put("cold", parseEffects(
                plugin.getConfig().getStringList("temperature.player_effects.cold.effects.cold")));
        temperatureEffects.put("severe_cold", parseEffects(
                plugin.getConfig().getStringList("temperature.player_effects.cold.effects.severe_cold")));

        // Load heat effects
        temperatureEffects.put("mild_heat", parseEffects(
                plugin.getConfig().getStringList("temperature.player_effects.heat.effects.mild_heat")));
        temperatureEffects.put("hot", parseEffects(
                plugin.getConfig().getStringList("temperature.player_effects.heat.effects.hot")));
        temperatureEffects.put("severe_heat", parseEffects(
                plugin.getConfig().getStringList("temperature.player_effects.heat.effects.severe_heat")));
    }

    private List<PotionEffect> parseEffects(List<String> effectStrings) {
        List<PotionEffect> effects = new ArrayList<>();

        for (String effectString : effectStrings) {
            if (effectString.isEmpty()) continue;

            try {
                String[] parts = effectString.split(":");
                if (parts.length >= 3) {
                    PotionEffectType type = PotionEffectType.getByName(parts[0]);
                    int amplifier = Integer.parseInt(parts[1]);
                    int duration = Integer.parseInt(parts[2]);

                    if (type != null) {
                        effects.add(new PotionEffect(type, duration, amplifier, true, false));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid temperature effect format: " + effectString);
            }
        }

        return effects;
    }

    private void startTemperatureTask() {
        if (!temperatureEnabled) return;

        temperatureTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayerTemperature(player);
                applyTemperatureEffects(player);
            }
        }, 0L, 20L); // Update every second
    }

    private void updatePlayerTemperature(Player player) {
        PlayerTemperatureData data = playerTemperatureData.computeIfAbsent(player,
                k -> new PlayerTemperatureData());

        // Check if player is indoors
        boolean isIndoors = windManager.isPlayerIndoors(player);
        data.setIndoors(isIndoors);

        // Calculate target temperature
        double targetTemp = calculateTargetTemperature(player);
        data.setTargetTemperature(targetTemp);

        // Gradually adjust current temperature towards target
        double currentTemp = data.getCurrentTemperature();
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - data.getLastUpdateTime();

        if (timeDiff > 0) {
            // Temperature change rate (degrees per second)
            double changeRate = isIndoors ? 2.0 : 0.5; // Faster change indoors
            double maxChange = (timeDiff / 1000.0) * changeRate;

            double tempDifference = targetTemp - currentTemp;
            if (Math.abs(tempDifference) <= maxChange) {
                data.setCurrentTemperature(targetTemp);
            } else {
                double change = Math.signum(tempDifference) * maxChange;
                data.setCurrentTemperature(currentTemp + change);
            }
        }

        data.setLastUpdateTime(currentTime);
    }

    private double calculateTargetTemperature(Player player) {
        // Start with climate zone base temperature
        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);
        double baseTemp = baseTemperatures.getOrDefault(zone, 15.0);

        // If player is indoors, gradually move towards comfortable temperature
        if (windManager.isPlayerIndoors(player)) {
            return 20.0; // Comfortable indoor temperature
        }

        // Apply climate zone temperature modifier
        String worldName = player.getWorld().getName();
        // You could get the modifier from configuration here if needed

        // Apply weather modifiers
        WeatherForecast.WeatherType weather = climateZoneManager.getPlayerZoneWeather(player);
        double weatherModifier = getWeatherTemperatureModifier(weather, zone);
        baseTemp += weatherModifier;

        // Apply seasonal modifiers
        Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
        double seasonalModifier = getSeasonalTemperatureModifier(currentSeason, zone);
        baseTemp += seasonalModifier;

        // Apply drought modifier for desert zones
        if (zone == ClimateZoneManager.ClimateZone.DESERT &&
                climateZoneManager.isPlayerInDrought(player)) {
            baseTemp += plugin.getConfig().getDouble("drought.effects.temperature_bonus", 15.0);
        }

        // Apply time of day modifier (day/night cycle)
        double timeModifier = getTimeOfDayModifier(player);
        baseTemp += timeModifier;

        // Apply altitude modifier (higher = colder)
        double altitudeModifier = getAltitudeModifier(player);
        baseTemp += altitudeModifier;

        return baseTemp;
    }

    private double getWeatherTemperatureModifier(WeatherForecast.WeatherType weather, ClimateZoneManager.ClimateZone zone) {
        switch (weather) {
            case CLEAR:
                // Clear weather tends to be warmer during day, colder at night
                return zone == ClimateZoneManager.ClimateZone.DESERT ? 5.0 : 2.0;
            case LIGHT_RAIN:
                return -3.0; // Rain cools things down
            case HEAVY_RAIN:
                return -5.0;
            case THUNDERSTORM:
                return -7.0; // Storms bring cooler air
            case SNOW:
                return -8.0; // Snow is cold
            case BLIZZARD:
                return -15.0; // Blizzards are very cold
            case SANDSTORM:
                return zone == ClimateZoneManager.ClimateZone.DESERT ? 8.0 : 0.0; // Hot in deserts
            default:
                return 0.0;
        }
    }

    private double getSeasonalTemperatureModifier(Season season, ClimateZoneManager.ClimateZone zone) {
        if (season == null) return 0.0;

        double baseModifier;
        switch (season) {
            case WINTER:
                baseModifier = -10.0;
                break;
            case SPRING:
                baseModifier = -2.0;
                break;
            case SUMMER:
                baseModifier = 8.0;
                break;
            case FALL: // RealisticSeasons uses FALL
                baseModifier = 2.0;
                break;
            default:
                baseModifier = 0.0;
        }

        // Zones react differently to seasons
        switch (zone) {
            case ARCTIC:
                return baseModifier * 1.5; // More extreme in Arctic
            case DESERT:
                return baseModifier * 1.2; // Somewhat more extreme in Desert
            case TEMPERATE:
            default:
                return baseModifier; // Normal seasonal variation
        }
    }

    private double getTimeOfDayModifier(Player player) {
        long time = player.getWorld().getTime();

        // Convert MC time to hour (0-24)
        double hour = ((time + 6000) % 24000) / 1000.0;

        // Temperature varies throughout the day
        // Coldest at 6 AM (hour 6), warmest at 2 PM (hour 14)
        double timeRadians = ((hour - 6) / 24.0) * 2 * Math.PI;
        double modifier = Math.sin(timeRadians + Math.PI) * 5.0; // ±5 degrees variation

        return modifier;
    }

    private double getAltitudeModifier(Player player) {
        int y = player.getLocation().getBlockY();

        // Temperature drops with altitude: ~0.5°C per 100 blocks above sea level (y=62)
        if (y > 62) {
            return -((y - 62) / 100.0) * 0.5;
        }
        return 0.0;
    }

    private void applyTemperatureEffects(Player player) {
        if (!temperatureEnabled) return;

        PlayerTemperatureData data = playerTemperatureData.get(player);
        if (data == null) return;

        double temperature = data.getCurrentTemperature();
        long currentTime = System.currentTimeMillis();

        // Check cooldown
        if (currentTime - data.getLastEffectTime() < (effectCooldownTicks * 50)) {
            return; // Still on cooldown
        }

        // Determine temperature level and apply effects
        String effectLevel = getTemperatureEffectLevel(temperature);
        if (effectLevel != null) {
            List<PotionEffect> effects = temperatureEffects.get(effectLevel);
            if (effects != null && !effects.isEmpty()) {
                for (PotionEffect effect : effects) {
                    player.addPotionEffect(effect, true);
                }

                data.setLastEffectTime(currentTime);

                // Send temperature warning if severe
                if (effectLevel.contains("severe") &&
                        plugin.getConfig().getBoolean("notifications.temperature_warnings", true) &&
                        player.hasPermission("orbisclimate.notifications")) {

                    if (temperature <= TemperatureLevel.SEVERE_COLD) {
                        player.sendMessage("§b§l❄ You are suffering from severe cold! Find shelter immediately!");
                    } else if (temperature >= TemperatureLevel.SEVERE_HEAT) {
                        player.sendMessage("§c§l☀ You are suffering from severe heat! Find shade and water!");
                    }
                }
            }
        }
    }

    private String getTemperatureEffectLevel(double temperature) {
        if (temperature <= TemperatureLevel.SEVERE_COLD) {
            return "severe_cold";
        } else if (temperature <= TemperatureLevel.COLD) {
            return "cold";
        } else if (temperature <= TemperatureLevel.MILD_COLD) {
            return "mild_cold";
        } else if (temperature >= TemperatureLevel.SEVERE_HEAT) {
            return "severe_heat";
        } else if (temperature >= TemperatureLevel.HOT) {
            return "hot";
        } else if (temperature >= TemperatureLevel.MILD_HEAT) {
            return "mild_heat";
        }

        return null; // Comfortable temperature range
    }

    // Public getters for other managers
    public double getPlayerTemperature(Player player) {
        PlayerTemperatureData data = playerTemperatureData.get(player);
        return data != null ? data.getCurrentTemperature() : 20.0;
    }

    public String getPlayerTemperatureLevel(Player player) {
        double temp = getPlayerTemperature(player);
        String level = getTemperatureEffectLevel(temp);

        if (level != null) {
            return level.replace("_", " ");
        } else if (temp >= TemperatureLevel.COMFORTABLE_MIN && temp <= TemperatureLevel.COMFORTABLE_MAX) {
            return "comfortable";
        } else {
            return "mild";
        }
    }

    public boolean isPlayerTooHot(Player player) {
        return getPlayerTemperature(player) >= TemperatureLevel.MILD_HEAT;
    }

    public boolean isPlayerTooCold(Player player) {
        return getPlayerTemperature(player) <= TemperatureLevel.MILD_COLD;
    }

    // Player data management
    public void addPlayer(Player player) {
        playerTemperatureData.put(player, new PlayerTemperatureData());
    }

    public void removePlayer(Player player) {
        playerTemperatureData.remove(player);
    }

    // Configuration reload
    public void reloadConfig() {
        loadConfiguration();
    }

    // Shutdown
    public void shutdown() {
        if (temperatureTask != null) {
            temperatureTask.cancel();
        }
        playerTemperatureData.clear();
    }
}