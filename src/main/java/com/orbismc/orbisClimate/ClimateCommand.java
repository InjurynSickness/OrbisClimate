package com.orbismc.orbisClimate;

import com.orbismc.orbisClimate.commands.SubCommand;
import com.orbismc.orbisClimate.commands.subcommands.*;
import com.orbismc.orbisClimate.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
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
        subCommands.put("snow", new SnowClearCommand(plugin));
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
            MessageUtils.send(sender, MessageUtils.error("Unknown command! Use /climate for help."));
            return true;
        }

        // Check permissions
        if (!subCommand.hasPermission(sender)) {
            MessageUtils.send(sender, MessageUtils.error("You don't have permission to use this command!"));
            return true;
        }

        // Execute subcommand
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.execute(sender, subArgs);
    }

    private void showMainHelp(CommandSender sender) {
        // Enhanced main help with Adventure components
        MessageUtils.send(sender, MessageUtils.header("OrbisClimate Commands"));

        // Player commands section
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Player Commands:", MessageUtils.SUCCESS, 
            Style.style(TextDecoration.BOLD)));

        showCommandHelp(sender, "info", "Show climate information", "/climate info");
        showCommandHelp(sender, "forecast", "Show weather forecast", "/climate forecast [detailed]");
        showCommandHelp(sender, "temperature", "Show temperature info", "/climate temperature");
        showCommandHelp(sender, "zone", "Show climate zone info", "/climate zone");
        showCommandHelp(sender, "toggle", "Toggle particles", "/climate toggle [on|off]");
        showCommandHelp(sender, "status", "Show integration status", "/climate status");

        // Admin commands section (if they have permission)
        if (sender.hasPermission("orbisclimate.admin")) {
            MessageUtils.send(sender, Component.text(""));
            MessageUtils.send(sender, MessageUtils.text("Admin Commands:", MessageUtils.ERROR, 
                Style.style(TextDecoration.BOLD)));

            showCommandHelp(sender, "reload", "Reload configuration", "/climate reload");
            showCommandHelp(sender, "weather", "Weather control", "/climate weather <set|clear|info>");
            showCommandHelp(sender, "performance", "Performance monitoring", "/climate performance <report|mode>");
            showCommandHelp(sender, "regenerate", "Regenerate forecast", "/climate regenerate");
            showCommandHelp(sender, "debug", "Debug information", "/climate debug");
            showCommandHelp(sender, "snow", "Clear snow blocks", "/climate snow <area|world|radius>");
        }

        // Footer with tips
        MessageUtils.send(sender, Component.text(""));
        Component tipLine = Component.text()
                .append(MessageUtils.text("💡 Tip: ", MessageUtils.ACCENT))
                .append(MessageUtils.text("Hover over commands for more details, click to execute!", MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, tipLine);

        // Show plugin status
        if (plugin.getPerformanceMonitor() != null && plugin.getPerformanceMonitor().isPerformanceMode()) {
            Component perfWarning = Component.text()
                    .append(Component.text("⚠ ", MessageUtils.WARNING))
                    .append(MessageUtils.text("Server is in performance mode - some effects may be reduced", 
                        MessageUtils.WARNING))
                    .build();
            MessageUtils.send(sender, perfWarning);
        }
    }

    private void showCommandHelp(CommandSender sender, String command, String description, String usage) {
        Component commandLine = Component.text()
                .append(MessageUtils.clickableCommand("/" + command, usage, MessageUtils.ACCENT))
                .append(MessageUtils.text(" - ", MessageUtils.MUTED))
                .append(MessageUtils.hoverable(description, 
                    "Usage: " + usage + "\n\nClick to execute this command!", 
                    MessageUtils.INFO))
                .build();
        MessageUtils.send(sender, commandLine);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Return available subcommands based on permissions with enhanced filtering
            return subCommands.entrySet().stream()
                    .filter(entry -> entry.getValue().hasPermission(sender))
                    .map(Map.Entry::getKey)
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted() // Sort alphabetically
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            // Delegate to subcommand tab completion
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);

            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                List<String> completions = subCommand.getTabCompletions(sender, subArgs);
                
                // Filter completions based on current input
                if (completions != null && !completions.isEmpty() && subArgs.length > 0) {
                    String currentArg = subArgs[subArgs.length - 1].toLowerCase();
                    return completions.stream()
                            .filter(completion -> completion.toLowerCase().startsWith(currentArg))
                            .sorted()
                            .collect(Collectors.toList());
                }
                
                return completions;
            }
        }

        return Collections.emptyList();
    }
}