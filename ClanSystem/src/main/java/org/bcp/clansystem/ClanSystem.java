package org.bcp.clansystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bcp.clansystem.command.ClanCommand;
import org.bcp.clansystem.gui.ColorGui;
import org.bcp.clansystem.listener.ClanListener;
import org.bcp.clansystem.manager.ClanManager;
import org.bcp.clansystem.manager.DisplayManager;
import org.bcp.clansystem.model.Clan;
import org.bcp.clansystem.model.Role;
import org.bcp.clansystem.perk.PerkIntegration;
import org.bcp.clansystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanSystem extends JavaPlugin {

    private ClanManager clanManager;
    private DisplayManager displayManager;
    private ColorGui colorGui;
    private PerkIntegration perkIntegration;
    private final Set<UUID> clanChatMode = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        clanManager = new ClanManager(this);
        clanManager.load();
        displayManager = new DisplayManager(this);
        colorGui = new ColorGui(this);
        perkIntegration = new PerkIntegration(this);

        ClanCommand command = new ClanCommand(this);
        registerCommand("clan", command);
        registerCommand("cc", command);

        Bukkit.getPluginManager().registerEvents(new ClanListener(this), this);
        Bukkit.getPluginManager().registerEvents(colorGui, this);
        Bukkit.getPluginManager().registerEvents(perkIntegration, this);

        displayManager.start();

        getLogger().info("ClanSystem " + getPluginMeta().getVersion() + " enabled."
                + (Bukkit.getPluginManager().getPlugin("PerkSystem") != null ? " PerkSystem found." : " PerkSystem not found, perk menu integration is inactive."));
    }

    @Override
    public void onDisable() {
        if (displayManager != null) {
            displayManager.shutdown();
        }
        if (clanManager != null) {
            clanManager.saveNow();
        }
    }

    private void registerCommand(String name, ClanCommand executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command /" + name + " is missing in plugin.yml!");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    // ------------------------------------------------------------------ accessors

    public ClanManager clans() {
        return clanManager;
    }

    public DisplayManager display() {
        return displayManager;
    }

    public ColorGui colorGui() {
        return colorGui;
    }

    public PerkIntegration perks() {
        return perkIntegration;
    }

    // ------------------------------------------------------------------ messaging

    public Component prefix() {
        return Text.c(getConfig().getString("prefix", "&8[&bClan&8] &7"));
    }

    /** Sends a prefixed message. Default colour is gray, use '&' codes for highlights. */
    public void msg(CommandSender to, String legacyText) {
        to.sendMessage(Component.text()
                .append(prefix())
                .append(Text.c(legacyText).colorIfAbsent(NamedTextColor.GRAY))
                .build());
    }

    /** Sends a prefixed message with a prebuilt component. */
    public void msg(CommandSender to, Component component) {
        to.sendMessage(Component.text()
                .append(prefix())
                .append(component.colorIfAbsent(NamedTextColor.GRAY))
                .build());
    }

    public void err(CommandSender to, String text) {
        msg(to, "&c" + text);
    }

    /** Sends a prefixed message to every online member of a clan. */
    public void broadcastClan(Clan clan, String legacyText) {
        for (UUID id : clan.members().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg(p, legacyText);
            }
        }
    }

    // ------------------------------------------------------------------ clan chat

    public boolean isClanChatMode(UUID id) {
        return clanChatMode.contains(id);
    }

    public void setClanChatMode(UUID id, boolean enabled) {
        if (enabled) {
            clanChatMode.add(id);
        } else {
            clanChatMode.remove(id);
        }
    }

    /** Sends a message to the private clan chat. Must run on the main thread. */
    public void sendClanChat(Player from, Clan clan, String text) {
        Role role = clan.roleOf(from.getUniqueId());
        if (role == null) {
            role = Role.MEMBER;
        }
        Component name = Component.text(from.getName(), role.getColor())
                .hoverEvent(HoverEvent.showText(Component.text(role.getDisplayName(), role.getColor())));
        Component line = Component.text()
                .append(Text.c(getConfig().getString("clan-chat.prefix", "&8[&bClan Chat&8] ")))
                .append(name)
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(Component.text(text, NamedTextColor.WHITE))
                .build();
        for (UUID id : clan.members().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(Component.text(
                "[ClanChat:" + clan.getName() + "] " + from.getName() + ": " + text));
    }
}
