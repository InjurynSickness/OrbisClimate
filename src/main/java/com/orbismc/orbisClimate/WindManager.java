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

public class WindManager {

    private final OrbisClimate plugin;
    private final Random random;
    private final WeatherForecast weatherForecast;
    private PerformanceMonitor performanceMonitor;
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

    public WindManager(OrbisClimate plugin, Random random, WeatherForecast weatherForecast) {
        this.plugin = plugin;
        this.random = random;
        this.weatherForecast = weatherForecast;
        this.performanceMonitor = plugin.getPerformanceMonitor();

        loadConfig();
        initializeBannedBlocks();
        startWindSystem();
    }

    // PERFORMANCE OPTIMIZATION: Batch particle processing
    private static class ParticleBatch {
        private final Map<Player, PlayerParticleQueue> playerQueues = new HashMap<>();
        private static final int MAX_PARTICLES_PER_PLAYER_PER_TICK = 20;
        private static final int BATCH_FLUSH_INTERVAL = 2; // Flush every 2 ticks
        private int tickCounter = 0;
        
        public void addParticle(Player player, Location loc, Particle type, Object data, Vector velocity) {
            PlayerParticleQueue queue = playerQueues.computeIfAbsent(player, k -> new PlayerParticleQueue());
            
            if (queue.size() >= MAX_PARTICLES_PER_PLAYER_PER_TICK) {
                return; // Drop particles if queue is full
            }
            
            queue.addParticle(new OptimizedParticleData(loc, velocity, type, data));
        }
        
        public void flush() {
            tickCounter++;
            boolean shouldFlush = tickCounter >= BATCH_FLUSH_INTERVAL;
            
            for (Map.Entry<Player, PlayerParticleQueue> entry : playerQueues.entrySet()) {
                Player player = entry.getKey();
                PlayerParticleQueue queue = entry.getValue();
                
                if (!player.isOnline()) {
                    continue;
                }
                
                if (shouldFlush || queue.isFull()) {
                    queue.flush(player);
                }
            }
            
            if (shouldFlush) {
                tickCounter = 0;
                // Clean up offline players
                playerQueues.entrySet().removeIf(entry -> !entry.getKey().isOnline());
            }
        }
        
        private static class PlayerParticleQueue {
            private final List<OptimizedParticleData> particles = new ArrayList<>();
            
            public void addParticle(OptimizedParticleData particle) {
                particles.add(particle);
            }
            
            public int size() {
                return particles.size();
            }
            
            public boolean isFull() {
                return particles.size() >= MAX_PARTICLES_PER_PLAYER_PER_TICK;
            }
            
            public void flush(Player player) {
                if (particles.isEmpty()) return;
                
                try {
                    // Spawn all particles for this player
                    for (OptimizedParticleData particle : particles) {
                        Location loc = particle.getLocation(player.getWorld());
                        Vector vel = particle.getVelocity();
                        
                        if (particle.hasExtraData()) {
                            Object data = particle.getExtraData();
                            player.spawnParticle(particle.getParticleType(), loc, 1,
                                vel.getX(), vel.getY(), vel.getZ(), 0, data);
                        } else {
                            player.spawnParticle(particle.getParticleType(), loc, 1,
                                vel.getX(), vel.getY(), vel.getZ(), 0);
                        }
                    }
                } catch (Exception e) {
                    // Silently handle any particle spawning errors
                } finally {
                    particles.clear();
                }
            }
        }
    }

    // Memory-optimized particle data structure
    private static final class OptimizedParticleData {
        private final float x, y, z;
        private final float vx, vy, vz;
        private final byte particleType;
        private final Object extraData;
        
        public OptimizedParticleData(Location loc, Vector velocity, Particle type, Object data) {
            this.x = (float) loc.getX();
            this.y = (float) loc.getY();
            this.z = (float) loc.getZ();
            this.vx = (float) velocity.getX();
            this.vy = (float) velocity.getY();
            this.vz = (float) velocity.getZ();
            this.particleType = (byte) type.ordinal();
            this.extraData = data;
        }
        
        public Location getLocation(World world) {
            return new Location(world, x, y, z);
        }
        
        public Vector getVelocity() {
            return new Vector(vx, vy, vz);
        }
        
        public Particle getParticleType() {
            return Particle.values()[particleType & 0xFF];
        }
        
        public boolean hasExtraData() {
            return extraData != null;
        }
        
        public Object getExtraData() {
            return extraData;
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

    // Wind direction particle trails
    private void createWindTrails(Player player, Vector windDirection, double force) {
        // Check if we should skip effects entirely
        if (performanceMonitor != null && performanceMonitor.shouldSkipEffects(player)) {
            return;
        }
        
        // Reduce stream complexity in performance mode
        double performanceMultiplier = performanceMonitor != null ? 
            performanceMonitor.getPerformanceMultiplier() : 1.0;
        
        int baseStreamCount = (int) (3 + force * 5);
        int streamCount = (int) (baseStreamCount * performanceMultiplier);
        streamCount = Math.max(1, streamCount);
        
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
        
        // Adjust particles in stream based on performance
        double performanceMultiplier = performanceMonitor != null ? 
            performanceMonitor.getPerformanceMultiplier() : 1.0;
            
        int baseParticlesInStream = (int) (6 + force * 4);
        int particlesInStream = (int) (baseParticlesInStream * performanceMultiplier);
        particlesInStream = Math.max(1, particlesInStream);
        
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
        // Check if we should skip effects entirely
        if (performanceMonitor != null && performanceMonitor.shouldSkipEffects(player)) {
            return;
        }
        
        Location loc = player.getLocation();
        Biome biome = loc.getBlock().getBiome();
        Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());

        // Get performance-adjusted particle count
        int baseParticleCount = calculateLODParticleCount(player, maxParticles);
        int adjustedParticleCount = performanceMonitor != null ? 
            performanceMonitor.getRecommendedParticleCount(baseParticleCount, player) : baseParticleCount;
        
        // Apply seasonal multiplier
        double seasonalMultiplier = getSeasonalMultiplier(currentSeason);
        int particleCount = (int) (adjustedParticleCount * 0.4 * seasonalMultiplier * force);
        particleCount = Math.max(2, Math.min(particleCount, maxParticles / 2));

        // REMOVED: Particle tracking for performance monitor

        BiomeParticleData particleData = getBiomeParticleData(biome, currentSeason, currentWeather);

        // Use view culling if enabled
        boolean useViewCulling = plugin.getConfig().getBoolean("performance.particles.use_view_culling", true);

        for (int i = 0; i < particleCount; i++) {
            double offsetX = (random.nextDouble() - 0.5) * particleRange;
            double offsetY = random.nextDouble() * 4 - 0.5;
            double offsetZ = (random.nextDouble() - 0.5) * particleRange;

            Location particleLoc = loc.clone().add(offsetX, offsetY, offsetZ);
            
            // Skip particles outside view if culling is enabled
            if (useViewCulling && !isParticleVisible(player, particleLoc)) {
                continue;
            }
            
            Vector velocity = windDirection.clone().multiply(force * 0.8);

            spawnBiomeParticleOptimized(player, particleLoc, velocity, force, particleData, true);

            if (random.nextInt(5) == 0) {
                spawnBiomeParticleOptimized(player, particleLoc, velocity, force, particleData, false);
            }
        }
    }

    // Helper method for seasonal multipliers
    private double getSeasonalMultiplier(Season season) {
        if (season == null) return 1.0;
        
        switch (season) {
            case WINTER:
                return 1.3;
            case SPRING:
            case FALL:
                return 1.1;
            case SUMMER:
            default:
                return 1.0;
        }
    }

    // Enhanced LOD calculation with better performance scaling
    private int calculateLODParticleCount(Player player, int baseCount) {
        if (player == null || !player.isOnline()) {
            return 0;
        }
        
        // Get performance multiplier first
        double performanceMultiplier = performanceMonitor != null ? 
            performanceMonitor.getPerformanceMultiplier() : 1.0;
        
        baseCount = (int) (baseCount * performanceMultiplier);
        
        Location playerLoc = player.getLocation();
        int nearbyPlayers = 0;
        double totalDistance = 0;
        
        // Use squared distance to avoid sqrt() calls
        double maxDistanceSquared = Math.pow(particleRange * 3, 2);
        
        try {
            for (Player other : player.getWorld().getPlayers()) {
                if (other == null || !other.isOnline() || other.equals(player)) {
                    continue;
                }
                
                double distanceSquared = playerLoc.distanceSquared(other.getLocation());
                if (distanceSquared <= maxDistanceSquared) {
                    nearbyPlayers++;
                    totalDistance += Math.sqrt(distanceSquared);
                }
            }
        } catch (Exception e) {
            return Math.max(1, baseCount / 2);
        }
        
        if (nearbyPlayers == 0) return baseCount;
        
        double avgDistance = totalDistance / nearbyPlayers;
        
        // More aggressive scaling for better performance
        double densityMultiplier = Math.max(0.1, 1.0 / (1 + nearbyPlayers * 0.4));
        double distanceMultiplier = Math.max(0.2, 1.0 - (avgDistance / (particleRange * 2)));
        
        int result = (int) (baseCount * densityMultiplier * distanceMultiplier);
        return Math.max(1, Math.min(result, baseCount));
    }

    // Enhanced visibility check with configurable FOV
    private boolean isParticleVisible(Player player, Location particleLocation) {
        Location playerLoc = player.getLocation();
        
        // Quick distance check first
        double distanceSquared = playerLoc.distanceSquared(particleLocation);
        double maxViewDistanceSquared = Math.pow(particleRange * 1.2, 2);
        
        if (distanceSquared > maxViewDistanceSquared) {
            return false;
        }
        
        // Skip FOV check in performance mode for better performance
        if (performanceMonitor != null && performanceMonitor.isPerformanceMode()) {
            return true;
        }
        
        // Check if particle is roughly in player's field of view
        Vector toParticle = particleLocation.toVector().subtract(playerLoc.toVector());
        if (toParticle.lengthSquared() < 0.01) return true;
        
        toParticle.normalize();
        Vector playerDirection = playerLoc.getDirection().normalize();
        
        double dotProduct = toParticle.dot(playerDirection);
        
        // Configurable FOV - default 120 degrees (dot product > -0.5)
        double fovThreshold = plugin.getConfig().getDouble("performance.particles.fov_threshold", -0.5);
        return dotProduct > fovThreshold;
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

            case BADLANDS:
            case ERODED_BADLANDS:
            case WOODED_BADLANDS:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(200, 150, 120),
                        Color.fromRGB(150, 100, 70),
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

    // Use batch processing for particles
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

    public void clearPlayerCache(Player player) {
        // Clear all player-specific cached data
        String playerWorld = player.getWorld().getName();
        locationExposureCache.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(playerWorld + ":"));
        exposureCacheTimestamps.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(playerWorld + ":"));
        
        // Clean up performance monitor data
        if (performanceMonitor != null) {
            performanceMonitor.cleanupPlayer(player);
        }
    }

    public void clearAllCaches() {
        locationExposureCache.clear();
        exposureCacheTimestamps.clear();
    }

    public void shutdown() {
        if (windTask != null) {
            windTask.cancel();
        }
        worldWindData.clear();
        clearAllCaches();
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