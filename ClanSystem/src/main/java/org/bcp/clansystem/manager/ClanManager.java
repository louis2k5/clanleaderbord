package org.bcp.clansystem.manager;

import org.bcp.clansystem.ClanSystem;
import org.bcp.clansystem.model.Clan;
import org.bcp.clansystem.model.RgbMode;
import org.bcp.clansystem.model.Role;
import org.bcp.clansystem.model.TagColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Holds all clan data (in memory) and persists it to plugins/ClanSystem/data.yml. */
public final class ClanManager {

    public record Invite(UUID clanId, UUID inviter, long expires) {
    }

    private final ClanSystem plugin;
    private final Map<UUID, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerClan = new ConcurrentHashMap<>();
    /** invitee -> invites */
    private final Map<UUID, List<Invite>> invites = new ConcurrentHashMap<>();
    /** target -> (requester -> expiry millis) */
    private final Map<UUID, Map<UUID, Long>> peaceRequests = new ConcurrentHashMap<>();
    private final Set<String> peace = ConcurrentHashMap.newKeySet();
    private final Map<UUID, RgbMode> rgb = new ConcurrentHashMap<>();

    private final AtomicLong saveSeq = new AtomicLong();
    private final Object ioLock = new Object();
    private long lastWritten = 0;

    public ClanManager(ClanSystem plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lookups

    public Collection<Clan> all() {
        return clans.values();
    }

    public Clan getClan(UUID clanId) {
        return clanId == null ? null : clans.get(clanId);
    }

    public Clan getClanOf(UUID player) {
        UUID id = playerClan.get(player);
        return id == null ? null : clans.get(id);
    }

    public Clan findByName(String name) {
        for (Clan c : clans.values()) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    public Clan findByTag(String tag) {
        for (Clan c : clans.values()) {
            if (c.getTag().equalsIgnoreCase(tag)) {
                return c;
            }
        }
        return null;
    }

    /** Name first, then tag. */
    public Clan find(String nameOrTag) {
        Clan c = findByName(nameOrTag);
        return c != null ? c : findByTag(nameOrTag);
    }

    // ------------------------------------------------------------------ validation

    /** Returns an error message or null if the name is valid. */
    public String validateName(String name, Clan ignore) {
        int min = plugin.getConfig().getInt("limits.name-min", 3);
        int max = plugin.getConfig().getInt("limits.name-max", 10);
        if (name.length() < min || name.length() > max) {
            return "The clan name must be " + min + "-" + max + " characters long.";
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            return "The clan name may only contain letters, numbers and underscores.";
        }
        if (isBlocked(name)) {
            return "That clan name is not allowed.";
        }
        Clan byName = findByName(name);
        if (byName != null && byName != ignore) {
            return "A clan with that name already exists.";
        }
        Clan byTag = findByTag(name);
        if (byTag != null && byTag != ignore) {
            return "That name is already used as a clan tag.";
        }
        return null;
    }

    /** Returns an error message or null if the tag is valid. */
    public String validateTag(String tag, Clan ignore) {
        int min = plugin.getConfig().getInt("limits.tag-min", 2);
        int max = plugin.getConfig().getInt("limits.tag-max", 4);
        if (tag.length() < min || tag.length() > max) {
            return "The clan tag must be " + min + "-" + max + " characters long.";
        }
        if (!tag.matches("[A-Za-z0-9]+")) {
            return "The clan tag may only contain letters and numbers.";
        }
        if (isBlocked(tag)) {
            return "That clan tag is not allowed.";
        }
        Clan byTag = findByTag(tag);
        if (byTag != null && byTag != ignore) {
            return "A clan with that tag already exists.";
        }
        Clan byName = findByName(tag);
        if (byName != null && byName != ignore) {
            return "That tag is already used as a clan name.";
        }
        return null;
    }

    private boolean isBlocked(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        for (String word : plugin.getConfig().getStringList("limits.blocked-words")) {
            if (!word.isBlank() && v.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public String normalizeTag(String tag) {
        return plugin.getConfig().getBoolean("tag.uppercase", false) ? tag.toUpperCase(Locale.ROOT) : tag;
    }

    public int maxMembers() {
        return Math.max(1, plugin.getConfig().getInt("limits.max-members", 20));
    }

    // ------------------------------------------------------------------ clan changes

    public Clan create(UUID leader, String leaderName, String name, String tag) {
        Clan clan = new Clan(UUID.randomUUID(), name, normalizeTag(tag), System.currentTimeMillis());
        clan.putMember(leader, Role.LEADER, leaderName);
        clans.put(clan.getId(), clan);
        playerClan.put(leader, clan.getId());
        removeInvitesOf(leader);
        saveAsync();
        return clan;
    }

    public void disband(Clan clan) {
        for (UUID member : clan.members().keySet()) {
            playerClan.remove(member);
        }
        clans.remove(clan.getId());
        for (List<Invite> list : invites.values()) {
            synchronized (list) {
                list.removeIf(i -> i.clanId().equals(clan.getId()));
            }
        }
        saveAsync();
    }

    public void addMember(Clan clan, UUID player, String playerName) {
        clan.putMember(player, Role.MEMBER, playerName);
        playerClan.put(player, clan.getId());
        removeInvitesOf(player);
        saveAsync();
    }

    public void removeMember(Clan clan, UUID player) {
        clan.removeMember(player);
        playerClan.remove(player);
        saveAsync();
    }

    public void setRole(Clan clan, UUID player, Role role) {
        clan.setRole(player, role);
        saveAsync();
    }

    public void rename(Clan clan, String name) {
        clan.setName(name);
        saveAsync();
    }

    public void retag(Clan clan, String tag) {
        clan.setTag(normalizeTag(tag));
        saveAsync();
    }

    public void setColor(Clan clan, TagColor color) {
        clan.setColor(color);
        saveAsync();
    }

    // ------------------------------------------------------------------ invites

    public void addInvite(UUID target, Invite invite) {
        List<Invite> list = invites.computeIfAbsent(target, k -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(i -> i.clanId().equals(invite.clanId()));
            list.add(invite);
        }
    }

    /** Returns a valid (non-expired) invite from that clan, or null. */
    public Invite getInvite(UUID target, UUID clanId) {
        List<Invite> list = invites.get(target);
        if (list == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        synchronized (list) {
            list.removeIf(i -> i.expires() < now);
            for (Invite i : list) {
                if (i.clanId().equals(clanId)) {
                    return i;
                }
            }
        }
        return null;
    }

    public void removeInvite(UUID target, UUID clanId) {
        List<Invite> list = invites.get(target);
        if (list != null) {
            synchronized (list) {
                list.removeIf(i -> i.clanId().equals(clanId));
            }
        }
    }

    public void removeInvitesOf(UUID target) {
        invites.remove(target);
    }

    /** Clans that currently have a valid invite for this player. */
    public List<Clan> pendingInviteClans(UUID target) {
        List<Clan> result = new ArrayList<>();
        List<Invite> list = invites.get(target);
        if (list == null) {
            return result;
        }
        long now = System.currentTimeMillis();
        synchronized (list) {
            list.removeIf(i -> i.expires() < now);
            for (Invite i : list) {
                Clan c = clans.get(i.clanId());
                if (c != null) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ peace

    private String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public boolean hasPeace(UUID a, UUID b) {
        return peace.contains(pairKey(a, b));
    }

    public void makePeace(UUID a, UUID b) {
        peace.add(pairKey(a, b));
        saveAsync();
    }

    public void endPeace(UUID a, UUID b) {
        peace.remove(pairKey(a, b));
        saveAsync();
    }

    public void addPeaceRequest(UUID requester, UUID target, long expiresAt) {
        peaceRequests.computeIfAbsent(target, k -> new ConcurrentHashMap<>()).put(requester, expiresAt);
    }

    public boolean hasPeaceRequest(UUID requester, UUID target) {
        Map<UUID, Long> map = peaceRequests.get(target);
        if (map == null) {
            return false;
        }
        Long expires = map.get(requester);
        if (expires == null) {
            return false;
        }
        if (expires < System.currentTimeMillis()) {
            map.remove(requester);
            return false;
        }
        return true;
    }

    public void removePeaceRequest(UUID requester, UUID target) {
        Map<UUID, Long> map = peaceRequests.get(target);
        if (map != null) {
            map.remove(requester);
        }
    }

    // ------------------------------------------------------------------ rgb perk settings

    public RgbMode getRgb(UUID player) {
        return rgb.getOrDefault(player, RgbMode.NONE);
    }

    public void setRgb(UUID player, RgbMode mode) {
        if (mode == null || mode == RgbMode.NONE) {
            rgb.remove(player);
        } else {
            rgb.put(player, mode);
        }
        saveAsync();
    }

    // ------------------------------------------------------------------ persistence

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        File file = dataFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection cs = y.getConfigurationSection("clans");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ConfigurationSection c = cs.getConfigurationSection(key);
                    if (c == null) {
                        continue;
                    }
                    Clan clan = new Clan(id, c.getString("name", "Unknown"), c.getString("tag", "??"),
                            c.getLong("created", System.currentTimeMillis()));
                    clan.setColor(TagColor.byKey(c.getString("color")));
                    ConfigurationSection ms = c.getConfigurationSection("members");
                    if (ms != null) {
                        for (String mk : ms.getKeys(false)) {
                            UUID memberId = UUID.fromString(mk);
                            Role role = Role.parse(ms.getString(mk + ".role"), Role.MEMBER);
                            clan.putMember(memberId, role, ms.getString(mk + ".name", "Unknown"));
                        }
                    }
                    if (clan.size() == 0) {
                        continue;
                    }
                    ensureLeader(clan);
                    clans.put(id, clan);
                    for (UUID memberId : clan.members().keySet()) {
                        playerClan.put(memberId, id);
                    }
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping invalid clan entry '" + key + "' in data.yml: " + ex.getMessage());
                }
            }
        }

        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                try {
                    RgbMode mode = RgbMode.parse(ps.getString(key));
                    if (mode != RgbMode.NONE) {
                        rgb.put(UUID.fromString(key), mode);
                    }
                } catch (IllegalArgumentException ignored) {
                    // invalid uuid, skip
                }
            }
        }

        peace.addAll(y.getStringList("peace"));
        plugin.getLogger().info("Loaded " + clans.size() + " clan(s).");
    }

    /** Makes sure every clan has a leader (e.g. after manual edits of data.yml). */
    private void ensureLeader(Clan clan) {
        if (clan.leaderId() != null) {
            return;
        }
        UUID best = null;
        for (Map.Entry<UUID, Clan.Member> e : clan.members().entrySet()) {
            if (best == null) {
                best = e.getKey();
                continue;
            }
            Clan.Member current = clan.members().get(best);
            Comparator<Clan.Member> byRole = Comparator.comparingInt(m -> m.getRole().getLevel());
            if (byRole.compare(e.getValue(), current) > 0) {
                best = e.getKey();
            }
        }
        if (best != null) {
            clan.setRole(best, Role.LEADER);
        }
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String base = "clans." + c.getId();
            y.set(base + ".name", c.getName());
            y.set(base + ".tag", c.getTag());
            y.set(base + ".color", c.getColor() == null ? null : c.getColor().getKey());
            y.set(base + ".created", c.getCreated());
            for (Map.Entry<UUID, Clan.Member> e : c.members().entrySet()) {
                String mb = base + ".members." + e.getKey();
                y.set(mb + ".role", e.getValue().getRole().name());
                y.set(mb + ".name", e.getValue().getName());
            }
        }
        for (Map.Entry<UUID, RgbMode> e : rgb.entrySet()) {
            y.set("players." + e.getKey(), e.getValue().shortName());
        }
        y.set("peace", new ArrayList<>(peace));
        return y.saveToString();
    }

    /** Serialises on the calling (main) thread and writes the file asynchronously. */
    public void saveAsync() {
        final String data = serialize();
        final long seq = saveSeq.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (ioLock) {
                if (seq < lastWritten) {
                    return; // a newer snapshot has already been written
                }
                lastWritten = seq;
                write(data);
            }
        });
    }

    /** Blocking save, used on shutdown. */
    public void saveNow() {
        final String data = serialize();
        final long seq = saveSeq.incrementAndGet();
        synchronized (ioLock) {
            lastWritten = seq;
            write(data);
        }
    }

    private void write(String data) {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder.");
                return;
            }
            Path target = dataFile().toPath();
            Path tmp = target.resolveSibling("data.yml.tmp");
            Files.writeString(tmp, data, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }
}
