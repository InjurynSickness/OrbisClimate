package com.orbismc.orbisClimate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Simplified performance monitoring system for OrbisClimate
 * Tracks TPS and memory usage to optimize performance
 */
public class PerformanceMonitor {
    
    private final OrbisClimate plugin;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // Configuration
    private boolean monitoringEnabled;
    private double tpsWarningThreshold;
    private boolean autoReduceEffects;
    private boolean memoryMonitoring;
    private int memoryWarningThreshold;
    
    // Runtime data
    private BukkitTask monitoringTask;
    private double currentTPS = 20.0;
    private boolean performanceMode = false;
    private long lastMemoryWarning = 0;
    private long lastTPSWarning = 0;
    private final long MEMORY_WARNING_COOLDOWN = 300000; // 5 minutes
    private final long TPS_WARNING_COOLDOWN = 60000; // 1 minute
    
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
    }
    
    private void startMonitoring() {
        monitoringTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateTPS();
            
            if (memoryMonitoring) {
                checkMemoryUsage();
            }
            
            // Check if we need to enter/exit performance mode
            updatePerformanceMode();
            
        }, 0L, 100L); // Every 5 seconds
    }
    
    public double getCurrentTPS() {
        try {
            Class<?> serverClass = Class.forName("org.bukkit.Bukkit");
            java.lang.reflect.Method getTPS = serverClass.getMethod("getTPS");
            double[] tps = (double[]) getTPS.invoke(null);
            return Math.round(tps[0] * 100.0) / 100.0;
        } catch (Exception e) {
            return 20.0; // Just return default if can't get real TPS
        }
    }
    
    private void updateTPS() {
        currentTPS = getCurrentTPS();
        
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
    
    public int getRecommendedParticleCount(int baseCount, Player player) {
        if (!monitoringEnabled) return baseCount;
        
        double multiplier = getPerformanceMultiplier();
        return Math.max(1, (int) (baseCount * multiplier));
    }
    
    public boolean shouldSkipEffects(Player player) {
        if (!monitoringEnabled) return false;
        
        // Skip effects entirely if performance is very poor
        return currentTPS < 10.0;
    }
    
    public void cleanupPlayer(Player player) {
        // Simple cleanup - no per-player data to clean
    }
    
    public void clearAllData() {
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
    }
}