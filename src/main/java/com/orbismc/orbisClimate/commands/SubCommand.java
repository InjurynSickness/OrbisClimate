package com.orbismc.orbisClimate.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for all OrbisClimate subcommands
 */
public interface SubCommand {

    /**
     * Execute the subcommand
     * @param sender The command sender
     * @param args The command arguments (excluding the subcommand name)
     * @return true if the command was handled successfully
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * Check if the sender has permission to use this subcommand
     * @param sender The command sender
     * @return true if the sender has permission
     */
    boolean hasPermission(CommandSender sender);

    /**
     * Get tab completions for this subcommand
     * @param sender The command sender
     * @param args The current arguments
     * @return List of possible completions
     */
    List<String> getTabCompletions(CommandSender sender, String[] args);

    /**
     * Get the description of this subcommand
     * @return Description string
     */
    String getDescription();

    /**
     * Get the usage string for this subcommand
     * @return Usage string
     */
    String getUsage();
}