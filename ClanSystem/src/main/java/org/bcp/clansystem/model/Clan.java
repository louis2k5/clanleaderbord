package org.bcp.clansystem.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Clan {

    public static final class Member {
        private volatile Role role;
        private volatile String name;

        public Member(Role role, String name) {
            this.role = role;
            this.name = name;
        }

        public Role getRole() {
            return role;
        }

        public String getName() {
            return name;
        }
    }

    private final UUID id;
    private final long created;
    private volatile String name;
    private volatile String tag;
    private volatile TagColor color;
    private final Map<UUID, Member> members = new ConcurrentHashMap<>();

    public Clan(UUID id, String name, String tag, long created) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.created = created;
    }

    public UUID getId() {
        return id;
    }

    public long getCreated() {
        return created;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    /** Selected tag colour, or null for the default (white). */
    public TagColor getColor() {
        return color;
    }

    public void setColor(TagColor color) {
        this.color = color;
    }

    public Map<UUID, Member> members() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public void putMember(UUID uuid, Role role, String playerName) {
        members.put(uuid, new Member(role, playerName));
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public Role roleOf(UUID uuid) {
        Member m = members.get(uuid);
        return m == null ? null : m.getRole();
    }

    public void setRole(UUID uuid, Role role) {
        Member m = members.get(uuid);
        if (m != null) {
            m.role = role;
        }
    }

    public void updateName(UUID uuid, String playerName) {
        Member m = members.get(uuid);
        if (m != null) {
            m.name = playerName;
        }
    }

    public String nameOf(UUID uuid) {
        Member m = members.get(uuid);
        return m == null ? "Unknown" : m.getName();
    }

    public UUID findMemberByName(String playerName) {
        for (Map.Entry<UUID, Member> e : members.entrySet()) {
            if (e.getValue().getName().equalsIgnoreCase(playerName)) {
                return e.getKey();
            }
        }
        return null;
    }

    public UUID leaderId() {
        for (Map.Entry<UUID, Member> e : members.entrySet()) {
            if (e.getValue().getRole() == Role.LEADER) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Members of one role, sorted alphabetically by name. */
    public List<UUID> membersWithRole(Role role) {
        List<Map.Entry<UUID, Member>> list = new ArrayList<>();
        for (Map.Entry<UUID, Member> e : members.entrySet()) {
            if (e.getValue().getRole() == role) {
                list.add(e);
            }
        }
        list.sort(Comparator.comparing((Map.Entry<UUID, Member> e) -> e.getValue().getName().toLowerCase(Locale.ROOT)));
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, Member> e : list) {
            result.add(e.getKey());
        }
        return result;
    }
}
