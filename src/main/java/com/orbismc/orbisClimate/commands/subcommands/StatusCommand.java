package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.ClimateZoneManager;
import com.orbismc.orbisClimate.utils.MessageUtils;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatusCommand extends BaseSubCommand {

    public StatusCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.use", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        MessageUtils.send(sender, MessageUtils.header("OrbisClimate Integration Status"));

        WeatherForecast weatherForecast = plugin.getWeatherForecast();

        // RealisticSeasons integration status with enhanced display
        if (weatherForecast.isRealisticSeasonsEnabled()) {
            Component rsStatus = Component.text()
                    .append(Component.text("✓ ", MessageUtils.SUCCESS))
                    .append(MessageUtils.text("RealisticSeasons: ", MessageUtils.INFO))
                    .append(MessageUtils.text("Connected", MessageUtils.SUCCESS, Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(sender, rsStatus);
            
            Component rsDescription = Component.text()
                    .append(MessageUtils.text("  Using RealisticSeasons time and seasons for weather generation", MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, rsDescription);
        } else {
            Component rsStatus = Component.text()
                    .append(Component.text("⚠ ", MessageUtils.WARNING))
                    .append(MessageUtils.text("RealisticSeasons: ", MessageUtils.INFO))
                    .append(MessageUtils.text("Not Available", MessageUtils.WARNING))
                    .build();
            MessageUtils.send(sender, rsStatus);
            
            Component rsDescription = Component.text()
                    .append(MessageUtils.text("  Using vanilla Minecraft time system", MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, rsDescription);
        }

        MessageUtils.send(sender, Component.text(""));

        // Enhanced feature status with categories
        MessageUtils.send(sender, MessageUtils.text("Core Features:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        showFeatureStatus(sender, "Climate Zones", "climate_zones", true);
        showFeatureStatus(sender, "Temperature System", "temperature.enabled", true);
        showFeatureStatus(sender, "Weather Progression", "weather_progression.enabled", true);
        showFeatureStatus(sender, "Wind System", "wind.enabled", true);
        
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Weather Effects:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        showFeatureStatus(sender, "Blizzards", "blizzard.enabled", false);
        showFeatureStatus(sender, "Sandstorms", "sandstorm.enabled", false);
        showFeatureStatus(sender, "Aurora Effects", "aurora.enabled", false);
        showFeatureStatus(sender, "Heat Mirages", "heat_mirages.enabled", false);
        showFeatureStatus(sender, "Drought System", "drought.effects.enabled", true);
        
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Advanced Features:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        showManagerStatus(sender, "Dynamic Sound System", plugin.getDynamicSoundManager() != null);
        showManagerStatus(sender, "Performance Monitoring", plugin.getPerformanceMonitor() != null);
        
        // Enhanced weather progression status
        if (plugin.getWeatherProgressionManager() != null) {
            showProgressionFeatures(sender);
        }

        // Show world-specific info if player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            showWorldSpecificStatus(player);
        }

        // Show performance summary
        showPerformanceSummary(sender);

        return true;
    }
    
    private void showFeatureStatus(CommandSender sender, String featureName, String configPath, boolean defaultValue) {
        boolean enabled = plugin.getConfig().getBoolean(configPath, defaultValue);
        Component featureLine = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text(enabled ? "✓ " : "✗ ", enabled ? MessageUtils.SUCCESS : MessageUtils.ERROR))
                .append(MessageUtils.text(featureName + ": ", MessageUtils.INFO))
                .append(MessageUtils.text(enabled ? "Enabled" : "Disabled", 
                    enabled ? MessageUtils.SUCCESS : MessageUtils.ERROR))
                .build();
        MessageUtils.send(sender, featureLine);
    }
    
    private void showManagerStatus(CommandSender sender, String managerName, boolean available) {
        Component managerLine = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text(available ? "✓ " : "✗ ", available ? MessageUtils.SUCCESS : MessageUtils.ERROR))
                .append(MessageUtils.text(managerName + ": ", MessageUtils.INFO))
                .append(MessageUtils.text(available ? "Available" : "Unavailable", 
                    available ? MessageUtils.SUCCESS : MessageUtils.ERROR))
                .build();
        MessageUtils.send(sender, managerLine);
    }
    
    private void showProgressionFeatures(CommandSender sender) {
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Weather Progression Features:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        showFeatureStatus(sender, "Enhanced Transitions", 
            "weather_progression.enhanced_transitions.enabled", true);
        showFeatureStatus(sender, "Pre-storm Effects", 
            "weather_progression.pre_storm_effects.enabled", true);
        showFeatureStatus(sender, "Lightning Warnings", 
            "weather_progression.pre_storm_effects.lightning_warnings.enabled", true);
        showFeatureStatus(sender, "Hail Effects", 
            "weather_progression.active_weather_effects.hail.enabled", true);
        showFeatureStatus(sender, "Post-storm Effects", 
            "weather_progression.post_storm_effects.enabled", true);
        showFeatureStatus(sender, "Forecast Integration", 
            "weather_progression.forecast_integration.use_forecast_transitions", true);
    }

    private void showWorldSpecificStatus(Player player) {
        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();
        WeatherForecast weatherForecast = plugin.getWeatherForecast();

        MessageUtils.send(player, Component.text(""));
        Component worldHeader = Component.text()
                .append(MessageUtils.text("World Status (", MessageUtils.INFO))
                .append(MessageUtils.text(player.getWorld().getName(), MessageUtils.PRIMARY, 
                    Style.style(TextDecoration.BOLD)))
                .append(MessageUtils.text("):", MessageUtils.INFO))
                .build();
        MessageUtils.send(player, worldHeader);

        // Show RealisticSeasons data if available
        if (weatherForecast.isRealisticSeasonsEnabled()) {
            Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
            Date currentDate = weatherForecast.getCurrentDate(player.getWorld());

            if (currentSeason != null) {
                Component seasonIcon = getSeasonIcon(currentSeason);
                Component seasonLine = Component.text()
                        .append(MessageUtils.text("• Season: ", MessageUtils.INFO))
                        .append(seasonIcon)
                        .append(Component.text(" "))
                        .append(MessageUtils.text(currentSeason.toString().toLowerCase(), MessageUtils.SECONDARY))
                        .build();
                MessageUtils.send(player, seasonLine);
            }

            if (currentDate != null) {
                String dateStr = currentDate.getMonth() + "/" + currentDate.getDay() + "/" + currentDate.getYear();
                MessageUtils.send(player, MessageUtils.infoLine("Date", dateStr, MessageUtils.SECONDARY));
            }
        }

        // Current weather with enhanced display
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        Component weatherLine = Component.text()
                .append(MessageUtils.text("• Weather: ", MessageUtils.INFO))
                .append(MessageUtils.weatherSymbol(currentWeather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(currentWeather.getDisplayName(), 
                    MessageUtils.getWeatherColor(currentWeather.getDisplayName())))
                .build();
        MessageUtils.send(player, weatherLine);

        // Player's climate zone
        if (climateZoneManager != null) {
            ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);
            Component zoneLine = Component.text()
                    .append(MessageUtils.text("• Your Zone: ", MessageUtils.INFO))
                    .append(getZoneIcon(zone))
                    .append(Component.text(" "))
                    .append(MessageUtils.text(zone.getDisplayName(), 
                        MessageUtils.getZoneColor(zone.getDisplayName())))
                    .build();
            MessageUtils.send(player, zoneLine);
        }

        // Active weather systems
        showActiveWeatherSystems(player);
    }
    
    private void showActiveWeatherSystems(Player player) {
        boolean hasActiveWeather = false;
        Component.Builder activeSystemsBuilder = Component.text()
                .append(MessageUtils.text("• Active Systems: ", MessageUtils.INFO));

        if (plugin.getBlizzardManager().isBlizzardActive(player.getWorld())) {
            activeSystemsBuilder.append(Component.text("❄ Blizzard ", MessageUtils.WEATHER_BLIZZARD));
            hasActiveWeather = true;
        }

        if (plugin.getSandstormManager().isSandstormActive(player.getWorld())) {
            activeSystemsBuilder.append(Component.text("🌪 Sandstorm ", MessageUtils.WEATHER_SAND));
            hasActiveWeather = true;
        }

        if (plugin.getWindManager().hasActiveWind(player.getWorld())) {
            activeSystemsBuilder.append(Component.text("💨 Wind ", MessageUtils.PRIMARY));
            hasActiveWeather = true;
        }

        if (plugin.getWeatherProgressionManager() != null && 
            plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
            activeSystemsBuilder.append(Component.text("❄ Hail ", NamedTextColor.WHITE));
            hasActiveWeather = true;
        }

        if (!hasActiveWeather) {
            activeSystemsBuilder.append(MessageUtils.text("None", MessageUtils.MUTED));
        }

        MessageUtils.send(player, activeSystemsBuilder.build());
    }
    
    private void showPerformanceSummary(CommandSender sender) {
        if (plugin.getPerformanceMonitor() == null) return;

        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Performance Summary:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));

        double tps = plugin.getPerformanceMonitor().getCurrentTPS();
        Component tpsLine = Component.text()
                .append(MessageUtils.text("• TPS: ", MessageUtils.INFO))
                .append(MessageUtils.text(String.format("%.2f", tps), getTpsColor(tps)))
                .build();
        MessageUtils.send(sender, tpsLine);

        boolean performanceMode = plugin.getPerformanceMonitor().isPerformanceMode();
        Component perfModeLine = Component.text()
                .append(MessageUtils.text("• Performance Mode: ", MessageUtils.INFO))
                .append(MessageUtils.text(performanceMode ? "Active" : "Inactive", 
                    performanceMode ? MessageUtils.WARNING : MessageUtils.SUCCESS))
                .build();
        MessageUtils.send(sender, perfModeLine);

        double multiplier = plugin.getPerformanceMonitor().getPerformanceMultiplier();
        Component multiplierLine = Component.text()
                .append(MessageUtils.text("• Effect Multiplier: ", MessageUtils.INFO))
                .append(MessageUtils.text(String.format("%.1fx", multiplier), 
                    multiplier >= 1.0 ? MessageUtils.SUCCESS : 
                    multiplier >= 0.5 ? MessageUtils.WARNING : MessageUtils.ERROR))
                .build();
        MessageUtils.send(sender, multiplierLine);
    }
    
    private Component getSeasonIcon(Season season) {
        switch (season) {
            case SPRING:
                return Component.text("🌸", MessageUtils.SUCCESS);
            case SUMMER:
                return Component.text("☀️", MessageUtils.WEATHER_CLEAR);
            case FALL:
                return Component.text("🍂", MessageUtils.WARNING);
            case WINTER:
                return Component.text("❄️", MessageUtils.WEATHER_SNOW);
            default:
                return Component.text("🌿", MessageUtils.SUCCESS);
        }
    }
    
    private Component getZoneIcon(ClimateZoneManager.ClimateZone zone) {
        switch (zone) {
            case ARCTIC:
                return Component.text("🧊", MessageUtils.ZONE_ARCTIC);
            case TEMPERATE:
                return Component.text("🌿", MessageUtils.ZONE_TEMPERATE);
            case DESERT:
                return Component.text("🌵", MessageUtils.ZONE_DESERT);
            case ARID:
                return Component.text("🏜️", MessageUtils.ZONE_ARID);
            default:
                return Component.text("🌍", MessageUtils.PRIMARY);
        }
    }
    
    private net.kyori.adventure.text.format.TextColor getTpsColor(double tps) {
        if (tps >= 19.5) return MessageUtils.SUCCESS;
        if (tps >= 18.0) return MessageUtils.WARNING;
        return MessageUtils.ERROR;
    }

    @Override
    public String getDescription() {
        return "Show plugin integration and feature status";
    }

    @Override
    public String getUsage() {
        return "/climate status";
    }
}