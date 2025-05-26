// Performance Command - PerformanceCommand.java
package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.PerformanceMonitor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
            sender.sendMessage(ChatColor.RED + "Performance monitoring is not available!");
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
                sender.sendMessage(ChatColor.RED + "Unknown performance command! Use '/climate performance' for help.");
                return true;
        }
    }

    private void showPerformanceHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Performance Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/climate performance report " + ChatColor.WHITE + "- Show performance report");
        sender.sendMessage(ChatColor.YELLOW + "/climate performance mode [on|off] " + ChatColor.WHITE + "- Toggle performance mode");
        sender.sendMessage(ChatColor.YELLOW + "/climate performance optimize " + ChatColor.WHITE + "- Run optimization");
        sender.sendMessage(ChatColor.YELLOW + "/climate performance clear " + ChatColor.WHITE + "- Clear performance data");
    }

    private boolean handleReport(CommandSender sender, PerformanceMonitor monitor) {
        String report = monitor.getPerformanceReport();
        sender.sendMessage(report);

        // Add additional server info
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage(ChatColor.AQUA + "Server Info:");
        sender.sendMessage(ChatColor.WHITE + "  Online Players: " + Bukkit.getOnlinePlayers().size());
        sender.sendMessage(ChatColor.WHITE + "  Max Memory: " + maxMemory + "MB");
        sender.sendMessage(ChatColor.WHITE + "  Used Memory: " + usedMemory + "MB");
        sender.sendMessage(ChatColor.WHITE + "  Free Memory: " + freeMemory + "MB");

        return true;
    }

    private boolean handleMode(CommandSender sender, PerformanceMonitor monitor, String[] args) {
        if (args.length < 2) {
            boolean isActive = monitor.isPerformanceMode();
            sender.sendMessage(ChatColor.AQUA + "Performance Mode: " +
                    (isActive ? ChatColor.RED + "ACTIVE" : ChatColor.GREEN + "INACTIVE"));
            return true;
        }

        boolean enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");

        if (enable && !monitor.isPerformanceMode()) {
            // Force enable performance mode
            monitor.enterPerformanceMode();
            sender.sendMessage(ChatColor.GREEN + "Performance mode enabled!");
        } else if (!enable && monitor.isPerformanceMode()) {
            // Force disable performance mode
            monitor.exitPerformanceMode();
            sender.sendMessage(ChatColor.GREEN + "Performance mode disabled!");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Performance mode is already " +
                    (enable ? "enabled" : "disabled"));
        }

        return true;
    }

    private boolean handleOptimize(CommandSender sender, PerformanceMonitor monitor) {
        sender.sendMessage(ChatColor.YELLOW + "Running optimization...");

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
                    sender.sendMessage(ChatColor.GREEN + "Optimization complete!");

                    if (monitor != null) {
                        double tps = monitor.getCurrentTPS();
                        sender.sendMessage(ChatColor.GRAY + "Current TPS: " + String.format("%.2f", tps));

                        // Show memory improvement
                        Runtime runtime = Runtime.getRuntime();
                        long freeMemory = runtime.freeMemory() / 1024 / 1024;
                        sender.sendMessage(ChatColor.GRAY + "Free Memory: " + freeMemory + "MB");
                    }
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.RED + "Optimization failed: " + e.getMessage());
                });
            }
        });

        return true;
    }

    private boolean handleClear(CommandSender sender, PerformanceMonitor monitor) {
        monitor.clearAllData();
        sender.sendMessage(ChatColor.GREEN + "Performance data cleared!");
        return true;
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