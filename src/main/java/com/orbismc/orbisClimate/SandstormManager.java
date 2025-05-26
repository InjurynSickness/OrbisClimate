package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SandstormManager {

    private final OrbisClimate plugin;
    private final WeatherForecast weatherForecast;
    private final WindManager windManager;
    private final Random random;

    // Configuration
    private int minSandstormHeight;
    private int particleRange;
    private int particleYRange;
    private double particleMultiplier;
    private int blindnessDuration;
    private int slownessDuration;
    private int slownessAmplifier;

    // Active sandstorms per world
    private final Set<World> activeSandstorms = new HashSet<>();
    private final Map<World, BukkitTask> sandstormTasks = new HashMap<>();

    public SandstormManager(OrbisClimate plugin, WeatherForecast weatherForecast, WindManager windManager) {
        this.plugin = plugin;
        this.weatherForecast = weatherForecast;
        this.windManager = windManager;
        this.random = new Random();

        loadConfig();
    }

    private void loadConfig() {
        minSandstormHeight = plugin.getConfig().getInt("sandstorm.min_height", 62);
        particleRange = plugin.getConfig().getInt("sandstorm.particle_range", 15);
        particleYRange = plugin.getConfig().getInt("sandstorm.particle_y_range", 25);
        particleMultiplier = plugin.getConfig().getDouble("sandstorm.particle_multiplier", 1.5);
        blindnessDuration = plugin.getConfig().getInt("sandstorm.blindness_duration", 100);
        slownessDuration = plugin.getConfig().getInt("sandstorm.slowness_duration", 100);
        slownessAmplifier = plugin.getConfig().getInt("sandstorm.slowness_amplifier", 1);
    }

    public void startSandstorm(World world) {
        if (activeSandstorms.contains(world)) return;

        activeSandstorms.add(world);

        // Start main sandstorm effects task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processSandstormEffects(world);
        }, 0L, 10L); // Run every 10 ticks (0.5 seconds)

        sandstormTasks.put(world, task);

        // Start async particle task for performance
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!activeSandstorms.contains(world)) return;
            processSandstormParticles(world);
        }, 0L, 2L); // Run every 2 ticks for intensive particles
    }

    public void stopSandstorm(World world) {
        if (!activeSandstorms.contains(world)) return;

        activeSandstorms.remove(world);

        BukkitTask task = sandstormTasks.remove(world);
        if (task != null) {
            task.cancel();
        }
    }

    private void processSandstormEffects(World world) {
        for (LivingEntity entity : world.getLivingEntities()) {
            Location loc = entity.getLocation();

            // Height requirement check
            if (loc.getBlockY() < minSandstormHeight) continue;

            // Biome check - only desert biomes can have sandstorms
            if (!isDesertBiome(loc.getBlock().getBiome())) continue;

            // Use wind manager's indoor detection for consistency
            if (entity instanceof Player && windManager.isPlayerIndoors((Player) entity)) continue;

            // Apply sandstorm effects
            applySandstormEffects(entity);
        }
    }

    private boolean isDesertBiome(org.bukkit.block.Biome biome) {
        // Check if biome is a desert/sandy biome
        switch (biome) {
            case DESERT:
            case BADLANDS:
            case ERODED_BADLANDS:
            case WOODED_BADLANDS:
                return true;
            // Include 1.18+ variants if they exist
            default:
                // Check for any biome with "desert" or "badlands" in the name
                String biomeName = biome.name().toLowerCase();
                return biomeName.contains("desert") || biomeName.contains("badlands");
        }
    }

    private void applySandstormEffects(LivingEntity entity) {
        // Apply blindness effect (sand in eyes)
        entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 0, true, false), true);

        // Apply slowness effect (hard to move in sandstorm)
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessDuration, slownessAmplifier, true, false), true);

        // Special effects for players
        if (entity instanceof Player) {
            Player player = (Player) entity;

            // Send sandstorm messages occasionally
            if (random.nextInt(600) == 0) { // 1 in 600 chance per tick
                player.sendMessage("§6§lThe sandstorm whips around you, blinding your vision...");
            }
        }
    }

    private void processSandstormParticles(World world) {
        for (Player player : world.getPlayers()) {
            if (!player.getWorld().equals(world)) continue;

            // Generate intensive sand particles around player
            generateSandstormParticles(player);
        }
    }

    private void generateSandstormParticles(Player player) {
        Location playerLoc = player.getLocation();

        // Check if player is in desert biome
        if (!isDesertBiome(playerLoc.getBlock().getBiome())) return;

        // Check height requirement
        if (playerLoc.getBlockY() < minSandstormHeight) return;

        // Performance optimization - adjust particle count
        int actualRange = (int) (particleRange * particleMultiplier);

        // Create intensive particle spam like old system
        for (int i = 0; i < 50; i++) { // 50 particles per tick per player
            // Random location around player
            double offsetX = (random.nextDouble() - 0.5) * actualRange;
            double offsetY = random.nextDouble() * particleYRange;
            double offsetZ = (random.nextDouble() - 0.5) * actualRange;

            Location particleLoc = playerLoc.clone().add(offsetX, offsetY, offsetZ);

            // Create sand-colored dust particles
            Particle.DustOptions dustOptions = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(237, 201, 175), // Light sand color
                    1.0f
            );

            // Spawn dust particles with movement
            player.spawnParticle(Particle.DUST_PLUME, particleLoc, 1,
                    0.5, 0.3, 0.5, // Spread
                    0.1, // Speed
                    dustOptions
            );

            // Add some regular dust particles for density
            if (random.nextInt(3) == 0) {
                player.spawnParticle(Particle.ASH, particleLoc, 1,
                        0.3, 0.2, 0.3, 0.05);
            }
        }

        // Create swirling sand effect around player
        for (int i = 0; i < 20; i++) {
            double angle = (System.currentTimeMillis() / 50.0 + i * 18) % 360;
            double radians = Math.toRadians(angle);
            double radius = 3.0 + Math.sin(System.currentTimeMillis() / 1000.0) * 1.0;

            double x = playerLoc.getX() + Math.cos(radians) * radius;
            double y = playerLoc.getY() + 1.0 + Math.sin(System.currentTimeMillis() / 800.0 + i) * 0.5;
            double z = playerLoc.getZ() + Math.sin(radians) * radius;

            Location swirl = new Location(player.getWorld(), x, y, z);

            Particle.DustOptions swirlingDust = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(194, 154, 108), // Darker sand color
                    0.8f
            );

            player.spawnParticle(Particle.DUST_PLUME, swirl, 1,
                    0.1, 0.1, 0.1, 0.02, swirlingDust);
        }

        // Dense particle walls at range edges for storm effect
        if (random.nextInt(10) == 0) {
            for (int edge = 0; edge < 8; edge++) {
                double angle = edge * 45; // 8 directions
                double radians = Math.toRadians(angle);
                double distance = actualRange * 0.8;

                Location edgeLoc = playerLoc.clone().add(
                        Math.cos(radians) * distance,
                        random.nextDouble() * 4,
                        Math.sin(radians) * distance
                );

                // Dense particle wall
                for (int p = 0; p < 5; p++) {
                    Location wallParticle = edgeLoc.clone().add(
                            (random.nextDouble() - 0.5) * 2,
                            (random.nextDouble() - 0.5) * 2,
                            (random.nextDouble() - 0.5) * 2
                    );

                    Particle.DustOptions wallDust = new Particle.DustOptions(
                            org.bukkit.Color.fromRGB(160, 120, 80), // Dark sand
                            1.2f
                    );

                    player.spawnParticle(Particle.DUST_PLUME, wallParticle, 1,
                            0.3, 0.3, 0.3, 0.1, wallDust);
                }
            }
        }

        // Play sandstorm sounds occasionally
        if (random.nextInt(80) == 0) { // Every ~4 seconds
            player.playSound(playerLoc, Sound.WEATHER_RAIN, 0.6f, 0.3f); // Low pitch for sand sound
        }

        // Add wind whoosh sounds
        if (random.nextInt(120) == 0) { // Every ~6 seconds
            player.playSound(playerLoc, Sound.ITEM_ELYTRA_FLYING, 0.4f, 0.5f);
        }
    }

    public boolean isSandstormActive(World world) {
        return activeSandstorms.contains(world);
    }

    public void checkForSandstorms() {
        for (World world : Bukkit.getWorlds()) {
            WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);

            // Check if conditions are right for sandstorms
            boolean shouldHaveSandstorm = false;

            // Sandstorms can happen during various weather but are more likely in summer
            if (currentWeather == WeatherForecast.WeatherType.CLEAR ||
                    currentWeather == WeatherForecast.WeatherType.LIGHT_RAIN ||
                    currentWeather == WeatherForecast.WeatherType.HEAVY_RAIN) {

                // Check if it's summer for elevated chance
                Season currentSeason = weatherForecast.getCurrentSeason(world);

                // Base chance for sandstorms
                double sandstormChance = 0.1; // 10% base chance

                if (currentSeason == Season.SUMMER) {
                    sandstormChance = 0.25; // 25% chance in summer
                }

                // Only check for sandstorms if there are desert biomes and players in the world
                if (hasDesertBiomes(world) && !world.getPlayers().isEmpty()) {
                    shouldHaveSandstorm = random.nextDouble() < sandstormChance;
                }
            }

            if (shouldHaveSandstorm) {
                if (!isSandstormActive(world)) {
                    startSandstorm(world);
                }
            } else {
                if (isSandstormActive(world)) {
                    stopSandstorm(world);
                }
            }
        }
    }

    private boolean hasDesertBiomes(World world) {
        // Quick check if world has any players in desert biomes
        for (Player player : world.getPlayers()) {
            if (isDesertBiome(player.getLocation().getBlock().getBiome())) {
                return true;
            }
        }
        return false;
    }

    public void shutdown() {
        // Stop all active sandstorms
        for (World world : new HashSet<>(activeSandstorms)) {
            stopSandstorm(world);
        }
    }

    public void reloadConfig() {
        loadConfig();
    }
}