package org.bcp.clansystem.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bcp.clansystem.ClanSystem;
import org.bcp.clansystem.manager.ClanManager;
import org.bcp.clansystem.model.Clan;
import org.bcp.clansystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class ClanListener implements Listener {

    private final ClanSystem plugin;

    public ClanListener(ClanSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Clan clan = plugin.clans().getClanOf(player.getUniqueId());
            if (clan != null) {
                // keep stored names up to date (name changes)
                clan.updateName(player.getUniqueId(), player.getName());
            }
            plugin.display().refresh(player);
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.setClanChatMode(player.getUniqueId(), false);
        plugin.display().cleanup(player);
    }

    /** Players in clan chat mode: the message goes to the clan only. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClanChatMode(AsyncChatEvent event) {
        final Player player = event.getPlayer();
        if (!plugin.isClanChatMode(player.getUniqueId())) {
            return;
        }
        final Clan clan = plugin.clans().getClanOf(player.getUniqueId());
        if (clan == null) {
            plugin.setClanChatMode(player.getUniqueId(), false);
            return;
        }
        event.setCancelled(true);
        final String text = Text.strip(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.sendClanChat(player, clan, text));
    }

    /** Puts the clan tag in front of the name in public chat. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChatFormat(AsyncChatEvent event) {
        if (!"RENDERER".equalsIgnoreCase(plugin.getConfig().getString("display.chat", "RENDERER"))) {
            return;
        }
        final Component tag = plugin.display().tagFor(event.getPlayer().getUniqueId());
        if (tag == null) {
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) -> Component.text()
                .append(tag)
                .append(sourceDisplayName)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message)
                .build());
    }

    /** Peace pacts and friendly fire. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        ClanManager cm = plugin.clans();
        UUID a = attacker.getUniqueId();
        UUID v = victim.getUniqueId();
        Clan ca = cm.getClanOf(a);
        Clan cv = cm.getClanOf(v);
        boolean sameClan = ca != null && ca == cv;

        boolean sameClanOnly = plugin.getConfig().getBoolean("peace.same-clan-only", true);
        if (cm.hasPeace(a, v) && (!sameClanOnly || sameClan)) {
            event.setCancelled(true);
            attacker.sendActionBar(Text.c("&aYou are at peace with &e" + victim.getName() + "&a."));
            return;
        }

        if (sameClan && !plugin.getConfig().getBoolean("friendly-fire", true)) {
            event.setCancelled(true);
            attacker.sendActionBar(Text.c("&cFriendly fire is disabled in your clan."));
        }
    }
}
