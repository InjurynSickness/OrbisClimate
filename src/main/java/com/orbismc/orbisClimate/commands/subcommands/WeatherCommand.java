package com.orbismc.orbisClimate.commands.subcommands;

import com.orbismc.orbisClimate.OrbisClimate;
import com.orbismc.orbisClimate.WeatherForecast;
import com.orbismc.orbisClimate.WeatherProgressionManager;
import com.orbismc.orbisClimate.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class WeatherCommand extends BaseSubCommand {

    public WeatherCommand(OrbisClimate plugin) {
        super(plugin, "orbisclimate.weather", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        if (args.length == 0) {
            showWeatherHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                return handleSetWeather(player, args);
            case "clear":
                return handleClearWeather(player);
            case "info":
                return handleWeatherInfo(player);
            case "progression":
                return handleProgressionInfo(player);
            default:
                MessageUtils.send(player, MessageUtils.error("Unknown weather command! Use '/climate weather' for help."));
                return true;
        }
    }

    private void showWeatherHelp(Player player) {
        MessageUtils.send(player, MessageUtils.header("Weather Control Commands"));
        
        // Command list with clickable commands and descriptions
        Component setCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate weather set <type> [duration]", 
                    "/climate weather set ", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Set weather manually", NamedTextColor.WHITE))
                .build();
        MessageUtils.send(player, setCmd);
        
        Component clearCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate weather clear", 
                    "/climate weather clear", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Clear weather locks", NamedTextColor.WHITE))
                .build();
        MessageUtils.send(player, clearCmd);
        
        Component infoCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate weather info", 
                    "/climate weather info", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Show detailed weather info", NamedTextColor.WHITE))
                .build();
        MessageUtils.send(player, infoCmd);
        
        Component progressionCmd = Component.text()
                .append(MessageUtils.clickableCommand("/climate weather progression", 
                    "/climate weather progression", MessageUtils.ACCENT))
                .append(MessageUtils.text(" - Show progression info", NamedTextColor.WHITE))
                .build();
        MessageUtils.send(player, progressionCmd);
        
        // Weather types with visual symbols
        MessageUtils.send(player, Component.text(""));
        MessageUtils.send(player, MessageUtils.text("Available Weather Types:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        showWeatherType(player, "clear", "☀", MessageUtils.WEATHER_CLEAR);
        showWeatherType(player, "light_rain", "🌦", MessageUtils.WEATHER_RAIN);
        showWeatherType(player, "heavy_rain", "🌧", MessageUtils.WEATHER_RAIN);
        showWeatherType(player, "thunderstorm", "⛈", MessageUtils.WEATHER_STORM);
        showWeatherType(player, "snow", "❄", MessageUtils.WEATHER_SNOW);
        showWeatherType(player, "blizzard", "🌪", MessageUtils.WEATHER_BLIZZARD);
        showWeatherType(player, "sandstorm", "🌵", MessageUtils.WEATHER_SAND);
    }
    
    private void showWeatherType(Player player, String type, String symbol, net.kyori.adventure.text.format.TextColor color) {
        Component weatherType = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text(symbol + " ", color))
                .append(MessageUtils.clickableCommand(type, "/climate weather set " + type + " 10", color))
                .build();
        MessageUtils.send(player, weatherType);
    }

    private boolean handleSetWeather(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.send(player, MessageUtils.error("Usage: /climate weather set <type> [duration_minutes]"));
            return true;
        }

        String weatherTypeName = args[1].toLowerCase();
        WeatherForecast.WeatherType weatherType = parseWeatherType(weatherTypeName);

        if (weatherType == null) {
            MessageUtils.send(player, MessageUtils.error("Invalid weather type!"));
            
            // Show available types with quick-click options
            Component suggestion = Component.text()
                    .append(MessageUtils.text("Valid types: ", MessageUtils.MUTED))
                    .append(MessageUtils.clickableCommand("clear", "/climate weather set clear 10", MessageUtils.WEATHER_CLEAR))
                    .append(MessageUtils.text(", ", MessageUtils.MUTED))
                    .append(MessageUtils.clickableCommand("rain", "/climate weather set heavy_rain 10", MessageUtils.WEATHER_RAIN))
                    .append(MessageUtils.text(", ", MessageUtils.MUTED))
                    .append(MessageUtils.clickableCommand("storm", "/climate weather set thunderstorm 10", MessageUtils.WEATHER_STORM))
                    .append(MessageUtils.text(", ", MessageUtils.MUTED))
                    .append(MessageUtils.clickableCommand("snow", "/climate weather set snow 10", MessageUtils.WEATHER_SNOW))
                    .build();
            MessageUtils.send(player, suggestion);
            return true;
        }

        int duration = 10; // Default 10 minutes
        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
                if (duration < 1 || duration > 120) {
                    MessageUtils.send(player, MessageUtils.error("Duration must be between 1 and 120 minutes!"));
                    return true;
                }
            } catch (NumberFormatException e) {
                MessageUtils.send(player, MessageUtils.error("Invalid duration! Must be a number."));
                return true;
            }
        }

        plugin.getWeatherForecast().setWeather(player.getWorld(), weatherType, duration);
        
        // Enhanced success message with visual confirmation
        Component successMsg = Component.text()
                .append(Component.text("✓ ", MessageUtils.SUCCESS))
                .append(MessageUtils.text("Weather set to ", MessageUtils.SUCCESS))
                .append(MessageUtils.weatherSymbol(weatherType.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(weatherType.getDisplayName(), 
                    MessageUtils.getWeatherColor(weatherType.getDisplayName()), 
                    Style.style(TextDecoration.BOLD)))
                .append(MessageUtils.text(" for ", MessageUtils.SUCCESS))
                .append(MessageUtils.text(duration + " minutes", MessageUtils.ACCENT))
                .append(MessageUtils.text(" in ", MessageUtils.SUCCESS))
                .append(MessageUtils.text(player.getWorld().getName(), MessageUtils.PRIMARY))
                .build();
        MessageUtils.send(player, successMsg);

        return true;
    }

    private boolean handleClearWeather(Player player) {
        plugin.getWeatherForecast().clearWeatherLock(player.getWorld());
        
        Component successMsg = Component.text()
                .append(Component.text("✓ ", MessageUtils.SUCCESS))
                .append(MessageUtils.text("Weather lock cleared for ", MessageUtils.SUCCESS))
                .append(MessageUtils.text(player.getWorld().getName(), MessageUtils.PRIMARY))
                .append(Component.newline())
                .append(MessageUtils.text("Weather will now follow the natural forecast.", MessageUtils.MUTED))
                .build();
        MessageUtils.send(player, successMsg);
        
        return true;
    }

    private boolean handleWeatherInfo(Player player) {
        MessageUtils.send(player, MessageUtils.header("Detailed Weather Information"));

        WeatherForecast weatherForecast = plugin.getWeatherForecast();

        // Current weather state with enhanced display
        WeatherForecast.WeatherType currentWeather = weatherForecast.getCurrentWeather(player.getWorld());
        Component currentWeatherLine = Component.text()
                .append(MessageUtils.text("Current Weather: ", MessageUtils.INFO))
                .append(MessageUtils.weatherSymbol(currentWeather.getDisplayName()))
                .append(Component.text(" "))
                .append(MessageUtils.text(currentWeather.getDisplayName(), 
                    MessageUtils.getWeatherColor(currentWeather.getDisplayName()), 
                    Style.style(TextDecoration.BOLD)))
                .build();
        MessageUtils.send(player, currentWeatherLine);

        // Minecraft weather state with technical details
        Component mcStormLine = Component.text()
                .append(MessageUtils.text("MC Storm: ", MessageUtils.INFO))
                .append(MessageUtils.text(player.getWorld().hasStorm() ? "Active" : "Inactive", 
                    player.getWorld().hasStorm() ? MessageUtils.SUCCESS : MessageUtils.MUTED))
                .build();
        MessageUtils.send(player, mcStormLine);
        
        Component mcThunderLine = Component.text()
                .append(MessageUtils.text("MC Thunder: ", MessageUtils.INFO))
                .append(MessageUtils.text(player.getWorld().isThundering() ? "Active" : "Inactive", 
                    player.getWorld().isThundering() ? MessageUtils.WARNING : MessageUtils.MUTED))
                .build();
        MessageUtils.send(player, mcThunderLine);
        
        int weatherDuration = player.getWorld().getWeatherDuration() / 20;
        Component durationLine = Component.text()
                .append(MessageUtils.text("Weather Duration: ", MessageUtils.INFO))
                .append(MessageUtils.text(weatherDuration + " seconds", 
                    weatherDuration > 1000000 ? MessageUtils.ACCENT : NamedTextColor.WHITE))
                .build();
        MessageUtils.send(player, durationLine);

        if (player.getWorld().isThundering()) {
            int thunderDuration = player.getWorld().getThunderDuration() / 20;
            Component thunderDurationLine = Component.text()
                    .append(MessageUtils.text("Thunder Duration: ", MessageUtils.INFO))
                    .append(MessageUtils.text(thunderDuration + " seconds", NamedTextColor.WHITE))
                    .build();
            MessageUtils.send(player, thunderDurationLine);
        }

        // Time information with enhanced formatting
        long time = player.getWorld().getTime();
        int hour = (int) (((time + 6000) % 24000) / 1000);
        Component timeIcon = getTimeIcon(hour);
        
        Component timeLine = Component.text()
                .append(MessageUtils.text("Current Hour: ", MessageUtils.INFO))
                .append(timeIcon)
                .append(Component.text(" "))
                .append(MessageUtils.text(hour + ":00", MessageUtils.ACCENT))
                .build();
        MessageUtils.send(player, timeLine);
        
        // Enhanced forecast transition status
        if (weatherForecast.isTransitionHour(player.getWorld())) {
            Component transitionMsg = Component.text()
                    .append(Component.text("⚡ ", MessageUtils.WARNING))
                    .append(MessageUtils.text("Current hour is a forecast transition hour", MessageUtils.WARNING, 
                        Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(player, transitionMsg);
        }

        // Show active weather systems with enhanced icons and status
        MessageUtils.send(player, Component.text(""));
        MessageUtils.send(player, MessageUtils.text("Active Weather Systems:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        showWeatherSystem(player, "Blizzard", plugin.getBlizzardManager().isBlizzardActive(player.getWorld()), "❄");
        showWeatherSystem(player, "Sandstorm", plugin.getSandstormManager().isSandstormActive(player.getWorld()), "🌪");
        showWeatherSystem(player, "Wind", plugin.getWindManager().hasActiveWind(player.getWorld()), "💨");

        // Enhanced weather progression
        if (plugin.getWeatherProgressionManager() != null) {
            MessageUtils.send(player, Component.text(""));
            showProgressionStatus(player);
        }

        return true;
    }
    
    private void showWeatherSystem(Player player, String name, boolean active, String icon) {
        Component statusLine = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text(icon + " ", active ? MessageUtils.SUCCESS : MessageUtils.MUTED))
                .append(MessageUtils.text(name + ": ", MessageUtils.INFO))
                .append(MessageUtils.text(active ? "Active" : "Inactive", 
                    active ? MessageUtils.SUCCESS : MessageUtils.MUTED))
                .build();
        MessageUtils.send(player, statusLine);
    }
    
    private void showProgressionStatus(Player player) {
        WeatherProgressionManager.WeatherProgression progression =
            plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
        String progressionName = progression.name().toLowerCase().replace("_", " ");
        
        MessageUtils.send(player, MessageUtils.infoLine("Weather Progression", progressionName, MessageUtils.ACCENT));

        if (plugin.getWeatherProgressionManager().isInTransition(player.getWorld())) {
            Component transitionMsg = Component.text()
                    .append(Component.text("⚡ ", MessageUtils.WARNING))
                    .append(MessageUtils.text("Progression transition active", MessageUtils.WARNING))
                    .build();
            MessageUtils.send(player, transitionMsg);
        }

        if (plugin.getWeatherProgressionManager().isHailActive(player.getWorld())) {
            Component hailMsg = Component.text()
                    .append(Component.text("❄ ", NamedTextColor.WHITE))
                    .append(MessageUtils.text("Hail is currently active", NamedTextColor.WHITE, 
                        Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(player, hailMsg);
        }
        
        // Show upcoming transitions
        WeatherForecast weatherForecast = plugin.getWeatherForecast();
        int hoursUntil = weatherForecast.getHoursUntilNextTransition(player.getWorld());
        if (hoursUntil != -1 && hoursUntil <= 3) {
            WeatherForecast.WeatherType nextWeather = weatherForecast.getNextTransitionWeather(player.getWorld());
            if (nextWeather != null) {
                String timeDesc = hoursUntil == 0 ? "this hour" : "in " + hoursUntil + " hour(s)";
                Component nextTransitionLine = Component.text()
                        .append(MessageUtils.text("Next transition: ", MessageUtils.MUTED))
                        .append(MessageUtils.weatherSymbol(nextWeather.getDisplayName()))
                        .append(Component.text(" "))
                        .append(MessageUtils.text(nextWeather.getDisplayName(), 
                            MessageUtils.getWeatherColor(nextWeather.getDisplayName())))
                        .append(MessageUtils.text(" " + timeDesc, MessageUtils.MUTED))
                        .build();
                MessageUtils.send(player, nextTransitionLine);
            }
        }
    }

    private boolean handleProgressionInfo(Player player) {
        if (plugin.getWeatherProgressionManager() == null) {
            MessageUtils.send(player, MessageUtils.error("Weather progression system not available!"));
            return true;
        }
        
        MessageUtils.send(player, MessageUtils.header("Weather Progression Information"));
        
        WeatherProgressionManager.WeatherProgression progression = 
            plugin.getWeatherProgressionManager().getWorldProgression(player.getWorld());
        String progressionName = progression.name().toLowerCase().replace("_", " ");
        
        Component stageDisplay = Component.text()
                .append(MessageUtils.text("Current Stage: ", MessageUtils.INFO))
                .append(getProgressionIcon(progression))
                .append(Component.text(" "))
                .append(MessageUtils.text(progressionName, MessageUtils.ACCENT, Style.style(TextDecoration.BOLD)))
                .build();
        MessageUtils.send(player, stageDisplay);
        
        boolean inTransition = plugin.getWeatherProgressionManager().isInTransition(player.getWorld());
        if (inTransition) {
            Component transitionStatus = Component.text()
                    .append(MessageUtils.text("Status: ", MessageUtils.INFO))
                    .append(Component.text("⚡ ", MessageUtils.WARNING))
                    .append(MessageUtils.text("Weather is transitioning", MessageUtils.WARNING))
                    .build();
            MessageUtils.send(player, transitionStatus);
        }
        
        boolean hailActive = plugin.getWeatherProgressionManager().isHailActive(player.getWorld());
        if (hailActive) {
            Component hailStatus = Component.text()
                    .append(Component.text("❄ ", NamedTextColor.WHITE))
                    .append(MessageUtils.text("Hail is currently active", NamedTextColor.WHITE, 
                        Style.style(TextDecoration.BOLD)))
                    .build();
            MessageUtils.send(player, hailStatus);
        }
        
        // Show forecast integration status
        WeatherForecast.DetailedForecast forecast = plugin.getWeatherForecast().getForecast(player.getWorld());
        if (forecast != null) {
            MessageUtils.send(player, Component.text(""));
            MessageUtils.send(player, MessageUtils.text("Forecast Integration:", MessageUtils.INFO, 
                Style.style(TextDecoration.BOLD)));
            
            int currentHour = plugin.getWeatherForecast().getCurrentHour(player.getWorld());
            boolean isTransitionHour = forecast.isTransitionHour(currentHour);
            
            MessageUtils.send(player, MessageUtils.infoLine("Current hour is transition", 
                isTransitionHour ? "Yes" : "No", 
                isTransitionHour ? MessageUtils.WARNING : MessageUtils.SUCCESS));
            
            // Show next forecast transition
            int hoursUntil = plugin.getWeatherForecast().getHoursUntilNextTransition(player.getWorld());
            if (hoursUntil != -1) {
                WeatherForecast.WeatherType nextWeather = plugin.getWeatherForecast().getNextTransitionWeather(player.getWorld());
                
                String timeDesc = hoursUntil == 0 ? "this hour" : 
                                 hoursUntil == 1 ? "next hour" : 
                                 "in " + hoursUntil + " hours";
                
                Component nextTransitionLine = Component.text()
                        .append(MessageUtils.text("Next forecast transition: ", MessageUtils.INFO))
                        .append(MessageUtils.weatherSymbol(nextWeather.getDisplayName()))
                        .append(Component.text(" "))
                        .append(MessageUtils.text(nextWeather.getDisplayName(), 
                            MessageUtils.getWeatherColor(nextWeather.getDisplayName())))
                        .append(MessageUtils.text(" " + timeDesc, MessageUtils.MUTED))
                        .build();
                MessageUtils.send(player, nextTransitionLine);
            }
        }
        
        // Show progression configuration status
        MessageUtils.send(player, Component.text(""));
        MessageUtils.send(player, MessageUtils.text("Configuration:", MessageUtils.INFO, 
            Style.style(TextDecoration.BOLD)));
        
        showConfigOption(player, "Enhanced Transitions", 
            plugin.getConfig().getBoolean("weather_progression.enhanced_transitions.enabled", true));
        showConfigOption(player, "Pre-storm Effects", 
            plugin.getConfig().getBoolean("weather_progression.pre_storm_effects.enabled", true));
        showConfigOption(player, "Hail Effects", 
            plugin.getConfig().getBoolean("weather_progression.active_weather_effects.hail.enabled", true));
        showConfigOption(player, "Forecast Integration", 
            plugin.getConfig().getBoolean("weather_progression.forecast_integration.use_forecast_transitions", true));
        
        return true;
    }
    
    private void showConfigOption(Player player, String name, boolean enabled) {
        Component configLine = Component.text()
                .append(Component.text("• ", MessageUtils.MUTED))
                .append(Component.text(enabled ? "✓ " : "✗ ", enabled ? MessageUtils.SUCCESS : MessageUtils.ERROR))
                .append(MessageUtils.text(name + ": ", MessageUtils.INFO))
                .append(MessageUtils.text(enabled ? "Enabled" : "Disabled", 
                    enabled ? MessageUtils.SUCCESS : MessageUtils.ERROR))
                .build();
        MessageUtils.send(player, configLine);
    }
    
    private Component getProgressionIcon(WeatherProgressionManager.WeatherProgression progression) {
        switch (progression) {
            case CLEAR:
                return Component.text("☀", MessageUtils.WEATHER_CLEAR);
            case PRE_STORM:
                return Component.text("⚠", MessageUtils.WARNING);
            case ACTIVE_WEATHER:
                return Component.text("🌊", MessageUtils.PRIMARY);
            case POST_STORM:
                return Component.text("🌤", MessageUtils.SUCCESS);
            case TRANSITION:
                return Component.text("⚡", MessageUtils.WARNING);
            default:
                return Component.text("❓", MessageUtils.MUTED);
        }
    }
    
    private Component getTimeIcon(int hour) {
        if (hour >= 6 && hour < 12) return Component.text("🌅", MessageUtils.ACCENT); // Morning
        if (hour >= 12 && hour < 18) return Component.text("☀️", MessageUtils.WEATHER_CLEAR); // Afternoon
        if (hour >= 18 && hour < 21) return Component.text("🌇", MessageUtils.WARNING); // Evening
        return Component.text("🌙", MessageUtils.PRIMARY); // Night
    }

    private WeatherForecast.WeatherType parseWeatherType(String typeName) {
        switch (typeName.toLowerCase()) {
            case "clear":
                return WeatherForecast.WeatherType.CLEAR;
            case "light_rain":
            case "lightrain":
            case "light":
                return WeatherForecast.WeatherType.LIGHT_RAIN;
            case "heavy_rain":
            case "heavyrain":
            case "heavy":
            case "rain":
                return WeatherForecast.WeatherType.HEAVY_RAIN;
            case "thunderstorm":
            case "thunder":
            case "storm":
                return WeatherForecast.WeatherType.THUNDERSTORM;
            case "snow":
                return WeatherForecast.WeatherType.SNOW;
            case "blizzard":
                return WeatherForecast.WeatherType.BLIZZARD;
            case "sandstorm":
            case "sand":
                return WeatherForecast.WeatherType.SANDSTORM;
            default:
                return null;
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "clear", "info", "progression");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("clear", "light_rain", "heavy_rain", "thunderstorm", "snow", "blizzard", "sandstorm");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("5", "10", "15", "30", "60");
        }
        return super.getTabCompletions(sender, args);
    }

    @Override
    public String getDescription() {
        return "Control and monitor weather systems";
    }

    @Override
    public String getUsage() {
        return "/climate weather <set|clear|info|progression>";
    }
}