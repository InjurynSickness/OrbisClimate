package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.commands.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public abstract class BaseSubCommand implements SubCommand {

    protected final OrbisClimate plugin;
    private final String permission;
    private final boolean requiresPlayer;

    public BaseSubCommand(OrbisClimate plugin, String permission, boolean requiresPlayer) {
        this.plugin = plugin;
        this.permission = permission;
        this.requiresPlayer = requiresPlayer;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (requiresPlayer && !(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        return executeCommand(sender, args);
    }

    /**
     * Execute the actual command logic
     */
    protected abstract boolean executeCommand(CommandSender sender, String[] args);

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList(); // Override in subclasses if needed
    }

    /**
     * Helper method to get player from sender
     */
    protected Player getPlayer(CommandSender sender) {
        return (Player) sender;
    }

    /**
     * Helper method to check if sender is player
     */
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }
}