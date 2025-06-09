package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends BaseSubCommand {

    public ReloadCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.reload", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Component reloadingMsg = Component.text()
                .append(Component.text("🔄 ", MessageUtils.WARNING))
                .append(MessageUtils.text("Reloading OrbisClimate configuration...", MessageUtils.WARNING))
                .build();
        MessageUtils.send(sender, reloadingMsg);

        try {
            plugin.reloadConfiguration();
            
            Component successMsg = Component.text()
                    .append(Component.text("✓ ", MessageUtils.SUCCESS))
                    .append(MessageUtils.text("OrbisClimate configuration reloaded successfully!", MessageUtils.SUCCESS))
                    .build();
            MessageUtils.send(sender, successMsg);

            // Show performance impact of reload
            if (plugin.getPerformanceMonitor() != null) {
                double tps = plugin.getPerformanceMonitor().getCurrentTPS();
                Component perfInfo = Component.text()
                        .append(MessageUtils.text("Current TPS: ", MessageUtils.MUTED))
                        .append(MessageUtils.text(String.format("%.2f", tps), getTpsColor(tps)))
                        .build();
                MessageUtils.send(sender, perfInfo);
            }
        } catch (Exception e) {
            Component errorMsg = Component.text()
                    .append(Component.text("✗ ", MessageUtils.ERROR))
                    .append(MessageUtils.text("Error reloading configuration: " + e.getMessage(), MessageUtils.ERROR))
                    .build();
            MessageUtils.send(sender, errorMsg);
            plugin.getLogger().severe("Configuration reload failed: " + e.getMessage());
        }

        return true;
    }
    
    private net.kyori.adventure.text.format.TextColor getTpsColor(double tps) {
        if (tps >= 19.5) return MessageUtils.SUCCESS;
        if (tps >= 18.0) return MessageUtils.WARNING;
        return MessageUtils.ERROR;
    }

    @Override
    public String getDescription() {
        return "Reload the plugin configuration";
    }

    @Override
    public String getUsage() {
        return "/climate reload";
    }
}