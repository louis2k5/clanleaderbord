package org.bcp.clansystem.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.HSVLike;
import org.bcp.clansystem.ClanSystem;
import org.bcp.clansystem.model.Clan;
import org.bcp.clansystem.model.RgbMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the clan tag component and applies it to the tab list, the name tag (scoreboard team prefix)
 * and optionally the display name. Also drives the two animated RGB perks.
 * <p>
 * The animated tag is only applied to the player who owns, has enabled and is allowed to use the perk,
 * and replaces that player's normal clan tag colour.
 */
public final class DisplayManager {

    private static final String TEAM_PREFIX = "cs_";
    private static final long PERMISSION_RECHECK_MILLIS = 5000L;

    private final ClanSystem plugin;
    /** Players whose animated perk is currently active (enabled + permission + in a clan). */
    private final Map<UUID, RgbMode> activeRgb = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long lastRecheck = 0;

    public DisplayManager(ClanSystem plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stop();
        cleanupLeftoverTeams();
        int interval = Math.max(1, plugin.getConfig().getInt("animation.interval-ticks", 2));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        refreshAll();
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        for (Player p : Bukkit.getOnlinePlayers()) {
            cleanup(p);
        }
    }

    // ------------------------------------------------------------------ tag building

    /** The coloured tag body without brackets. */
    public Component tagBody(Clan clan, RgbMode mode, long now) {
        String tag = clan.getTag();
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        double t = now / 1000.0;

        if (mode == RgbMode.GRADIENT) {
            double speed = cfg.getDouble("animation.gradient.speed", 0.25);
            double spread = cfg.getDouble("animation.gradient.spread", 1.0);
            int len = Math.max(1, tag.length());
            TextComponent.Builder builder = Component.text();
            for (int i = 0; i < tag.length(); i++) {
                double hue = (t * speed + spread * i / len) % 1.0;
                if (hue < 0) {
                    hue += 1.0;
                }
                TextColor color = TextColor.color(HSVLike.hsvLike((float) hue, 1.0f, 1.0f));
                builder.append(Component.text(String.valueOf(tag.charAt(i)), color));
            }
            return builder.build();
        }

        if (mode == RgbMode.BREATHING) {
            double hueSpeed = cfg.getDouble("animation.breathing.hue-speed", 0.05);
            double pulseSeconds = Math.max(0.2, cfg.getDouble("animation.breathing.pulse-seconds", 3.0));
            double min = Math.min(1.0, Math.max(0.0, cfg.getDouble("animation.breathing.min-brightness", 0.35)));
            double hue = (t * hueSpeed) % 1.0;
            double pulse = (Math.sin(2 * Math.PI * t / pulseSeconds - Math.PI / 2) + 1.0) / 2.0;
            double value = min + (1.0 - min) * pulse;
            TextColor color = TextColor.color(HSVLike.hsvLike((float) hue, 1.0f, (float) value));
            return Component.text(tag, color);
        }

        TextColor color = clan.getColor() != null ? clan.getColor().getColor() : NamedTextColor.WHITE;
        return Component.text(tag, color);
    }

    private TextColor bracketColor() {
        String name = plugin.getConfig().getString("tag.bracket-color", "DARK_GRAY");
        NamedTextColor color = name == null ? null : NamedTextColor.NAMES.value(name.toLowerCase(java.util.Locale.ROOT));
        return color == null ? NamedTextColor.DARK_GRAY : color;
    }

    /** "[TAG]" or "[TAG] " with brackets. */
    public Component bracketed(Clan clan, RgbMode mode, long now, boolean trailingSpace) {
        TextColor bc = bracketColor();
        TextComponent.Builder b = Component.text();
        b.append(Component.text("[", bc));
        b.append(tagBody(clan, mode, now));
        b.append(Component.text(trailingSpace ? "] " : "]", bc));
        return b.build();
    }

    /** Static (non animated) bracketed tag for info screens. */
    public Component staticTag(Clan clan) {
        return bracketed(clan, RgbMode.NONE, System.currentTimeMillis(), false);
    }

    /**
     * Tag for a player, including the trailing space, or null if the player has no clan.
     * Safe to call from async threads (used by the chat renderer).
     */
    public Component tagFor(UUID playerId) {
        Clan clan = plugin.clans().getClanOf(playerId);
        if (clan == null) {
            return null;
        }
        RgbMode mode = activeRgb.getOrDefault(playerId, RgbMode.NONE);
        return bracketed(clan, mode, System.currentTimeMillis(), true);
    }

    // ------------------------------------------------------------------ refreshing

    /** Re-evaluates the perk state of a player and applies the tag everywhere. */
    public void refresh(Player player) {
        updateActive(player);
        apply(player);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    public void refreshClan(Clan clan) {
        for (UUID id : clan.members().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                refresh(p);
            }
        }
    }

    private void updateActive(Player player) {
        UUID id = player.getUniqueId();
        RgbMode setting = plugin.clans().getRgb(id);
        if (setting != RgbMode.NONE
                && plugin.clans().getClanOf(id) != null
                && player.hasPermission(setting.permission())) {
            activeRgb.put(id, setting);
        } else {
            activeRgb.remove(id);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean recheck = now - lastRecheck >= PERMISSION_RECHECK_MILLIS;
        if (recheck) {
            lastRecheck = now;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean wanted = plugin.clans().getRgb(id) != RgbMode.NONE;
            boolean active = activeRgb.containsKey(id);
            if (!wanted && !active) {
                continue;
            }
            if (recheck) {
                RgbMode before = activeRgb.getOrDefault(id, RgbMode.NONE);
                updateActive(p);
                RgbMode after = activeRgb.getOrDefault(id, RgbMode.NONE);
                if (before != after) {
                    apply(p);
                    continue;
                }
            }
            if (activeRgb.containsKey(id)) {
                apply(p);
            }
        }
    }

    // ------------------------------------------------------------------ applying

    private void apply(Player player) {
        Component tag = tagFor(player.getUniqueId());
        Component name = Component.text(player.getName());
        Component full = tag == null ? null : tag.append(name);

        if (plugin.getConfig().getBoolean("display.tablist", true)) {
            player.playerListName(full);
        }
        if (plugin.getConfig().getBoolean("display.nametag", true)) {
            updateTeam(player, tag);
        }
        if ("DISPLAYNAME".equalsIgnoreCase(plugin.getConfig().getString("display.chat", "RENDERER"))) {
            player.displayName(full);
        }
    }

    private String teamName(Player player) {
        return TEAM_PREFIX + player.getUniqueId().toString().replace("-", "").substring(0, 12);
    }

    private void updateTeam(Player player, Component prefix) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamName(player);
        Team ours = sb.getTeam(teamName);

        if (prefix == null) {
            if (ours != null) {
                try {
                    ours.unregister();
                } catch (IllegalStateException ignored) {
                    // already gone
                }
            }
            return;
        }

        Team current = sb.getEntryTeam(player.getName());
        if (current != null && !current.getName().equals(teamName)) {
            return; // the player is in a team managed by another plugin, do not interfere
        }
        if (ours == null) {
            ours = sb.registerNewTeam(teamName);
        }
        ours.prefix(prefix);
        if (!ours.hasEntry(player.getName())) {
            ours.addEntry(player.getName());
        }
    }

    /** Removes everything this plugin applied to the player. */
    public void cleanup(Player player) {
        activeRgb.remove(player.getUniqueId());
        player.playerListName(null);
        if ("DISPLAYNAME".equalsIgnoreCase(plugin.getConfig().getString("display.chat", "RENDERER"))) {
            player.displayName(null);
        }
        updateTeam(player, null);
    }

    private void cleanupLeftoverTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : sb.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                try {
                    team.unregister();
                } catch (IllegalStateException ignored) {
                    // already gone
                }
            }
        }
    }
}
