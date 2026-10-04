package org.bcp.clansystem.model;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The two animated clan tag perks. They follow the PerkSystem permission scheme "perks.&lt;id&gt;".
 */
public enum RgbMode {
    NONE(null, "Off", "", Material.BARRIER),
    GRADIENT("clan_gradient", "Clan Tag Gradient", "Your clan tag flows in a smooth rainbow gradient.", Material.AMETHYST_SHARD),
    BREATHING("clan_breathing", "Clan Tag Breathing", "Your clan tag softly breathes in shifting RGB colors.", Material.NAUTILUS_SHELL);

    private final String id;
    private final String displayName;
    private final String description;
    private final Material icon;

    RgbMode(String id, String displayName, String description, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    /** Permission node, e.g. perks.clan_gradient. Null for NONE. */
    public String permission() {
        return id == null ? null : "perks." + id;
    }

    /** Short name used in commands and storage ("gradient"). */
    public String shortName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RgbMode parse(String value) {
        if (value == null) {
            return NONE;
        }
        String v = value.toLowerCase(Locale.ROOT);
        for (RgbMode m : values()) {
            if (m != NONE && (m.shortName().equals(v) || m.id.equals(v))) {
                return m;
            }
        }
        return NONE;
    }

    /** Looks up a perk by its id ("clan_gradient") or short name ("gradient"); null if unknown. */
    public static RgbMode fromId(String value) {
        RgbMode m = parse(value);
        return m == NONE ? null : m;
    }
}
