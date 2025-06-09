package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.*;
import com.orbismc.orbisClimate.utils.MessageUtils;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InfoCommand extends BaseSubCommand {

    public InfoCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.info", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        ClimateZoneManager climateZoneManager = plugin.getClimateZoneManager();
        TemperatureManager temperatureManager = plugin.getTemperatureManager();
        WeatherForecast weatherForecast = plugin.getWeatherForecast();
        WindManager windManager = plugin.getWindManager();

        if (climateZoneManager == null || temperatureManager == null) {
            MessageUtils.send(sender, MessageUtils.error("Climate system not fully initialized!"));
            return true;
        }

        // Header
        MessageUtils.send(sender, MessageUtils.header("Climate Information"));

        // Current weather info with enhanced display
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        WeatherForecast.WeatherType zoneWeather = climateZoneManager.getPlayerZoneWeather(player);

        Component weatherLine = Component.text()
                .append(MessageUtils.text("World Weather: ", MessageUtils.INFO))
                .append(MessageUtils.weatherSymbol(currentWeather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(currentWeather.getDisplayName(), 
                    MessageUtils.getWeatherColor(currentWeather.getDisplayName())))
                .build();
        MessageUtils.send(sender, weatherLine);

        if (zoneWeather != currentWeather) {
            Component zoneWeatherLine = Component.text()
                    .append(MessageUtils.text("Your Zone Weather: ", MessageUtils.INFO))
                    .append(MessageUtils.weatherSymbol(zoneWeather.getDisplayName()))
                    .append(Component.text(" "))
                    .append(MessageUtils.text(zoneWeather.getDisplayName(), 
                        MessageUtils.getWeatherColor(zoneWeather.getDisplayName())))
                    .build();
            MessageUtils.send(sender, zoneWeatherLine);
        }

        // Climate zone info with color coding
        ClimateZoneManager.ClimateZone zone = climateZoneManager.getPlayerClimateZone(player);
        Component zoneLine = Component.text()
                .append(MessageUtils.text("Climate Zone: ", MessageUtils.INFO))
                .append(MessageUtils.text(zone.getDisplayName(), 
                    MessageUtils.getZoneColor(zone.getDisplayName())).style(Style.style(TextDecoration.BOLD)))
                .build();
        MessageUtils.send(sender, zoneLine);

        // Temperature info with color coding and hover details
        double temperature = temperatureManager.getPlayerTemperature(player);
        String tempLevel = temperatureManager.getPlayerTemperatureLevel(player);
        
        Component tempHover = Component.text()
                .append(Component.text("Zone Range: ", MessageUtils.MUTED))
                .append(Component.text(zone.getMinTemp() + "°C to " + zone.getMaxTemp() + "°C", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Comfort Level: ", MessageUtils.MUTED))
                .append(MessageUtils.text(tempLevel, MessageUtils.getTemperatureColor(tempLevel)))
                .build();
        
        Component tempLine = Component.text()
                .append(MessageUtils.text("Temperature: ", MessageUtils.INFO))
                .append(MessageUtils.hoverable(
                    MessageUtils.getPlainText(MessageUtils.temperatureDisplay(temperature)),  // FIXED: Use getPlainText
                    tempHover,
                    MessageUtils.getTemperatureColorFromValue(temperature)
                ))
                .build();
        MessageUtils.send(sender, tempLine);

        // Show season information if RealisticSeasons is enabled
        if (weatherForecast.isRealisticSeasonsEnabled()) {
            Season currentSeason = weatherForecast.getCurrentSeason(player.getWorld());
            Date currentDate = weatherForecast.getCurrentDate(player.getWorld());

            if (currentSeason != null) {
                MessageUtils.send(sender, MessageUtils.infoLine("Current Season", 
                    currentSeason.toString().toLowerCase(), MessageUtils.SECONDARY));
            }

            if (currentDate != null) {
                String dateStr = currentDate.getMonth() + "/" + currentDate.getDay() + "/" + currentDate.getYear();
                MessageUtils.send(sender, MessageUtils.infoLine("Current Date", dateStr));
            }
        }

        // Special conditions with enhanced formatting
        if (climateZoneManager.isPlayerInDrought(player)) {
            Component droughtWarning = Component.text()
                    .append(Component.text("⚠ ", MessageUtils.WARNING))
                    .append(MessageUtils.text("Drought conditions active in your area!", MessageUtils.ERROR).style(Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(sender, droughtWarning);
        }

        // Wind chances with interactive hover
        String windChance = getWindChanceDescription(zoneWeather);
        Component windHover = Component.text()
                .append(Component.text("Wind chances vary by weather:", MessageUtils.MUTED))
                .append(Component.newline())
                .append(Component.text("• Clear: 10%", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("• Rain: 25%", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("• Snow: 15%", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("• Storms: 100%", NamedTextColor.WHITE))
                .build();
        
        MessageUtils.send(sender, MessageUtils.hoverable("(" + windChance + ")", windHover, MessageUtils.MUTED));

        // Indoor/outdoor status with enhanced display
        boolean isIndoors = windManager.isPlayerIndoors(player);
        Component locationIcon = isIndoors ? 
            Component.text("🏠 ", MessageUtils.SUCCESS) : 
            Component.text("🌍 ", MessageUtils.PRIMARY);
        
        Component locationLine = Component.text()
                .append(MessageUtils.text("Location: ", MessageUtils.INFO))
                .append(locationIcon)
                .append(MessageUtils.text(isIndoors ? "Indoors (protected from weather)" : "Outdoors",
                    isIndoors ? MessageUtils.SUCCESS : NamedTextColor.WHITE))
                .build();
        MessageUtils.send(sender, locationLine);

        // Show particle status with toggle command
        boolean particlesEnabled = plugin.isPlayerParticlesEnabled(player);
        Component particleToggle = MessageUtils.clickableCommand(
            "(click to toggle)",
            "/climate toggle",
            MessageUtils.MUTED
        );
        
        Component particleLine = Component.text()
                .append(MessageUtils.text("Particles: ", MessageUtils.INFO))
                .append(MessageUtils.text(particlesEnabled ? "Enabled" : "Disabled",
                    particlesEnabled ? MessageUtils.SUCCESS : MessageUtils.ERROR))
                .append(Component.text(" "))
                .append(particleToggle)
                .build();
        MessageUtils.send(sender, particleLine);

        // Show active effects
        if (!isIndoors) {
            boolean hasActiveWind = windManager.hasActiveWind(player.getWorld());
            Component windIcon = hasActiveWind ? 
                Component.text("💨 ", MessageUtils.PRIMARY) : 
                Component.text("🌀 ", MessageUtils.MUTED);
            
            Component windLine = Component.text()
                    .append(MessageUtils.text("Wind Status: ", MessageUtils.INFO))
                    .append(windIcon)
                    .append(MessageUtils.text(hasActiveWind ? "Active" : "Calm",
                        hasActiveWind ? MessageUtils.SUCCESS : MessageUtils.MUTED))
                    .build();
            MessageUtils.send(sender, windLine);

            // Weather progression information with enhanced display
            if (plugin.getWeatherProgressionManager() != null) {
                WeatherProgressionManager.WeatherProgression progression =
                        plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
                if (progression != WeatherProgressionManager.WeatherProgression.CLEAR) {
                    String progressionName = progression.name().toLowerCase().replace("_", " ");
                    MessageUtils.send(sender, MessageUtils.infoLine("Weather Stage", progressionName, MessageUtils.ACCENT));
                }

                // Show if in transition
                if (plugin.getWeatherProgressionManager().isInTransition(player.getWorld())) {
                    Component transitionMsg = Component.text()
                            .append(Component.text("⚡ ", MessageUtils.WARNING))
                            .append(MessageUtils.text("Weather is transitioning...", MessageUtils.WARNING))
                            .build();
                    MessageUtils.send(sender, transitionMsg);
                }

                // Show special effects
                if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
                    Component hailMsg = Component.text()
                            .append(Component.text("❄ ", NamedTextColor.WHITE))
                            .append(MessageUtils.text("Hail is currently falling!", NamedTextColor.WHITE).style(Style.style(TextDecoration.BOLD)))
                            .build();
                    MessageUtils.send(sender, hailMsg);
                }

                // Show upcoming weather transitions with clickable forecast
                if (weatherForecast.getHoursUntilNextTransition(player.getWorld()) != -1) {
                    int hoursUntil = weatherForecast.getHoursUntilNextTransition(player.getWorld());
                    WeatherForecast.WeatherType nextWeather = weatherForecast.getNextTransitionWeather(player.getWorld());
                    if (nextWeather != null && hoursUntil <= 3) {
                        String timeDesc = hoursUntil == 0 ? "soon" : "in " + hoursUntil + " hour(s)";
                        
                        Component nextWeatherLine = Component.text()
                                .append(MessageUtils.text("Next: ", MessageUtils.MUTED))
                                .append(MessageUtils.weatherSymbol(nextWeather.getDisplayName()))
                                .append(Component.text(" "))
                                .append(MessageUtils.text(nextWeather.getDisplayName(), 
                                    MessageUtils.getWeatherColor(nextWeather.getDisplayName())))
                                .append(MessageUtils.text(" " + timeDesc, MessageUtils.MUTED))
                                .append(Component.text(" "))
                                .append(MessageUtils.clickableCommand("(forecast)", "/climate forecast", MessageUtils.MUTED))
                                .build();
                        MessageUtils.send(sender, nextWeatherLine);
                    }
                }
            }
        }

        // Add performance context with enhanced display
        showPerformanceContext(sender);

        return true;
    }

    private void showPerformanceContext(CommandSender sender) {
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) return;

        if (monitor.isPerformanceMode()) {
            Component perfWarning = Component.text()
                    .append(Component.text("⚠ ", MessageUtils.WARNING))
                    .append(MessageUtils.text("Server is in performance mode - some effects may be reduced", 
                        MessageUtils.WARNING))
                    .build();
            MessageUtils.send(sender, perfWarning);
        }

        if (sender instanceof Player && monitor.shouldSkipEffects((Player) sender)) {
            Component effectWarning = Component.text()
                    .append(Component.text("⚠ ", MessageUtils.ERROR))
                    .append(MessageUtils.text("Effects are currently disabled for you due to performance", 
                        MessageUtils.ERROR))
                    .build();
            MessageUtils.send(sender, effectWarning);
        }
    }

    private String getWindChanceDescription(WeatherForecast.WeatherType weather) {
        switch (weather) {
            case THUNDERSTORM:
                return "100% wind chance";
            case HEAVY_RAIN:
            case LIGHT_RAIN:
            case BLIZZARD:
                return "25% wind chance";
            case SNOW:
                return "15% wind chance";
            case SANDSTORM:
                return "High wind chance";
            default:
                return "10% wind chance";
        }
    }

    @Override
    public String getDescription() {
        return "Show detailed climate information for your location";
    }

    @Override
    public String getUsage() {
        return "/climate info";
    }
}