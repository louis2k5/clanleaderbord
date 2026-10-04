package org.bcp.clansystem.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Small text helpers. Only ever feed trusted strings (config, validated names) into {@link #c(String)}. */
public final class Text {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Legacy '&' colour codes to a component. */
    public static Component c(String legacy) {
        return AMP.deserialize(legacy);
    }

    /** Same as {@link #c(String)} but with italics disabled (for item names and lore). */
    public static Component item(String legacy) {
        return AMP.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** Component to plain text without any formatting. */
    public static String strip(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Legacy '&' string to plain text without any formatting. */
    public static String stripLegacy(String legacy) {
        return strip(AMP.deserialize(legacy));
    }
}
