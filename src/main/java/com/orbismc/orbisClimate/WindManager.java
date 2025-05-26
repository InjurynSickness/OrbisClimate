package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class WindManager {

    private final OrbisClimate plugin;
    private final Random random;
    private BukkitTask windTask;

    // Wind configuration
    private int interiorHeightDistance;
    private int maxParticles;
    private double particleRange;
    private boolean windEnabled;
    private int minWindHeight; // New: minimum height for wind effects

    // Wind chance configuration
    private double clearWeatherChance;
    private double rainChance;
    private double snowChance;
    private double thunderstormChance;

    // Wind event duration
    private int minWindDuration;
    private int maxWindDuration;

    // Wind state
    private final Map<World, WindData> worldWindData = new HashMap<>();
    private final Map<World, Integer> windCheckCooldown = new HashMap<>();

    // Blocks that don't count as "solid ceiling"
    private final Set<Material> bannedBlocks = new HashSet<>();

    private final WeatherForecast weatherForecast;

    public WindManager(OrbisClimate plugin, Random random, WeatherForecast weatherForecast) {
        this.plugin = plugin;
        this.random = random;
        this.weatherForecast = weatherForecast;

        loadConfig();
        initializeBannedBlocks();
        startWindSystem();
    }

    private void loadConfig() {
        interiorHeightDistance = plugin.getConfig().getInt("wind.interior_height_distance", 50);
        maxParticles = plugin.getConfig().getInt("wind.max_particles", 100);
        particleRange = plugin.getConfig().getDouble("wind.particle_range", 10.0);
        windEnabled = plugin.getConfig().getBoolean("wind.enabled", true);
        minWindHeight = plugin.getConfig().getInt("wind.min_height", 50); // New: minimum height for wind

        // Wind event chances (percentage)
        clearWeatherChance = plugin.getConfig().getDouble("wind.chances.clear_weather", 10.0);
        rainChance = plugin.getConfig().getDouble("wind.chances.rain", 25.0);
        snowChance = plugin.getConfig().getDouble("wind.chances.snow", 15.0);
        thunderstormChance = plugin.getConfig().getDouble("wind.chances.thunderstorm", 100.0);

        // Wind duration (in seconds)
        minWindDuration = plugin.getConfig().getInt("wind.duration.min_seconds", 30);
        maxWindDuration = plugin.getConfig().getInt("wind.duration.max_seconds", 120);
    }

    private void initializeBannedBlocks() {
        // Add blocks that shouldn't count as solid ceiling
        bannedBlocks.addAll(Arrays.asList(
                Material.SNOW, Material.LADDER, Material.VINE,
                Material.TORCH, Material.WALL_TORCH,
                Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH,
                Material.SOUL_TORCH, Material.SOUL_WALL_TORCH
        ));

        // Add all carpet types
        for (Material mat : Material.values()) {
            if (mat.name().contains("_CARPET")) {
                bannedBlocks.add(mat);
            }
        }

        // Add all leaf types - leaves shouldn't count as solid ceiling
        for (Material mat : Material.values()) {
            if (mat.name().contains("_LEAVES")) {
                bannedBlocks.add(mat);
            }
        }
    }

    private void startWindSystem() {
        if (!windEnabled) return;

        windTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateWind, 0L, 1L);
    }

    private void updateWind() {
        for (World world : Bukkit.getWorlds()) {
            updateWorldWind(world);
        }
    }

    private void updateWorldWind(World world) {
        WindData windData = worldWindData.get(world);
        if (windData == null) {
            windData = new WindData();
            worldWindData.put(world, windData);
        }

        // Check for wind events periodically
        Integer cooldown = windCheckCooldown.get(world);
        if (cooldown == null || cooldown <= 0) {
            // Check if wind should start (every 30 seconds)
            windCheckCooldown.put(world, 600); // 30 seconds * 20 ticks

            if (!windData.isWindActive()) {
                checkForWindEvent(world, windData);
            }
        } else {
            windCheckCooldown.put(world, cooldown - 1);
        }

        // Update existing wind
        windData.update();

        if (!windData.isWindActive()) {
            return; // No wind event active
        }

        // Update wind direction occasionally - seasonal influence
        if (random.nextInt(200) == 0) { // Change direction every ~10 seconds
            windData.updateDirection(random, weatherForecast.getCurrentSeason(world));
        }

        // Process players
        for (Player player : world.getPlayers()) {
            // Check minimum height requirement for wind effects
            if (player.getLocation().getBlockY() < minWindHeight) {
                continue;
            }

            if (player.isDead() || isPlayerIndoors(player)) {
                continue;
            }

            createWindEffects(player, windData);
        }
    }

    private void checkForWindEvent(World world, WindData windData) {
        double chance = getWindChanceForWeather(world);
        double roll = random.nextDouble() * 100.0; // 0-100

        if (roll <= chance) {
            // Start wind event!
            int duration = minWindDuration + random.nextInt(maxWindDuration - minWindDuration + 1);
            double intensity = getWindIntensityForWeather(world);

            // Seasonal duration modifiers
            Season currentSeason = weatherForecast.getCurrentSeason(world);
            if (currentSeason != null) {
                switch (currentSeason) {
                    case WINTER:
                        duration = (int) (duration * 1.3); // 30% longer in winter
                        break;
                    case SPRING:
                        duration = (int) (duration * 1.1); // 10% longer in spring
                        break;
                    case FALL: // Note: RealisticSeasons uses FALL, not AUTUMN
                        duration = (int) (duration * 1.2); // 20% longer in autumn
                        break;
                    // Summer keeps default duration
                }
            }

            windData.startWindEvent(duration * 20, intensity); // Convert to ticks

            // Notify players in the world (only those with permission)
            String weatherType = getWeatherName(world);
            String seasonStr = currentSeason != null ? " (" + currentSeason.toString().toLowerCase() + ")" : "";

            for (Player player : world.getPlayers()) {
                if (player.hasPermission("orbisclimate.notifications")) {
                    player.sendMessage("§6[OrbisClimate] §aWind event started! " +
                            "§7(" + weatherType + seasonStr + " - " + duration + "s)");
                }
            }
        }
    }

    private double getWindChanceForWeather(World world) {
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
        Season currentSeason = weatherForecast.getCurrentSeason(world);

        double baseChance;
        switch (currentWeather) {
            case THUNDERSTORM:
                baseChance = thunderstormChance;
                break;
            case HEAVY_RAIN:
            case LIGHT_RAIN:
                baseChance = rainChance;
                break;
            case SNOW:
                baseChance = snowChance;
                break;
            case BLIZZARD:
                baseChance = thunderstormChance * 0.8; // Slightly less than thunderstorm
                break;
            case CLEAR:
            default:
                baseChance = clearWeatherChance;
                break;
        }

        // Seasonal modifiers
        if (currentSeason != null) {
            switch (currentSeason) {
                case WINTER:
                    baseChance *= 1.4; // 40% more wind in winter
                    break;
                case SPRING:
                    baseChance *= 1.2; // 20% more wind in spring
                    break;
                case FALL: // Note: RealisticSeasons uses FALL, not AUTUMN
                    baseChance *= 1.3; // 30% more wind in autumn
                    break;
                case SUMMER:
                    baseChance *= 0.8; // 20% less wind in summer
                    break;
            }
        }

        return Math.min(100.0, baseChance); // Cap at 100%
    }

    private double getWindIntensityForWeather(World world) {
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
        Season currentSeason = weatherForecast.getCurrentSeason(world);

        double baseIntensity;
        switch (currentWeather) {
            case THUNDERSTORM:
                baseIntensity = 1.0; // 100% intensity
                break;
            case BLIZZARD:
                baseIntensity = 0.9; // 90% intensity
                break;
            case HEAVY_RAIN:
                baseIntensity = 0.4; // 40% intensity for heavy rain
                break;
            case LIGHT_RAIN:
                baseIntensity = 0.25; // 25% intensity for light rain
                break;
            case SNOW:
                baseIntensity = 0.2; // 20% intensity for light snow
                break;
            case CLEAR:
            default:
                baseIntensity = 0.1; // 10% intensity
                break;
        }

        // Seasonal intensity modifiers
        if (currentSeason != null) {
            switch (currentSeason) {
                case WINTER:
                    baseIntensity *= 1.2; // 20% more intense in winter
                    break;
                case SPRING:
                    baseIntensity *= 1.1; // 10% more intense in spring
                    break;
                case FALL: // Note: RealisticSeasons uses FALL, not AUTUMN
                    baseIntensity *= 1.15; // 15% more intense in autumn
                    break;
                // Summer keeps base intensity
            }
        }

        return Math.min(1.0, baseIntensity); // Cap at 100%
    }

    private String getWeatherName(World world) {
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
        return currentWeather.getDisplayName();
    }

    public boolean isPlayerIndoors(Player player) {
        Location loc = player.getLocation();

        // Multi-directional shelter detection - check if player has solid blocks protecting them
        return hasOverheadShelter(loc) || hasDirectionalShelter(loc);
    }

    private boolean hasOverheadShelter(Location loc) {
        // Check directly above player
        Block block = loc.clone().add(0, 2, 0).getBlock();
        for (int i = 0; i < interiorHeightDistance; i++) {
            if (block.getType().isSolid() && !bannedBlocks.contains(block.getType())) {
                return true;
            }
            block = block.getRelative(BlockFace.UP);
        }
        return false;
    }

    private boolean hasDirectionalShelter(Location loc) {
        // Check for walls/shelter in multiple directions (like DeadlyDisasters does)
        Vector[] directions = {
                new Vector(1, 0, 0),   // East
                new Vector(-1, 0, 0),  // West
                new Vector(0, 0, 1),   // South
                new Vector(0, 0, -1),  // North
                new Vector(1, 0, 1),   // Southeast
                new Vector(-1, 0, 1),  // Southwest
                new Vector(1, 0, -1),  // Northeast
                new Vector(-1, 0, -1)  // Northwest
        };

        int shelterCount = 0;
        for (Vector direction : directions) {
            if (hasShelterInDirection(loc, direction, 5)) {
                shelterCount++;
            }
        }

        // If player has shelter in at least 4 directions, consider them indoors
        return shelterCount >= 4;
    }

    private boolean hasShelterInDirection(Location loc, Vector direction, int maxDistance) {
        Location checkLoc = loc.clone();
        for (int i = 1; i <= maxDistance; i++) {
            checkLoc.add(direction);
            Block block = checkLoc.getBlock();

            if (block.getType().isSolid() && !bannedBlocks.contains(block.getType())) {
                // Check vertically too - need ceiling protection
                Block aboveBlock = block.getRelative(BlockFace.UP);
                if (aboveBlock.getType().isSolid() && !bannedBlocks.contains(aboveBlock.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasActiveWind(World world) {
        WindData windData = worldWindData.get(world);
        return windData != null && windData.isWindActive();
    }

    private void createWindEffects(Player player, WindData windData) {
        Location loc = player.getLocation();
        Vector windDirection = windData.getWindDirection();
        double force = windData.getCurrentForce();

        // Add 2-second delay between wind gusts
        if (windData.getLastGustTime() + 40 > System.currentTimeMillis() / 50) { // 40 ticks = 2 seconds
            return; // Skip this gust, too soon
        }
        windData.setLastGustTime(System.currentTimeMillis() / 50);

        // Create particle effects
        createWindParticles(player, windDirection, force);

        // Play wind sounds occasionally with seasonal variation
        if (random.nextInt(60) == 0) { // Every ~3 seconds
            playSeasonalWindSound(player, force);
        }
    }

    private void playSeasonalWindSound(Player player, double force) {
        Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());

        Location loc = player.getLocation();

        // Dynamic volume and pitch based on wind force
        float baseVolume = 0.3f;
        float maxVolume = 1.0f;
        float volume = (float) (baseVolume + (force * (maxVolume - baseVolume)));

        float basePitch = 0.5f;
        float maxPitch = 1.2f;
        float pitch = (float) (basePitch + (force * (maxPitch - basePitch)));

        // Choose sound based on weather intensity
        Sound windSound;
        if (force > 0.7) {
            // Strong wind - use weather rain sound
            windSound = Sound.WEATHER_RAIN;
            pitch *= 0.8f; // Lower pitch for stronger winds
        } else if (force > 0.4) {
            // Medium wind - use ambient weather sound
            windSound = Sound.WEATHER_RAIN_ABOVE;
            pitch *= 0.9f;
        } else {
            // Light wind - use item sounds for subtle effect
            windSound = Sound.ITEM_ELYTRA_FLYING;
            volume *= 0.5f; // Quieter for light winds
            pitch *= 1.1f;  // Higher pitch for gentler sound
        }

        // Seasonal sound modifications
        if (currentWeather == WeatherForecast.WeatherType.SNOW || currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
            // Snow/winter wind sounds - lower and more haunting
            pitch *= 0.7f;
            volume *= 1.2f; // Slightly louder for blizzard effect
        } else if (currentSeason == Season.WINTER) {
            pitch *= 0.85f; // Slightly lower for winter
        } else if (currentSeason == Season.SUMMER) {
            pitch *= 1.1f; // Slightly higher for summer breeze
            volume *= 0.8f; // Quieter for gentler summer winds
        }

        // Cap the values to prevent audio issues
        volume = Math.min(Math.max(volume, 0.1f), 2.0f);
        pitch = Math.min(Math.max(pitch, 0.5f), 2.0f);

        player.playSound(loc, windSound, volume, pitch);
    }

    private void createWindParticles(Player player, Vector windDirection, double force) {
        Location loc = player.getLocation();
        Biome biome = loc.getBlock().getBiome();
        Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());

        // Performance optimization: reduce particles based on nearby player count
        int nearbyPlayers = (int) player.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(player.getLocation()) <= particleRange * 2)
                .count();

        // Reduce particle count if many players are nearby
        double performanceMultiplier = Math.max(0.3, 1.0 / Math.max(1, nearbyPlayers - 1));

        // Seasonal particle count modifiers
        double seasonalMultiplier = 1.0;
        if (currentSeason != null) {
            switch (currentSeason) {
                case WINTER:
                    seasonalMultiplier = 1.3; // More particles in winter
                    break;
                case SPRING:
                case FALL: // Note: RealisticSeasons uses FALL, not AUTUMN
                    seasonalMultiplier = 1.1; // Slightly more in transitional seasons
                    break;
            }
        }

        // Make particles more subtle - reduce base count and make force influence less dramatic
        int baseParticleCount = (int) (maxParticles * 0.4 * seasonalMultiplier);
        int particleCount = (int) (baseParticleCount * force * performanceMultiplier);

        // Ensure minimum particles for visibility but cap the maximum
        particleCount = Math.max(5, Math.min(particleCount, maxParticles / 2));

        // Determine particle type and colors based on biome and season
        BiomeParticleData particleData = getBiomeParticleData(biome, currentSeason, currentWeather);

        for (int i = 0; i < particleCount; i++) {
            // Create particles around the player
            double offsetX = (random.nextDouble() - 0.5) * particleRange;
            double offsetY = random.nextDouble() * 4 - 0.5; // -0.5 to 3.5 blocks high
            double offsetZ = (random.nextDouble() - 0.5) * particleRange;

            Location particleLoc = loc.clone().add(offsetX, offsetY, offsetZ);

            // More subtle particle velocity - reduced speed
            Vector velocity = windDirection.clone().multiply(force * 0.8);

            // Spawn biome and season-specific particles
            spawnBiomeParticle(player, particleLoc, velocity, force, particleData, true);

            // Add some variety with secondary particle types (less frequent)
            if (random.nextInt(5) == 0) {
                spawnBiomeParticle(player, particleLoc, velocity, force, particleData, false);
            }
        }
    }

    private BiomeParticleData getBiomeParticleData(Biome biome, Season season, WeatherForecast.WeatherType weather) {
        // Weather-specific overrides
        if (weather == WeatherForecast.WeatherType.SNOW || weather == WeatherForecast.WeatherType.BLIZZARD) {
            return new BiomeParticleData(
                    Particle.DUST_COLOR_TRANSITION,
                    Color.fromRGB(255, 255, 255), // Pure white
                    Color.fromRGB(220, 240, 255), // Slight blue tint
                    Particle.SNOWFLAKE // Secondary particle
            );
        }

        // Season-influenced biome particles
        switch (biome) {
            // Desert biomes - sand particles
            case DESERT:
                if (season == Season.WINTER) {
                    // Cooler desert colors in winter
                    return new BiomeParticleData(
                            Particle.DUST_COLOR_TRANSITION,
                            Color.fromRGB(220, 180, 150),
                            Color.fromRGB(180, 130, 90),
                            Particle.ASH
                    );
                }
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(237, 201, 175), // Light sand color
                        Color.fromRGB(194, 154, 108), // Darker sand color
                        Particle.ASH // Secondary particle
                );

            // Cold/Snow biomes - always snow particles
            case TAIGA:
            case SNOWY_PLAINS:
            case SNOWY_SLOPES:
            case SNOWY_TAIGA:
            case SNOWY_BEACH:
            case FROZEN_RIVER:
            case FROZEN_OCEAN:
            case DEEP_FROZEN_OCEAN:
            case ICE_SPIKES:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(255, 255, 255), // Pure white
                        Color.fromRGB(220, 240, 255), // Slight blue tint
                        Particle.SNOWFLAKE // Secondary particle
                );

            // Forest biomes - gray particles
            case FOREST:
            case BIRCH_FOREST:
            case PLAINS:
            case DARK_FOREST:
            case FLOWER_FOREST:
            case SAVANNA_PLATEAU:
            case SAVANNA:
            case WINDSWEPT_FOREST:
            case WINDSWEPT_HILLS:
            case WINDSWEPT_SAVANNA:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(169, 169, 169), // Light gray
                        Color.fromRGB(105, 105, 105), // Dark gray
                        Particle.ASH
                );

            // Swamp biomes - gray particles
            case SWAMP:
            case MANGROVE_SWAMP:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(169, 169, 169), // Light gray
                        Color.fromRGB(105, 105, 105), // Dark gray
                        Particle.ASH
                );

            // Ocean/Beach biomes - white particles
            case OCEAN:
            case DEEP_OCEAN:
            case WARM_OCEAN:
            case LUKEWARM_OCEAN:
            case COLD_OCEAN:
            case BEACH:
            case RIVER:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(255, 255, 255), // White
                        Color.fromRGB(240, 248, 255), // Alice blue (very light blue-white)
                        Particle.RAIN // Secondary particle
                );

            // Plains and other biomes - white particles
            default:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(255, 255, 255), // White
                        Color.fromRGB(245, 245, 245), // White smoke
                        Particle.ASH // Secondary particle
                );
        }
    }

    private void spawnBiomeParticle(Player player, Location particleLoc, Vector velocity,
                                    double force, BiomeParticleData particleData, boolean isPrimary) {

        Particle particleType = isPrimary ? particleData.primaryParticle : particleData.secondaryParticle;

        if (particleType == Particle.DUST_COLOR_TRANSITION) {
            // Create dust color transition data
            Particle.DustTransition dustTransition = new Particle.DustTransition(
                    particleData.fromColor, particleData.toColor, (float) (0.8 + force * 0.4)
            );

            player.spawnParticle(particleType, particleLoc, 0,
                    velocity.getX(), velocity.getY() * 0.05, velocity.getZ(), 0, dustTransition);
        } else {
            // Regular particle spawning
            double forceMultiplier = isPrimary ? force * 0.5 : force * 0.3;
            player.spawnParticle(particleType, particleLoc, 0,
                    velocity.getX(), velocity.getY() * 0.05, velocity.getZ(), forceMultiplier);
        }
    }

    // Inner class to hold biome particle data
    private static class BiomeParticleData {
        final Particle primaryParticle;
        final Color fromColor;
        final Color toColor;
        final Particle secondaryParticle;

        public BiomeParticleData(Particle primaryParticle, Color fromColor, Color toColor, Particle secondaryParticle) {
            this.primaryParticle = primaryParticle;
            this.fromColor = fromColor;
            this.toColor = toColor;
            this.secondaryParticle = secondaryParticle;
        }
    }

    public void shutdown() {
        if (windTask != null) {
            windTask.cancel();
        }
        worldWindData.clear();
    }

    public void reloadConfig() {
        loadConfig();
        plugin.getLogger().info("Wind configuration reloaded!");
    }

    // Inner class to track wind data per world
    private static class WindData {
        private Vector windDirection;
        private double windIntensity;
        private double currentForce;
        private double[] oscillation = {0, 0.015}; // [current, increment] - slower oscillation
        private int gustPhase = 0; // Track gust phases
        private int windDuration; // Remaining ticks
        private boolean windActive;
        private long lastGustTime; // For 2-second delay between gusts

        public WindData() {
            // Set wind to blow in one of the four cardinal directions
            Random rand = new Random();
            int direction = rand.nextInt(4);
            switch (direction) {
                case 0: // North
                    windDirection = new Vector(0, 0, -1);
                    break;
                case 1: // East
                    windDirection = new Vector(1, 0, 0);
                    break;
                case 2: // South
                    windDirection = new Vector(0, 0, 1);
                    break;
                case 3: // West
                    windDirection = new Vector(-1, 0, 0);
                    break;
            }
            windIntensity = 0;
            currentForce = 0;
            windDuration = 0;
            windActive = false;
            lastGustTime = 0;
        }

        public void startWindEvent(int durationTicks, double intensity) {
            windDuration = durationTicks;
            windIntensity = intensity;
            windActive = true;
            oscillation[0] = 0; // Reset oscillation
            gustPhase = 0;
        }

        public void update() {
            if (windActive) {
                windDuration--;
                if (windDuration <= 0) {
                    windActive = false;
                    windIntensity = 0;
                    currentForce = 0;
                    return;
                }

                // Realistic wind gusts with multiple phases
                updateWindGusts();
            }
        }

        private void updateWindGusts() {
            gustPhase++;

            // Create realistic wind pattern with varying intensities
            double gustCycle = gustPhase / 60.0; // 3-second cycles (60 ticks)
            double gustStrength = Math.sin(gustCycle) * 0.3; // Sine wave for smooth gusts

            // Add some randomness for natural variation
            if (gustPhase % 20 == 0) { // Every second, add small random variation
                gustStrength += (Math.random() - 0.5) * 0.1;
            }

            // Major gust every 5-8 seconds
            if (gustPhase % (100 + (int) (Math.random() * 60)) == 0) {
                gustStrength += 0.4 + (Math.random() * 0.3);
            }

            // Calculate final force with base intensity and gust effects
            currentForce = windIntensity * (0.7 + gustStrength); // Base 70% + gusts
            currentForce = Math.max(0, Math.min(currentForce, windIntensity * 1.5)); // Cap at 150% of base
        }

        public void updateDirection(Random random, Season season) {
            // Keep the wind direction fixed in cardinal directions
            // Only change direction occasionally to a new cardinal direction
            if (random.nextInt(1200) == 0) { // Very rarely change direction (once per minute)
                int direction = random.nextInt(4);
                switch (direction) {
                    case 0: // North
                        windDirection = new Vector(0, 0, -1);
                        break;
                    case 1: // East
                        windDirection = new Vector(1, 0, 0);
                        break;
                    case 2: // South
                        windDirection = new Vector(0, 0, 1);
                        break;
                    case 3: // West
                        windDirection = new Vector(-1, 0, 0);
                        break;
                }
            }
        }

        public Vector getWindDirection() {
            return windDirection.clone();
        }

        public double getCurrentForce() {
            return windActive ? currentForce : 0;
        }

        public boolean isWindActive() {
            return windActive;
        }

        public int getRemainingDuration() {
            return windDuration;
        }

        public long getLastGustTime() {
            return lastGustTime;
        }

        public void setLastGustTime(long time) {
            lastGustTime = time;
        }
    }
}