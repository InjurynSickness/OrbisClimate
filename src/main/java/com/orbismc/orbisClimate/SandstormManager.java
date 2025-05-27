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
import java.util.concurrent.ConcurrentHashMap;

public class SandstormManager {

    private final OrbisClimate plugin;
    private final WeatherForecast weatherForecast;
    private final WindManager windManager;
    private final Random random;

    // Configuration
    private boolean sandstormsEnabled;
    private int minSandstormHeight;
    private int particleRange;
    private int particleYRange;
    private double particleMultiplier;
    private int blindnessDuration;
    private int slownessDuration;
    private int slownessAmplifier;
    private boolean enableLocalizedSandstorms;
    private int maxPlayersPerSandstorm;

    // Active sandstorms per player (localized system)
    private final Map<Player, PlayerSandstormData> activePlayerSandstorms = new ConcurrentHashMap<>();
    private final Set<World> activeSandstorms = new HashSet<>();
    private final Map<World, BukkitTask> sandstormTasks = new HashMap<>();

    // Performance tracking
    private final Map<Player, Long> lastParticleTime = new ConcurrentHashMap<>();
    private static final long PARTICLE_COOLDOWN_MS = 40; // 40ms between particle updates per player

    public SandstormManager(OrbisClimate plugin, WeatherForecast weatherForecast, WindManager windManager) {
        this.plugin = plugin;
        this.weatherForecast = weatherForecast;
        this.windManager = windManager;
        this.random = new Random();

        loadConfig();
    }

    private void loadConfig() {
        sandstormsEnabled = plugin.getConfig().getBoolean("sandstorm.enabled", true);
        minSandstormHeight = plugin.getConfig().getInt("sandstorm.min_height", 62);
        particleRange = plugin.getConfig().getInt("sandstorm.particle_range", 12);
        particleYRange = plugin.getConfig().getInt("sandstorm.particle_y_range", 20);
        particleMultiplier = plugin.getConfig().getDouble("sandstorm.particle_multiplier", 1.2);
        blindnessDuration = plugin.getConfig().getInt("sandstorm.blindness_duration", 100);
        slownessDuration = plugin.getConfig().getInt("sandstorm.slowness_duration", 100);
        slownessAmplifier = plugin.getConfig().getInt("sandstorm.slowness_amplifier", 1);
        enableLocalizedSandstorms = plugin.getConfig().getBoolean("sandstorm.localized_sandstorms", true);
        maxPlayersPerSandstorm = plugin.getConfig().getInt("sandstorm.max_players_per_sandstorm", 8);
    }

    public void startSandstorm(World world) {
        if (!sandstormsEnabled || activeSandstorms.contains(world)) return;

        activeSandstorms.add(world);

        if (enableLocalizedSandstorms) {
            startLocalizedSandstorms(world);
        } else {
            startWorldSandstorm(world);
        }
    }

    private void startLocalizedSandstorms(World world) {
        // Start individual sandstorms for players in desert areas
        for (Player player : world.getPlayers()) {
            if (shouldPlayerHaveSandstorm(player)) {
                startPlayerSandstorm(player);
            }
        }

        // Task to manage player sandstorms
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check for new players entering desert areas
            for (Player player : world.getPlayers()) {
                if (shouldPlayerHaveSandstorm(player) && !activePlayerSandstorms.containsKey(player)) {
                    if (activePlayerSandstorms.size() < maxPlayersPerSandstorm) {
                        startPlayerSandstorm(player);
                    }
                } else if (!shouldPlayerHaveSandstorm(player) && activePlayerSandstorms.containsKey(player)) {
                    stopPlayerSandstorm(player);
                }
            }

            // Process existing player sandstorms
            Iterator<Map.Entry<Player, PlayerSandstormData>> iterator = activePlayerSandstorms.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Player, PlayerSandstormData> entry = iterator.next();
                Player player = entry.getKey();
                PlayerSandstormData data = entry.getValue();

                if (!player.isOnline() || !shouldPlayerHaveSandstorm(player)) {
                    iterator.remove();
                    continue;
                }

                processPlayerSandstormEffects(player, data);
            }
        }, 0L, 10L); // Every 0.5 seconds

        sandstormTasks.put(world, task);

        // Separate async task for particles
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!activeSandstorms.contains(world)) return;
            
            // Process particles for active player sandstorms
            for (Map.Entry<Player, PlayerSandstormData> entry : activePlayerSandstorms.entrySet()) {
                Player player = entry.getKey();
                if (!player.getWorld().equals(world)) continue;
                
                // Skip if player has particles disabled or performance issues
                if (!plugin.isPlayerParticlesEnabled(player)) continue;
                
                // Rate limit particles per player
                long currentTime = System.currentTimeMillis();
                Long lastTime = lastParticleTime.get(player);
                if (lastTime != null && (currentTime - lastTime) < PARTICLE_COOLDOWN_MS) {
                    continue;
                }
                lastParticleTime.put(player, currentTime);
                
                generateLocalizedSandstormParticles(player);
            }
        }, 0L, 1L); // Every tick for particles, but rate limited per player
    }

    private void startWorldSandstorm(World world) {
        // Original world-wide sandstorm system
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processSandstormEffects(world);
        }, 0L, 10L);

        sandstormTasks.put(world, task);

        // Async particle task
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!activeSandstorms.contains(world)) return;
            processSandstormParticles(world);
        }, 0L, 2L);
    }

    private boolean shouldPlayerHaveSandstorm(Player player) {
        Location loc = player.getLocation();

        // Height requirement check
        if (loc.getBlockY() < minSandstormHeight) return false;

        // Biome check - only desert biomes can have sandstorms
        if (!isDesertBiome(loc.getBlock().getBiome())) return false;

        // Use wind manager's indoor detection for consistency
        if (windManager.isPlayerIndoors(player)) return false;

        return true;
    }

    private void startPlayerSandstorm(Player player) {
        PlayerSandstormData data = new PlayerSandstormData();
        activePlayerSandstorms.put(player, data);
        
        // Notify player
        if (random.nextInt(3) == 0) {
            player.sendMessage("§6§lA sandstorm begins to swirl around you...");
        }
    }

    private void stopPlayerSandstorm(Player player) {
        activePlayerSandstorms.remove(player);
        lastParticleTime.remove(player);
    }

    private void processPlayerSandstormEffects(Player player, PlayerSandstormData data) {
        // Apply sandstorm effects to the player
        applySandstormEffects(player);
        
        // Send messages less frequently
        if (random.nextInt(1200) == 0) {
            sendSandstormMessages(player);
        }
    }

    private void generateLocalizedSandstormParticles(Player player) {
        Location playerLoc = player.getLocation();

        // Check if player is in desert biome
        if (!isDesertBiome(playerLoc.getBlock().getBiome())) return;

        // Check height requirement
        if (playerLoc.getBlockY() < minSandstormHeight) return;

        // Performance optimization
        double performanceMultiplier = 1.0;
        if (plugin.getPerformanceMonitor() != null) {
            performanceMultiplier = plugin.getPerformanceMonitor().getPerformanceMultiplier();
            if (plugin.getPerformanceMonitor().shouldSkipEffects(player)) {
                return;
            }
        }

        int actualRange = (int) (particleRange * particleMultiplier * performanceMultiplier);

        // Create intensive particle effects (like DeadlyDisasters style but optimized)
        int particleCount = (int) (25 * performanceMultiplier); // Reduced from 50
        
        for (int i = 0; i < particleCount; i++) {
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
            player.spawnParticle(Particle.DUST, particleLoc, 1,
                    0.5, 0.3, 0.5, 0.1, dustOptions);

            // Add some regular dust particles for density
            if (random.nextInt(4) == 0) { // Reduced frequency
                player.spawnParticle(Particle.ASH, particleLoc, 1,
                        0.3, 0.2, 0.3, 0.05);
            }
        }

        // Create swirling sand effect around player (reduced complexity)
        if (random.nextInt(2) == 0) { // Only 50% of the time
            for (int i = 0; i < 10; i++) { // Reduced from 20
                double angle = (System.currentTimeMillis() / 50.0 + i * 36) % 360; // Increased angle step
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

                player.spawnParticle(Particle.DUST, swirl, 1,
                        0.1, 0.1, 0.1, 0.02, swirlingDust);
            }
        }

        // Play sandstorm sounds occasionally
        if (random.nextInt(120) == 0) { // Reduced frequency
            player.playSound(playerLoc, Sound.WEATHER_RAIN, 0.6f, 0.3f);
        }

        // Add wind whoosh sounds
        if (random.nextInt(180) == 0) { // Reduced frequency
            player.playSound(playerLoc, Sound.ITEM_ELYTRA_FLYING, 0.4f, 0.5f);
        }
    }

    public void stopSandstorm(World world) {
        if (!activeSandstorms.contains(world)) return;

        activeSandstorms.remove(world);

        BukkitTask task = sandstormTasks.remove(world);
        if (task != null) {
            task.cancel();
        }

        // Clear all player sandstorms in this world
        Iterator<Map.Entry<Player, PlayerSandstormData>> iterator = activePlayerSandstorms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, PlayerSandstormData> entry = iterator.next();
            if (entry.getKey().getWorld().equals(world)) {
                iterator.remove();
            }
        }
        
        lastParticleTime.clear();
    }

    // Original methods for compatibility
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

    private void processSandstormParticles(World world) {
        for (Player player : world.getPlayers()) {
            if (!player.getWorld().equals(world)) continue;

            // Skip if player has particles disabled
            if (!plugin.isPlayerParticlesEnabled(player)) continue;

            // Rate limiting
            long currentTime = System.currentTimeMillis();
            Long lastTime = lastParticleTime.get(player);
            if (lastTime != null && (currentTime - lastTime) < PARTICLE_COOLDOWN_MS) {
                continue;
            }
            lastParticleTime.put(player, currentTime);

            generateSandstormParticles(player);
        }
    }

    private void generateSandstormParticles(Player player) {
        Location playerLoc = player.getLocation();

        // Check if player is in desert biome
        if (!isDesertBiome(playerLoc.getBlock().getBiome())) return;

        // Check height requirement
        if (playerLoc.getBlockY() < minSandstormHeight) return;

        // Performance optimization - adjust particle count based on nearby players
        int nearbyPlayers = (int) player.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(player.getLocation()) <= particleRange * 2)
                .count();
        
        double performanceMultiplier = Math.max(0.3, 1.0 / Math.max(1, nearbyPlayers - 1));
        if (plugin.getPerformanceMonitor() != null) {
            performanceMultiplier *= plugin.getPerformanceMonitor().getPerformanceMultiplier();
        }
        
        int actualRange = (int) (particleRange * particleMultiplier * performanceMultiplier);

        // Create intensive particle effects but with performance consideration
        int particleCount = (int) (30 * performanceMultiplier); // Reduced base count
        
        for (int i = 0; i < particleCount; i++) {
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
            player.spawnParticle(Particle.DUST, particleLoc, 1,
                    0.5, 0.3, 0.5, 0.1, dustOptions);

            // Add some regular dust particles for density
            if (random.nextInt(4) == 0) {
                player.spawnParticle(Particle.ASH, particleLoc, 1,
                        0.3, 0.2, 0.3, 0.05);
            }
        }

        // Play sandstorm sounds occasionally
        if (random.nextInt(120) == 0) {
            player.playSound(playerLoc, Sound.WEATHER_RAIN, 0.6f, 0.3f);
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
            if (random.nextInt(1200) == 0) { // Reduced frequency
                sendSandstormMessages(player);
            }
        }
    }

    private void sendSandstormMessages(Player player) {
        if (!plugin.getConfig().getBoolean("notifications.sandstorm_messages", true)) {
            return;
        }
        
        String[] sandstormMessages = {
            "§6§lThe sandstorm whips around you, blinding your vision...",
            "§e§lStinging sand fills the air, making it hard to breathe!",
            "§7§lThe desert wind carries walls of sand across the landscape!",
            "§6§lSand devils dance in the swirling storm around you!",
            "§e§lThe relentless sandstorm shows no signs of stopping!",
            "§7§lVisibility drops to nothing in the howling desert wind!"
        };
        
        String message = sandstormMessages[random.nextInt(sandstormMessages.length)];
        player.sendMessage("§6[OrbisClimate] " + message);
    }

    public boolean isSandstormActive(World world) {
        return activeSandstorms.contains(world);
    }

    public boolean hasPlayerSandstorm(Player player) {
        return activePlayerSandstorms.containsKey(player);
    }

    public void checkForSandstorms() {
        if (!sandstormsEnabled) return;
        
        for (World world : Bukkit.getWorlds()) {
            WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(world);

            // Check if conditions are right for sandstorms
            boolean shouldHaveSandstorm = false;

            // Sandstorms can happen during various weather but are more likely in summer
            if (currentWeather == WeatherForecast.WeatherType.SANDSTORM) {
                shouldHaveSandstorm = true;
            } else if (currentWeather == WeatherForecast.WeatherType.CLEAR ||
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
                    
                    // Notify players
                    for (Player player : world.getPlayers()) {
                        if (player.hasPermission("orbisclimate.notifications") && 
                            isDesertBiome(player.getLocation().getBlock().getBiome())) {
                            player.sendMessage("§6[OrbisClimate] §c§lA sandstorm is approaching! Seek shelter!");
                        }
                    }
                }
            } else {
                if (isSandstormActive(world)) {
                    stopSandstorm(world);
                    
                    // Notify players that sandstorm has ended
                    for (Player player : world.getPlayers()) {
                        if (player.hasPermission("orbisclimate.notifications") && 
                            isDesertBiome(player.getLocation().getBlock().getBiome())) {
                            player.sendMessage("§6[OrbisClimate] §a§lThe sandstorm has passed.");
                        }
                    }
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
        activePlayerSandstorms.clear();
        lastParticleTime.clear();
    }

    public void reloadConfig() {
        loadConfig();
    }

    // Data class for player sandstorm tracking
    private static class PlayerSandstormData {
        private long startTime;
        private long lastEffectTime;
        
        public PlayerSandstormData() {
            this.startTime = System.currentTimeMillis();
            this.lastEffectTime = 0;
        }
        
        public long getStartTime() { return startTime; }
        public long getLastEffectTime() { return lastEffectTime; }
        public void setLastEffectTime(long time) { this.lastEffectTime = time; }
    }
}