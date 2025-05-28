package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Color;

import java.util.*;

public class ClimateZoneManager {

    public enum ClimateZone {
        ARCTIC("Arctic", -30, 0),
        TEMPERATE("Temperate", 0, 25),
        DESERT("Desert", 15, 50),
        ARID("Arid", 10, 45);

        private final String displayName;
        private final int minTemp;
        private final int maxTemp;

        ClimateZone(String displayName, int minTemp, int maxTemp) {
            this.displayName = displayName;
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
        }

        public String getDisplayName() { return displayName; }
        public int getMinTemp() { return minTemp; }
        public int getMaxTemp() { return maxTemp; }
    }

    public static class ZoneWeatherData {
        private final ClimateZone zone;
        private WeatherForecast.WeatherType currentWeather;
        private int consecutiveClearDays;
        private boolean isDroughtActive;
        private long lastDroughtCheck;
        private double currentTemperature;
        private long lastTemperatureUpdate;

        public ZoneWeatherData(ClimateZone zone) {
            this.zone = zone;
            this.currentWeather = WeatherForecast.WeatherType.CLEAR;
            this.consecutiveClearDays = 0;
            this.isDroughtActive = false;
            this.lastDroughtCheck = 0;
            this.currentTemperature = (zone.minTemp + zone.maxTemp) / 2.0;
            this.lastTemperatureUpdate = System.currentTimeMillis();
        }

        // Getters and setters
        public ClimateZone getZone() { return zone; }
        public WeatherForecast.WeatherType getCurrentWeather() { return currentWeather; }
        public void setCurrentWeather(WeatherForecast.WeatherType weather) { this.currentWeather = weather; }
        public int getConsecutiveClearDays() { return consecutiveClearDays; }
        public void setConsecutiveClearDays(int days) { this.consecutiveClearDays = days; }
        public boolean isDroughtActive() { return isDroughtActive; }
        public void setDroughtActive(boolean active) { this.isDroughtActive = active; }
        public double getCurrentTemperature() { return currentTemperature; }
        public void setCurrentTemperature(double temp) { this.currentTemperature = temp; }
        public long getLastDroughtCheck() { return lastDroughtCheck; }
        public void setLastDroughtCheck(long time) { this.lastDroughtCheck = time; }
        public long getLastTemperatureUpdate() { return lastTemperatureUpdate; }
        public void setLastTemperatureUpdate(long time) { this.lastTemperatureUpdate = time; }
    }

    private final OrbisClimate plugin;
    private final WeatherForecast weatherForecast;
    private final WindManager windManager;
    private final Random random;

    // Runtime data
    private final Map<String, Map<ClimateZone, ZoneWeatherData>> worldZoneData = new HashMap<>();
    private final Map<Player, ClimateZone> playerZoneCache = new HashMap<>();
    private final Map<String, Long> worldDayTracker = new HashMap<>();
    
    // Tasks
    private BukkitTask climateTask;
    private BukkitTask temperatureTask;

    public ClimateZoneManager(OrbisClimate plugin, WeatherForecast weatherForecast, WindManager windManager) {
        this.plugin = plugin;
        this.weatherForecast = weatherForecast;
        this.windManager = windManager;
        this.random = new Random();

        initializeWorldData();
        startClimateTasks();
    }

    private void initializeWorldData() {
        for (World world : Bukkit.getWorlds()) {
            Map<ClimateZone, ZoneWeatherData> zoneData = new HashMap<>();
            for (ClimateZone zone : ClimateZone.values()) {
                zoneData.put(zone, new ZoneWeatherData(zone));
            }
            worldZoneData.put(world.getName(), zoneData);
            worldDayTracker.put(world.getName(), getCurrentDay(world));
        }
    }

    private void startClimateTasks() {
        // Main climate effects task
        climateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                updateWorldClimate(world);
                processPlayerClimateEffects(world);
            }
        }, 0L, 20L); // Every second

        // Temperature update task (less frequent)
        temperatureTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                updateWorldTemperatures(world);
                checkForDroughts(world);
            }
        }, 0L, 1200L); // Every minute
    }

    public ClimateZone getPlayerClimateZone(Player player) {
        // Check cache first
        ClimateZone cached = playerZoneCache.get(player);
        if (cached != null) {
            return cached;
        }

        Location loc = player.getLocation();
        Biome biome = loc.getBlock().getBiome();
        
        // Simple biome-based detection only
        ClimateZone zone;
        if (isArcticBiome(biome)) {
            zone = ClimateZone.ARCTIC;
        } else if (isDesertBiome(biome)) {
            zone = ClimateZone.DESERT;
        } else if (isAridBiome(biome)) {
            zone = ClimateZone.ARID;
        } else {
            zone = ClimateZone.TEMPERATE;
        }
        
        playerZoneCache.put(player, zone);
        return zone;
    }

    private boolean isArcticBiome(Biome biome) {
        switch (biome) {
            case SNOWY_PLAINS:
            case SNOWY_TAIGA:
            case SNOWY_SLOPES:
            case SNOWY_BEACH:
            case FROZEN_RIVER:
            case FROZEN_OCEAN:
            case DEEP_FROZEN_OCEAN:
            case ICE_SPIKES:
            case GROVE:
            case JAGGED_PEAKS:
            case FROZEN_PEAKS:
            case TAIGA:
                return true;
            default:
                return false;
        }
    }

    private boolean isDesertBiome(Biome biome) {
        switch (biome) {
            case DESERT:
                return true;
            default:
                return false;
        }
    }

    private boolean isAridBiome(Biome biome) {
        switch (biome) {
            case BADLANDS:
            case ERODED_BADLANDS:
            case WOODED_BADLANDS:
                return true;
            default:
                return false;
        }
    }

    private void updateWorldClimate(World world) {
        WeatherForecast.WeatherType worldWeather = weatherForecast.getCurrentWeather(world);
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(world.getName());
        
        if (zoneData == null) return;

        // Update each zone's weather based on world weather state
        for (Map.Entry<ClimateZone, ZoneWeatherData> entry : zoneData.entrySet()) {
            ClimateZone zone = entry.getKey();
            ZoneWeatherData data = entry.getValue();
            
            WeatherForecast.WeatherType zoneWeather = translateWorldWeatherToZone(worldWeather, zone, world);
            data.setCurrentWeather(zoneWeather);
        }

        // Check for day changes and update drought tracking
        long currentDay = getCurrentDay(world);
        Long lastDay = worldDayTracker.get(world.getName());
        
        if (lastDay == null || currentDay != lastDay) {
            updateDayTracking(world, worldWeather);
            worldDayTracker.put(world.getName(), currentDay);
        }
    }

    private WeatherForecast.WeatherType translateWorldWeatherToZone(WeatherForecast.WeatherType worldWeather, 
                                                                  ClimateZone zone, World world) {
        Season currentSeason = weatherForecast.getCurrentSeason(world);
        
        switch (worldWeather) {
            case CLEAR:
                return translateClearWeather(zone, currentSeason, world);
            case LIGHT_RAIN:
            case HEAVY_RAIN:
                return translateStormWeather(zone, currentSeason, false);
            case THUNDERSTORM:
                return translateStormWeather(zone, currentSeason, true);
            case SNOW:
                return translateSnowWeather(zone, currentSeason);
            case BLIZZARD:
                return translateBlizzardWeather(zone, currentSeason);
            case SANDSTORM:
                return translateSandstormWeather(zone, currentSeason);
            default:
                return worldWeather;
        }
    }

    private WeatherForecast.WeatherType translateClearWeather(ClimateZone zone, Season season, World world) {
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(world.getName());
        ZoneWeatherData data = zoneData.get(zone);
        
        switch (zone) {
            case ARCTIC:
                // Arctic: Wind-blown snow, Aurora at night
                if (world.getTime() > 13000 && world.getTime() < 23000) {
                    // Night time - potential aurora
                    return WeatherForecast.WeatherType.CLEAR; // Will show aurora effects
                }
                return WeatherForecast.WeatherType.CLEAR; // Will show wind-blown snow
                
            case DESERT:
            case ARID:
                // Desert/Arid: Heat mirages, potential drought
                if (data.isDroughtActive()) {
                    return WeatherForecast.WeatherType.CLEAR; // Will show drought/heat effects
                }
                return WeatherForecast.WeatherType.CLEAR;
                
            case TEMPERATE:
            default:
                return WeatherForecast.WeatherType.CLEAR;
        }
    }

    private WeatherForecast.WeatherType translateStormWeather(ClimateZone zone, Season season, boolean hasThunder) {
        switch (zone) {
            case ARCTIC:
                return hasThunder ? WeatherForecast.WeatherType.BLIZZARD : WeatherForecast.WeatherType.SNOW;
            case DESERT:
            case ARID:
                // No rain in arid zones
                return WeatherForecast.WeatherType.CLEAR;
            case TEMPERATE:
            default:
                return hasThunder ? WeatherForecast.WeatherType.THUNDERSTORM : WeatherForecast.WeatherType.LIGHT_RAIN;
        }
    }

    private WeatherForecast.WeatherType translateSnowWeather(ClimateZone zone, Season season) {
        switch (zone) {
            case ARCTIC:
                return WeatherForecast.WeatherType.SNOW;
            case DESERT:
            case ARID:
                return WeatherForecast.WeatherType.CLEAR; // Deserts/Arid don't get snow
            case TEMPERATE:
            default:
                return season == Season.WINTER ? WeatherForecast.WeatherType.SNOW : WeatherForecast.WeatherType.LIGHT_RAIN;
        }
    }

    private WeatherForecast.WeatherType translateBlizzardWeather(ClimateZone zone, Season season) {
        switch (zone) {
            case ARCTIC:
                return WeatherForecast.WeatherType.BLIZZARD;
            case DESERT:
            case ARID:
                return WeatherForecast.WeatherType.SANDSTORM; // Desert equivalent
            case TEMPERATE:
            default:
                return WeatherForecast.WeatherType.HEAVY_RAIN; // Hurricane-like effects
        }
    }

    private WeatherForecast.WeatherType translateSandstormWeather(ClimateZone zone, Season season) {
        switch (zone) {
            case ARCTIC:
                return WeatherForecast.WeatherType.BLIZZARD; // Arctic equivalent
            case DESERT:
            case ARID:
                return WeatherForecast.WeatherType.SANDSTORM;
            case TEMPERATE:
            default:
                return WeatherForecast.WeatherType.HEAVY_RAIN; // Severe storm
        }
    }

    private void processPlayerClimateEffects(World world) {
        for (Player player : world.getPlayers()) {
            ClimateZone playerZone = getPlayerClimateZone(player);
            ZoneWeatherData zoneData = worldZoneData.get(world.getName()).get(playerZone);
            
            if (zoneData == null) continue;

            // Apply zone-specific effects
            applyZoneEffects(player, playerZone, zoneData, world);
        }
    }

    private void applyZoneEffects(Player player, ClimateZone zone, ZoneWeatherData zoneData, World world) {
        WeatherForecast.WeatherType zoneWeather = zoneData.getCurrentWeather();
        
        // Skip if player is indoors
        if (windManager.isPlayerIndoors(player)) {
            return;
        }

        switch (zone) {
            case ARCTIC:
                applyArcticEffects(player, zoneWeather, world);
                break;
            case DESERT:
            case ARID:
                applyDesertEffects(player, zoneWeather, zoneData, world);
                break;
            case TEMPERATE:
                applyTemperateEffects(player, zoneWeather, world);
                break;
        }
    }

    private void applyArcticEffects(Player player, WeatherForecast.WeatherType weather, World world) {
        Location loc = player.getLocation();
        
        if (weather == WeatherForecast.WeatherType.CLEAR) {
            // Wind-blown snow effects
            if (random.nextInt(10) == 0) {
                createWindBlownSnow(player);
            }
            
            // Aurora effects at night
            if (world.getTime() > 13000 && world.getTime() < 23000 && random.nextInt(30) == 0) {
                createAuroraEffects(player);
            }
        }
        
        // Temperature effects for all Arctic weather
        applyTemperatureEffects(player, ClimateZone.ARCTIC, weather);
    }

    private void applyDesertEffects(Player player, WeatherForecast.WeatherType weather, 
                                  ZoneWeatherData zoneData, World world) {
        if (weather == WeatherForecast.WeatherType.CLEAR) {
            // Heat mirages
            if (world.getTime() > 6000 && world.getTime() < 18000 && random.nextInt(20) == 0) {
                createHeatMirageEffects(player);
            }
            
            // Drought effects
            if (zoneData.isDroughtActive() && random.nextInt(40) == 0) {
                createDroughtEffects(player);
            }
        }
        
        // Temperature effects
        applyTemperatureEffects(player, ClimateZone.DESERT, weather);
    }

    private void applyTemperateEffects(Player player, WeatherForecast.WeatherType weather, World world) {
        // Temperate zone has standard effects, but can have hurricane effects during severe weather
        if (weather == WeatherForecast.WeatherType.HEAVY_RAIN || weather == WeatherForecast.WeatherType.THUNDERSTORM) {
            // Hurricane-like effects
            if (random.nextInt(15) == 0) {
                createHurricaneEffects(player);
            }
        }
        
        applyTemperatureEffects(player, ClimateZone.TEMPERATE, weather);
    }

    private void createWindBlownSnow(Player player) {
        Location loc = player.getLocation();
        
        for (int i = 0; i < 15; i++) {
            Location particleLoc = loc.clone().add(
                (random.nextDouble() - 0.5) * 20,
                random.nextDouble() * 5,
                (random.nextDouble() - 0.5) * 20
            );
            
            player.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1,
                0.5, 0, 0.5, 0.1);
            player.spawnParticle(Particle.CLOUD, particleLoc, 1,
                0.3, 0, 0.3, 0.05);
        }
        
        if (random.nextInt(3) == 0) {
            player.playSound(loc, Sound.WEATHER_RAIN, 0.3f, 0.5f);
        }
    }

    private void createAuroraEffects(Player player) {
        Location loc = player.getLocation().add(0, 30, 0);
        
        // Create colorful aurora particles
        Color[] auroraColors = {
            Color.fromRGB(0, 255, 100),   // Green
            Color.fromRGB(0, 100, 255),   // Blue  
            Color.fromRGB(255, 0, 255),   // Purple
            Color.fromRGB(0, 255, 255)    // Cyan
        };
        
        for (int i = 0; i < 30; i++) {
            double angle = (System.currentTimeMillis() / 100.0 + i * 12) % 360;
            double radians = Math.toRadians(angle);
            double radius = 15 + Math.sin(System.currentTimeMillis() / 1000.0 + i) * 5;
            
            Location auroraLoc = loc.clone().add(
                Math.cos(radians) * radius,
                Math.sin(System.currentTimeMillis() / 800.0 + i) * 3,
                Math.sin(radians) * radius
            );
            
            Color color = auroraColors[random.nextInt(auroraColors.length)];
            Particle.DustOptions dustOptions = new Particle.DustOptions(color, 2.0f);
            
            player.spawnParticle(Particle.DUST, auroraLoc, 1, 0.1, 0.1, 0.1, 0, dustOptions);
        }
    }

    private void createHeatMirageEffects(Player player) {
        Location loc = player.getLocation();
        
        // Create shimmering effects on the horizon
        for (int i = 0; i < 20; i++) {
            Location mirageLoc = loc.clone().add(
                (random.nextDouble() - 0.5) * 30,
                0.5,
                (random.nextDouble() - 0.5) * 30
            );
            
            // Make it shimmer by varying Y position
            mirageLoc.add(0, Math.sin(System.currentTimeMillis() / 200.0 + i) * 0.3, 0);
            
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, mirageLoc, 1,
                0.2, 0.1, 0.2, 0,
                new Particle.DustTransition(
                    Color.fromRGB(255, 255, 200), // Light yellow
                    Color.fromRGB(255, 200, 100), // Orange
                    1.0f
                ));
        }
    }

    private void createDroughtEffects(Player player) {
        Location loc = player.getLocation();
        
        // Dust and heat particles
        for (int i = 0; i < 10; i++) {
            Location dustLoc = loc.clone().add(
                (random.nextDouble() - 0.5) * 15,
                random.nextDouble() * 2,
                (random.nextDouble() - 0.5) * 15
            );
            
            player.spawnParticle(Particle.ASH, dustLoc, 1,
                0.3, 0.1, 0.3, 0.02);
        }
        
        // Heat exhaustion message occasionally
        if (random.nextInt(200) == 0) {
            player.sendMessage("§c§lThe scorching heat of the drought saps your strength...");
        }
    }

    private void createHurricaneEffects(Player player) {
        Location loc = player.getLocation();
        
        // Strong wind particles
        for (int i = 0; i < 25; i++) {
            Location windLoc = loc.clone().add(
                (random.nextDouble() - 0.5) * 25,
                random.nextDouble() * 8,
                (random.nextDouble() - 0.5) * 25
            );
            
            player.spawnParticle(Particle.CLOUD, windLoc, 1,
                1.0, 0.5, 1.0, 0.2);
            player.spawnParticle(Particle.RAIN, windLoc, 1,
                0.8, 0.3, 0.8, 0.1);
        }
        
        if (random.nextInt(5) == 0) {
            player.playSound(loc, Sound.WEATHER_RAIN, 0.8f, 0.6f);
        }
    }

    private void applyTemperatureEffects(Player player, ClimateZone zone, WeatherForecast.WeatherType weather) {
        // Temperature-based effects will be implemented based on your existing temperature system
        // This is a placeholder for temperature modifications
    }

    private void updateWorldTemperatures(World world) {
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(world.getName());
        if (zoneData == null) return;

        for (ZoneWeatherData data : zoneData.values()) {
            updateZoneTemperature(data, world);
        }
    }

    private void updateZoneTemperature(ZoneWeatherData data, World world) {
        ClimateZone zone = data.getZone();
        WeatherForecast.WeatherType weather = data.getCurrentWeather();
        Season season = weatherForecast.getCurrentSeason(world);
        
        double baseTemp = (zone.getMinTemp() + zone.getMaxTemp()) / 2.0;
        double modifier = 0;
        
        // Weather modifiers
        switch (weather) {
            case CLEAR:
                if ((zone == ClimateZone.DESERT || zone == ClimateZone.ARID) && data.isDroughtActive()) {
                    modifier += 15; // Drought heat
                }
                break;
            case BLIZZARD:
                modifier -= 20; // Extreme cold
                break;
            case SNOW:
                modifier -= 10;
                break;
            case SANDSTORM:
                modifier += 5; // Sandstorms can be hot
                break;
        }
        
        // Seasonal modifiers
        if (season != null) {
            switch (season) {
                case SUMMER:
                    modifier += (zone == ClimateZone.DESERT || zone == ClimateZone.ARID) ? 10 : 5;
                    break;
                case WINTER:
                    modifier -= zone == ClimateZone.ARCTIC ? 15 : 10;
                    break;
            }
        }
        
        data.setCurrentTemperature(baseTemp + modifier);
        data.setLastTemperatureUpdate(System.currentTimeMillis());
    }

    private void checkForDroughts(World world) {
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(world.getName());
        if (zoneData == null) return;

        // Check desert zones
        ZoneWeatherData desertData = zoneData.get(ClimateZone.DESERT);
        checkZoneForDrought(world, desertData, ClimateZone.DESERT);
        
        // Check arid zones
        ZoneWeatherData aridData = zoneData.get(ClimateZone.ARID);
        checkZoneForDrought(world, aridData, ClimateZone.ARID);
    }

    private void checkZoneForDrought(World world, ZoneWeatherData zoneData, ClimateZone zone) {
        if (zoneData == null) return;

        // Check if drought conditions are met (5+ consecutive clear days)
        if (zoneData.getConsecutiveClearDays() >= 5 && !zoneData.isDroughtActive()) {
            zoneData.setDroughtActive(true);
            zoneData.setLastDroughtCheck(System.currentTimeMillis());
            
            // Notify players in this zone
            for (Player player : world.getPlayers()) {
                if (getPlayerClimateZone(player) == zone &&
                    player.hasPermission("orbisclimate.notifications")) {
                    player.sendMessage("§6[OrbisClimate] §c§lDrought conditions have begun in the " + 
                        zone.getDisplayName().toLowerCase() + "!");
                }
            }
        } else if (zoneData.getCurrentWeather() != WeatherForecast.WeatherType.CLEAR && 
                   zoneData.isDroughtActive()) {
            // End drought if weather changes
            zoneData.setDroughtActive(false);
            zoneData.setConsecutiveClearDays(0);
            
            for (Player player : world.getPlayers()) {
                if (getPlayerClimateZone(player) == zone &&
                    player.hasPermission("orbisclimate.notifications")) {
                    player.sendMessage("§6[OrbisClimate] §b§lThe drought has ended!");
                }
            }
        }
    }

    private void updateDayTracking(World world, WeatherForecast.WeatherType worldWeather) {
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(world.getName());
        if (zoneData == null) return;

        for (ZoneWeatherData data : zoneData.values()) {
            if (data.getCurrentWeather() == WeatherForecast.WeatherType.CLEAR) {
                data.setConsecutiveClearDays(data.getConsecutiveClearDays() + 1);
            } else {
                data.setConsecutiveClearDays(0);
            }
        }
    }

    private long getCurrentDay(World world) {
        if (weatherForecast.isRealisticSeasonsEnabled()) {
            // Use RealisticSeasons day tracking
            return weatherForecast.getCurrentDate(world) != null ? 
                weatherForecast.getCurrentDate(world).getDay() : world.getFullTime() / 24000;
        } else {
            return world.getFullTime() / 24000;
        }
    }

    // Public getters for other managers
    public WeatherForecast.WeatherType getPlayerZoneWeather(Player player) {
        ClimateZone zone = getPlayerClimateZone(player);
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(player.getWorld().getName());
        return zoneData != null && zoneData.get(zone) != null ? 
            zoneData.get(zone).getCurrentWeather() : WeatherForecast.WeatherType.CLEAR;
    }

    public double getPlayerZoneTemperature(Player player) {
        ClimateZone zone = getPlayerClimateZone(player);
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(player.getWorld().getName());
        return zoneData != null && zoneData.get(zone) != null ? 
            zoneData.get(zone).getCurrentTemperature() : 20.0;
    }

    public boolean isPlayerInDrought(Player player) {
        ClimateZone zone = getPlayerClimateZone(player);
        if (zone != ClimateZone.DESERT && zone != ClimateZone.ARID) return false;
        
        Map<ClimateZone, ZoneWeatherData> zoneData = worldZoneData.get(player.getWorld().getName());
        return zoneData != null && zoneData.get(zone) != null && zoneData.get(zone).isDroughtActive();
    }

    // Cache management
    public void clearPlayerCache(Player player) {
        playerZoneCache.remove(player);
    }

    public void clearPlayerCache() {
        playerZoneCache.clear();
    }

    // Configuration reload
    public void reloadConfig() {
        clearPlayerCache(); // Clear cache when config changes
    }

    // Shutdown
    public void shutdown() {
        if (climateTask != null) {
            climateTask.cancel();
        }
        if (temperatureTask != null) {
            temperatureTask.cancel();
        }
        
        worldZoneData.clear();
        playerZoneCache.clear();
        worldDayTracker.clear();
    }
}