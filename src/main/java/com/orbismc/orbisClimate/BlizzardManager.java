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
import java.util.concurrent.ConcurrentHashMap;

public class BlizzardManager {

    private final OrbisClimate plugin;
    private final WeatherForecast weatherForecast;
    private final WindManager windManager;
    private final Random random;
    private SeasonsAPI seasonsAPI;
    private boolean realisticSeasonsEnabled;

    // Configuration
    private boolean blizzardsEnabled;
    private double blizzardDamage;
    private int minFreezingHeight;
    private int particleRange;
    private int particleYRange;
    private double particleMultiplier;
    private double temperatureThreshold;
    private boolean enableLocalizedBlizzards;
    private int maxPlayersPerBlizzard;

    // Active blizzards per player (localized system)
    private final Map<Player, PlayerBlizzardData> activePlayerBlizzards = new ConcurrentHashMap<>();
    private final Set<World> activeWorldBlizzards = new HashSet<>();
    private final Map<World, BukkitTask> blizzardTasks = new HashMap<>();

    // Performance tracking
    private final Map<Player, Long> lastParticleTime = new ConcurrentHashMap<>();
    private static final long PARTICLE_COOLDOWN_MS = 50; // 50ms between particle updates per player

    public BlizzardManager(OrbisClimate plugin, WeatherForecast weatherForecast, WindManager windManager) {
        this.plugin = plugin;
        this.weatherForecast = weatherForecast;
        this.windManager = windManager;
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
        blizzardsEnabled = plugin.getConfig().getBoolean("blizzard.enabled", true);
        blizzardDamage = plugin.getConfig().getDouble("blizzard.damage", 1.0);
        minFreezingHeight = plugin.getConfig().getInt("blizzard.min_freezing_height", 60);
        particleRange = plugin.getConfig().getInt("blizzard.particle_range", 15);
        particleYRange = plugin.getConfig().getInt("blizzard.particle_y_range", 20);
        particleMultiplier = plugin.getConfig().getDouble("blizzard.particle_multiplier", 1.0);
        temperatureThreshold = plugin.getConfig().getDouble("blizzard.temperature_threshold", 0.15);
        enableLocalizedBlizzards = plugin.getConfig().getBoolean("blizzard.localized_blizzards", true);
        maxPlayersPerBlizzard = plugin.getConfig().getInt("blizzard.max_players_per_blizzard", 10);
    }

    public void startBlizzard(World world) {
        if (!blizzardsEnabled || activeWorldBlizzards.contains(world)) return;

        activeWorldBlizzards.add(world);

        // Force storm weather
        world.setStorm(true);
        world.setThundering(false);

        if (enableLocalizedBlizzards) {
            startLocalizedBlizzards(world);
        } else {
            startWorldBlizzard(world);
        }
    }

    private void startLocalizedBlizzards(World world) {
        // Start individual blizzards for players in cold areas
        for (Player player : world.getPlayers()) {
            if (shouldPlayerHaveBlizzard(player)) {
                startPlayerBlizzard(player);
            }
        }

        // Task to manage player blizzards
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check for new players entering cold areas
            for (Player player : world.getPlayers()) {
                if (shouldPlayerHaveBlizzard(player) && !activePlayerBlizzards.containsKey(player)) {
                    if (activePlayerBlizzards.size() < maxPlayersPerBlizzard) {
                        startPlayerBlizzard(player);
                    }
                } else if (!shouldPlayerHaveBlizzard(player) && activePlayerBlizzards.containsKey(player)) {
                    stopPlayerBlizzard(player);
                }
            }

            // Process existing player blizzards
            Iterator<Map.Entry<Player, PlayerBlizzardData>> iterator = activePlayerBlizzards.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Player, PlayerBlizzardData> entry = iterator.next();
                Player player = entry.getKey();
                PlayerBlizzardData data = entry.getValue();

                if (!player.isOnline() || !shouldPlayerHaveBlizzard(player)) {
                    iterator.remove();
                    continue;
                }

                processPlayerBlizzardEffects(player, data);
            }
        }, 0L, 20L); // Every second

        blizzardTasks.put(world, task);

        // Separate async task for particles only
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!activeWorldBlizzards.contains(world)) return;
            
            // Process particles for active player blizzards
            for (Map.Entry<Player, PlayerBlizzardData> entry : activePlayerBlizzards.entrySet()) {
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
                
                generateLocalizedBlizzardParticles(player);
            }
        }, 0L, 1L); // Every tick for particles, but rate limited per player
    }

    private void startWorldBlizzard(World world) {
        // Original world-wide blizzard system
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processBlizzardEffects(world);
        }, 0L, 20L);

        blizzardTasks.put(world, task);

        // Async particle task
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!activeWorldBlizzards.contains(world)) return;
            processBlizzardParticles(world);
        }, 0L, 2L);
    }

    private boolean shouldPlayerHaveBlizzard(Player player) {
        Location loc = player.getLocation();

        // Height requirement check
        if (loc.getBlockY() < minFreezingHeight) return false;

        // Biome check - only snowy biomes can have blizzards
        if (!isSnowyBiome(loc.getBlock().getBiome())) return false;

        // Temperature check
        if (!isLocationColdEnough(loc, player)) return false;

        // Skip if entity is protected by region/indoor
        if (isEntityProtected(player)) return false;

        return true;
    }

    private void startPlayerBlizzard(Player player) {
        PlayerBlizzardData data = new PlayerBlizzardData();
        activePlayerBlizzards.put(player, data);
        
        // Notify player
        if (random.nextInt(3) == 0) {
            player.sendMessage("§b§lA localized blizzard forms around you...");
        }
    }

    private void stopPlayerBlizzard(Player player) {
        activePlayerBlizzards.remove(player);
        lastParticleTime.remove(player);
    }

    private void processPlayerBlizzardEffects(Player player, PlayerBlizzardData data) {
        // Apply blizzard effects to the player
        applyBlizzardEffects(player);
        
        // Extinguish nearby torches
        if (random.nextInt(4) == 0) { // Reduced frequency
            extinguishNearbyTorches(player);
        }
        
        // Send messages less frequently
        if (random.nextInt(1200) == 0) { // Much less frequent
            sendBlizzardIntensityMessages(player);
        }
    }

    private void generateLocalizedBlizzardParticles(Player player) {
        Location playerLoc = player.getLocation();
        
        // Performance optimization - get performance multiplier
        double performanceMultiplier = 1.0;
        if (plugin.getPerformanceMonitor() != null) {
            performanceMultiplier = plugin.getPerformanceMonitor().getPerformanceMultiplier();
            if (plugin.getPerformanceMonitor().shouldSkipEffects(player)) {
                return;
            }
        }
        
        // Adjust particle count based on performance
        int adjustedRange = (int) (particleRange * performanceMultiplier);
        double adjustedMultiplier = particleMultiplier * performanceMultiplier;
        
        // DeadlyDisasters-style particle generation (localized to player)
        for (int x = -adjustedRange; x <= adjustedRange; x++) {
            for (int z = -adjustedRange; z <= adjustedRange; z++) {
                if (random.nextDouble() >= adjustedMultiplier) continue;
                
                Location temp = playerLoc.clone().add(x, 0, z);
                Location b = temp.getWorld().getHighestBlockAt(temp).getLocation();
                
                // Temperature and biome checks
                if (!isLocationColdEnough(b, player) || !isSnowyBiome(b.getBlock().getBiome())) {
                    continue;
                }
                
                int diff = b.getBlockY() - temp.getBlockY();
                if (diff > particleYRange) continue;
                
                // Edge particles for storm effect (like DeadlyDisasters)
                if (x == adjustedRange || x == -adjustedRange || z == adjustedRange || z == -adjustedRange) {
                    player.spawnParticle(Particle.CLOUD, b.add(0.5, 3, 0.5), 2, 0.5, 0.7, 0.5, 0.05);
                    continue;
                }
                
                if (diff < 0) {
                    b.setY(b.getY() + (diff * -1));
                }
                
                if (diff > 0) {
                    player.spawnParticle(Particle.CLOUD, b.add(0.5, 3, 0.5), 2, 0.5, 0.7, 0.5, 0.05);
                } else {
                    // Multiple particles at different heights like DeadlyDisasters
                    for (int i = 0; i < 2; i++) {
                        Location particleLoc = b.clone().add(
                            random.nextDouble(), 
                            3 + (random.nextDouble() * 2), 
                            random.nextDouble()
                        );
                        Vector velocity = new Vector(
                            (random.nextDouble() / 2.5) - 0.2,
                            -(random.nextDouble() / 0.6),
                            (random.nextDouble() / 2.5) - 0.2
                        );
                        player.spawnParticle(Particle.CLOUD, particleLoc, 0,
                            velocity.getX(), velocity.getY(), velocity.getZ());
                    }
                }
            }
        }
        
        // Play localized sound
        if (random.nextInt(80) == 0) {
            player.playSound(playerLoc, Sound.WEATHER_RAIN_ABOVE, 0.75f, 0.5f);
        }
    }

    public void stopBlizzard(World world) {
        if (!activeWorldBlizzards.contains(world)) return;

        activeWorldBlizzards.remove(world);

        // Stop world task
        BukkitTask task = blizzardTasks.remove(world);
        if (task != null) {
            task.cancel();
        }

        // Clear all player blizzards in this world
        Iterator<Map.Entry<Player, PlayerBlizzardData>> iterator = activePlayerBlizzards.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, PlayerBlizzardData> entry = iterator.next();
            if (entry.getKey().getWorld().equals(world)) {
                iterator.remove();
            }
        }
        
        lastParticleTime.clear();
    }

    // Original methods for compatibility (simplified for performance)
    private void processBlizzardEffects(World world) {
        for (LivingEntity entity : world.getLivingEntities()) {
            Location loc = entity.getLocation();

            // Height requirement check
            if (loc.getBlockY() < minFreezingHeight) continue;

            // Biome check
            if (!isSnowyBiome(loc.getBlock().getBiome())) continue;

            // Temperature check
            if (!isLocationColdEnough(loc, entity)) continue;

            // Skip if entity is protected
            if (isEntityProtected(entity)) continue;

            // Apply effects
            applyBlizzardEffects(entity);

            if (entity instanceof Player) {
                Player player = (Player) entity;
                
                // Reduced frequency for torch extinguishing
                if (random.nextInt(8) == 0) {
                    extinguishNearbyTorches(player);
                }
                
                // Less frequent messages
                if (random.nextInt(1200) == 0) {
                    sendBlizzardIntensityMessages(player);
                }
            }
        }
    }

    private void processBlizzardParticles(World world) {
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
            
            generateBlizzardParticles(player);
        }
    }

    private void generateBlizzardParticles(Player player) {
        Location playerLoc = player.getLocation();
        
        // Performance optimization
        int nearbyPlayers = (int) player.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(player.getLocation()) <= particleRange * 2)
                .count();
        
        double performanceMultiplier = Math.max(0.3, 1.0 / Math.max(1, nearbyPlayers - 1));
        if (plugin.getPerformanceMonitor() != null) {
            performanceMultiplier *= plugin.getPerformanceMonitor().getPerformanceMultiplier();
        }
        
        int actualRange = (int) (particleRange * particleMultiplier * performanceMultiplier);

        for (int x = -actualRange; x <= actualRange; x++) {
            for (int z = -actualRange; z <= actualRange; z++) {
                if (random.nextDouble() > particleMultiplier * performanceMultiplier) continue;

                Location particleLoc = playerLoc.clone().add(x, 0, z);
                Block surface = particleLoc.getWorld().getHighestBlockAt(particleLoc);
                particleLoc.setY(surface.getY());

                int yDiff = Math.abs(particleLoc.getBlockY() - playerLoc.getBlockY());
                if (yDiff > particleYRange) continue;

                if (!isLocationColdEnough(particleLoc, player) || !isSnowyBiome(particleLoc.getBlock().getBiome())) continue;

                // Generate particles at different heights
                for (int i = 0; i < 2; i++) { // Reduced from 3 to 2
                    Location spawnLoc = particleLoc.clone().add(
                            random.nextDouble() - 0.5,
                            2 + (random.nextDouble() * 3),
                            random.nextDouble() - 0.5
                    );

                    Vector windEffect = new Vector(
                        (random.nextDouble() - 0.5) * 0.2,
                        -0.1,
                        (random.nextDouble() - 0.5) * 0.2
                    );

                    player.spawnParticle(Particle.CLOUD, spawnLoc, 1,
                            windEffect.getX(), windEffect.getY(), windEffect.getZ(), 0.02);

                    if (random.nextInt(3) == 0) {
                        player.spawnParticle(Particle.SNOWFLAKE, spawnLoc, 1,
                                windEffect.getX() * 0.5, windEffect.getY() * 0.5, windEffect.getZ() * 0.5, 0.01);
                    }
                }
            }
        }

        // Enhanced wind sounds
        if (random.nextInt(100) == 0) { // Reduced frequency
            float pitch = 0.3f + random.nextFloat() * 0.2f;
            float volume = 0.6f + random.nextFloat() * 0.4f;
            player.playSound(playerLoc, Sound.WEATHER_RAIN_ABOVE, volume, pitch);
        }
    }

    // Helper methods remain the same but simplified
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
        return windManager.isPlayerIndoors((Player) entity);
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
            if (random.nextInt(800) == 0) { // Reduced frequency
                player.sendMessage("§b§lYou feel the bitter cold of the blizzard...");
            }
        }
    }

    private void extinguishNearbyTorches(Player player) {
        Location loc = player.getLocation();
        int range = 2; // Reduced from 3

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

    private void sendBlizzardIntensityMessages(Player player) {
        if (!plugin.getConfig().getBoolean("notifications.blizzard_messages", true)) {
            return;
        }
        
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

    public boolean isBlizzardActive(World world) {
        return activeWorldBlizzards.contains(world);
    }

    public boolean hasPlayerBlizzard(Player player) {
        return activePlayerBlizzards.containsKey(player);
    }

    public void checkForBlizzards() {
        if (!blizzardsEnabled) return;
        
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
        for (World world : new HashSet<>(activeWorldBlizzards)) {
            stopBlizzard(world);
        }
        activePlayerBlizzards.clear();
        lastParticleTime.clear();
    }

    public void reloadConfig() {
        loadConfig();
    }

    // Data class for player blizzard tracking
    private static class PlayerBlizzardData {
        private long startTime;
        private long lastEffectTime;
        
        public PlayerBlizzardData() {
            this.startTime = System.currentTimeMillis();
            this.lastEffectTime = 0;
        }
        
        public long getStartTime() { return startTime; }
        public long getLastEffectTime() { return lastEffectTime; }
        public void setLastEffectTime(long time) { this.lastEffectTime = time; }
    }
}