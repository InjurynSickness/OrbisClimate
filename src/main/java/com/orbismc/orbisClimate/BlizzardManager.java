package com.orbismc.orbisClimate;

import me.casperge.realisticseasons.api.SeasonsAPI;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BlizzardManager {

    private final OrbisClimate plugin;
    private final WeatherForecast weatherForecast;
    private final Random random;
    private SeasonsAPI seasonsAPI;
    private boolean realisticSeasonsEnabled;

    // Configuration
    private double blizzardDamage;
    private int minFreezingHeight;
    private int particleRange;
    private int particleYRange;
    private double particleMultiplier;
    private double temperatureThreshold;

    // Active blizzards per world
    private final Set<World> activeBlizzards = new HashSet<>();
    private final Map<World, BukkitTask> blizzardTasks = new HashMap<>();

    public BlizzardManager(OrbisClimate plugin, WeatherForecast weatherForecast) {
        this.plugin = plugin;
        this.weatherForecast = weatherForecast;
        this.random = new Random();

        // Check for RealisticSeasons
        if (Bukkit.getPluginManager().getPlugin("RealisticSeasons") != null) {
            try {
                seasonsAPI = SeasonsAPI.getInstance();
                realisticSeasonsEnabled = true;
            } catch (Exception e) {
                realisticSeasonsEnabled = false;
            }
        }

        loadConfig();
    }

    private void loadConfig() {
        blizzardDamage = plugin.getConfig().getDouble("blizzard.damage", 1.0);
        minFreezingHeight = plugin.getConfig().getInt("blizzard.min_freezing_height", 60);
        particleRange = plugin.getConfig().getInt("blizzard.particle_range", 20);
        particleYRange = plugin.getConfig().getInt("blizzard.particle_y_range", 30);
        particleMultiplier = plugin.getConfig().getDouble("blizzard.particle_multiplier", 1.0);
        temperatureThreshold = plugin.getConfig().getDouble("blizzard.temperature_threshold", 0.15);
    }

    public void startBlizzard(World world) {
        if (activeBlizzards.contains(world)) return;

        activeBlizzards.add(world);

        // Force storm weather
        world.setStorm(true);
        world.setThundering(false); // Blizzards don't have thunder

        // Start main blizzard effects task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processBlizzardEffects(world);
        }, 0L, 5L); // Run every 5 ticks (0.25 seconds)

        blizzardTasks.put(world, task);

        // Start async particle task for performance
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!activeBlizzards.contains(world)) return;
            processBlizzardParticles(world);
        }, 0L, 1L); // Run every tick for smooth particles
    }

    public void stopBlizzard(World world) {
        if (!activeBlizzards.contains(world)) return;

        activeBlizzards.remove(world);

        BukkitTask task = blizzardTasks.remove(world);
        if (task != null) {
            task.cancel();
        }
    }

    private void processBlizzardEffects(World world) {
        for (LivingEntity entity : world.getLivingEntities()) {
            Location loc = entity.getLocation();

            // Height requirement check
            if (loc.getBlockY() < minFreezingHeight) continue;

            // Biome check - only snowy biomes can have blizzards
            if (!isSnowyBiome(loc.getBlock().getBiome())) continue;

            // Temperature check
            if (!isLocationColdEnough(loc, entity)) continue;

            // Skip if entity is protected by region/indoor
            if (isEntityProtected(entity)) continue;

            // Apply blizzard effects
            applyBlizzardEffects(entity);

            // Extinguish nearby torches
            if (entity instanceof Player) {
                extinguishNearbyTorches((Player) entity);
            }
        }
    }

    private boolean isSnowyBiome(org.bukkit.block.Biome biome) {
        // Check if biome is a snowy/cold biome
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
                return true;
            case TAIGA:
            case OLD_GROWTH_SPRUCE_TAIGA:
            case OLD_GROWTH_PINE_TAIGA:
                // Taiga biomes can have blizzards if cold enough
                return true;
            default:
                return false;
        }
    }

    private boolean isLocationColdEnough(Location loc, LivingEntity entity) {
        if (realisticSeasonsEnabled && entity instanceof Player) {
            // Use RealisticSeasons temperature for players
            try {
                int temperature = seasonsAPI.getTemperature((Player) entity);
                return temperature <= -10; // Cold temperature threshold
            } catch (Exception e) {
                // Fallback to biome temperature
            }
        }

        // Use biome temperature as fallback
        return loc.getBlock().getTemperature() <= temperatureThreshold;
    }

    private boolean isEntityProtected(LivingEntity entity) {
        // Check if entity is indoors (you can integrate with your existing indoor detection)
        Location loc = entity.getLocation();
        Block block = loc.clone().add(0, 2, 0).getBlock();

        // Simple overhead protection check
        for (int i = 0; i < 10; i++) {
            if (block.getType().isSolid()) {
                return true; // Has overhead protection
            }
            block = block.getRelative(org.bukkit.block.BlockFace.UP);
        }

        return false;
    }

    private void applyBlizzardEffects(LivingEntity entity) {
        // Apply slowness effect
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, true, false), true);

        // Apply freezing damage and effect
        if (!entity.isInvulnerable()) {
            // Damage
            entity.damage(blizzardDamage);

            // Freezing effect (using wither for visual effect, you could create custom effect)
            entity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, false), true);
        }

        // Special effects for players
        if (entity instanceof Player) {
            Player player = (Player) entity;

            // Send cold messages occasionally
            if (random.nextInt(400) == 0) { // Reduced frequency: 1 in 400 chance per tick
                player.sendMessage("§b§lYou feel the bitter cold of the blizzard...");
            }
        }
    }

    private void extinguishNearbyTorches(Player player) {
        Location loc = player.getLocation();
        int range = 3; // 3 block radius around player

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();

                    if (isTorch(block.getType())) {
                        // Extinguish torch
                        block.setType(Material.AIR);

                        // Drop stick
                        ItemStack stick = new ItemStack(Material.STICK, 1);
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stick);

                        // Play extinguish sound
                        player.playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
                    }
                }
            }
        }
    }

    private boolean isTorch(Material material) {
        return material == Material.TORCH ||
                material == Material.WALL_TORCH ||
                material == Material.REDSTONE_TORCH ||
                material == Material.REDSTONE_WALL_TORCH ||
                material == Material.SOUL_TORCH ||
                material == Material.SOUL_WALL_TORCH;
    }

    private void processBlizzardParticles(World world) {
        for (Player player : world.getPlayers()) {
            if (!player.getWorld().equals(world)) continue;

            // Generate particles around player
            generateBlizzardParticles(player);
        }
    }

    private void generateBlizzardParticles(Player player) {
        Location playerLoc = player.getLocation();

        // Performance optimization - reduce particles based on distance and settings
        int actualRange = (int) (particleRange * particleMultiplier);

        for (int x = -actualRange; x <= actualRange; x++) {
            for (int z = -actualRange; z <= actualRange; z++) {
                // Skip some particles for performance
                if (random.nextDouble() > particleMultiplier) continue;

                Location particleLoc = playerLoc.clone().add(x, 0, z);

                // Get surface level
                Block surface = particleLoc.getWorld().getHighestBlockAt(particleLoc);
                particleLoc.setY(surface.getY());

                // Check Y range limit
                int yDiff = Math.abs(particleLoc.getBlockY() - playerLoc.getBlockY());
                if (yDiff > particleYRange) continue;

                // Check if location is cold enough for snow AND in snowy biome
                if (!isLocationColdEnough(particleLoc, player) || !isSnowyBiome(particleLoc.getBlock().getBiome())) continue;

                // Generate particles at different heights
                for (int i = 0; i < 3; i++) {
                    Location spawnLoc = particleLoc.clone().add(
                            random.nextDouble() - 0.5,
                            2 + (random.nextDouble() * 3), // 2-5 blocks above surface
                            random.nextDouble() - 0.5
                    );

                    // Create falling snow particles
                    player.spawnParticle(Particle.CLOUD, spawnLoc, 1,
                            0.1, // X spread
                            0.0, // Y spread
                            0.1, // Z spread
                            0.02 // Speed (slow falling)
                    );

                    // Add some snowflake particles for effect
                    if (random.nextInt(3) == 0) {
                        player.spawnParticle(Particle.SNOWFLAKE, spawnLoc, 1,
                                0.2, 0.2, 0.2, 0.01);
                    }
                }

                // Edge particles for storm effect
                if (Math.abs(x) == actualRange || Math.abs(z) == actualRange) {
                    player.spawnParticle(Particle.CLOUD, particleLoc.add(0.5, 4, 0.5), 3,
                            0.5, 1.0, 0.5, 0.1);
                }
            }
        }

        // Play wind sounds occasionally
        if (random.nextInt(60) == 0) { // Every ~3 seconds
            player.playSound(playerLoc, Sound.WEATHER_RAIN_ABOVE, 0.8f, 0.4f);
        }
    }

    public boolean isBlizzardActive(World world) {
        return activeBlizzards.contains(world);
    }

    public void checkForBlizzards() {
        for (World world : Bukkit.getWorlds()) {
            WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);

            if (currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
                if (!isBlizzardActive(world)) {
                    startBlizzard(world);
                }
            } else {
                if (isBlizzardActive(world)) {
                    stopBlizzard(world);
                }
            }
        }
    }

    public void shutdown() {
        // Stop all active blizzards
        for (World world : new HashSet<>(activeBlizzards)) {
            stopBlizzard(world);
        }
    }

    public void reloadConfig() {
        loadConfig();
    }
}