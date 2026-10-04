package org.bcp.clansystem.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bcp.clansystem.ClanSystem;
import org.bcp.clansystem.manager.ClanManager;
import org.bcp.clansystem.model.Clan;
import org.bcp.clansystem.model.RgbMode;
import org.bcp.clansystem.model.Role;
import org.bcp.clansystem.perk.PerkIntegration;
import org.bcp.clansystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Handles /clan and /cc. */
public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "delete", "change", "info", "invite", "accept", "decline", "promote", "demote",
            "kick", "leave", "chat", "peace", "color", "rgb", "help", "perkvoucher", "reload");

    private final ClanSystem plugin;

    public ClanCommand(ClanSystem plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.clans();
    }

    // ================================================================== dispatch

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cc")) {
            chat(sender, args, 0);
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "change" -> change(sender, args);
            case "info" -> info(sender, args);
            case "invite" -> invite(sender, args);
            case "accept" -> accept(sender, args);
            case "decline" -> decline(sender, args);
            case "promote" -> promote(sender, args, true);
            case "demote" -> promote(sender, args, false);
            case "kick" -> kick(sender, args);
            case "leave" -> leave(sender);
            case "chat" -> chat(sender, args, 1);
            case "peace" -> peace(sender, args);
            case "color", "colour" -> color(sender);
            case "rgb" -> rgb(sender, args);
            case "perkvoucher" -> perkVoucher(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    // ================================================================== helpers

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        plugin.err(sender, "This command can only be used by players.");
        return null;
    }

    private boolean perm(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.err(sender, "You don't have permission to do that.");
        return false;
    }

    /** The clan of the player or null (after telling the player). */
    private Clan ownClan(Player player) {
        Clan clan = cm().getClanOf(player.getUniqueId());
        if (clan == null) {
            plugin.err(player, "You are not in a clan.");
        }
        return clan;
    }

    private Role roleIn(Clan clan, Player player) {
        Role role = clan.roleOf(player.getUniqueId());
        return role == null ? Role.MEMBER : role;
    }

    // ================================================================== help

    private void help(CommandSender sender) {
        plugin.msg(sender, "&e&lClan commands");
        helpLine(sender, "clan.create", "/clan create <Name> <Tag>", "Create a clan");
        helpLine(sender, "clan.delete", "/clan delete <Name> <Tag>", "Delete your clan (Leader)");
        helpLine(sender, "clan.change", "/clan change <name|tag> <value>", "Change name or tag (Leader, Co-Leader)");
        helpLine(sender, "clan.info", "/clan info [Name/Tag]", "Show clan info");
        helpLine(sender, "clan.invite", "/clan invite <Player>", "Invite a player (Moderator+)");
        helpLine(sender, "clan.accept", "/clan accept <Name/Tag>", "Accept a clan invite");
        helpLine(sender, "clan.decline", "/clan decline <Name/Tag>", "Decline a clan invite");
        helpLine(sender, "clan.promote", "/clan promote <Player>", "Promote a member (Leader, Co-Leader)");
        helpLine(sender, "clan.demote", "/clan demote <Player>", "Demote a member (Leader, Co-Leader)");
        helpLine(sender, "clan.kick", "/clan kick <Player>", "Kick a member (Leader, Co-Leader)");
        helpLine(sender, "clan.leave", "/clan leave", "Leave your clan");
        helpLine(sender, "clan.chat", "/clan chat [message] or /cc [message]", "Private clan chat (no message = toggle)");
        helpLine(sender, "clan.peace", "/clan peace <Player>", "Make peace with a player");
        helpLine(sender, "clan.color", "/clan color", "Change the tag color (Leader)");
        helpLine(sender, "clan.rgb", "/clan rgb [gradient|breathing|off]", "Toggle your animated tag perk");
        if (sender.hasPermission("clan.admin.voucher")) {
            helpLine(sender, "clan.admin.voucher", "/clan perkvoucher <perk> [duration] [player]", "Create a perk voucher");
        }
        if (sender.hasPermission("clan.admin.reload")) {
            helpLine(sender, "clan.admin.reload", "/clan reload", "Reload the config");
        }
    }

    private void helpLine(CommandSender sender, String permission, String usage, String description) {
        if (sender.hasPermission(permission)) {
            sender.sendMessage(Text.c("&8» &e" + usage + " &8- &7" + description));
        }
    }

    // ================================================================== create / delete / change

    private void create(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.create")) {
            return;
        }
        if (args.length != 3) {
            plugin.err(player, "Usage: /clan create <Name> <Tag>");
            return;
        }
        if (cm().getClanOf(player.getUniqueId()) != null) {
            plugin.err(player, "You are already in a clan. Leave it first.");
            return;
        }
        String name = args[1];
        String tag = args[2];
        String error = cm().validateName(name, null);
        if (error == null) {
            error = cm().validateTag(tag, null);
        }
        if (error != null) {
            plugin.err(player, error);
            return;
        }
        Clan clan = cm().create(player.getUniqueId(), player.getName(), name, tag);
        plugin.display().refresh(player);
        plugin.msg(player, Component.text("Your clan ", NamedTextColor.GRAY)
                .append(Component.text(clan.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(plugin.display().staticTag(clan))
                .append(Component.text(" was created!", NamedTextColor.GRAY)));
        if (plugin.getConfig().getBoolean("announce-creation", true)) {
            Bukkit.broadcast(Component.text()
                    .append(plugin.prefix())
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" founded the clan ", NamedTextColor.GRAY))
                    .append(Component.text(clan.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" ", NamedTextColor.GRAY))
                    .append(plugin.display().staticTag(clan))
                    .append(Component.text("!", NamedTextColor.GRAY))
                    .build());
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (!perm(sender, "clan.delete")) {
            return;
        }
        if (args.length != 3) {
            plugin.err(sender, "Usage: /clan delete <Name> <Tag>");
            return;
        }
        Clan target = cm().findByName(args[1]);
        if (target == null || !target.getTag().equalsIgnoreCase(args[2])) {
            plugin.err(sender, "No clan matches that name and tag.");
            return;
        }
        boolean admin = sender.hasPermission("clan.admin");
        boolean isLeader = false;
        if (sender instanceof Player player) {
            isLeader = target.roleOf(player.getUniqueId()) == Role.LEADER;
        }
        if (!isLeader && !admin) {
            plugin.err(sender, "Only the clan leader can delete the clan.");
            return;
        }

        List<UUID> members = new ArrayList<>(target.members().keySet());
        plugin.broadcastClan(target, "&cYour clan &e" + target.getName() + " &chas been deleted by &e" + sender.getName() + "&c.");
        String deletedName = target.getName();
        cm().disband(target);
        for (UUID id : members) {
            plugin.setClanChatMode(id, false);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                plugin.display().refresh(online);
            }
        }
        if (!(sender instanceof Player p && members.contains(p.getUniqueId()))) {
            plugin.msg(sender, "Deleted the clan &e" + deletedName + "&7.");
        }
    }

    private void change(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.change")) {
            return;
        }
        if (args.length != 3) {
            plugin.err(player, "Usage: /clan change <name|tag> <value>");
            return;
        }
        Clan clan = ownClan(player);
        if (clan == null) {
            return;
        }
        if (!roleIn(clan, player).atLeast(Role.CO_LEADER)) {
            plugin.err(player, "Only the Leader and Co-Leaders can change the clan name or tag.");
            return;
        }
        String what = args[1].toLowerCase(Locale.ROOT);
        String value = args[2];
        if (what.equals("name")) {
            String error = cm().validateName(value, clan);
            if (error != null) {
                plugin.err(player, error);
                return;
            }
            String old = clan.getName();
            cm().rename(clan, value);
            plugin.broadcastClan(clan, "&e" + player.getName() + " &7renamed the clan from &e" + old + " &7to &e" + value + "&7.");
        } else if (what.equals("tag")) {
            String error = cm().validateTag(value, clan);
            if (error != null) {
                plugin.err(player, error);
                return;
            }
            cm().retag(clan, value);
            plugin.broadcastClan(clan, "&e" + player.getName() + " &7changed the clan tag to &e" + clan.getTag() + "&7.");
            plugin.display().refreshClan(clan);
        } else {
            plugin.err(player, "Usage: /clan change <name|tag> <value>");
        }
    }

    // ================================================================== info

    private void info(CommandSender sender, String[] args) {
        if (!perm(sender, "clan.info")) {
            return;
        }
        Clan clan;
        if (args.length >= 2) {
            clan = cm().find(args[1]);
        } else if (sender instanceof Player player) {
            clan = cm().getClanOf(player.getUniqueId());
            if (clan == null) {
                plugin.err(sender, "You are not in a clan. Use /clan info <Name/Tag>.");
                return;
            }
        } else {
            plugin.err(sender, "Usage: /clan info <Name/Tag>");
            return;
        }
        if (clan == null) {
            plugin.err(sender, "That clan does not exist.");
            return;
        }

        sender.sendMessage(Text.c("&8&m                                        "));
        sender.sendMessage(Text.c("&e&l" + clan.getName()));
        sender.sendMessage(Component.text("Tag: ", NamedTextColor.GRAY).append(plugin.display().staticTag(clan)));
        sender.sendMessage(Text.c("&7Members: &f" + clan.size() + "&8/&f" + cm().maxMembers()));
        for (Role role : Role.values()) {
            List<UUID> list = clan.membersWithRole(role);
            if (list.isEmpty()) {
                continue;
            }
            sender.sendMessage(Component.text(role.getDisplayName()
                    + (role == Role.LEADER ? "" : " (" + list.size() + ")"), role.getColor()));
            for (UUID id : list) {
                boolean online = Bukkit.getPlayer(id) != null;
                sender.sendMessage(Component.text()
                        .append(Component.text("  ● ", online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                        .append(Component.text(clan.nameOf(id), role.getColor()))
                        .build());
            }
        }
        sender.sendMessage(Text.c("&8&m                                        "));
    }

    // ================================================================== invite / accept / decline

    private void invite(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.invite")) {
            return;
        }
        if (args.length != 2) {
            plugin.err(player, "Usage: /clan invite <Player>");
            return;
        }
        Clan clan = ownClan(player);
        if (clan == null) {
            return;
        }
        if (!roleIn(clan, player).atLeast(Role.MODERATOR)) {
            plugin.err(player, "Only Moderators and higher can invite players.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.err(player, "That player is not online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.err(player, "You cannot invite yourself.");
            return;
        }
        if (cm().getClanOf(target.getUniqueId()) != null) {
            plugin.err(player, target.getName() + " is already in a clan.");
            return;
        }
        if (clan.size() >= cm().maxMembers()) {
            plugin.err(player, "Your clan is full (" + cm().maxMembers() + " members).");
            return;
        }

        long seconds = Math.max(10, plugin.getConfig().getLong("invites.expire-seconds", 300));
        cm().addInvite(target.getUniqueId(),
                new ClanManager.Invite(clan.getId(), player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L));

        plugin.msg(player, "You invited &e" + target.getName() + " &7to your clan.");

        Component accept = Text.c("&a&l[ACCEPT]")
                .clickEvent(ClickEvent.runCommand("/clan accept " + clan.getName()))
                .hoverEvent(HoverEvent.showText(Text.c("&7Click to join &e" + clan.getName())));
        Component decline = Text.c("&c&l[DECLINE]")
                .clickEvent(ClickEvent.runCommand("/clan decline " + clan.getName()))
                .hoverEvent(HoverEvent.showText(Text.c("&7Click to decline")));
        plugin.msg(target, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" invited you to the clan ", NamedTextColor.GRAY))
                .append(Component.text(clan.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(plugin.display().staticTag(clan))
                .append(Component.text(". ", NamedTextColor.GRAY))
                .append(accept)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(decline)
                .build());
    }

    private void accept(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.accept")) {
            return;
        }
        if (args.length != 2) {
            plugin.err(player, "Usage: /clan accept <Name/Tag>");
            return;
        }
        if (cm().getClanOf(player.getUniqueId()) != null) {
            plugin.err(player, "You are already in a clan.");
            return;
        }
        Clan clan = cm().find(args[1]);
        if (clan == null) {
            plugin.err(player, "That clan does not exist.");
            return;
        }
        if (cm().getInvite(player.getUniqueId(), clan.getId()) == null) {
            plugin.err(player, "You have no (valid) invite from that clan.");
            return;
        }
        if (clan.size() >= cm().maxMembers()) {
            plugin.err(player, "That clan is full.");
            return;
        }
        cm().addMember(clan, player.getUniqueId(), player.getName());
        plugin.display().refresh(player);
        plugin.broadcastClan(clan, "&e" + player.getName() + " &ajoined the clan!");
    }

    private void decline(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.decline")) {
            return;
        }
        if (args.length != 2) {
            plugin.err(player, "Usage: /clan decline <Name/Tag>");
            return;
        }
        Clan clan = cm().find(args[1]);
        if (clan == null) {
            plugin.err(player, "That clan does not exist.");
            return;
        }
        ClanManager.Invite invite = cm().getInvite(player.getUniqueId(), clan.getId());
        if (invite == null) {
            plugin.err(player, "You have no (valid) invite from that clan.");
            return;
        }
        cm().removeInvite(player.getUniqueId(), clan.getId());
        plugin.msg(player, "You declined the invite from &e" + clan.getName() + "&7.");
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            plugin.msg(inviter, "&e" + player.getName() + " &cdeclined &7your clan invite.");
        }
    }

    // ================================================================== promote / demote / kick / leave

    private void promote(CommandSender sender, String[] args, boolean up) {
        Player player = player(sender);
        if (player == null || !perm(player, up ? "clan.promote" : "clan.demote")) {
            return;
        }
        if (args.length != 2) {
            plugin.err(player, "Usage: /clan " + (up ? "promote" : "demote") + " <Player>");
            return;
        }
        Clan clan = ownClan(player);
        if (clan == null) {
            return;
        }
        Role actor = roleIn(clan, player);
        if (!actor.atLeast(Role.CO_LEADER)) {
            plugin.err(player, "Only the Leader and Co-Leaders can " + (up ? "promote" : "demote") + " members.");
            return;
        }
        UUID targetId = clan.findMemberByName(args[1]);
        if (targetId == null) {
            plugin.err(player, "That player is not in your clan.");
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            plugin.err(player, "You cannot " + (up ? "promote" : "demote") + " yourself.");
            return;
        }
        Role current = clan.roleOf(targetId);
        if (current == null) {
            return;
        }
        String targetName = clan.nameOf(targetId);

        Role next;
        if (up) {
            next = Role.ofLevel(current.getLevel() + 1);
            if (next == null || next == Role.LEADER) {
                plugin.err(player, targetName + " cannot be promoted any further.");
                return;
            }
            if (next.getLevel() >= actor.getLevel()) {
                plugin.err(player, "You can only promote members to a rank below your own.");
                return;
            }
        } else {
            if (current == Role.MEMBER) {
                plugin.err(player, targetName + " already has the lowest rank.");
                return;
            }
            if (current.getLevel() >= actor.getLevel()) {
                plugin.err(player, "You can only demote members ranked below you.");
                return;
            }
            next = Role.ofLevel(current.getLevel() - 1);
            if (next == null) {
                return;
            }
        }

        cm().setRole(clan, targetId, next);
        plugin.broadcastClan(clan, "&e" + targetName + " &7was " + (up ? "&apromoted" : "&cdemoted") + " &7to "
                + roleCode(next) + next.getDisplayName() + " &7by &e" + player.getName() + "&7.");
    }

    private String roleCode(Role role) {
        return switch (role) {
            case LEADER -> "&6";
            case CO_LEADER -> "&c";
            case MODERATOR -> "&a";
            case MEMBER -> "&7";
        };
    }

    private void kick(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.kick")) {
            return;
        }
        if (args.length != 2) {
            plugin.err(player, "Usage: /clan kick <Player>");
            return;
        }
        Clan clan = ownClan(player);
        if (clan == null) {
            return;
        }
        Role actor = roleIn(clan, player);
        if (!actor.atLeast(Role.CO_LEADER)) {
            plugin.err(player, "Only the Leader and Co-Leaders can kick members.");
            return;
        }
        UUID targetId = clan.findMemberByName(args[1]);
        if (targetId == null) {
            plugin.err(player, "That player is not in your clan.");
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            plugin.err(player, "You cannot kick yourself. Use /clan leave.");
            return;
        }
        Role targetRole = clan.roleOf(targetId);
        if (targetRole == null || targetRole.getLevel() >= actor.getLevel()) {
            plugin.err(player, "You can only kick members ranked below you.");
            return;
        }

        String targetName = clan.nameOf(targetId);
        cm().removeMember(clan, targetId);
        plugin.setClanChatMode(targetId, false);
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            plugin.display().refresh(online);
            plugin.msg(online, "&cYou were kicked from the clan &e" + clan.getName() + " &cby &e" + player.getName() + "&c.");
        }
        plugin.broadcastClan(clan, "&e" + targetName + " &7was kicked from the clan by &e" + player.getName() + "&7.");
    }

    private void leave(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.leave")) {
            return;
        }
        Clan clan = ownClan(player);
        if (clan == null) {
            return;
        }
        if (roleIn(clan, player) == Role.LEADER) {
            plugin.err(player, "The leader cannot leave the clan. Delete it with /clan delete <Name> <Tag>.");
            return;
        }
        cm().removeMember(clan, player.getUniqueId());
        plugin.setClanChatMode(player.getUniqueId(), false);
        plugin.display().refresh(player);
        plugin.msg(player, "You left the clan &e" + clan.getName() + "&7.");
        plugin.broadcastClan(clan, "&e" + player.getName() + " &7left the clan.");
    }

    // ================================================================== chat

    private void chat(CommandSender sender, String[] args, int from) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.chat")) {
            return;
        }
        Clan clan = ownClan(player);
        if (clan == null) {
            return;
        }
        if (args.length <= from) {
            boolean enable = !plugin.isClanChatMode(player.getUniqueId());
            plugin.setClanChatMode(player.getUniqueId(), enable);
            plugin.msg(player, enable
                    ? "Clan chat &aenabled&7. All your messages now go to your clan. Use &e/cc &7to switch back."
                    : "Clan chat &cdisabled&7. You are back in the public chat.");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, from, args.length));
        plugin.sendClanChat(player, clan, text);
    }

    // ================================================================== peace

    private void peace(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.peace")) {
            return;
        }
        if (args.length < 2 || args.length > 3) {
            plugin.err(player, "Usage: /clan peace <Player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.err(player, "That player is not online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.err(player, "You cannot make peace with yourself.");
            return;
        }
        if (plugin.getConfig().getBoolean("peace.same-clan-only", true)) {
            Clan mine = cm().getClanOf(player.getUniqueId());
            Clan theirs = cm().getClanOf(target.getUniqueId());
            if (mine == null || mine != theirs) {
                plugin.err(player, "You can only make peace with members of your own clan.");
                return;
            }
        }

        UUID me = player.getUniqueId();
        UUID other = target.getUniqueId();

        if (args.length == 3) {
            if (!args[2].equalsIgnoreCase("end")) {
                plugin.err(player, "Usage: /clan peace <Player> [end]");
                return;
            }
            if (!cm().hasPeace(me, other)) {
                plugin.err(player, "You are not at peace with " + target.getName() + ".");
                return;
            }
            cm().endPeace(me, other);
            plugin.msg(player, "Your peace with &e" + target.getName() + " &7has ended.");
            plugin.msg(target, "Your peace with &e" + player.getName() + " &7has ended.");
            return;
        }

        if (cm().hasPeace(me, other)) {
            plugin.err(player, "You are already at peace with " + target.getName()
                    + ". Use /clan peace " + target.getName() + " end to end it.");
            return;
        }

        // The other player already asked me -> this command is the acceptance.
        if (cm().hasPeaceRequest(other, me)) {
            cm().removePeaceRequest(other, me);
            cm().makePeace(me, other);
            plugin.msg(player, "You are now at peace with &e" + target.getName() + "&7.");
            plugin.msg(target, "You are now at peace with &e" + player.getName() + "&7.");
            return;
        }

        long seconds = Math.max(5, plugin.getConfig().getLong("peace.request-seconds", 60));
        cm().addPeaceRequest(me, other, System.currentTimeMillis() + seconds * 1000L);
        plugin.msg(player, "You asked &e" + target.getName() + " &7to make peace.");

        Component command = Text.c("&e/clan peace " + player.getName())
                .clickEvent(ClickEvent.runCommand("/clan peace " + player.getName()))
                .hoverEvent(HoverEvent.showText(Text.c("&7Click to accept")));
        plugin.msg(target, Component.text()
                .append(Component.text(player.getName() + " wants to make peace with you! ", NamedTextColor.GRAY))
                .append(command)
                .build());
    }

    // ================================================================== color / rgb

    private void color(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.color")) {
            return;
        }
        Clan clan = ownClan(player);
        if (clan == null) {
            return;
        }
        if (roleIn(clan, player) != Role.LEADER) {
            plugin.err(player, "Only the clan leader can change the tag color.");
            return;
        }
        plugin.colorGui().open(player, clan);
    }

    private void rgb(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !perm(player, "clan.rgb")) {
            return;
        }
        RgbMode current = cm().getRgb(player.getUniqueId());
        if (args.length < 2) {
            plugin.msg(player, "Animated tag: &e" + (current == RgbMode.NONE ? "off" : current.getDisplayName()));
            for (RgbMode mode : new RgbMode[]{RgbMode.GRADIENT, RgbMode.BREATHING}) {
                plugin.msg(player, "&8» &e" + mode.shortName() + " &8- &7" + mode.getDescription() + " "
                        + (player.hasPermission(mode.permission()) ? "&a(unlocked)" : "&c(locked)"));
            }
            plugin.msg(player, "Use &e/clan rgb <gradient|breathing|off>&7.");
            return;
        }
        String arg = args[1].toLowerCase(Locale.ROOT);
        if (arg.equals("off") || arg.equals("none")) {
            cm().setRgb(player.getUniqueId(), RgbMode.NONE);
            plugin.display().refresh(player);
            plugin.msg(player, "Animated clan tag &cdisabled&7.");
            return;
        }
        RgbMode mode = RgbMode.fromId(arg);
        if (mode == null) {
            plugin.err(player, "Usage: /clan rgb <gradient|breathing|off>");
            return;
        }
        if (!player.hasPermission(mode.permission())) {
            plugin.err(player, "You don't own this perk!");
            return;
        }
        plugin.perks().toggle(player, mode);
    }

    // ================================================================== admin

    private void perkVoucher(CommandSender sender, String[] args) {
        if (!perm(sender, "clan.admin.voucher")) {
            return;
        }
        plugin.perks().giveVoucher(sender, args);
    }

    private void reload(CommandSender sender) {
        if (!perm(sender, "clan.admin.reload")) {
            return;
        }
        plugin.reloadConfig();
        plugin.display().start();
        plugin.msg(sender, "Configuration reloaded.");
    }

    // ================================================================== tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("cc")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String s : SUBCOMMANDS) {
                if (canUse(sender, s)) {
                    subs.add(s);
                }
            }
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Player player = sender instanceof Player p ? p : null;
        Clan own = player == null ? null : cm().getClanOf(player.getUniqueId());

        if (args.length == 2) {
            switch (sub) {
                case "change":
                    return filter(List.of("name", "tag"), args[1]);
                case "info": {
                    List<String> names = new ArrayList<>();
                    for (Clan c : cm().all()) {
                        names.add(c.getName());
                        names.add(c.getTag());
                    }
                    return filter(names, args[1]);
                }
                case "delete":
                    return own == null ? List.of() : filter(List.of(own.getName()), args[1]);
                case "invite": {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (cm().getClanOf(online.getUniqueId()) == null) {
                            names.add(online.getName());
                        }
                    }
                    return filter(names, args[1]);
                }
                case "accept":
                case "decline": {
                    List<String> names = new ArrayList<>();
                    if (player != null) {
                        for (Clan c : cm().pendingInviteClans(player.getUniqueId())) {
                            names.add(c.getName());
                        }
                    }
                    return filter(names, args[1]);
                }
                case "promote":
                case "demote":
                case "kick": {
                    List<String> names = new ArrayList<>();
                    if (own != null && player != null) {
                        for (Clan.Member m : own.members().values()) {
                            if (!m.getName().equalsIgnoreCase(player.getName())) {
                                names.add(m.getName());
                            }
                        }
                    }
                    return filter(names, args[1]);
                }
                case "peace": {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (player == null || !online.getUniqueId().equals(player.getUniqueId())) {
                            names.add(online.getName());
                        }
                    }
                    return filter(names, args[1]);
                }
                case "rgb":
                    return filter(List.of("gradient", "breathing", "off"), args[1]);
                case "perkvoucher":
                    return filter(List.of("gradient", "breathing"), args[1]);
                default:
                    return List.of();
            }
        }

        if (args.length == 3) {
            if (sub.equals("delete") && own != null) {
                return filter(List.of(own.getTag()), args[2]);
            }
            if (sub.equals("peace")) {
                return filter(List.of("end"), args[2]);
            }
            if (sub.equals("perkvoucher")) {
                return filter(new ArrayList<>(PerkIntegration.durations()), args[2]);
            }
        }

        if (args.length == 4 && sub.equals("perkvoucher")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[3]);
        }
        return List.of();
    }

    private boolean canUse(CommandSender sender, String sub) {
        return switch (sub) {
            case "help" -> true;
            case "perkvoucher" -> sender.hasPermission("clan.admin.voucher");
            case "reload" -> sender.hasPermission("clan.admin.reload");
            default -> sender.hasPermission("clan." + sub);
        };
    }

    private List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
