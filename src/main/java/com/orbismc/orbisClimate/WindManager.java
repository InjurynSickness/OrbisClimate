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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WindManager {

    private final OrbisClimate plugin;
    private final Random random;
    private BukkitTask windTask;

    // Wind configuration
    private int interiorHeightDistance;
    private int maxParticles;
    private double particleRange;
    private boolean windEnabled;
    private int minWindHeight;

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

    // Performance optimizations
    private final ParticleBatch particleBatch = new ParticleBatch();
    private final Map<String, Boolean> locationExposureCache = new ConcurrentHashMap<>();
    private final Map<String, Long> exposureCacheTimestamps = new ConcurrentHashMap<>();
    private static final long EXPOSURE_CACHE_DURATION = 15000; // 15 seconds

    private final WeatherForecast weatherForecast;

    public WindManager(OrbisClimate plugin, Random random, WeatherForecast weatherForecast) {
        this.plugin = plugin;
        this.random = random;
        this.weatherForecast = weatherForecast;

        loadConfig();
        initializeBannedBlocks();
        startWindSystem();
    }

    // PERFORMANCE OPTIMIZATION: Batch particle processing
    private static class ParticleBatch {
        private final List<ParticleData> particles = new ArrayList<>();
        private static final int MAX_BATCH_SIZE = 50;
        
        public void addParticle(Player player, Location loc, Particle type, Object data, Vector velocity) {
            particles.add(new ParticleData(player, loc, type, data, velocity));
            
            if (particles.size() >= MAX_BATCH_SIZE) {
                flush();
            }
        }
        
        public void flush() {
            if (particles.isEmpty()) return;
            
            // Group particles by player for better performance
            Map<Player, List<ParticleData>> playerParticles = particles.stream()
                .collect(Collectors.groupingBy(p -> p.player));
                
            for (Map.Entry<Player, List<ParticleData>> entry : playerParticles.entrySet()) {
                Player player = entry.getKey();
                List<ParticleData> playerBatch = entry.getValue();
                
                // Spawn all particles for this player in one batch
                for (ParticleData p : playerBatch) {
                    if (p.data != null) {
                        player.spawnParticle(p.type, p.location, 1, 
                            p.velocity.getX(), p.velocity.getY(), p.velocity.getZ(), 0, p.data);
                    } else {
                        player.spawnParticle(p.type, p.location, 1,
                            p.velocity.getX(), p.velocity.getY(), p.velocity.getZ(), 0);
                    }
                }
            }
            
            particles.clear();
        }
        
        private static class ParticleData {
            final Player player;
            final Location location;
            final Particle type;
            final Object data;
            final Vector velocity;
            
            ParticleData(Player player, Location loc, Particle type, Object data, Vector velocity) {
                this.player = player;
                this.location = loc.clone();
                this.type = type;
                this.data = data;
                this.velocity = velocity.clone();
            }
        }
    }

    private void loadConfig() {
        interiorHeightDistance = plugin.getConfig().getInt("wind.interior_height_distance", 50);
        maxParticles = plugin.getConfig().getInt("wind.max_particles", 100);
        particleRange = plugin.getConfig().getDouble("wind.particle_range", 10.0);
        windEnabled = plugin.getConfig().getBoolean("wind.enabled", true);
        minWindHeight = plugin.getConfig().getInt("wind.min_height", 50);

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
        
        // Flush any remaining particles
        particleBatch.flush();
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
            return;
        }

        // Update wind direction occasionally - seasonal influence
        if (random.nextInt(200) == 0) {
            windData.updateDirection(random, weatherForecast.getCurrentSeason(world));
        }

        // Process players
        for (Player player : world.getPlayers()) {
            // Skip if player has particles disabled
            if (!plugin.isPlayerParticlesEnabled(player)) {
                continue;
            }
            
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
        double roll = random.nextDouble() * 100.0;

        if (roll <= chance) {
            int duration = minWindDuration + random.nextInt(maxWindDuration - minWindDuration + 1);
            double intensity = getWindIntensityForWeather(world);

            // Seasonal duration modifiers
            Season currentSeason = weatherForecast.getCurrentSeason(world);
            if (currentSeason != null) {
                switch (currentSeason) {
                    case WINTER:
                        duration = (int) (duration * 1.3);
                        break;
                    case SPRING:
                        duration = (int) (duration * 1.1);
                        break;
                    case FALL:
                        duration = (int) (duration * 1.2);
                        break;
                }
            }

            windData.startWindEvent(duration * 20, intensity);

            // Notify players
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
                baseChance = thunderstormChance * 0.8;
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
                    baseChance *= 1.4;
                    break;
                case SPRING:
                    baseChance *= 1.2;
                    break;
                case FALL:
                    baseChance *= 1.3;
                    break;
                case SUMMER:
                    baseChance *= 0.8;
                    break;
            }
        }

        return Math.min(100.0, baseChance);
    }

    private double getWindIntensityForWeather(World world) {
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
        Season currentSeason = weatherForecast.getCurrentSeason(world);

        double baseIntensity;
        switch (currentWeather) {
            case THUNDERSTORM:
                baseIntensity = 1.0;
                break;
            case BLIZZARD:
                baseIntensity = 0.9;
                break;
            case HEAVY_RAIN:
                baseIntensity = 0.4;
                break;
            case LIGHT_RAIN:
                baseIntensity = 0.25;
                break;
            case SNOW:
                baseIntensity = 0.2;
                break;
            case CLEAR:
            default:
                baseIntensity = 0.1;
                break;
        }

        // Seasonal intensity modifiers
        if (currentSeason != null) {
            switch (currentSeason) {
                case WINTER:
                    baseIntensity *= 1.2;
                    break;
                case SPRING:
                    baseIntensity *= 1.1;
                    break;
                case FALL:
                    baseIntensity *= 1.15;
                    break;
            }
        }

        return Math.min(1.0, baseIntensity);
    }

    private String getWeatherName(World world) {
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
        return currentWeather.getDisplayName();
    }

    // ENHANCED INDOOR DETECTION with caching
    public boolean isPlayerIndoors(Player player) {
        Location loc = player.getLocation();
        String locationKey = getLocationKey(loc);
        long currentTime = System.currentTimeMillis();
        
        // Check cache first
        Long cacheTime = exposureCacheTimestamps.get(locationKey);
        if (cacheTime != null && (currentTime - cacheTime) < EXPOSURE_CACHE_DURATION) {
            Boolean cached = locationExposureCache.get(locationKey);
            if (cached != null) {
                return !cached; // Cache stores exposure, we want indoor status
            }
        }

        // Calculate exposure
        boolean isExposed = hasOverheadShelter(loc) ? false : isLocationExposed(loc);
        
        // Cache result
        locationExposureCache.put(locationKey, isExposed);
        exposureCacheTimestamps.put(locationKey, currentTime);
        
        // Clean cache periodically
        if (random.nextInt(200) == 0) {
            cleanExposureCache(currentTime);
        }
        
        return !isExposed;
    }

    private String getLocationKey(Location loc) {
        // Round to 5-block chunks for caching
        int chunkX = loc.getBlockX() / 5;
        int chunkZ = loc.getBlockZ() / 5;
        int chunkY = loc.getBlockY() / 5;
        return loc.getWorld().getName() + ":" + chunkX + ":" + chunkY + ":" + chunkZ;
    }

    private void cleanExposureCache(long currentTime) {
        exposureCacheTimestamps.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > EXPOSURE_CACHE_DURATION) {
                locationExposureCache.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private boolean hasOverheadShelter(Location loc) {
        Block block = loc.clone().add(0, 2, 0).getBlock();
        for (int i = 0; i < interiorHeightDistance; i++) {
            if (block.getType().isSolid() && !bannedBlocks.contains(block.getType())) {
                return true;
            }
            block = block.getRelative(BlockFace.UP);
        }
        return false;
    }

    private boolean isLocationExposed(Location loc) {
        // Advanced raycast detection for complex structures
        Vector[] rayDirections = {
            new Vector(0, 1, 0),     // Up
            new Vector(1, 0.5, 0),   // Northeast up
            new Vector(-1, 0.5, 0),  // Northwest up
            new Vector(0, 0.5, 1),   // Southeast up
            new Vector(0, 0.5, -1),  // Southwest up
            new Vector(1, 0, 0),     // East
            new Vector(-1, 0, 0),    // West
            new Vector(0, 0, 1),     // South
            new Vector(0, 0, -1)     // North
        };
        
        int exposedRays = 0;
        int totalRays = rayDirections.length;
        
        for (Vector direction : rayDirections) {
            if (!isRayBlocked(loc, direction, 8)) {
                exposedRays++;
            }
        }
        
        // If more than 40% of rays are exposed, consider location exposed
        return (double) exposedRays / totalRays > 0.4;
    }

    private boolean isRayBlocked(Location start, Vector direction, int maxDistance) {
        Location current = start.clone();
        
        for (int i = 0; i < maxDistance; i++) {
            current.add(direction);
            Block block = current.getBlock();
            
            if (block.getType().isSolid() && !bannedBlocks.contains(block.getType())) {
                return true;
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

        // Add delay between wind gusts
        if (windData.getLastGustTime() + 40 > System.currentTimeMillis() / 50) {
            return;
        }
        windData.setLastGustTime(System.currentTimeMillis() / 50);

        // Create enhanced particle effects
        createWindTrails(player, windDirection, force);
        createWindParticles(player, windDirection, force);

        // Play wind sounds occasionally with seasonal variation
        if (random.nextInt(60) == 0) {
            playSeasonalWindSound(player, force);
        }
    }

    // NEW: Wind direction particle trails
    private void createWindTrails(Player player, Vector windDirection, double force) {
        int streamCount = (int) (3 + force * 5);
        
        for (int stream = 0; stream < streamCount; stream++) {
            createParticleStream(player, windDirection, force, stream);
        }
    }

    private void createParticleStream(Player player, Vector windDirection, double force, int streamIndex) {
        Location playerLoc = player.getLocation();
        
        // Create stream starting position
        double angle = (streamIndex * 360.0 / 8.0) + random.nextDouble() * 45;
        double startDistance = 8 + random.nextDouble() * 4;
        
        Vector startOffset = new Vector(
            Math.cos(Math.toRadians(angle)) * startDistance,
            random.nextDouble() * 3 + 1,
            Math.sin(Math.toRadians(angle)) * startDistance
        );
        
        Location streamStart = playerLoc.clone().add(startOffset);
        
        // Create particle trail flowing in wind direction
        int particlesInStream = (int) (6 + force * 4);
        Vector flowDirection = windDirection.clone().multiply(2.0);
        
        for (int i = 0; i < particlesInStream; i++) {
            Location particleLoc = streamStart.clone().add(
                flowDirection.clone().multiply(i * 0.5)
            );
            
            // Add random drift
            particleLoc.add(
                (random.nextDouble() - 0.5) * 0.8,
                (random.nextDouble() - 0.5) * 0.4,
                (random.nextDouble() - 0.5) * 0.8
            );
            
            Vector velocity = windDirection.clone().multiply(force * 0.6);
            
            BiomeParticleData particleData = getBiomeParticleData(
                particleLoc.getBlock().getBiome(), 
                weatherForecast.getCurrentSeason(player.getWorld()),
                weatherForecast.getCurrentWeather(player.getWorld())
            );
            
            spawnBiomeParticleOptimized(player, particleLoc, velocity, force, particleData, true);
        }
    }

    private void playSeasonalWindSound(Player player, double force) {
        Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());

        Location loc = player.getLocation();

        // Dynamic volume and pitch
        float baseVolume = 0.3f;
        float maxVolume = 1.0f;
        float volume = (float) (baseVolume + (force * (maxVolume - baseVolume)));

        float basePitch = 0.5f;
        float maxPitch = 1.2f;
        float pitch = (float) (basePitch + (force * (maxPitch - basePitch)));

        Sound windSound;
        if (force > 0.7) {
            windSound = Sound.WEATHER_RAIN;
            pitch *= 0.8f;
        } else if (force > 0.4) {
            windSound = Sound.WEATHER_RAIN_ABOVE;
            pitch *= 0.9f;
        } else {
            windSound = Sound.ITEM_ELYTRA_FLYING;
            volume *= 0.5f;
            pitch *= 1.1f;
        }

        // Seasonal sound modifications
        if (currentWeather == WeatherForecast.WeatherType.SNOW || currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
            pitch *= 0.7f;
            volume *= 1.2f;
        } else if (currentSeason == Season.WINTER) {
            pitch *= 0.85f;
        } else if (currentSeason == Season.SUMMER) {
            pitch *= 1.1f;
            volume *= 0.8f;
        }

        volume = Math.min(Math.max(volume, 0.1f), 2.0f);
        pitch = Math.min(Math.max(pitch, 0.5f), 2.0f);

        player.playSound(loc, windSound, volume, pitch);
    }

    private void createWindParticles(Player player, Vector windDirection, double force) {
        Location loc = player.getLocation();
        Biome biome = loc.getBlock().getBiome();
        Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());

        // PERFORMANCE OPTIMIZATION: Distance-based LOD
        int baseParticleCount = calculateLODParticleCount(player, maxParticles);
        
        // Seasonal particle count modifiers
        double seasonalMultiplier = 1.0;
        if (currentSeason != null) {
            switch (currentSeason) {
                case WINTER:
                    seasonalMultiplier = 1.3;
                    break;
                case SPRING:
                case FALL:
                    seasonalMultiplier = 1.1;
                    break;
            }
        }

        int particleCount = (int) (baseParticleCount * 0.4 * seasonalMultiplier * force);
        particleCount = Math.max(5, Math.min(particleCount, maxParticles / 2));

        BiomeParticleData particleData = getBiomeParticleData(biome, currentSeason, currentWeather);

        for (int i = 0; i < particleCount; i++) {
            double offsetX = (random.nextDouble() - 0.5) * particleRange;
            double offsetY = random.nextDouble() * 4 - 0.5;
            double offsetZ = (random.nextDouble() - 0.5) * particleRange;

            Location particleLoc = loc.clone().add(offsetX, offsetY, offsetZ);
            Vector velocity = windDirection.clone().multiply(force * 0.8);

            spawnBiomeParticleOptimized(player, particleLoc, velocity, force, particleData, true);

            if (random.nextInt(5) == 0) {
                spawnBiomeParticleOptimized(player, particleLoc, velocity, force, particleData, false);
            }
        }
    }

    // PERFORMANCE OPTIMIZATION: Distance-based particle count calculation
    private int calculateLODParticleCount(Player player, int baseCount) {
        int nearbyPlayers = 0;
        double avgDistance = 0;
        
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player)) {
                double distance = player.getLocation().distance(other.getLocation());
                if (distance <= particleRange * 3) {
                    nearbyPlayers++;
                    avgDistance += distance;
                }
            }
        }
        
        if (nearbyPlayers == 0) return baseCount;
        
        avgDistance /= nearbyPlayers;
        
        double densityMultiplier = Math.max(0.2, 1.0 / (1 + nearbyPlayers * 0.3));
        double distanceMultiplier = Math.max(0.3, 1.0 - (avgDistance / (particleRange * 2)));
        
        return (int) (baseCount * densityMultiplier * distanceMultiplier);
    }

    private BiomeParticleData getBiomeParticleData(Biome biome, Season season, WeatherForecast.WeatherType weather) {
        // Weather-specific overrides
        if (weather == WeatherForecast.WeatherType.SNOW || weather == WeatherForecast.WeatherType.BLIZZARD) {
            return new BiomeParticleData(
                    Particle.DUST_COLOR_TRANSITION,
                    Color.fromRGB(255, 255, 255),
                    Color.fromRGB(220, 240, 255),
                    Particle.SNOWFLAKE
            );
        }

        // Season-influenced biome particles
        switch (biome) {
            case DESERT:
                if (season == Season.WINTER) {
                    return new BiomeParticleData(
                            Particle.DUST_COLOR_TRANSITION,
                            Color.fromRGB(220, 180, 150),
                            Color.fromRGB(180, 130, 90),
                            Particle.ASH
                    );
                }
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(237, 201, 175),
                        Color.fromRGB(194, 154, 108),
                        Particle.ASH
                );

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
                        Color.fromRGB(255, 255, 255),
                        Color.fromRGB(220, 240, 255),
                        Particle.SNOWFLAKE
                );

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
                        Color.fromRGB(169, 169, 169),
                        Color.fromRGB(105, 105, 105),
                        Particle.ASH
                );

            case SWAMP:
            case MANGROVE_SWAMP:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(169, 169, 169),
                        Color.fromRGB(105, 105, 105),
                        Particle.ASH
                );

            case OCEAN:
            case DEEP_OCEAN:
            case WARM_OCEAN:
            case LUKEWARM_OCEAN:
            case COLD_OCEAN:
            case BEACH:
            case RIVER:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(255, 255, 255),
                        Color.fromRGB(240, 248, 255),
                        Particle.RAIN
                );

            default:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(255, 255, 255),
                        Color.fromRGB(245, 245, 245),
                        Particle.ASH
                );
        }
    }

    // OPTIMIZED: Use batch processing for particles
    private void spawnBiomeParticleOptimized(Player player, Location particleLoc, Vector velocity,
                                    double force, BiomeParticleData particleData, boolean isPrimary) {

        Particle particleType = isPrimary ? particleData.primaryParticle : particleData.secondaryParticle;

        if (particleType == Particle.DUST_COLOR_TRANSITION) {
            Particle.DustTransition dustTransition = new Particle.DustTransition(
                    particleData.fromColor, particleData.toColor, (float) (0.8 + force * 0.4)
            );
            particleBatch.addParticle(player, particleLoc, particleType, dustTransition, velocity);
        } else {
            particleBatch.addParticle(player, particleLoc, particleType, null, velocity);
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
        locationExposureCache.clear();
        exposureCacheTimestamps.clear();
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
        private double[] oscillation = {0, 0.015};
        private int gustPhase = 0;
        private int windDuration;
        private boolean windActive;
        private long lastGustTime;

        public WindData() {
            Random rand = new Random();
            int direction = rand.nextInt(4);
            switch (direction) {
                case 0:
                    windDirection = new Vector(0, 0, -1);
                    break;
                case 1:
                    windDirection = new Vector(1, 0, 0);
                    break;
                case 2:
                    windDirection = new Vector(0, 0, 1);
                    break;
                case 3:
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
            oscillation[0] = 0;
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

                updateWindGusts();
            }
        }

        private void updateWindGusts() {
            gustPhase++;

            double gustCycle = gustPhase / 60.0;
            double gustStrength = Math.sin(gustCycle) * 0.3;

            if (gustPhase % 20 == 0) {
                gustStrength += (Math.random() - 0.5) * 0.1;
            }

            if (gustPhase % (100 + (int)(Math.random() * 60)) == 0) {
                gustStrength += 0.4 + (Math.random() * 0.3);
            }

            currentForce = windIntensity * (0.7 + gustStrength);
            currentForce = Math.max(0, Math.min(currentForce, windIntensity * 1.5));
        }

        public void updateDirection(Random random, Season season) {
            if (random.nextInt(1200) == 0) {
                int direction = random.nextInt(4);
                switch (direction) {
                    case 0:
                        windDirection = new Vector(0, 0, -1);
                        break;
                    case 1:
                        windDirection = new Vector(1, 0, 0);
                        break;
                    case 2:
                        windDirection = new Vector(0, 0, 1);
                        break;
                    case 3:
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