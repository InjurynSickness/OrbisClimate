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
import org.bukkit.util.Vector;

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
        world.setThundering(false);

        // Start main blizzard effects task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processBlizzardEffects(world);
        }, 0L, 5L);

        blizzardTasks.put(world, task);

        // Start async particle task for performance
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!activeBlizzards.contains(world)) return;
            processBlizzardParticles(world);
        }, 0L, 1L);
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

            // Enhanced player effects with messaging
            if (entity instanceof Player) {
                Player player = (Player) entity;
                
                // Extinguish nearby torches
                extinguishNearbyTorches(player);
                
                // Send immersive snow messages based on weather type
                WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);
                if (currentWeather == WeatherForecast.WeatherType.SNOW) {
                    sendSnowAccumulationMessages(player);
                } else if (currentWeather == WeatherForecast.WeatherType.BLIZZARD) {
                    sendBlizzardIntensityMessages(player);
                }
            }
        }
    }

    private boolean isSnowyBiome(org.bukkit.block.Biome biome) {
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
                return true;
            default:
                return false;
        }
    }

    private boolean isLocationColdEnough(Location loc, LivingEntity entity) {
        if (realisticSeasonsEnabled && entity instanceof Player) {
            try {
                int temperature = seasonsAPI.getTemperature((Player) entity);
                return temperature <= -10;
            } catch (Exception e) {
                // Fallback to biome temperature
            }
        }

        return loc.getBlock().getTemperature() <= temperatureThreshold;
    }

    private boolean isEntityProtected(LivingEntity entity) {
        Location loc = entity.getLocation();
        Block block = loc.clone().add(0, 2, 0).getBlock();

        for (int i = 0; i < 10; i++) {
            if (block.getType().isSolid()) {
                return true;
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
            entity.damage(blizzardDamage);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, false), true);
        }

        // Special effects for players
        if (entity instanceof Player) {
            Player player = (Player) entity;

            if (random.nextInt(400) == 0) {
                player.sendMessage("§b§lYou feel the bitter cold of the blizzard...");
            }
        }
    }

    private void extinguishNearbyTorches(Player player) {
        Location loc = player.getLocation();
        int range = 3;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();

                    if (isTorch(block.getType())) {
                        block.setType(Material.AIR);

                        ItemStack stick = new ItemStack(Material.STICK, 1);
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stick);

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

    // NEW: Snow accumulation messaging for regular snow weather
    private void sendSnowAccumulationMessages(Player player) {
        // Only send messages if enabled in config
        if (!plugin.getConfig().getBoolean("notifications.snow_accumulation_messages", true)) {
            return;
        }
        
        // Send immersive snow messages occasionally
        if (random.nextInt(800) == 0) { // Very rare messages
            String[] snowMessages = {
                "§f§lSnow begins to accumulate around your feet...",
                "§7§lThe falling snow creates a thin white layer on the ground.",
                "§f§lSnowflakes gather on nearby surfaces, painting the world white.",
                "§7§lA blanket of snow slowly covers the landscape around you.",
                "§f§lThe gentle snowfall creates a serene winter scene.",
                "§7§lSnow drifts begin to form against nearby obstacles."
            };
            
            String message = snowMessages[random.nextInt(snowMessages.length)];
            player.sendMessage("§6[OrbisClimate] " + message);
        }
    }

    // NEW: Intense blizzard messaging for blizzard weather
    private void sendBlizzardIntensityMessages(Player player) {
        // Send intense blizzard messages during blizzards
        if (random.nextInt(1200) == 0) { // Even rarer for blizzards
            String[] blizzardMessages = {
                "§b§lThe fierce blizzard whips snow into towering drifts!",
                "§f§lVisibility drops to near zero as the blizzard intensifies!",
                "§7§lThe howling wind drives snow deep into every crevice!",
                "§b§lSnow accumulates rapidly, transforming the landscape!",
                "§f§lThe relentless blizzard creates a winter wonderland!",
                "§7§lDrifts of snow pile high against any shelter!"
            };
            
            String message = blizzardMessages[random.nextInt(blizzardMessages.length)];
            player.sendMessage("§6[OrbisClimate] " + message);
        }
    }

    private void processBlizzardParticles(World world) {
        for (Player player : world.getPlayers()) {
            if (!player.getWorld().equals(world)) continue;
            
            // Skip if player has particles disabled
            if (!plugin.isPlayerParticlesEnabled(player)) {
                continue;
            }

            // Generate enhanced blizzard particles
            generateBlizzardParticles(player);
            
            // NEW: Create blizzard walls for immersive effect
            if (random.nextInt(4) == 0) { // 25% chance per tick
                createBlizzardWalls(player);
            }
        }
    }

    // NEW: Enhanced blizzard particle walls
    private void createBlizzardWalls(Player player) {
        Location playerLoc = player.getLocation();
        int wallCount = 4; // Four walls (N, S, E, W)
        
        for (int wall = 0; wall < wallCount; wall++) {
            createBlizzardWall(player, playerLoc, wall);
        }
    }

    private void createBlizzardWall(Player player, Location center, int wallIndex) {
        // Calculate wall position and orientation
        Vector wallDirection;
        Vector wallNormal;
        
        switch (wallIndex) {
            case 0: // North wall
                wallDirection = new Vector(1, 0, 0);
                wallNormal = new Vector(0, 0, 1);
                break;
            case 1: // South wall
                wallDirection = new Vector(1, 0, 0);
                wallNormal = new Vector(0, 0, -1);
                break;
            case 2: // East wall
                wallDirection = new Vector(0, 0, 1);
                wallNormal = new Vector(-1, 0, 0);
                break;
            case 3: // West wall
                wallDirection = new Vector(0, 0, 1);
                wallNormal = new Vector(1, 0, 0);
                break;
            default:
                return;
        }
        
        // Position wall at edge of effect range
        double wallDistance = particleRange * 0.8;
        Location wallCenter = center.clone().add(wallNormal.clone().multiply(-wallDistance));
        
        // Create wall particles
        int wallWidth = 15;
        int wallHeight = 6;
        
        for (int x = -wallWidth/2; x <= wallWidth/2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                if (random.nextInt(3) != 0) continue; // Sparse wall
                
                Location particleLoc = wallCenter.clone().add(
                    wallDirection.clone().multiply(x * 0.8)
                ).add(0, y * 0.5, 0);
                
                // Add randomness
                particleLoc.add(
                    (random.nextDouble() - 0.5) * 0.6,
                    (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.6
                );
                
                // Move wall toward player
                Vector velocity = wallNormal.clone().multiply(0.3);
                
                player.spawnParticle(Particle.CLOUD, particleLoc, 1,
                    velocity.getX(), velocity.getY(), velocity.getZ(), 0.05);
                    
                // Add snow particles
                if (random.nextInt(2) == 0) {
                    player.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1,
                        velocity.getX() * 0.5, velocity.getY() * 0.5, velocity.getZ() * 0.5, 0.02);
                }
            }
        }
    }

    private void generateBlizzardParticles(Player player) {
        Location playerLoc = player.getLocation();

        // Performance optimization - reduce particles based on nearby players
        int nearbyPlayers = (int) player.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(player.getLocation()) <= particleRange * 2)
                .count();
        
        double performanceMultiplier = Math.max(0.3, 1.0 / Math.max(1, nearbyPlayers - 1));
        int actualRange = (int) (particleRange * particleMultiplier * performanceMultiplier);

        for (int x = -actualRange; x <= actualRange; x++) {
            for (int z = -actualRange; z <= actualRange; z++) {
                // Skip some particles for performance
                if (random.nextDouble() > particleMultiplier * performanceMultiplier) continue;

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
                            2 + (random.nextDouble() * 3),
                            random.nextDouble() - 0.5
                    );

                    // Create falling snow particles with enhanced movement
                    Vector windEffect = new Vector(
                        (random.nextDouble() - 0.5) * 0.2,
                        -0.1, // Downward motion
                        (random.nextDouble() - 0.5) * 0.2
                    );

                    player.spawnParticle(Particle.CLOUD, spawnLoc, 1,
                            windEffect.getX(), windEffect.getY(), windEffect.getZ(), 0.02);

                    // Add snowflake particles for enhanced effect
                    if (random.nextInt(3) == 0) {
                        player.spawnParticle(Particle.SNOWFLAKE, spawnLoc, 1,
                                windEffect.getX() * 0.5, windEffect.getY() * 0.5, windEffect.getZ() * 0.5, 0.01);
                    }
                    
                    // Add white dust particles for density
                    if (random.nextInt(4) == 0) {
                        Particle.DustOptions whiteDust = new Particle.DustOptions(
                            org.bukkit.Color.fromRGB(255, 255, 255), 1.0f
                        );
                        player.spawnParticle(Particle.DUST, spawnLoc, 1,
                            windEffect.getX(), windEffect.getY(), windEffect.getZ(), 0.02, whiteDust);
                    }
                }

                // Enhanced edge particles for storm effect
                if (Math.abs(x) == actualRange || Math.abs(z) == actualRange) {
                    Location edgeLoc = particleLoc.add(0.5, 4, 0.5);
                    
                    // Create swirling effect at edges
                    for (int swirl = 0; swirl < 3; swirl++) {
                        double angle = (System.currentTimeMillis() / 100.0 + swirl * 120) % 360;
                        Vector swirlOffset = new Vector(
                            Math.cos(Math.toRadians(angle)) * 1.5,
                            Math.sin(System.currentTimeMillis() / 500.0 + swirl) * 0.5,
                            Math.sin(Math.toRadians(angle)) * 1.5
                        );
                        
                        Location swirlLoc = edgeLoc.clone().add(swirlOffset);
                        
                        player.spawnParticle(Particle.CLOUD, swirlLoc, 1,
                                0.5, 1.0, 0.5, 0.1);
                        
                        if (random.nextInt(2) == 0) {
                            player.spawnParticle(Particle.SNOWFLAKE, swirlLoc, 1,
                                0.3, 0.3, 0.3, 0.05);
                        }
                    }
                }
            }
        }

        // Enhanced wind sounds with directional effect
        if (random.nextInt(60) == 0) {
            // Vary pitch and volume based on "wind direction"
            float pitch = 0.3f + random.nextFloat() * 0.2f;
            float volume = 0.6f + random.nextFloat() * 0.4f;
            player.playSound(playerLoc, Sound.WEATHER_RAIN_ABOVE, volume, pitch);
        }
        
        // Add howling wind sound occasionally
        if (random.nextInt(120) == 0) {
            player.playSound(playerLoc, Sound.ITEM_ELYTRA_FLYING, 0.4f, 0.3f);
        }
        
        // Distant thunder-like sounds for intense blizzard
        if (random.nextInt(200) == 0) {
            player.playSound(playerLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 0.5f);
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