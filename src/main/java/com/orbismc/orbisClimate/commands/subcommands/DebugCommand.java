package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DebugCommand extends BaseSubCommand {

    public DebugCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.debug", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();
        TemperatureManager temperatureManager = plugin.getTemperatureManager();
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();

        player.sendMessage(ChatColor.GOLD + "=== Debug Information ===");

        // Manager status
        player.sendMessage(ChatColor.AQUA + "Managers Loaded:");
        player.sendMessage(ChatColor.WHITE + "  WindManager: " + (plugin.getWindManager() != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  ClimateZoneManager: " + (climateZoneManager != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  TemperatureManager: " + (temperatureManager != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  WeatherProgressionManager: " + (plugin.getWeatherProgressionManager() != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  DynamicSoundManager: " + (plugin.getDynamicSoundManager() != null ? "✓" : "✗"));
        player.sendMessage(ChatColor.WHITE + "  PerformanceMonitor: " + (monitor != null ? "✓" : "✗"));

        if (climateZoneManager != null && temperatureManager != null) {
            // Current values
            player.sendMessage(ChatColor.AQUA + "Current Values:");
            player.sendMessage(ChatColor.WHITE + "  Zone: " + climateZoneManager.getPlayerClimateZone(player));
            player.sendMessage(ChatColor.WHITE + "  Temperature: " + String.format("%.2f°C", temperatureManager.getPlayerTemperature(player)));
            player.sendMessage(ChatColor.WHITE + "  Zone Weather: " + climateZoneManager.getPlayerZoneWeather(player));
            player.sendMessage(ChatColor.WHITE + "  World Weather: " + plugin.getWeatherForecast().getCurrentWeather(player.getWorld()));
            player.sendMessage(ChatColor.WHITE + "  Indoors: " + plugin.getWindManager().isPlayerIndoors(player));
            player.sendMessage(ChatColor.WHITE + "  Drought: " + climateZoneManager.isPlayerInDrought(player));
            player.sendMessage(ChatColor.WHITE + "  Particles Enabled: " + plugin.isPlayerParticlesEnabled(player));
        }

        // Performance info
        if (monitor != null) {
            player.sendMessage(ChatColor.AQUA + "Performance:");
            player.sendMessage(ChatColor.WHITE + "  TPS: " + String.format("%.2f", monitor.getCurrentTPS()));
            player.sendMessage(ChatColor.WHITE + "  Performance Mode: " + (monitor.isPerformanceMode() ? "ACTIVE" : "INACTIVE"));
            player.sendMessage(ChatColor.WHITE + "  Effect Multiplier: " + String.format("%.2fx", monitor.getPerformanceMultiplier()));

            if (monitor.shouldSkipEffects(player)) {
                player.sendMessage(ChatColor.RED + "  ⚠ Effects are being skipped for performance");
            }
        }

        // System info
        player.sendMessage(ChatColor.AQUA + "System:");
        player.sendMessage(ChatColor.WHITE + "  Online Players: " + player.getServer().getOnlinePlayers().size());
        player.sendMessage(ChatColor.WHITE + "  World: " + player.getWorld().getName());

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        double memoryPercent = (double) usedMemory / maxMemory * 100;

        player.sendMessage(ChatColor.WHITE + "  Memory: " + usedMemory + "MB/" + maxMemory + "MB (" +
                String.format("%.1f%%", memoryPercent) + ")");

        return true;
    }

    @Override
    public String getDescription() {
        return "Show detailed debug information";
    }

    @Override
    public String getUsage() {
        return "/climate debug";
    }
}