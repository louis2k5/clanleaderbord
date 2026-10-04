package org.bcp.clansystem.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

public enum Role {
    LEADER("Leader", NamedTextColor.GOLD, 4),
    CO_LEADER("Co-Leader", NamedTextColor.RED, 3),
    MODERATOR("Moderator", NamedTextColor.GREEN, 2),
    MEMBER("Member", NamedTextColor.GRAY, 1);

    private final String displayName;
    private final TextColor color;
    private final int level;

    Role(String displayName, TextColor color, int level) {
        this.displayName = displayName;
        this.color = color;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TextColor getColor() {
        return color;
    }

    public int getLevel() {
        return level;
    }

    public boolean atLeast(Role other) {
        return level >= other.level;
    }

    public static Role ofLevel(int level) {
        for (Role r : values()) {
            if (r.level == level) {
                return r;
            }
        }
        return null;
    }

    public static Role parse(String value, Role fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
