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

            plugin.getLogger().info("Wind event started in " + world.getName() +
                    " (" + weatherType + seasonStr + ") for " + duration + " seconds");
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
        Block block = loc.clone().add(0, 2, 0).getBlock();

        // Check for solid ceiling within the configured distance
        for (int i = 0; i < interiorHeightDistance; i++) {
            if (block.getType().isSolid() && !bannedBlocks.contains(block.getType())) {
                return true; // Found a solid ceiling, player is indoors
            }
            block = block.getRelative(BlockFace.UP);
        }

        return false; // No ceiling found, player is outdoors
    }

    public boolean hasActiveWind(World world) {
        WindData windData = worldWindData.get(world);
        return windData != null && windData.isWindActive();
    }

    private void createWindEffects(Player player, WindData windData) {
        Location loc = player.getLocation();
        Vector windDirection = windData.getWindDirection();
        double force = windData.getCurrentForce();

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
        float volume = (float) (force * 0.3);
        float pitch = 0.5f + (float) (force * 0.3);

        // Choose sound based on season and weather
        Sound windSound = Sound.WEATHER_RAIN; // Default

        if (currentWeather == WeatherForecast.WeatherType.SNOW || currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
            // Snow/winter wind sounds
            windSound = Sound.WEATHER_RAIN;
            pitch *= 0.8f; // Lower pitch for winter winds
        } else if (currentSeason == Season.WINTER) {
            pitch *= 0.9f; // Slightly lower for winter
        } else if (currentSeason == Season.SUMMER) {
            pitch *= 1.1f; // Slightly higher for summer
        }

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

            // Forest biomes - seasonal leaf colors
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
                if (season == Season.FALL) { // Note: RealisticSeasons uses FALL, not AUTUMN
                    // Orange/red leaf particles in autumn
                    return new BiomeParticleData(
                            Particle.DUST_COLOR_TRANSITION,
                            Color.fromRGB(255, 165, 0),  // Orange
                            Color.fromRGB(139, 69, 19),  // Brown
                            Particle.ASH
                    );
                } else if (season == Season.SPRING) {
                    // Green leaf particles in spring
                    return new BiomeParticleData(
                            Particle.DUST_COLOR_TRANSITION,
                            Color.fromRGB(144, 238, 144), // Light green
                            Color.fromRGB(34, 139, 34),   // Forest green
                            Particle.ASH
                    );
                }
                // Default forest particles
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(255, 255, 255), // White
                        Color.fromRGB(192, 192, 192), // Light gray
                        Particle.ASH
                );

            // Swamp biomes - murky particles
            case SWAMP:
            case MANGROVE_SWAMP:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(128, 128, 64),  // Murky green
                        Color.fromRGB(64, 64, 32),    // Dark murky
                        Particle.ASH
                );

            // Ocean/Beach biomes - salt spray
            case OCEAN:
            case DEEP_OCEAN:
            case WARM_OCEAN:
            case LUKEWARM_OCEAN:
            case COLD_OCEAN:
            case BEACH:
            case RIVER:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(255, 255, 255), // White foam
                        Color.fromRGB(176, 224, 230), // Light blue
                        Particle.RAIN // Secondary particle
                );

            // Plains and other biomes - default with seasonal variation
            default:
                if (season == Season.FALL) { // Note: RealisticSeasons uses FALL, not AUTUMN
                    return new BiomeParticleData(
                            Particle.DUST_COLOR_TRANSITION,
                            Color.fromRGB(210, 180, 140), // Tan
                            Color.fromRGB(160, 82, 45),   // Saddle brown
                            Particle.DUST_PLUME
                    );
                }
                return new BiomeParticleData(
                        Particle.ASH,
                        null,
                        null,
                        Particle.DUST_PLUME // Secondary particle
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
        private double[] oscillation = {0, 0.02}; // [current, increment]
        private int windDuration; // Remaining ticks
        private boolean windActive;

        public WindData() {
            Random rand = new Random();
            windDirection = new Vector(
                    rand.nextDouble() - 0.5,
                    0,
                    rand.nextDouble() - 0.5
            ).normalize();
            windIntensity = 0;
            windDuration = 0;
            windActive = false;
        }

        public void startWindEvent(int durationTicks, double intensity) {
            windDuration = durationTicks;
            windIntensity = intensity;
            windActive = true;
        }

        public void update() {
            if (windActive) {
                windDuration--;
                if (windDuration <= 0) {
                    windActive = false;
                    windIntensity = 0;
                }

                // Add oscillation for realistic wind gusts
                oscillation[0] += oscillation[1];
                if ((oscillation[1] > 0 && oscillation[0] > windIntensity * 0.3) ||
                        (oscillation[1] < 0 && oscillation[0] < -windIntensity * 0.3)) {
                    oscillation[1] *= -1;
                }
            }
        }

        public void updateDirection(Random random, Season season) {
            // Gradually change wind direction with seasonal influence
            double changeIntensity = 0.2;

            // Seasonal wind pattern influences
            if (season != null) {
                switch (season) {
                    case WINTER:
                        // More directional changes in winter (gusty)
                        changeIntensity = 0.3;
                        break;
                    case SPRING:
                        // Very variable in spring
                        changeIntensity = 0.35;
                        break;
                    case SUMMER:
                        // More stable in summer
                        changeIntensity = 0.15;
                        break;
                    case FALL: // Note: RealisticSeasons uses FALL, not AUTUMN
                        // Moderate changes in autumn
                        changeIntensity = 0.25;
                        break;
                }
            }

            Vector newDirection = new Vector(
                    windDirection.getX() + (random.nextDouble() - 0.5) * changeIntensity,
                    0,
                    windDirection.getZ() + (random.nextDouble() - 0.5) * changeIntensity
            ).normalize();

            windDirection = windDirection.clone().add(newDirection).normalize();
        }

        public Vector getWindDirection() {
            return windDirection.clone();
        }

        public double getCurrentForce() {
            if (!windActive) return 0;
            return Math.max(0, windIntensity + oscillation[0]);
        }

        public boolean isWindActive() {
            return windActive;
        }

        public int getRemainingDuration() {
            return windDuration;
        }
    }
}