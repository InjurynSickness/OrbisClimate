package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.ClimateZoneManager;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ZoneCommand extends BaseSubCommand {

    public ZoneCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.info", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();

        if (climateZoneManager == null) {
            MessageUtils.send(sender, MessageUtils.error("Climate zone system not available!"));
            return true;
        }

        MessageUtils.send(sender, MessageUtils.header("Climate Zone Information"));

        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);
        Component zoneLine = Component.text()
                .append(MessageUtils.text("Current Zone: ", MessageUtils.INFO))
                .append(getZoneIcon(zone))
                .append(Component.text(" "))
                .append(MessageUtils.text(zone.getDisplayName(), 
                    MessageUtils.getZoneColor(zone.getDisplayName()), 
                    Style.style(TextDecoration.BOLD)))
                .build();
        MessageUtils.send(sender, zoneLine);

        // Zone characteristics with enhanced display
        Component tempRangeLine = Component.text()
                .append(MessageUtils.text("Temperature Range: ", MessageUtils.INFO))
                .append(MessageUtils.text(zone.getMinTemp() + "°C to " + zone.getMaxTemp() + "°C", 
                    MessageUtils.getZoneColor(zone.getDisplayName())))
                .build();
        MessageUtils.send(sender, tempRangeLine);

        // Zone-specific weather with weather symbol
        WeatherForecast.WeatherType zoneWeather = climateZoneManager.getPlayerZoneWeather(player);
        Component zoneWeatherLine = Component.text()
                .append(MessageUtils.text("Zone Weather: ", MessageUtils.INFO))
                .append(MessageUtils.weatherSymbol(zoneWeather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(zoneWeather.getDisplayName(), 
                    MessageUtils.getWeatherColor(zoneWeather.getDisplayName())))
                .build();
        MessageUtils.send(sender, zoneWeatherLine);

        // Enhanced special zone effects
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Zone Effects:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        switch (zone) {
            case ARCTIC:
                showZoneEffect(sender, "❄", "Extreme Cold", "Freezing temperatures year-round");
                showZoneEffect(sender, "🌌", "Aurora Effects", "Beautiful aurora displays at night");
                showZoneEffect(sender, "💨", "Wind-blown Snow", "Constant snow particles in wind");
                break;
            case DESERT:
                showZoneEffect(sender, "🌡️", "Intense Heat", "Scorching temperatures during day");
                showZoneEffect(sender, "✨", "Heat Mirages", "Shimmering mirage effects in heat");
                showZoneEffect(sender, "🌪", "Sandstorms", "Powerful sandstorm events");
                if (climateZoneManager.isPlayerInDrought(player)) {
                    Component droughtWarning = Component.text()
                            .append(Component.text("🌵 ", MessageUtils.ERROR))
                            .append(MessageUtils.text("Drought Active", MessageUtils.ERROR, Style.style(TextDecoration.BOLD)))
                            .append(MessageUtils.text(" - Increased heat and effects!", MessageUtils.WARNING))
                            .build();
                    MessageUtils.send(sender, droughtWarning);
                }
                break;
            case TEMPERATE:
                showZoneEffect(sender, "🌿", "Seasonal Variation", "Weather changes with seasons");
                showZoneEffect(sender, "🌀", "Hurricane Potential", "Severe storms during bad weather");
                showZoneEffect(sender, "🌡️", "Moderate Climate", "Comfortable temperature range");
                break;
            case ARID:
                showZoneEffect(sender, "🏜️", "Dry Heat", "Hot and dry conditions");
                showZoneEffect(sender, "🌵", "Desert Adaptation", "Resistant to extreme conditions");
                showZoneEffect(sender, "💨", "Dust Storms", "Occasional dust storm events");
                break;
        }

        // Enhanced position info
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Location Details:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        Component coordsLine = Component.text()
                .append(MessageUtils.text("Coordinates: ", MessageUtils.INFO))
                .append(MessageUtils.text(String.format("%d, %d, %d", 
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ()), MessageUtils.ACCENT))
                .build();
        MessageUtils.send(sender, coordsLine);
        
        String biomeName = player.getLocation().getBlock().getBiome().name().toLowerCase().replace("_", " ");
        Component biomeLine = Component.text()
                .append(MessageUtils.text("Biome: ", MessageUtils.INFO))
                .append(MessageUtils.text(biomeName, MessageUtils.SUCCESS))
                .build();
        MessageUtils.send(sender, biomeLine);

        return true;
    }
    
    private void showZoneEffect(CommandSender sender, String icon, String name, String description) {
        Component effectLine = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text(icon + " ", MessageUtils.PRIMARY))
                .append(MessageUtils.hoverable(name, description, MessageUtils.INFO))
                .append(MessageUtils.text(": ", MessageUtils.INFO))
                .append(MessageUtils.text(description, NamedTextColor.WHITE))
                .build();
        MessageUtils.send(sender, effectLine);
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

    @Override
    public String getDescription() {
        return "Show detailed climate zone information";
    }

    @Override
    public String getUsage() {
        return "/climate zone";
    }
}