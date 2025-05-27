package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class SnowClearCommand extends BaseSubCommand {

    public SnowClearCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.weather", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        if (args.length == 0) {
            showSnowClearHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "area":
                return handleAreaClear(player, args);
            case "world":
                return handleWorldClear(player, args);
            case "radius":
                return handleRadiusClear(player, args);
            default:
                player.sendMessage(ChatColor.RED + "Unknown snow clear command! Use '/climate snow' for help.");
                return true;
        }
    }

    private void showSnowClearHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Snow Clear Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/climate snow area <radius> " + ChatColor.WHITE + "- Clear snow in area around you");
        player.sendMessage(ChatColor.YELLOW + "/climate snow world " + ChatColor.WHITE + "- Clear all snow in current world");
        player.sendMessage(ChatColor.YELLOW + "/climate snow radius <blocks> " + ChatColor.WHITE + "- Clear snow in specific radius");
        player.sendMessage(ChatColor.GRAY + "Note: This removes snow blocks placed by vanilla weather, not our particle effects");
    }

    private boolean handleAreaClear(Player player, String[] args) {
        int radius = 50; // Default radius

        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
                if (radius < 1 || radius > 200) {
                    player.sendMessage(ChatColor.RED + "Radius must be between 1 and 200 blocks!");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid radius! Must be a number.");
                return true;
            }
        }

        return clearSnowInRadius(player, radius);
    }

    private boolean handleWorldClear(Player player, String[] args) {
        if (!player.hasPermission("orbisclimate.admin")) {
            player.sendMessage(ChatColor.RED + "You need admin permission to clear snow from entire world!");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Clearing all snow from world " + player.getWorld().getName() + "...");
        player.sendMessage(ChatColor.GRAY + "This may take a while for large worlds!");

        // Run async to prevent lag
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int clearedBlocks = clearSnowInWorld(player.getWorld());

            // Send result back on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (clearedBlocks > 0) {
                    player.sendMessage(ChatColor.GREEN + "Cleared " + clearedBlocks + " snow blocks from " +
                            player.getWorld().getName() + "!");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "No snow blocks found to clear in " +
                            player.getWorld().getName());
                }
            });
        });

        return true;
    }

    private boolean handleRadiusClear(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /climate snow radius <blocks>");
            return true;
        }

        try {
            int radius = Integer.parseInt(args[1]);
            if (radius < 1 || radius > 200) {
                player.sendMessage(ChatColor.RED + "Radius must be between 1 and 200 blocks!");
                return true;
            }

            return clearSnowInRadius(player, radius);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid radius! Must be a number.");
            return true;
        }
    }

    private boolean clearSnowInRadius(Player player, int radius) {
        Location center = player.getLocation();
        player.sendMessage(ChatColor.YELLOW + "Clearing snow in " + radius + " block radius...");

        // Run async to prevent lag
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int clearedBlocks = 0;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = -radius; y <= radius; y++) {
                        Location blockLoc = center.clone().add(x, y, z);

                        // Check if we're still within the radius (circular area)
                        if (blockLoc.distance(center) > radius) continue;

                        Block block = blockLoc.getBlock();
                        if (isSnowBlock(block.getType())) {
                            // Schedule block removal on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                block.setType(Material.AIR);
                            });
                            clearedBlocks++;
                        }
                    }
                }
            }

            // Send result back on main thread
            final int finalClearedBlocks = clearedBlocks;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalClearedBlocks > 0) {
                    player.sendMessage(ChatColor.GREEN + "Cleared " + finalClearedBlocks +
                            " snow blocks in " + radius + " block radius!");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "No snow blocks found to clear in the specified area.");
                }
            });
        });

        return true;
    }

    private int clearSnowInWorld(World world) {
        int clearedBlocks = 0;

        // This is a simplified approach - in a real implementation, you'd want to
        // use chunk loading and more efficient methods for large worlds
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (isSnowBlock(block.getType())) {
                            // Schedule block removal on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                block.setType(Material.AIR);
                            });
                            clearedBlocks++;
                        }
                    }
                }
            }
        }

        return clearedBlocks;
    }

    private boolean isSnowBlock(Material material) {
        return material == Material.SNOW ||
                material == Material.SNOW_BLOCK ||
                material == Material.POWDER_SNOW;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("area", "world", "radius");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("area") || args[0].equalsIgnoreCase("radius"))) {
            return Arrays.asList("10", "25", "50", "100");
        }
        return super.getTabCompletions(sender, args);
    }

    @Override
    public String getDescription() {
        return "Clear snow blocks from areas (does not affect particle effects)";
    }

    @Override
    public String getUsage() {
        return "/climate snow <area|world|radius> [radius]";
    }
}