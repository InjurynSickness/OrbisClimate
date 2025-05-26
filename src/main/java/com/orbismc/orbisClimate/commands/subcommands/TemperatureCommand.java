package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.ClimateZoneManager;
import com.orbismc.orbisClimate.TemperatureManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TemperatureCommand extends BaseSubCommand {

    public TemperatureCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.info", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        TemperatureManager temperatureManager = plugin.getTemperatureManager();
        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();

        if (temperatureManager == null || climateZoneManager == null) {
            player.sendMessage(ChatColor.RED + "Temperature system not available!");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "=== Temperature Information ===");

        double currentTemp = temperatureManager.getPlayerTemperature(player);
        String tempLevel = temperatureManager.getPlayerTemperatureLevel(player);
        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);

        player.sendMessage(ChatColor.AQUA + "Current Temperature: " + ChatColor.WHITE +
                String.format("%.1f°C (%.1f°F)", currentTemp, (currentTemp * 9/5) + 32));

        player.sendMessage(ChatColor.AQUA + "Comfort Level: " + ChatColor.WHITE + tempLevel);

        // Zone temperature range
        player.sendMessage(ChatColor.AQUA + "Zone Range: " + ChatColor.WHITE +
                zone.getMinTemp() + "°C to " + zone.getMaxTemp() + "°C");

        // Temperature effects
        if (temperatureManager.isPlayerTooHot(player)) {
            player.sendMessage(ChatColor.RED + "⚠ You are experiencing heat effects!");
        } else if (temperatureManager.isPlayerTooCold(player)) {
            player.sendMessage(ChatColor.BLUE + "⚠ You are experiencing cold effects!");
        } else {
            player.sendMessage(ChatColor.GREEN + "✓ Temperature is comfortable");
        }

        // Drought bonus
        if (climateZoneManager.isPlayerInDrought(player)) {
            double droughtBonus = plugin.getConfig().getDouble("drought.effects.temperature_bonus", 15.0);
            player.sendMessage(ChatColor.YELLOW + "Drought Heat Bonus: +" + droughtBonus + "°C");
        }

        // Indoor status
        boolean isIndoors = plugin.getWindManager().isPlayerIndoors(player);
        if (isIndoors) {
            player.sendMessage(ChatColor.GRAY + "(You are indoors - temperature is stabilizing toward 20°C)");
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Show detailed temperature information";
    }

    @Override
    public String getUsage() {
        return "/climate temperature";
    }
}