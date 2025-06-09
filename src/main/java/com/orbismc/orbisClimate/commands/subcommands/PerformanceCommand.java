package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.PerformanceMonitor;
import com.orbismc.orbisClimate.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public class PerformanceCommand extends BaseSubCommand {

    public PerformanceCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.performance", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();

        if (monitor == null) {
            MessageUtils.send(sender, MessageUtils.error("Performance monitoring is not available!"));
            return true;
        }

        if (args.length == 0) {
            showPerformanceHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "report":
                return handleReport(sender, monitor);
            case "mode":
                return handleMode(sender, monitor, args);
            case "optimize":
                return handleOptimize(sender, monitor);
            case "clear":
                return handleClear(sender, monitor);
            default:
                MessageUtils.send(sender, MessageUtils.error("Unknown performance command! Use '/climate performance' for help."));
                return true;
        }
    }

    private void showPerformanceHelp(CommandSender sender) {
        MessageUtils.send(sender, MessageUtils.header("Performance Commands"));
        
        // Enhanced command list with clickable commands
        Component reportCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate performance report", 
                    "/climate performance report", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Show performance report", MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, reportCmd);
        
        Component modeCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate performance mode [on|off]", 
                    "/climate performance mode", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Toggle performance mode", MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, modeCmd);
        
        Component optimizeCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate performance optimize", 
                    "/climate performance optimize", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Run optimization", MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, optimizeCmd);
        
        Component clearCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate performance clear", 
                    "/climate performance clear", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Clear performance data", MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, clearCmd);
    }

    private boolean handleReport(CommandSender sender, PerformanceMonitor monitor) {
        // Parse the performance report and enhance it with Adventure formatting
        String rawReport = monitor.getPerformanceReport();
        
        MessageUtils.send(sender, MessageUtils.header("OrbisClimate Performance Report"));
        
        // Current TPS with color coding
        double tps = monitor.getCurrentTPS();
        Component tpsLine = Component.text()
                .append(MessageUtils.text("TPS: ", MessageUtils.INFO))
                .append(MessageUtils.text(String.format("%.2f", tps), getTpsColor(tps), Style.style(TextDecoration.BOLD)))
                .append(MessageUtils.text(" (threshold: 18.0)", MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, tpsLine);
        
        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        double memoryPercent = (double) usedMemory / maxMemory * 100;
        
        Component memoryLine = Component.text()
                .append(MessageUtils.text("Memory: ", MessageUtils.INFO))
                .append(MessageUtils.text(String.format("%.1f%%", memoryPercent), getMemoryColor(memoryPercent)))
                .append(MessageUtils.text(" (" + usedMemory + "MB/" + maxMemory + "MB)", MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, memoryLine);
        
        // Performance mode status
        boolean performanceMode = monitor.isPerformanceMode();
        Component perfModeIcon = performanceMode ? 
            Component.text("⚠ ", MessageUtils.WARNING) : 
            Component.text("✓ ", MessageUtils.SUCCESS);
        
        Component perfModeLine = Component.text()
                .append(MessageUtils.text("Performance Mode: ", MessageUtils.INFO))
                .append(perfModeIcon)
                .append(MessageUtils.text(performanceMode ? "Active" : "Inactive", 
                    performanceMode ? MessageUtils.WARNING : MessageUtils.SUCCESS))
                .build();
        MessageUtils.send(sender, perfModeLine);
        
        // Effect multiplier
        double multiplier = monitor.getPerformanceMultiplier();
        Component multiplierLine = Component.text()
                .append(MessageUtils.text("Effect Multiplier: ", MessageUtils.INFO))
                .append(MessageUtils.text(String.format("%.1fx", multiplier), 
                    multiplier >= 1.0 ? MessageUtils.SUCCESS : 
                    multiplier >= 0.5 ? MessageUtils.WARNING : MessageUtils.ERROR))
                .build();
        MessageUtils.send(sender, multiplierLine);

        // Additional server info
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Server Information:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        MessageUtils.send(sender, MessageUtils.infoLine("Online Players", 
            String.valueOf(Bukkit.getOnlinePlayers().size()), MessageUtils.ACCENT));
        MessageUtils.send(sender, MessageUtils.infoLine("Max Memory", maxMemory + "MB"));
        MessageUtils.send(sender, MessageUtils.infoLine("Used Memory", usedMemory + "MB"));
        MessageUtils.send(sender, MessageUtils.infoLine("Free Memory", freeMemory + "MB"));

        return true;
    }

    private boolean handleMode(CommandSender sender, PerformanceMonitor monitor, String[] args) {
        if (args.length < 2) {
            boolean isActive = monitor.isPerformanceMode();
            Component statusLine = Component.text()
                    .append(MessageUtils.text("Performance Mode: ", MessageUtils.INFO))
                    .append(MessageUtils.text(isActive ? "Active" : "Inactive", 
                        isActive ? MessageUtils.WARNING : MessageUtils.SUCCESS))
                    .build();
            MessageUtils.send(sender, statusLine);
            return true;
        }

        boolean enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");

        if (enable && !monitor.isPerformanceMode()) {
            // Force enable performance mode
            monitor.enterPerformanceMode();
            Component enableMsg = Component.text()
                    .append(Component.text("✓ ", MessageUtils.SUCCESS))
                    .append(MessageUtils.text("Performance mode enabled!", MessageUtils.SUCCESS))
                    .build();
            MessageUtils.send(sender, enableMsg);
        } else if (!enable && monitor.isPerformanceMode()) {
            // Force disable performance mode
            monitor.exitPerformanceMode();
            Component disableMsg = Component.text()
                    .append(Component.text("✓ ", MessageUtils.SUCCESS))
                    .append(MessageUtils.text("Performance mode disabled!", MessageUtils.SUCCESS))
                    .build();
            MessageUtils.send(sender, disableMsg);
        } else {
            Component alreadyMsg = Component.text()
                    .append(Component.text("ℹ ", MessageUtils.INFO))
                    .append(MessageUtils.text("Performance mode is already " + (enable ? "enabled" : "disabled"), 
                        MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, alreadyMsg);
        }

        return true;
    }

    private boolean handleOptimize(CommandSender sender, PerformanceMonitor monitor) {
        Component optimizingMsg = Component.text()
                .append(Component.text("🔄 ", MessageUtils.WARNING))
                .append(MessageUtils.text("Running optimization...", MessageUtils.WARNING))
                .build();
        MessageUtils.send(sender, optimizingMsg);

        // Run various optimization tasks
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Clear caches
                if (plugin.getWindManager() != null) {
                    plugin.getWindManager().clearAllCaches();
                }
                if (plugin.getClimateZoneManager() != null) {
                    plugin.getClimateZoneManager().clearPlayerCache();
                }

                // Run garbage collection
                System.gc();

                // Wait a moment for GC to complete
                Thread.sleep(1000);

                // Send result back on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Component successMsg = Component.text()
                            .append(Component.text("✓ ", MessageUtils.SUCCESS))
                            .append(MessageUtils.text("Optimization complete!", MessageUtils.SUCCESS))
                            .build();
                    MessageUtils.send(sender, successMsg);

                    if (monitor != null) {
                        double tps = monitor.getCurrentTPS();
                        Component tpsInfo = Component.text()
                                .append(MessageUtils.text("Current TPS: ", MessageUtils.MUTED))
                                .append(MessageUtils.text(String.format("%.2f", tps), getTpsColor(tps)))
                                .build();
                        MessageUtils.send(sender, tpsInfo);

                        // Show memory improvement
                        Runtime runtime = Runtime.getRuntime();
                        long freeMemory = runtime.freeMemory() / 1024 / 1024;
                        Component memInfo = Component.text()
                                .append(MessageUtils.text("Free Memory: ", MessageUtils.MUTED))
                                .append(MessageUtils.text(freeMemory + "MB", MessageUtils.SUCCESS))
                                .build();
                        MessageUtils.send(sender, memInfo);
                    }
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Component errorMsg = Component.text()
                            .append(Component.text("✗ ", MessageUtils.ERROR))
                            .append(MessageUtils.text("Optimization failed: " + e.getMessage(), MessageUtils.ERROR))
                            .build();
                    MessageUtils.send(sender, errorMsg);
                });
            }
        });

        return true;
    }

    private boolean handleClear(CommandSender sender, PerformanceMonitor monitor) {
        monitor.clearAllData();
        Component clearMsg = Component.text()
                .append(Component.text("✓ ", MessageUtils.SUCCESS))
                .append(MessageUtils.text("Performance data cleared!", MessageUtils.SUCCESS))
                .build();
        MessageUtils.send(sender, clearMsg);
        return true;
    }
    
    private net.kyori.adventure.text.format.TextColor getTpsColor(double tps) {
        if (tps >= 19.5) return MessageUtils.SUCCESS;
        if (tps >= 18.0) return MessageUtils.WARNING;
        return MessageUtils.ERROR;
    }
    
    private net.kyori.adventure.text.format.TextColor getMemoryColor(double percentage) {
        if (percentage <= 70) return MessageUtils.SUCCESS;
        if (percentage <= 85) return MessageUtils.WARNING;
        return MessageUtils.ERROR;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("report", "mode", "optimize", "clear");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return Arrays.asList("on", "off");
        }
        return super.getTabCompletions(sender, args);
    }

    @Override
    public String getDescription() {
        return "Monitor and control plugin performance";
    }

    @Override
    public String getUsage() {
        return "/climate performance <report|mode|optimize|clear>";
    }
}