package org.bcp.clansystem.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Locale;

/** The 7 selectable clan tag colours. Each one has its own permission: clan.color.&lt;key&gt; */
public enum TagColor {
    RED("red", "Red", NamedTextColor.RED, Material.RED_DYE),
    ORANGE("orange", "Orange", NamedTextColor.GOLD, Material.ORANGE_DYE),
    YELLOW("yellow", "Yellow", NamedTextColor.YELLOW, Material.YELLOW_DYE),
    GREEN("green", "Green", NamedTextColor.GREEN, Material.LIME_DYE),
    CYAN("cyan", "Cyan", NamedTextColor.AQUA, Material.CYAN_DYE),
    BLUE("blue", "Blue", NamedTextColor.BLUE, Material.BLUE_DYE),
    PURPLE("purple", "Purple", NamedTextColor.DARK_PURPLE, Material.PURPLE_DYE);

    private final String key;
    private final String displayName;
    private final TextColor color;
    private final Material icon;

    TagColor(String key, String displayName, TextColor color, Material icon) {
        this.key = key;
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TextColor getColor() {
        return color;
    }

    public Material getIcon() {
        return icon;
    }

    public String permission() {
        return "clan.color." + key;
    }

    public static TagColor byKey(String key) {
        if (key == null) {
            return null;
        }
        String k = key.toLowerCase(Locale.ROOT);
        for (TagColor c : values()) {
            if (c.key.equals(k)) {
                return c;
            }
        }
        return null;
    }
}
