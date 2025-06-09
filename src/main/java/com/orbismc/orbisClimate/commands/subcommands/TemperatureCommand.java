package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.ClimateZoneManager;
import com.orbismc.orbisClimate.TemperatureManager;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.utils.MessageUtils;
import me.casperge.realisticseasons.season.Season;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
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
            MessageUtils.send(sender, MessageUtils.error("Temperature system not available!"));
            return true;
        }

        // Enhanced header
        MessageUtils.send(sender, MessageUtils.header("Temperature Information"));

        double currentTemp = temperatureManager.getPlayerTemperature(player);
        String tempLevel = temperatureManager.getPlayerTemperatureLevel(player);
        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);

        // Enhanced temperature display with thermometer visual
        Component tempDisplay = Component.text()
                .append(MessageUtils.text("Current Temperature: ", MessageUtils.INFO))
                .append(getTemperatureIcon(currentTemp))
                .append(Component.text(" "))
                .append(MessageUtils.temperatureDisplay(currentTemp))
                .build();
        MessageUtils.send(sender, tempDisplay);

        // Comfort level with enhanced visual indicators
        Component comfortDisplay = Component.text()
                .append(MessageUtils.text("Comfort Level: ", MessageUtils.INFO))
                .append(getComfortIcon(tempLevel))
                .append(Component.text(" "))
                .append(MessageUtils.text(tempLevel, MessageUtils.getTemperatureColor(tempLevel), 
                    Style.style(TextDecoration.BOLD)))
                .build();
        MessageUtils.send(sender, comfortDisplay);

        // Zone temperature range with visual progress bar
        MessageUtils.send(sender, MessageUtils.infoLine("Zone Range", 
            zone.getMinTemp() + "°C to " + zone.getMaxTemp() + "°C", 
            MessageUtils.getZoneColor(zone.getDisplayName())));

        // Visual temperature range indicator
        double zoneProgress = (currentTemp - zone.getMinTemp()) / (zone.getMaxTemp() - zone.getMinTemp());
        Component tempBar = MessageUtils.progressBar(zoneProgress, 20, 
            MessageUtils.getTemperatureColorFromValue(currentTemp), MessageUtils.MUTED);
        
        Component tempBarDisplay = Component.text()
                .append(MessageUtils.text("Zone Position: ", MessageUtils.INFO))
                .append(tempBar)
                .append(Component.text(" "))
                .append(MessageUtils.text(String.format("(%.0f%%)", zoneProgress * 100), MessageUtils.MUTED))
                .build();
        MessageUtils.send(sender, tempBarDisplay);

        // Temperature effects with enhanced formatting and icons
        if (temperatureManager.isPlayerTooHot(player)) {
            Component heatWarning = Component.text()
                    .append(Component.text("🔥 ", MessageUtils.ERROR))
                    .append(MessageUtils.text("You are experiencing heat effects!", MessageUtils.ERROR, 
                        Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(sender, heatWarning);
            
            // Show heat mitigation tips
            Component heatTip = Component.text()
                    .append(MessageUtils.text("💡 Tip: ", MessageUtils.ACCENT))
                    .append(MessageUtils.text("Find shade or water to cool down", MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, heatTip);
            
        } else if (temperatureManager.isPlayerTooCold(player)) {
            Component coldWarning = Component.text()
                    .append(Component.text("❄ ", MessageUtils.TEMP_FREEZING))
                    .append(MessageUtils.text("You are experiencing cold effects!", MessageUtils.TEMP_FREEZING, 
                        Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(sender, coldWarning);
            
            // Show cold mitigation tips
            Component coldTip = Component.text()
                    .append(MessageUtils.text("💡 Tip: ", MessageUtils.ACCENT))
                    .append(MessageUtils.text("Find shelter or build a fire to warm up", MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, coldTip);
            
        } else {
            Component comfortMsg = Component.text()
                    .append(Component.text("✓ ", MessageUtils.SUCCESS))
                    .append(MessageUtils.text("Temperature is comfortable", MessageUtils.SUCCESS))
                    .build();
            MessageUtils.send(sender, comfortMsg);
        }

        // Drought bonus with enhanced display
        if (climateZoneManager.isPlayerInDrought(player)) {
            double droughtBonus = plugin.getConfig().getDouble("drought.effects.temperature_bonus", 15.0);
            Component droughtEffect = Component.text()
                    .append(Component.text("🌵 ", MessageUtils.WARNING))
                    .append(MessageUtils.text("Drought Heat Bonus: ", MessageUtils.WARNING))
                    .append(MessageUtils.text("+" + droughtBonus + "°C", MessageUtils.ERROR, 
                        Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(sender, droughtEffect);
        }

        // Indoor status with enhanced formatting
        boolean isIndoors = plugin.getWindManager().isPlayerIndoors(player);
        if (isIndoors) {
            Component indoorMsg = Component.text()
                    .append(Component.text("🏠 ", MessageUtils.SUCCESS))
                    .append(MessageUtils.text("You are indoors", MessageUtils.SUCCESS))
                    .append(MessageUtils.text(" - temperature is stabilizing toward 20°C", MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, indoorMsg);
        } else {
            Component outdoorMsg = Component.text()
                    .append(Component.text("🌍 ", MessageUtils.PRIMARY))
                    .append(MessageUtils.text("You are outdoors", MessageUtils.PRIMARY))
                    .append(MessageUtils.text(" - affected by weather and climate", MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, outdoorMsg);
        }

        // Additional temperature factors with interactive details
        showTemperatureFactors(sender, player);

        return true;
    }

    private void showTemperatureFactors(CommandSender sender, Player player) {
        MessageUtils.send(sender, Component.text(""));
        MessageUtils.send(sender, MessageUtils.text("Temperature Factors:", MessageUtils.ACCENT, 
            Style.style(TextDecoration.BOLD)));

        // Time of day factor
        long time = player.getWorld().getTime();
        double hour = ((time + 6000) % 24000) / 1000.0;
        String timeOfDay = getTimeOfDayDescription((int) hour);
        Component timeIcon = getTimeIcon((int) hour);
        
        Component timeFactor = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(timeIcon)
                .append(Component.text(" "))
                .append(MessageUtils.hoverable("Time of Day", 
                    "Temperature varies throughout the day. Coldest at 6 AM, warmest at 2 PM", 
                    MessageUtils.INFO))
                .append(MessageUtils.text(": ", MessageUtils.INFO))
                .append(MessageUtils.text(timeOfDay, NamedTextColor.WHITE))
                .build();
        MessageUtils.send(sender, timeFactor);

        // Altitude factor
        int altitude = player.getLocation().getBlockY();
        double altitudeEffect = altitude > 62 ? -((altitude - 62) / 100.0) * 0.5 : 0;
        String altitudeDesc = altitude > 62 ? String.format("%.1f°C cooler", Math.abs(altitudeEffect)) : "No effect";
        
        Component altitudeFactor = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text("🏔️ ", MessageUtils.PRIMARY))
                .append(MessageUtils.hoverable("Altitude", 
                    "Temperature drops ~0.5°C per 100 blocks above sea level (Y=62)", 
                    MessageUtils.INFO))
                .append(MessageUtils.text(": ", MessageUtils.INFO))
                .append(MessageUtils.text(altitude + "Y (" + altitudeDesc + ")", NamedTextColor.WHITE))
                .build();
        MessageUtils.send(sender, altitudeFactor);

        // Weather factor
        WeatherForecast.WeatherType currentWeather = plugin.getWeatherForecast().getCurrentWeather(player.getWorld());
        String weatherEffect = getWeatherTemperatureEffect(currentWeather);
        
        Component weatherFactor = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(MessageUtils.weatherSymbol(currentWeather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.hoverable("Weather", 
                    "Different weather types affect temperature differently", 
                    MessageUtils.INFO))
                .append(MessageUtils.text(": ", MessageUtils.INFO))
                .append(MessageUtils.text(currentWeather.getDisplayName() + " (" + weatherEffect + ")", 
                    MessageUtils.getWeatherColor(currentWeather.getDisplayName())))
                .build();
        MessageUtils.send(sender, weatherFactor);

        // Season factor (if RealisticSeasons is enabled)
        if (plugin.getWeatherForecast().isRealisticSeasonsEnabled()) {
            Season currentSeason = plugin.getWeatherForecast().getCurrentSeason(player.getWorld());
            if (currentSeason != null) {
                String seasonEffect = getSeasonTemperatureEffect(currentSeason);
                Component seasonIcon = getSeasonIcon(currentSeason);
                
                Component seasonFactor = Component.text()
                        .append(Component.text("• ", MessageUtils.MUTED))
                        .append(seasonIcon)
                        .append(Component.text(" "))
                        .append(MessageUtils.hoverable("Season", 
                            "Seasons provide temperature modifiers throughout the year", 
                            MessageUtils.INFO))
                        .append(MessageUtils.text(": ", MessageUtils.INFO))
                        .append(MessageUtils.text(currentSeason.toString().toLowerCase() + " (" + seasonEffect + ")", 
                            MessageUtils.SECONDARY))
                        .build();
                MessageUtils.send(sender, seasonFactor);
            }
        }

        // Biome factor
        String biomeName = player.getLocation().getBlock().getBiome().name().toLowerCase().replace("_", " ");
        Component biomeFactor = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text("🌿 ", MessageUtils.SUCCESS))
                .append(MessageUtils.hoverable("Biome", 
                    "Different biomes have different base temperatures", 
                    MessageUtils.INFO))
                .append(MessageUtils.text(": ", MessageUtils.INFO))
                .append(MessageUtils.text(biomeName, NamedTextColor.WHITE))
                .build();
        MessageUtils.send(sender, biomeFactor);
    }

    private Component getTemperatureIcon(double temp) {
        if (temp <= -25) return Component.text("🧊", MessageUtils.TEMP_FREEZING);
        if (temp <= -10) return Component.text("❄️", MessageUtils.TEMP_COLD);
        if (temp <= 0) return Component.text("🌡️", MessageUtils.TEMP_COOL);
        if (temp <= 25) return Component.text("🌡️", MessageUtils.TEMP_COMFORTABLE);
        if (temp <= 35) return Component.text("🌡️", MessageUtils.TEMP_WARM);
        if (temp <= 45) return Component.text("🔥", MessageUtils.TEMP_HOT);
        return Component.text("🌋", MessageUtils.TEMP_SCORCHING);
    }

    private Component getComfortIcon(String tempLevel) {
        switch (tempLevel.toLowerCase()) {
            case "severe cold":
                return Component.text("🥶", MessageUtils.TEMP_FREEZING);
            case "cold":
                return Component.text("🧊", MessageUtils.TEMP_COLD);
            case "mild cold":
                return Component.text("😰", MessageUtils.TEMP_COOL);
            case "comfortable":
                return Component.text("😊", MessageUtils.TEMP_COMFORTABLE);
            case "mild heat":
                return Component.text("😅", MessageUtils.TEMP_WARM);
            case "hot":
                return Component.text("🥵", MessageUtils.TEMP_HOT);
            case "severe heat":
                return Component.text("🔥", MessageUtils.TEMP_SCORCHING);
            default:
                return Component.text("🌡️", NamedTextColor.WHITE);
        }
    }

    private Component getTimeIcon(int hour) {
        if (hour >= 6 && hour < 12) return Component.text("🌅", MessageUtils.ACCENT); // Morning
        if (hour >= 12 && hour < 18) return Component.text("☀️", MessageUtils.WEATHER_CLEAR); // Afternoon
        if (hour >= 18 && hour < 21) return Component.text("🌇", MessageUtils.WARNING); // Evening
        return Component.text("🌙", MessageUtils.PRIMARY); // Night
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

    private String getTimeOfDayDescription(int hour) {
        if (hour >= 6 && hour < 12) return "Morning (warming)";
        if (hour >= 12 && hour < 18) return "Afternoon (warmest)";
        if (hour >= 18 && hour < 21) return "Evening (cooling)";
        return "Night (coolest)";
    }

    private String getWeatherTemperatureEffect(WeatherForecast.WeatherType weather) {
        switch (weather) {
            case CLEAR:
                return "slight warming";
            case LIGHT_RAIN:
                return "-3°C cooling";
            case HEAVY_RAIN:
                return "-5°C cooling";
            case THUNDERSTORM:
                return "-7°C cooling";
            case SNOW:
                return "-8°C cooling";
            case BLIZZARD:
                return "-15°C extreme cooling";
            case SANDSTORM:
                return "+5°C heating";
            default:
                return "no effect";
        }
    }

    private String getSeasonTemperatureEffect(Season season) {
        switch (season) {
            case WINTER:
                return "cold modifier";
            case SPRING:
                return "mild cooling";
            case SUMMER:
                return "warm modifier";
            case FALL:
                return "mild warming";
            default:
                return "no effect";
        }
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