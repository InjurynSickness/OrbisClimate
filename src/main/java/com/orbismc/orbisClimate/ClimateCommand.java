package com.orbismc.orbisClimate;

import com.orbismc.orbisClimate.commands.SubCommand;
import com.orbismc.orbisClimate.commands.subcommands.*;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class ClimateCommand implements CommandExecutor, TabCompleter {

    private final OrbisClimate plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public ClimateCommand(OrbisClimate plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        // Player commands
        subCommands.put("info", new InfoCommand(plugin));
        subCommands.put("forecast", new ForecastCommand(plugin));
        subCommands.put("temperature", new TemperatureCommand(plugin));
        subCommands.put("temp", new TemperatureCommand(plugin)); // Alias
        subCommands.put("zone", new ZoneCommand(plugin));
        subCommands.put("toggle", new ToggleCommand(plugin));
        subCommands.put("status", new StatusCommand(plugin));

        // Admin commands
        subCommands.put("reload", new ReloadCommand(plugin));
        subCommands.put("weather", new WeatherCommand(plugin));
        subCommands.put("performance", new PerformanceCommand(plugin));
        subCommands.put("perf", new PerformanceCommand(plugin)); // Alias
        subCommands.put("regenerate", new RegenerateCommand(plugin));
        subCommands.put("debug", new DebugCommand(plugin));
        subCommands.put("snow", new SnowClearCommand(plugin)); // NEW: Snow clear command
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showMainHelp(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown command! Use /climate for help.");
            return true;
        }

        // Check permissions
        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Execute subcommand
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.execute(sender, subArgs);
    }

    private void showMainHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== OrbisClimate Commands ===");

        // Player commands
        sender.sendMessage(ChatColor.GREEN + "Player Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/climate info " + ChatColor.WHITE + "- Show climate information");
        sender.sendMessage(ChatColor.YELLOW + "/climate forecast " + ChatColor.WHITE + "- Show weather forecast");
        sender.sendMessage(ChatColor.YELLOW + "/climate temperature " + ChatColor.WHITE + "- Show temperature info");
        sender.sendMessage(ChatColor.YELLOW + "/climate zone " + ChatColor.WHITE + "- Show climate zone info");
        sender.sendMessage(ChatColor.YELLOW + "/climate toggle [on|off] " + ChatColor.WHITE + "- Toggle particles");
        sender.sendMessage(ChatColor.YELLOW + "/climate status " + ChatColor.WHITE + "- Show integration status");

        // Admin commands (if they have permission)
        if (sender.hasPermission("orbisclimate.admin")) {
            sender.sendMessage(ChatColor.RED + "Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/climate reload " + ChatColor.WHITE + "- Reload configuration");
            sender.sendMessage(ChatColor.YELLOW + "/climate weather " + ChatColor.WHITE + "- Weather control");
            sender.sendMessage(ChatColor.YELLOW + "/climate performance " + ChatColor.WHITE + "- Performance monitoring");
            sender.sendMessage(ChatColor.YELLOW + "/climate regenerate " + ChatColor.WHITE + "- Regenerate forecast");
            sender.sendMessage(ChatColor.YELLOW + "/climate debug " + ChatColor.WHITE + "- Debug information");
            sender.sendMessage(ChatColor.YELLOW + "/climate snow " + ChatColor.WHITE + "- Clear snow blocks"); // NEW
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Return available subcommands based on permissions
            return subCommands.entrySet().stream()
                    .filter(entry -> entry.getValue().hasPermission(sender))
                    .map(Map.Entry::getKey)
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            // Delegate to subcommand tab completion
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);

            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.getTabCompletions(sender, subArgs);
            }
        }

        return null;
    }
}