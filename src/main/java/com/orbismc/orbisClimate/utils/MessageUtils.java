package com.orbismc.orbisClimate.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility class for sending Adventure-based messages with consistent formatting
 */
public class MessageUtils {
    
    // Common color scheme for OrbisClimate
    public static final TextColor PRIMARY = TextColor.fromHexString("#4A90E2");     // Blue
    public static final TextColor SECONDARY = TextColor.fromHexString("#7ED321");   // Green
    public static final TextColor ACCENT = TextColor.fromHexString("#F5A623");      // Orange/Gold
    public static final TextColor ERROR = NamedTextColor.RED;
    public static final TextColor WARNING = NamedTextColor.YELLOW;
    public static final TextColor SUCCESS = NamedTextColor.GREEN;
    public static final TextColor MUTED = NamedTextColor.GRAY;
    public static final TextColor INFO = NamedTextColor.AQUA;
    
    // Weather-specific colors
    public static final TextColor WEATHER_CLEAR = TextColor.fromHexString("#FFD700");      // Gold
    public static final TextColor WEATHER_RAIN = TextColor.fromHexString("#4682B4");       // Steel Blue
    public static final TextColor WEATHER_STORM = TextColor.fromHexString("#2F4F4F");      // Dark Slate Gray
    public static final TextColor WEATHER_SNOW = TextColor.fromHexString("#F0F8FF");       // Alice Blue
    public static final TextColor WEATHER_BLIZZARD = TextColor.fromHexString("#B0C4DE");   // Light Steel Blue
    public static final TextColor WEATHER_SAND = TextColor.fromHexString("#DEB887");       // Burlywood
    
    // Temperature colors
    public static final TextColor TEMP_FREEZING = TextColor.fromHexString("#87CEEB");      // Sky Blue
    public static final TextColor TEMP_COLD = TextColor.fromHexString("#4169E1");          // Royal Blue
    public static final TextColor TEMP_COOL = TextColor.fromHexString("#20B2AA");          // Light Sea Green
    public static final TextColor TEMP_COMFORTABLE = NamedTextColor.GREEN;
    public static final TextColor TEMP_WARM = TextColor.fromHexString("#FF8C00");          // Dark Orange
    public static final TextColor TEMP_HOT = TextColor.fromHexString("#FF6347");           // Tomato
    public static final TextColor TEMP_SCORCHING = TextColor.fromHexString("#DC143C");     // Crimson
    
    // Zone colors
    public static final TextColor ZONE_ARCTIC = TextColor.fromHexString("#B0E0E6");        // Powder Blue
    public static final TextColor ZONE_TEMPERATE = TextColor.fromHexString("#90EE90");     // Light Green
    public static final TextColor ZONE_DESERT = TextColor.fromHexString("#F4A460");        // Sandy Brown
    public static final TextColor ZONE_ARID = TextColor.fromHexString("#D2B48C");          // Tan
    
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    
    /**
     * Send a message to a command sender using Adventure components
     */
    public static void send(CommandSender sender, Component message) {
        if (sender instanceof Player) {
            ((Player) sender).sendMessage(message);
        } else {
            // Console - convert to legacy for better compatibility
            sender.sendMessage(legacySerializer.serialize(message));
        }
    }
    
    /**
     * Send multiple messages to a command sender
     */
    public static void send(CommandSender sender, Component... messages) {
        for (Component message : messages) {
            send(sender, message);
        }
    }
    
    /**
     * Create a prefixed OrbisClimate message
     */
    public static Component prefixed(Component message) {
        return Component.text()
                .append(Component.text("[", MUTED))
                .append(Component.text("OrbisClimate", PRIMARY).style(Style.style(TextDecoration.BOLD)))
                .append(Component.text("] ", MUTED))
                .append(message)
                .build();
    }
    
    /**
     * Create a prefixed message from MiniMessage string
     */
    public static Component prefixed(String miniMessageString) {
        return prefixed(miniMessage.deserialize(miniMessageString));
    }
    
    /**
     * Create a header component
     */
    public static Component header(String title) {
        return Component.text()
                .append(Component.text("=== ", ACCENT))
                .append(Component.text(title, PRIMARY).style(Style.style(TextDecoration.BOLD)))
                .append(Component.text(" ===", ACCENT))
                .build();
    }
    
    /**
     * Create a simple text component with color
     */
    public static Component text(String text, TextColor color) {
        return Component.text(text, color);
    }
    
    /**
     * Create a simple text component with color and style - FIXED
     */
    public static Component text(String text, TextColor color, Style style) {
        return Component.text(text, color).style(style);
    }
    
    /**
     * Create an info line (label: value format)
     */
    public static Component infoLine(String label, String value) {
        return Component.text()
                .append(Component.text(label + ": ", INFO))
                .append(Component.text(value, NamedTextColor.WHITE))
                .build();
    }
    
    /**
     * Create an info line with custom value color
     */
    public static Component infoLine(String label, String value, TextColor valueColor) {
        return Component.text()
                .append(Component.text(label + ": ", INFO))
                .append(Component.text(value, valueColor))
                .build();
    }
    
    /**
     * Create an info line with a component value
     */
    public static Component infoLine(String label, Component value) {
        return Component.text()
                .append(Component.text(label + ": ", INFO))
                .append(value)
                .build();
    }
    
    /**
     * Create a success message
     */
    public static Component success(String message) {
        return Component.text("✓ " + message, SUCCESS);
    }
    
    /**
     * Create an error message
     */
    public static Component error(String message) {
        return Component.text("✗ " + message, ERROR);
    }
    
    /**
     * Create a warning message
     */
    public static Component warning(String message) {
        return Component.text("⚠ " + message, WARNING);
    }
    
    /**
     * Get color for weather type
     */
    public static TextColor getWeatherColor(String weatherType) {
        switch (weatherType.toLowerCase()) {
            case "clear":
                return WEATHER_CLEAR;
            case "light_rain":
            case "heavy_rain":
                return WEATHER_RAIN;
            case "thunderstorm":
                return WEATHER_STORM;
            case "snow":
                return WEATHER_SNOW;
            case "blizzard":
                return WEATHER_BLIZZARD;
            case "sandstorm":
                return WEATHER_SAND;
            default:
                return NamedTextColor.WHITE;
        }
    }
    
    /**
     * Get color for temperature level
     */
    public static TextColor getTemperatureColor(String tempLevel) {
        switch (tempLevel.toLowerCase()) {
            case "severe cold":
                return TEMP_FREEZING;
            case "cold":
                return TEMP_COLD;
            case "mild cold":
                return TEMP_COOL;
            case "comfortable":
                return TEMP_COMFORTABLE;
            case "mild heat":
                return TEMP_WARM;
            case "hot":
                return TEMP_HOT;
            case "severe heat":
                return TEMP_SCORCHING;
            default:
                return NamedTextColor.WHITE;
        }
    }
    
    /**
     * Get color for climate zone
     */
    public static TextColor getZoneColor(String zoneName) {
        switch (zoneName.toLowerCase()) {
            case "arctic":
                return ZONE_ARCTIC;
            case "temperate":
                return ZONE_TEMPERATE;
            case "desert":
                return ZONE_DESERT;
            case "arid":
                return ZONE_ARID;
            default:
                return NamedTextColor.WHITE;
        }
    }
    
    /**
     * Create a weather symbol component
     */
    public static Component weatherSymbol(String weatherType) {
        String symbol;
        TextColor color;
        
        switch (weatherType.toLowerCase()) {
            case "clear":
                symbol = "☀";
                color = WEATHER_CLEAR;
                break;
            case "light_rain":
                symbol = "🌦";
                color = WEATHER_RAIN;
                break;
            case "heavy_rain":
                symbol = "🌧";
                color = WEATHER_RAIN;
                break;
            case "thunderstorm":
                symbol = "⛈";
                color = WEATHER_STORM;
                break;
            case "snow":
                symbol = "❄";
                color = WEATHER_SNOW;
                break;
            case "blizzard":
                symbol = "🌪";
                color = WEATHER_BLIZZARD;
                break;
            case "sandstorm":
                symbol = "🌵";
                color = WEATHER_SAND;
                break;
            default:
                symbol = "?";
                color = NamedTextColor.WHITE;
                break;
        }
        
        return Component.text(symbol, color);
    }
    
    /**
     * Create a temperature display component
     */
    public static Component temperatureDisplay(double celsius) {
        double fahrenheit = (celsius * 9/5) + 32;
        TextColor color = getTemperatureColorFromValue(celsius);
        
        return Component.text(String.format("%.1f°C (%.1f°F)", celsius, fahrenheit), color);
    }
    
    /**
     * Get temperature color from actual temperature value
     */
    public static TextColor getTemperatureColorFromValue(double celsius) {
        if (celsius <= -25) return TEMP_FREEZING;
        if (celsius <= -10) return TEMP_COLD;
        if (celsius <= 0) return TEMP_COOL;
        if (celsius <= 25) return TEMP_COMFORTABLE;
        if (celsius <= 35) return TEMP_WARM;
        if (celsius <= 45) return TEMP_HOT;
        return TEMP_SCORCHING;
    }
    
    /**
     * Create a progress bar component
     */
    public static Component progressBar(double progress, int length, TextColor fillColor, TextColor emptyColor) {
        progress = Math.max(0, Math.min(1, progress)); // Clamp between 0 and 1
        int filled = (int) (progress * length);
        
        StringBuilder filledStr = new StringBuilder();
        StringBuilder emptyStr = new StringBuilder();
        
        // Filled portion
        for (int i = 0; i < filled; i++) {
            filledStr.append("█");
        }
        
        // Empty portion
        for (int i = filled; i < length; i++) {
            emptyStr.append("░");
        }
        
        return Component.text()
                .append(Component.text(filledStr.toString(), fillColor))
                .append(Component.text(emptyStr.toString(), emptyColor))
                .build();
    }
    
    /**
     * Create a clickable command component
     */
    public static Component clickableCommand(String text, String command, TextColor color) {
        return Component.text(text, color)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    Component.text("Click to run: " + command, MUTED)
                ));
    }
    
    /**
     * Create a hoverable info component
     */
    public static Component hoverable(String text, Component hoverText, TextColor color) {
        return Component.text(text, color)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverText));
    }
    
    /**
     * Create a hoverable info component with simple hover text
     */
    public static Component hoverable(String text, String hoverText, TextColor color) {
        return hoverable(text, Component.text(hoverText, MUTED), color);
    }
    
    /**
     * Parse a MiniMessage string
     */
    public static Component miniMessage(String text) {
        return miniMessage.deserialize(text);
    }
    
    /**
     * Convert legacy color codes to Component
     */
    public static Component legacy(String legacyText) {
        return legacySerializer.deserialize(legacyText);
    }
    
    /**
     * Get plain text content from a component - FIXED
     */
    public static String getPlainText(Component component) {
        return plainSerializer.serialize(component);
    }
}