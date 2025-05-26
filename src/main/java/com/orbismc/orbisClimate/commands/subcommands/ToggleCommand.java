package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ToggleCommand extends BaseSubCommand {

    public ToggleCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.toggle", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        if (args.length == 0) {
            // Toggle current setting
            boolean current = plugin.isPlayerParticlesEnabled(player);
            plugin.setPlayerParticlesEnabled(player, !current);

            player.sendMessage(ChatColor.GREEN + "Weather particles " +
                    (!current ? "enabled" : "disabled") + "!");

            // Show additional info if needed
            if (!current && plugin.getPerformanceMonitor() != null &&
                    plugin.getPerformanceMonitor().isPerformanceMode()) {
                player.sendMessage(ChatColor.YELLOW + "Note: Server is in performance mode - effects may still be reduced");
            }
        } else {
            // Set specific setting
            boolean setting = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
            plugin.setPlayerParticlesEnabled(player, setting);

            player.sendMessage(ChatColor.GREEN + "Weather particles " +
                    (setting ? "enabled" : "disabled") + "!");
        }

        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("on", "off");
        }
        return super.getTabCompletions(sender, args);
    }

    @Override
    public String getDescription() {
        return "Toggle weather particle effects on or off";
    }

    @Override
    public String getUsage() {
        return "/climate toggle [on|off]";
    }
}
