package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.WeatherProgressionManager;
import com.orbismc.orbisClimate.utils.MessageUtils;
import me.casperge.realisticseasons.calendar.Date;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ForecastCommand extends BaseSubCommand {

    public ForecastCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.forecast", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        WeatherForecast weatherForecast = plugin.getWeatherForecast();
        
        // Check for detailed forecast argument
        boolean showDetailed = args.length > 0 && args[0].equalsIgnoreCase("detailed");
        
        if (showDetailed) {
            return showDetailedForecast(player, weatherForecast);
        } else {
            return showStandardForecast(player, weatherForecast);
        }
    }
    
    private boolean showStandardForecast(Player player, WeatherForecast weatherForecast) {
        WeatherForecast.DetailedForecast forecast = weatherForecast.getForecast(player.getWorld());

        if (forecast == null) {
            MessageUtils.send(player, MessageUtils.error("No forecast available for this world yet!"));
            return true;
        }

        // Build enhanced header with date and season info
        Component header;
        if (weatherForecast.isRealisticSeasonsEnabled() && forecast.getDate() != null) {
            Date date = forecast.getDate();
            String dateStr = date.getMonth() + "/" + date.getDay() + "/" + date.getYear();
            
            Component.Builder headerBuilder = Component.text()
                    .append(Component.text("=== ", MessageUtils.ACCENT))
                    .append(Component.text("Weather Forecast", MessageUtils.PRIMARY, Style.style(TextDecoration.BOLD)))
                    .append(Component.text(" - ", MessageUtils.ACCENT))
                    .append(Component.text(dateStr, MessageUtils.SECONDARY));

            if (forecast.getSeason() != null) {
                headerBuilder.append(Component.text(" (", MessageUtils.MUTED))
                        .append(Component.text(forecast.getSeason().toString().toLowerCase(), MessageUtils.SECONDARY))
                        .append(Component.text(")", MessageUtils.MUTED));
            }
            
            header = headerBuilder.append(Component.text(" ===", MessageUtils.ACCENT)).build();
        } else {
            header = Component.text()
                    .append(Component.text("=== ", MessageUtils.ACCENT))
                    .append(Component.text("Weather Forecast", MessageUtils.PRIMARY, Style.style(TextDecoration.BOLD)))
                    .append(Component.text(" - ", MessageUtils.ACCENT))
                    .append(Component.text(forecast.getForecastId(), MessageUtils.SECONDARY))
                    .append(Component.text(" ===", MessageUtils.ACCENT))
                    .build();
        }

        MessageUtils.send(player, header);

        // Show enhanced forecast periods with weather symbols and colors
        showForecastPeriod(player, "Morning (6AM-12PM)", forecast.getMorningWeather());
        showForecastPeriod(player, "Afternoon (12PM-6PM)", forecast.getAfternoonWeather());
        showForecastPeriod(player, "Evening (6PM-12AM)", forecast.getEveningWeather());
        showForecastPeriod(player, "Night (12AM-6AM)", forecast.getNightWeather());

        // Show current status with enhanced formatting
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        int currentHour = getCurrentHour(player, weatherForecast);
        
        MessageUtils.send(player, MessageUtils.infoLine("Current Time", 
            String.format("%02d:00", currentHour), MessageUtils.ACCENT));
        
        Component currentWeatherLine = Component.text()
                .append(MessageUtils.text("Current Weather: ", MessageUtils.INFO))
                .append(MessageUtils.weatherSymbol(currentWeather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(currentWeather.getDisplayName(), 
                    MessageUtils.getWeatherColor(currentWeather.getDisplayName())))
                .build();
        MessageUtils.send(player, currentWeatherLine);
        
        // Enhanced weather progression status
        if (plugin.getWeatherProgressionManager() != null) {
            WeatherProgressionManager.WeatherProgression progression =
                    plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
            if (progression != WeatherProgressionManager.WeatherProgression.CLEAR) {
                String progressionName = progression.name().toLowerCase().replace("_", " ");
                MessageUtils.send(player, MessageUtils.infoLine("Weather Stage", progressionName, MessageUtils.ACCENT));
            }
            
            // Show transition status with enhanced formatting
            if (plugin.getWeatherProgressionManager().isInTransition(player.getWorld())) {
                Component transitionMsg = Component.text()
                        .append(Component.text("Status: ", MessageUtils.INFO))
                        .append(Component.text("⚡ ", MessageUtils.WARNING))
                        .append(MessageUtils.text("Weather is transitioning", MessageUtils.WARNING))
                        .build();
                MessageUtils.send(player, transitionMsg);
            }

            // Show special effects with enhanced formatting
            if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
                Component hailMsg = Component.text()
                        .append(Component.text("❄ ", NamedTextColor.WHITE))
                        .append(MessageUtils.text("Hail is currently falling!", NamedTextColor.WHITE, 
                            Style.style(TextDecoration.BOLD)))
                        .build();
                MessageUtils.send(player, hailMsg);
            }
        }
        
        // Show next transition with enhanced display
        WeatherForecast.WeatherType nextWeather = getNextTransition(forecast, currentHour);
        if (nextWeather != null && !nextWeather.equals(currentWeather)) {
            int nextTransitionHour = getNextTransitionHour(forecast, currentHour);
            int hoursUntil = nextTransitionHour > currentHour ? 
                nextTransitionHour - currentHour : 
                (24 - currentHour) + nextTransitionHour;
            
            String timeDesc = hoursUntil == 0 ? "this hour" : 
                             hoursUntil == 1 ? "next hour" : 
                             "in " + hoursUntil + " hours";
            
            Component nextChangeMsg = Component.text()
                    .append(MessageUtils.text("Next change: ", MessageUtils.MUTED))
                    .append(MessageUtils.weatherSymbol(nextWeather.getDisplayName()))
                    .append(Component.text(" "))
                    .append(MessageUtils.text(nextWeather.getDisplayName(), 
                        MessageUtils.getWeatherColor(nextWeather.getDisplayName())))
                    .append(MessageUtils.text(" " + timeDesc + " (", MessageUtils.MUTED))
                    .append(MessageUtils.text(String.format("%02d:00", nextTransitionHour), MessageUtils.ACCENT))
                    .append(MessageUtils.text(")", MessageUtils.MUTED))
                    .build();
            MessageUtils.send(player, nextChangeMsg);
                
            // Enhanced progression system warnings
            if (plugin.getWeatherProgressionManager() != null && hoursUntil <= 3) {
                boolean isStormWeather = nextWeather == WeatherForecast.WeatherType.THUNDERSTORM ||
                                       nextWeather == WeatherForecast.WeatherType.BLIZZARD ||
                                       nextWeather == WeatherForecast.WeatherType.SANDSTORM;
                
                if (isStormWeather && plugin.getConfig().getBoolean("weather_progression.pre_storm_effects.enabled", true)) {
                    Component stormWarning = Component.text()
                            .append(Component.text("  ", NamedTextColor.WHITE))
                            .append(Component.text("⚡ ", MessageUtils.WARNING))
                            .append(MessageUtils.text("Storm warnings will begin beforehand", MessageUtils.MUTED))
                            .build();
                    MessageUtils.send(player, stormWarning);
                }
            }
        }
        
        // Show clickable detailed option
        Component detailedOption = Component.text()
                .append(MessageUtils.text("Use ", MessageUtils.MUTED))
                .append(MessageUtils.clickableCommand("detailed forecast", "/climate forecast detailed", MessageUtils.ACCENT))
                .append(MessageUtils.text(" for hour-by-hour forecast", MessageUtils.MUTED))
                .build();
        MessageUtils.send(player, detailedOption);

        return true;
    }
    
    private void showForecastPeriod(Player player, String period, WeatherForecast.WeatherType weather) {
        Component periodLine = Component.text()
                .append(MessageUtils.text(period + ": ", MessageUtils.ACCENT))
                .append(MessageUtils.weatherSymbol(weather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(weather.getDisplayName(), 
                    MessageUtils.getWeatherColor(weather.getDisplayName())))
                .build();
        MessageUtils.send(player, periodLine);
    }
    
    private boolean showDetailedForecast(Player player, WeatherForecast weatherForecast) {
        WeatherForecast.DetailedForecast forecast = weatherForecast.getForecast(player.getWorld());

        if (forecast == null) {
            MessageUtils.send(player, MessageUtils.error("No detailed forecast available for this world yet!"));
            return true;
        }
        
        // Enhanced header
        Component header;
        if (weatherForecast.isRealisticSeasonsEnabled() && forecast.getDate() != null) {
            Date date = forecast.getDate();
            String dateStr = date.getMonth() + "/" + date.getDay() + "/" + date.getYear();
            header = Component.text()
                    .append(Component.text("=== ", MessageUtils.ACCENT))
                    .append(Component.text("24-Hour Detailed Forecast", MessageUtils.PRIMARY, Style.style(TextDecoration.BOLD)))
                    .append(Component.text(" - ", MessageUtils.ACCENT))
                    .append(Component.text(dateStr, MessageUtils.SECONDARY))
                    .append(Component.text(" ===", MessageUtils.ACCENT))
                    .build();
        } else {
            header = Component.text()
                    .append(Component.text("=== ", MessageUtils.ACCENT))
                    .append(Component.text("24-Hour Detailed Forecast", MessageUtils.PRIMARY, Style.style(TextDecoration.BOLD)))
                    .append(Component.text(" - ", MessageUtils.ACCENT))
                    .append(Component.text(forecast.getForecastId(), MessageUtils.SECONDARY))
                    .append(Component.text(" ===", MessageUtils.ACCENT))
                    .build();
        }
        
        MessageUtils.send(player, header);
        
        int currentHour = getCurrentHour(player, weatherForecast);
        
        // Show enhanced hour-by-hour forecast
        MessageUtils.send(player, MessageUtils.text("Hour-by-Hour Forecast:", MessageUtils.ACCENT, 
            Style.style(TextDecoration.BOLD)));
        
        // Create forecast grid with enhanced formatting
        for (int startHour = 0; startHour < 24; startHour += 6) {
            Component.Builder lineBuilder = Component.text();
            
            for (int hour = startHour; hour < Math.min(startHour + 6, 24); hour++) {
                WeatherForecast.WeatherType weather = forecast.getWeatherForHour(hour);
                boolean isTransition = forecast.isTransitionHour(hour);
                boolean isCurrent = (hour == currentHour);
                
                // Enhanced formatting with icons and colors
                Component hourComponent;
                if (isCurrent) {
                    hourComponent = Component.text()
                            .append(Component.text("►", MessageUtils.SUCCESS))
                            .append(Component.text(String.format("%02d", hour), MessageUtils.SUCCESS, Style.style(TextDecoration.BOLD)))
                            .append(Component.text(":", MessageUtils.SUCCESS))
                            .append(MessageUtils.weatherSymbol(weather.getDisplayName()))
                            .build();
                } else if (isTransition) {
                    hourComponent = Component.text()
                            .append(Component.text("•", MessageUtils.WARNING))
                            .append(Component.text(String.format("%02d", hour), MessageUtils.WARNING))
                            .append(Component.text(":", MessageUtils.WARNING))
                            .append(MessageUtils.weatherSymbol(weather.getDisplayName()))
                            .build();
                } else {
                    hourComponent = Component.text()
                            .append(Component.text(" ", NamedTextColor.WHITE))
                            .append(Component.text(String.format("%02d", hour), NamedTextColor.WHITE))
                            .append(Component.text(":", NamedTextColor.WHITE))
                            .append(MessageUtils.weatherSymbol(weather.getDisplayName()))
                            .build();
                }
                
                lineBuilder.append(hourComponent).append(Component.text(" "));
            }
            
            MessageUtils.send(player, lineBuilder.build());
        }
        
        // Enhanced legend with better formatting
        Component legend = Component.text()
                .append(MessageUtils.text("Legend: ", MessageUtils.MUTED))
                .append(Component.text("►", MessageUtils.SUCCESS))
                .append(MessageUtils.text(" Current", MessageUtils.SUCCESS))
                .append(MessageUtils.text(" | ", MessageUtils.MUTED))
                .append(Component.text("•", MessageUtils.WARNING))
                .append(MessageUtils.text(" Transition", MessageUtils.WARNING))
                .append(MessageUtils.text(" | ", MessageUtils.MUTED))
                .append(MessageUtils.text("  Regular", NamedTextColor.WHITE))
                .build();
        MessageUtils.send(player, legend);
        
        // Weather symbols legend with clickable hover info
        Component symbolsLegend = Component.text()
                .append(MessageUtils.text("Symbols: ", MessageUtils.MUTED))
                .append(MessageUtils.hoverable("☀ Clear", "Clear skies", MessageUtils.WEATHER_CLEAR))
                .append(MessageUtils.text(" | ", MessageUtils.MUTED))
                .append(MessageUtils.hoverable("🌧 Rain", "Light/Heavy Rain", MessageUtils.WEATHER_RAIN))
                .append(MessageUtils.text(" | ", MessageUtils.MUTED))
                .append(MessageUtils.hoverable("⛈ Storm", "Thunderstorm", MessageUtils.WEATHER_STORM))
                .append(MessageUtils.text(" | ", MessageUtils.MUTED))
                .append(MessageUtils.hoverable("❄ Snow", "Snow/Blizzard", MessageUtils.WEATHER_SNOW))
                .append(MessageUtils.text(" | ", MessageUtils.MUTED))
                .append(MessageUtils.hoverable("🌵 Sand", "Sandstorm", MessageUtils.WEATHER_SAND))
                .build();
        MessageUtils.send(player, symbolsLegend);
        
        // Current status with enhanced formatting
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        Component currentStatus = Component.text()
                .append(MessageUtils.text("Currently: ", MessageUtils.INFO))
                .append(MessageUtils.weatherSymbol(currentWeather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(currentWeather.getDisplayName(), 
                    MessageUtils.getWeatherColor(currentWeather.getDisplayName())))
                .append(MessageUtils.text(" at ", MessageUtils.MUTED))
                .append(MessageUtils.text(String.format("%02d:00", currentHour), MessageUtils.ACCENT))
                .build();
        MessageUtils.send(player, currentStatus);
        
        // Enhanced progression status for detailed forecast
        if (plugin.getWeatherProgressionManager() != null) {
            WeatherProgressionManager.WeatherProgression progression = 
                plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
                
            String progressionName = progression.name().toLowerCase().replace("_", " ");
            MessageUtils.send(player, MessageUtils.infoLine("Progression Stage", progressionName, MessageUtils.ACCENT));
            
            if (plugin.getWeatherProgressionManager().isInTransition(player.getWorld())) {
                Component transitionStatus = Component.text()
                        .append(MessageUtils.text("Status: ", MessageUtils.INFO))
                        .append(Component.text("⚡ ", MessageUtils.WARNING))
                        .append(MessageUtils.text("Weather transition in progress", MessageUtils.WARNING))
                        .build();
                MessageUtils.send(player, transitionStatus);
            }
            
            // Show upcoming progression events with enhanced formatting
            for (int lookAhead = 1; lookAhead <= 3; lookAhead++) {
                int futureHour = (currentHour + lookAhead) % 24;
                
                if (forecast.isTransitionHour(futureHour)) {
                    WeatherForecast.WeatherType upcomingWeather = forecast.getWeatherForHour(futureHour);
                    boolean isStormWeather = upcomingWeather == WeatherForecast.WeatherType.THUNDERSTORM ||
                                           upcomingWeather == WeatherForecast.WeatherType.BLIZZARD ||
                                           upcomingWeather == WeatherForecast.WeatherType.SANDSTORM;
                    
                    if (isStormWeather) {
                        String timeDesc = lookAhead == 1 ? "next hour" : "in " + lookAhead + " hours";
                        Component stormWarning = Component.text()
                                .append(MessageUtils.text("Storm warnings will begin before ", MessageUtils.MUTED))
                                .append(MessageUtils.weatherSymbol(upcomingWeather.getDisplayName()))
                                .append(Component.text(" "))
                                .append(MessageUtils.text(upcomingWeather.getDisplayName(), 
                                    MessageUtils.getWeatherColor(upcomingWeather.getDisplayName())))
                                .append(MessageUtils.text(" arrives " + timeDesc, MessageUtils.MUTED))
                                .build();
                        MessageUtils.send(player, stormWarning);
                        break;
                    }
                }
            }
        }
        
        return true;
    }
    
    private WeatherForecast.WeatherType getNextTransition(WeatherForecast.DetailedForecast forecast, int currentHour) {
        for (int hour = currentHour + 1; hour < 24; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return forecast.getWeatherForHour(hour);
            }
        }
        // Check next day (hour 0-5)
        for (int hour = 0; hour <= currentHour; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return forecast.getWeatherForHour(hour);
            }
        }
        return null;
    }
    
    private int getNextTransitionHour(WeatherForecast.DetailedForecast forecast, int currentHour) {
        for (int hour = currentHour + 1; hour < 24; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return hour;
            }
        }
        // Check next day
        for (int hour = 0; hour <= currentHour; hour++) {
            if (forecast.isTransitionHour(hour)) {
                return hour;
            }
        }
        return -1;
    }
    
    private int getCurrentHour(Player player, WeatherForecast weatherForecast) {
        if (weatherForecast.isRealisticSeasonsEnabled()) {
            return plugin.getWeatherForecast().getCurrentHour(player.getWorld());
        } else {
            long timeOfDay = player.getWorld().getTime() % 24000;
            return (int) ((timeOfDay + 6000) / 1000) % 24;
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("detailed");
        }
        return super.getTabCompletions(sender, args);
    }

    @Override
    public String getDescription() {
        return "Show the weather forecast (use 'detailed' for hour-by-hour)";
    }

    @Override
    public String getUsage() {
        return "/climate forecast [detailed]";
    }
}