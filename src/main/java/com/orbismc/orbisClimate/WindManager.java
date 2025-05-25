package com.orbismc.orbisClimate;

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
    private double thunderstormChance;

    // Wind event duration
    private int minWindDuration;
    private int maxWindDuration;

    // Wind state
    private final Map<World, WindData> worldWindData = new HashMap<>();
    private final Map<World, Integer> windCheckCooldown = new HashMap<>();

    // Blocks that don't count as "solid ceiling"
    private final Set<Material> bannedBlocks = new HashSet<>();

    public WindManager(OrbisClimate plugin, Random random) {
        this.plugin = plugin;
        this.random = random;

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

        // Update wind direction occasionally
        if (random.nextInt(200) == 0) { // Change direction every ~10 seconds
            windData.updateDirection(random);
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

            windData.startWindEvent(duration * 20, intensity); // Convert to ticks

            // Notify players in the world
            String weatherType = getWeatherName(world);
            for (Player player : world.getPlayers()) {
                player.sendMessage("§6[OrbisClimate] §aWind event started! " +
                        "§7(" + weatherType + " - " + duration + "s)");
            }

            plugin.getLogger().info("Wind event started in " + world.getName() +
                    " (" + weatherType + ") for " + duration + " seconds");
        }
    }

    private double getWindChanceForWeather(World world) {
        if (world.isThundering()) {
            return thunderstormChance;
        } else if (world.hasStorm()) {
            return rainChance;
        } else {
            return clearWeatherChance;
        }
    }

    private double getWindIntensityForWeather(World world) {
        if (world.isThundering()) {
            return 1.0; // 100% intensity
        } else if (world.hasStorm()) {
            return 0.25; // 25% intensity
        } else {
            return 0.1; // 10% intensity
        }
    }

    private String getWeatherName(World world) {
        if (world.isThundering()) {
            return "Thunderstorm";
        } else if (world.hasStorm()) {
            return "Rain";
        } else {
            return "Clear";
        }
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

        // Play wind sounds occasionally
        if (random.nextInt(60) == 0) { // Every ~3 seconds
            float volume = (float) (force * 0.3);
            float pitch = 0.5f + (float) (force * 0.3);
            player.playSound(loc, Sound.WEATHER_RAIN, volume, pitch);
        }
    }

    private void createWindParticles(Player player, Vector windDirection, double force) {
        Location loc = player.getLocation();
        Biome biome = loc.getBlock().getBiome();

        // Performance optimization: reduce particles based on nearby player count
        int nearbyPlayers = (int) player.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(player.getLocation()) <= particleRange * 2)
                .count();

        // Reduce particle count if many players are nearby
        double performanceMultiplier = Math.max(0.3, 1.0 / Math.max(1, nearbyPlayers - 1));

        // Make particles more subtle - reduce base count and make force influence less dramatic
        int baseParticleCount = (int) (maxParticles * 0.4); // 40% of max particles
        int particleCount = (int) (baseParticleCount * force * performanceMultiplier);

        // Ensure minimum particles for visibility but cap the maximum
        particleCount = Math.max(5, Math.min(particleCount, maxParticles / 2));

        // Determine particle type and colors based on biome
        BiomeParticleData particleData = getBiomeParticleData(biome);

        for (int i = 0; i < particleCount; i++) {
            // Create particles around the player
            double offsetX = (random.nextDouble() - 0.5) * particleRange;
            double offsetY = random.nextDouble() * 4 - 0.5; // -0.5 to 3.5 blocks high
            double offsetZ = (random.nextDouble() - 0.5) * particleRange;

            Location particleLoc = loc.clone().add(offsetX, offsetY, offsetZ);

            // More subtle particle velocity - reduced speed
            Vector velocity = windDirection.clone().multiply(force * 0.8); // Reduced from 2 to 0.8

            // Spawn biome-specific particles
            spawnBiomeParticle(player, particleLoc, velocity, force, particleData, true);

            // Add some variety with secondary particle types (less frequent)
            if (random.nextInt(5) == 0) { // Reduced from 1 in 3 to 1 in 5
                spawnBiomeParticle(player, particleLoc, velocity, force, particleData, false);
            }
        }
    }

    private BiomeParticleData getBiomeParticleData(Biome biome) {
        switch (biome) {
            // Desert biomes - sand particles
            case DESERT:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(237, 201, 175), // Light sand color
                        Color.fromRGB(194, 154, 108), // Darker sand color
                        Particle.ASH // Secondary particle
                );

            // Cold/Snow biomes - snow particles
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

            // Forest biomes - leaf particles
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
            case SWAMP:
            case MANGROVE_SWAMP:
                return new BiomeParticleData(
                        Particle.DUST_COLOR_TRANSITION,
                        Color.fromRGB(34, 139, 34), // Forest green
                        Color.fromRGB(85, 107, 47), // Darker olive green
                        Particle.ASH // Secondary particle
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
                        Particle.DRIPPING_WATER // Secondary particle
                );

            // Plains and other biomes - default ash
            default:
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

        public void updateDirection(Random random) {
            // Gradually change wind direction
            Vector newDirection = new Vector(
                    windDirection.getX() + (random.nextDouble() - 0.5) * 0.2,
                    0,
                    windDirection.getZ() + (random.nextDouble() - 0.5) * 0.2
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