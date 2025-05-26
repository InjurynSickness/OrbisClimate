package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DynamicSoundManager {
    private final OrbisClimate plugin;
    private final Random random;
    private final Map<World, AmbientSoundData> worldSounds = new HashMap<>();
    private BukkitTask soundTask;

    // Configuration
    private boolean dynamicSoundsEnabled;
    private double indoorVolumeMultiplier;
    private int soundUpdateInterval;

    public DynamicSoundManager(OrbisClimate plugin) {
        this.plugin = plugin;
        this.random = new Random();

        loadConfiguration();
        startSoundTask();
    }

    private void loadConfiguration() {
        dynamicSoundsEnabled = plugin.getConfig().getBoolean("dynamic_sounds.enabled", true);
        indoorVolumeMultiplier = plugin.getConfig().getDouble("dynamic_sounds.indoor_volume_multiplier", 0.3);
        soundUpdateInterval = plugin.getConfig().getInt("dynamic_sounds.update_interval_ticks", 60);
    }

    private void startSoundTask() {
        if (!dynamicSoundsEnabled) return;

        soundTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                updateWorldAmbientSounds(world);
            }
        }, 0L, soundUpdateInterval);
    }

    private void updateWorldAmbientSounds(World world) {
        WeatherForecast.WeatherType weather = plugin.getWeatherForecast().getCurrentWeather(world);
        Season season = plugin.getWeatherForecast().getCurrentSeason(world);

        for (Player player : world.getPlayers()) {
            // Respect particle setting for sounds too
            if (!plugin.isPlayerParticlesEnabled(player)) continue;

            ClimateZoneManager.ClimateZone zone = plugin.getClimateZoneManager().getPlayerClimateZone(player);
            playAmbientSounds(player, weather, season, zone);
        }
    }

    private void playAmbientSounds(Player player, WeatherForecast.WeatherType weather,
                                   Season season, ClimateZoneManager.ClimateZone zone) {
        Location loc = player.getLocation();
        boolean isIndoors = plugin.getWindManager().isPlayerIndoors(player);

        if (isIndoors) {
            playIndoorAmbientSounds(player, weather, zone);
        } else {
            playOutdoorAmbientSounds(player, weather, season, zone);
        }
    }

    private void playOutdoorAmbientSounds(Player player, WeatherForecast.WeatherType weather,
                                          Season season, ClimateZoneManager.ClimateZone zone) {
        Location loc = player.getLocation();

        // Base weather sounds
        switch (weather) {
            case CLEAR:
                playZoneAmbientSounds(player, zone, season);
                break;
            case LIGHT_RAIN:
                if (random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.3f, 1.0f);
                }
                break;
            case HEAVY_RAIN:
                if (random.nextInt(3) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.6f, 0.9f);
                }
                break;
            case THUNDERSTORM:
                if (random.nextInt(3) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.8f, 0.8f);
                }
                if (random.nextInt(10) == 0) {
                    player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 0.7f);
                }
                break;
            case SNOW:
            case BLIZZARD:
                if (random.nextInt(5) == 0) {
                    player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.2f, 0.5f);
                }
                break;
            case SANDSTORM:
                if (random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.4f, 0.3f);
                }
                break;
        }

        // Seasonal overlay sounds
        if (season != null && random.nextInt(8) == 0) {
            playSeasonalSounds(player, season, zone);
        }

        // Time of day sounds
        if (random.nextInt(12) == 0) {
            playTimeOfDaySounds(player, zone, season);
        }
    }

    private void playZoneAmbientSounds(Player player, ClimateZoneManager.ClimateZone zone, Season season) {
        if (random.nextInt(5) != 0) return; // Don't play every time

        Location loc = player.getLocation();

        switch (zone) {
            case ARCTIC:
                // Cold wind whistling
                player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.1f, 0.6f);
                if (random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.BLOCK_SNOW_STEP, 0.2f, 0.8f);
                }
                break;
            case DESERT:
                // Hot wind and sand shifting
                player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.15f, 1.2f);
                if (random.nextInt(3) == 0) {
                    player.playSound(loc, Sound.BLOCK_SAND_STEP, 0.1f, 0.9f);
                }
                break;
            case TEMPERATE:
                // Gentle breeze through trees
                player.playSound(loc, Sound.BLOCK_GRASS_STEP, 0.1f, 1.1f);
                if (season == Season.SUMMER && random.nextInt(6) == 0) {
                    player.playSound(loc, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 0.05f, 1.3f);
                }
                break;
        }
    }

    private void playSeasonalSounds(Player player, Season season, ClimateZoneManager.ClimateZone zone) {
        Location loc = player.getLocation();

        switch (season) {
            case SPRING:
                // Birds chirping more frequently
                if (zone == ClimateZoneManager.ClimateZone.TEMPERATE) {
                    player.playSound(loc, Sound.ENTITY_PARROT_AMBIENT, 0.2f, 1.3f);
                }
                // Spring breeze
                if (random.nextInt(2) == 0) {
                    player.playSound(loc, Sound.BLOCK_GRASS_BREAK, 0.1f, 1.2f);
                }
                break;
            case SUMMER:
                // Insect sounds
                if (zone != ClimateZoneManager.ClimateZone.ARCTIC) {
                    player.playSound(loc, Sound.BLOCK_HONEY_BLOCK_STEP, 0.1f, 2.0f);
                }
                // Cicadas
                if (zone == ClimateZoneManager.ClimateZone.TEMPERATE && random.nextInt(3) == 0) {
                    player.playSound(loc, Sound.ENTITY_BEE_LOOP, 0.15f, 0.8f);
                }
                break;
            case FALL:
                // Wind through dry leaves
                if (zone == ClimateZoneManager.ClimateZone.TEMPERATE) {
                    player.playSound(loc, Sound.BLOCK_GRASS_BREAK, 0.15f, 0.8f);
                }
                // Rustling leaves
                if (random.nextInt(3) == 0) {
                    player.playSound(loc, Sound.BLOCK_AZALEA_LEAVES_STEP, 0.2f, 0.9f);
                }
                break;
            case WINTER:
                // Harsh winter winds
                if (zone != ClimateZoneManager.ClimateZone.DESERT) {
                    player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.2f, 0.4f);
                }
                // Creaking trees
                if (zone == ClimateZoneManager.ClimateZone.TEMPERATE && random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.BLOCK_BAMBOO_STEP, 0.1f, 0.6f);
                }
                break;
        }
    }

    private void playTimeOfDaySounds(Player player, ClimateZoneManager.ClimateZone zone, Season season) {
        Location loc = player.getLocation();
        long time = player.getWorld().getTime();

        // Convert MC time to approximate hour
        int hour = (int) (((time + 6000) % 24000) / 1000);

        if (hour >= 20 || hour <= 6) { // Night time (8 PM to 6 AM)
            playNightSounds(player, zone, season);
        } else if (hour >= 6 && hour <= 10) { // Dawn/Morning
            playDawnSounds(player, zone, season);
        } else if (hour >= 18 && hour <= 20) { // Dusk
            playDuskSounds(player, zone, season);
        }
        // No special sounds for midday
    }

    private void playNightSounds(Player player, ClimateZoneManager.ClimateZone zone, Season season) {
        Location loc = player.getLocation();

        switch (zone) {
            case ARCTIC:
                // Arctic night - howling wind, occasional wolf
                if (random.nextInt(6) == 0) {
                    player.playSound(loc, Sound.ENTITY_WOLF_HOWL, 0.3f, 0.8f);
                }
                break;
            case DESERT:
                // Desert night - cooler, different wind
                if (random.nextInt(5) == 0) {
                    player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.1f, 0.9f);
                }
                break;
            case TEMPERATE:
                // Night creatures
                if (season != Season.WINTER && random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.ENTITY_BAT_AMBIENT, 0.1f, 1.0f);
                }
                // Owl hoots
                if (random.nextInt(8) == 0) {
                    player.playSound(loc, Sound.ENTITY_PHANTOM_AMBIENT, 0.15f, 0.5f);
                }
                break;
        }
    }

    private void playDawnSounds(Player player, ClimateZoneManager.ClimateZone zone, Season season) {
        Location loc = player.getLocation();

        if (zone == ClimateZoneManager.ClimateZone.TEMPERATE && season != Season.WINTER) {
            // Dawn chorus - birds waking up
            if (random.nextInt(3) == 0) {
                player.playSound(loc, Sound.ENTITY_PARROT_AMBIENT, 0.3f, 1.4f);
            }
        }
    }

    private void playDuskSounds(Player player, ClimateZoneManager.ClimateZone zone, Season season) {
        Location loc = player.getLocation();

        if (zone == ClimateZoneManager.ClimateZone.TEMPERATE) {
            // Evening wind
            if (random.nextInt(4) == 0) {
                player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.15f, 1.1f);
            }
        }
    }

    private void playIndoorAmbientSounds(Player player, WeatherForecast.WeatherType weather,
                                         ClimateZoneManager.ClimateZone zone) {
        Location loc = player.getLocation();
        float volumeMultiplier = (float) indoorVolumeMultiplier;

        // Muffled outdoor sounds when indoors
        switch (weather) {
            case LIGHT_RAIN:
                if (random.nextInt(6) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.2f * volumeMultiplier, 0.8f);
                }
                break;
            case HEAVY_RAIN:
                if (random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.4f * volumeMultiplier, 0.7f);
                }
                break;
            case THUNDERSTORM:
                if (random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.5f * volumeMultiplier, 0.6f);
                }
                if (random.nextInt(15) == 0) {
                    player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f * volumeMultiplier, 0.5f);
                }
                break;
            case BLIZZARD:
                if (random.nextInt(5) == 0) {
                    player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.15f * volumeMultiplier, 0.4f);
                }
                break;
            case SANDSTORM:
                if (random.nextInt(6) == 0) {
                    player.playSound(loc, Sound.WEATHER_RAIN, 0.25f * volumeMultiplier, 0.2f);
                }
                break;
        }

        // Subtle indoor ambience
        if (random.nextInt(20) == 0) {
            playIndoorAmbience(player, zone);
        }
    }

    private void playIndoorAmbience(Player player, ClimateZoneManager.ClimateZone zone) {
        Location loc = player.getLocation();

        // Very subtle indoor sounds based on zone
        switch (zone) {
            case ARCTIC:
                // Creaking from cold
                if (random.nextInt(3) == 0) {
                    player.playSound(loc, Sound.BLOCK_BAMBOO_STEP, 0.05f, 0.5f);
                }
                break;
            case DESERT:
                // Settling sounds from heat
                if (random.nextInt(4) == 0) {
                    player.playSound(loc, Sound.BLOCK_WOOD_STEP, 0.03f, 0.8f);
                }
                break;
            case TEMPERATE:
                // General settling
                if (random.nextInt(5) == 0) {
                    player.playSound(loc, Sound.BLOCK_WOOD_STEP, 0.02f, 1.0f);
                }
                break;
        }
    }

    // Configuration reload
    public void reloadConfig() {
        loadConfiguration();
        plugin.getLogger().info("Dynamic sound configuration reloaded!");
    }

    // Shutdown
    public void shutdown() {
        if (soundTask != null) {
            soundTask.cancel();
        }
        worldSounds.clear();
    }

    // Inner class for ambient sound data
    private static class AmbientSoundData {
        private long lastSoundTime;
        private String lastSoundType;

        public AmbientSoundData() {
            this.lastSoundTime = 0;
            this.lastSoundType = "";
        }

        public long getLastSoundTime() { return lastSoundTime; }
        public void setLastSoundTime(long time) { this.lastSoundTime = time; }
        public String getLastSoundType() { return lastSoundType; }
        public void setLastSoundType(String type) { this.lastSoundType = type; }
    }
}