package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance monitoring system for OrbisClimate
 * Tracks TPS, memory usage, and particle counts to optimize performance
 */
public class PerformanceMonitor {
    
    private final OrbisClimate plugin;
    private final Map<Player, ParticleStats> playerParticleStats = new ConcurrentHashMap<>();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // Configuration
    private boolean monitoringEnabled;
    private double tpsWarningThreshold;
    private boolean autoReduceEffects;
    private boolean memoryMonitoring;
    private int memoryWarningThreshold;
    private boolean trackParticleCounts;
    private int maxParticlesPerPlayerWarning;
    
    // Runtime data
    private BukkitTask monitoringTask;
    private double currentTPS = 20.0;
    private boolean performanceMode = false;
    private long lastMemoryWarning = 0;
    private long lastTPSWarning = 0;
    private final long MEMORY_WARNING_COOLDOWN = 300000; // 5 minutes
    private final long TPS_WARNING_COOLDOWN = 60000; // 1 minute
    private long lastTickTime = System.nanoTime();
    private double estimatedTPS = 20.0;
    
    public PerformanceMonitor(OrbisClimate plugin) {
        this.plugin = plugin;
        loadConfiguration();
        
        if (monitoringEnabled) {
            startMonitoring();
        }
    }
    
    private void loadConfiguration() {
        monitoringEnabled = plugin.getConfig().getBoolean("monitoring.enabled", true);
        tpsWarningThreshold = plugin.getConfig().getDouble("monitoring.tps_warning_threshold", 18.0);
        autoReduceEffects = plugin.getConfig().getBoolean("monitoring.auto_reduce_effects", true);
        memoryMonitoring = plugin.getConfig().getBoolean("monitoring.memory_monitoring", true);
        memoryWarningThreshold = plugin.getConfig().getInt("monitoring.memory_warning_threshold", 85);
        trackParticleCounts = plugin.getConfig().getBoolean("monitoring.track_particle_counts", true);
        maxParticlesPerPlayerWarning = plugin.getConfig().getInt("monitoring.max_particles_per_player_warning", 150);
    }
    
    private void startMonitoring() {
        monitoringTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateTPS();
            
            if (memoryMonitoring) {
                checkMemoryUsage();
            }
            
            if (trackParticleCounts) {
                checkParticleCounts();
            }
            
            // Check if we need to enter/exit performance mode
            updatePerformanceMode();
            
        }, 0L, 100L); // Every 5 seconds
    }
    
    private void updateTPS() {
        try {
            // Try to get server TPS
            Class<?> serverClass = Class.forName("org.bukkit.Bukkit");
            java.lang.reflect.Method getTPS = serverClass.getMethod("getTPS");
            double[] tps = (double[]) getTPS.invoke(null);
            currentTPS = Math.round(tps[0] * 100.0) / 100.0;
        } catch (Exception e) {
            try {
                // Fallback method
                Object server = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
                double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
                currentTPS = Math.round(tps[0] * 100.0) / 100.0;
            } catch (Exception ex) {
                // Calculate rough TPS based on task timing
                currentTPS = estimateTPS();
            }
        }
        
        // Warn if TPS is low
        if (currentTPS < tpsWarningThreshold) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTPSWarning > TPS_WARNING_COOLDOWN) {
                plugin.getLogger().warning(String.format(
                    "Low TPS detected: %.2f (threshold: %.2f) - Consider reducing particle effects",
                    currentTPS, tpsWarningThreshold
                ));
                lastTPSWarning = currentTime;
            }
        }
    }
    
    private double estimateTPS() {
        long currentTime = System.nanoTime();
        long timeDiff = currentTime - lastTickTime;
        lastTickTime = currentTime;
        
        if (timeDiff > 0) {
            double tickTime = timeDiff / 1_000_000.0; // Convert to milliseconds
            double currentTickTPS = 1000.0 / Math.max(tickTime, 50.0); // Minimum 50ms per tick
            
            // Smooth the estimate
            estimatedTPS = (estimatedTPS * 0.9) + (currentTickTPS * 0.1);
        }
        
        return Math.min(20.0, estimatedTPS);
    }
    
    private void checkMemoryUsage() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        if (memoryUsagePercent > memoryWarningThreshold) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMemoryWarning > MEMORY_WARNING_COOLDOWN) {
                plugin.getLogger().warning(String.format(
                    "High memory usage: %.1f%% (threshold: %d%%) - %dMB used of %dMB max",
                    memoryUsagePercent, memoryWarningThreshold,
                    usedMemory / 1024 / 1024, maxMemory / 1024 / 1024
                ));
                lastMemoryWarning = currentTime;
                
                // Suggest garbage collection
                if (memoryUsagePercent > 90) {
                    plugin.getLogger().info("Suggesting garbage collection due to high memory usage");
                    System.gc();
                }
            }
        }
    }
    
    private void checkParticleCounts() {
        for (Map.Entry<Player, ParticleStats> entry : playerParticleStats.entrySet()) {
            Player player = entry.getKey();
            ParticleStats stats = entry.getValue();
            
            if (!player.isOnline()) {
                continue;
            }
            
            if (stats.getCurrentParticleCount() > maxParticlesPerPlayerWarning) {
                plugin.getLogger().warning(String.format(
                    "Player %s has high particle count: %d (threshold: %d)",
                    player.getName(), stats.getCurrentParticleCount(), maxParticlesPerPlayerWarning
                ));
            }
            
            // Reset counters for next check
            stats.resetCounters();
        }
    }
    
    private void updatePerformanceMode() {
        boolean shouldEnterPerformanceMode = currentTPS < (tpsWarningThreshold - 2.0) || 
                                           getMemoryUsagePercent() > (memoryWarningThreshold + 10);
        
        boolean shouldExitPerformanceMode = currentTPS > (tpsWarningThreshold + 2.0) && 
                                          getMemoryUsagePercent() < (memoryWarningThreshold - 10);
        
        if (!performanceMode && shouldEnterPerformanceMode && autoReduceEffects) {
            enterPerformanceMode();
        } else if (performanceMode && shouldExitPerformanceMode) {
            exitPerformanceMode();
        }
    }
    
    public void enterPerformanceMode() {
        performanceMode = true;
        plugin.getLogger().info("Entering performance mode - reducing particle effects");
        
        // Notify admins
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("orbisclimate.admin")) {
                player.sendMessage("§6[OrbisClimate] §cPerformance mode activated - effects reduced");
            }
        }
    }
    
    public void exitPerformanceMode() {
        performanceMode = false;
        plugin.getLogger().info("Exiting performance mode - restoring normal effects");
        
        // Notify admins
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("orbisclimate.admin")) {
                player.sendMessage("§6[OrbisClimate] §aPerformance mode deactivated - effects restored");
            }
        }
    }
    
    private double getMemoryUsagePercent() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return (double) usedMemory / maxMemory * 100;
    }
    
    // Public methods for other managers to use
    
    public void trackParticles(Player player, int particleCount) {
        if (!trackParticleCounts) return;
        
        ParticleStats stats = playerParticleStats.computeIfAbsent(player, k -> new ParticleStats());
        stats.addParticles(particleCount);
    }
    
    public double getPerformanceMultiplier() {
        if (!monitoringEnabled) return 1.0;
        
        if (performanceMode) {
            // Reduce effects significantly in performance mode
            return 0.3;
        }
        
        // Gradual reduction based on TPS
        if (currentTPS < tpsWarningThreshold) {
            double tpsRatio = currentTPS / 20.0;
            return Math.max(0.5, tpsRatio); // Never go below 50%
        }
        
        return 1.0;
    }
    
    public boolean isPerformanceMode() {
        return performanceMode;
    }
    
    public double getCurrentTPS() {
        return currentTPS;
    }
    
    public int getRecommendedParticleCount(int baseCount, Player player) {
        if (!monitoringEnabled) return baseCount;
        
        double multiplier = getPerformanceMultiplier();
        
        // Additional reduction for players with high particle counts
        if (trackParticleCounts) {
            ParticleStats stats = playerParticleStats.get(player);
            if (stats != null && stats.getCurrentParticleCount() > maxParticlesPerPlayerWarning * 0.8) {
                multiplier *= 0.7; // Further reduce for high-particle players
            }
        }
        
        return Math.max(1, (int) (baseCount * multiplier));
    }
    
    public boolean shouldSkipEffects(Player player) {
        if (!monitoringEnabled) return false;
        
        // Skip effects entirely if performance is very poor
        if (currentTPS < 10.0) {
            return true;
        }
        
        // Skip for players with extremely high particle counts
        if (trackParticleCounts && player != null) {
            ParticleStats stats = playerParticleStats.get(player);
            if (stats != null && stats.getCurrentParticleCount() > maxParticlesPerPlayerWarning * 1.5) {
                return true;
            }
        }
        
        return false;
    }
    
    public void cleanupPlayer(Player player) {
        playerParticleStats.remove(player);
    }
    
    public void clearAllData() {
        playerParticleStats.clear();
        lastMemoryWarning = 0;
        lastTPSWarning = 0;
    }
    
    public String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("§6=== OrbisClimate Performance Report ===\n");
        report.append(String.format("§fTPS: §%s%.2f §7(threshold: %.2f)\n", 
            currentTPS >= tpsWarningThreshold ? "a" : "c", currentTPS, tpsWarningThreshold));
        
        double memUsage = getMemoryUsagePercent();
        report.append(String.format("§fMemory: §%s%.1f%% §7(threshold: %d%%)\n",
            memUsage <= memoryWarningThreshold ? "a" : "c", memUsage, memoryWarningThreshold));
        
        report.append(String.format("§fPerformance Mode: §%s%s\n",
            performanceMode ? "c" : "a", performanceMode ? "ACTIVE" : "INACTIVE"));
        
        report.append(String.format("§fEffect Multiplier: §f%.1fx\n", getPerformanceMultiplier()));
        
        if (trackParticleCounts) {
            int totalParticles = playerParticleStats.values().stream()
                .mapToInt(ParticleStats::getCurrentParticleCount)
                .sum();
            report.append(String.format("§fTotal Active Particles: §f%d\n", totalParticles));
            report.append(String.format("§fTracked Players: §f%d\n", playerParticleStats.size()));
        }
        
        return report.toString();
    }
    
    public void reloadConfig() {
        loadConfiguration();
        
        if (monitoringEnabled && monitoringTask == null) {
            startMonitoring();
        } else if (!monitoringEnabled && monitoringTask != null) {
            monitoringTask.cancel();
            monitoringTask = null;
        }
    }
    
    public void shutdown() {
        if (monitoringTask != null) {
            monitoringTask.cancel();
        }
        playerParticleStats.clear();
    }
    
    // Inner class to track particle statistics per player
    private static class ParticleStats {
        private int currentParticleCount = 0;
        private int totalParticlesThisPeriod = 0;
        private long lastReset = System.currentTimeMillis();
        
        public void addParticles(int count) {
            currentParticleCount += count;
            totalParticlesThisPeriod += count;
        }
        
        public int getCurrentParticleCount() {
            return currentParticleCount;
        }
        
        public void resetCounters() {
            // Decay current count over time
            long timeSinceReset = System.currentTimeMillis() - lastReset;
            if (timeSinceReset > 5000) { // 5 seconds
                currentParticleCount = Math.max(0, currentParticleCount - (int)(timeSinceReset / 100));
                lastReset = System.currentTimeMillis();
            }
            
            totalParticlesThisPeriod = 0;
        }
    }
}