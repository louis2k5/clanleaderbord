package org.bcp.clansystem.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bcp.clansystem.ClanSystem;
import org.bcp.clansystem.model.Clan;
import org.bcp.clansystem.model.Role;
import org.bcp.clansystem.model.TagColor;
import org.bcp.clansystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small 27 slot GUI where the clan leader picks one of the 7 tag colours. */
public final class ColorGui implements Listener {

    private static final int SIZE = 27;
    private static final int[] COLOR_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int RESET_SLOT = 22;

    public static final class Holder implements InventoryHolder {
        private final UUID clanId;
        private Inventory inventory;

        Holder(UUID clanId) {
            this.clanId = clanId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ClanSystem plugin;

    public ColorGui(ClanSystem plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Holder holder = new Holder(clan.getId());
        Inventory inv = Bukkit.createInventory(holder, SIZE, Text.c("&8Clan Tag Color"));
        holder.inventory = inv;
        fill(inv, player, clan);
        player.openInventory(inv);
    }

    private void fill(Inventory inv, Player player, Clan clan) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        TagColor[] colors = TagColor.values();
        for (int i = 0; i < colors.length && i < COLOR_SLOTS.length; i++) {
            TagColor color = colors[i];
            boolean allowed = player.hasPermission(color.permission());
            boolean selected = clan.getColor() == color;

            ItemStack item = new ItemStack(allowed ? color.getIcon() : Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(color.getDisplayName(), color.getColor(), TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("&7Preview: &8[")
                    .append(Component.text(clan.getTag(), color.getColor()).decoration(TextDecoration.ITALIC, false))
                    .append(Text.item("&8]")));
            lore.add(Component.text(" "));
            if (!allowed) {
                lore.add(Text.item("&c» You don't have access to this color"));
            } else if (selected) {
                lore.add(Text.item("&a» Currently selected"));
            } else {
                lore.add(Text.item("&e» Click to select"));
            }
            meta.lore(lore);
            if (selected) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
            inv.setItem(COLOR_SLOTS[i], item);
        }

        ItemStack reset = new ItemStack(Material.WHITE_DYE);
        ItemMeta rm = reset.getItemMeta();
        rm.displayName(Component.text("Default (White)", NamedTextColor.WHITE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> rl = new ArrayList<>();
        rl.add(Text.item("&7Preview: &8[&f" + clan.getTag() + "&8]"));
        rl.add(Component.text(" "));
        rl.add(Text.item(clan.getColor() == null ? "&a» Currently selected" : "&e» Click to reset"));
        rm.lore(rl);
        if (clan.getColor() == null) {
            rm.setEnchantmentGlintOverride(true);
        }
        reset.setItemMeta(rm);
        inv.setItem(RESET_SLOT, reset);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        Clan clan = plugin.clans().getClan(holder.clanId);
        if (clan == null || clan.roleOf(player.getUniqueId()) != Role.LEADER) {
            player.closeInventory();
            plugin.err(player, "Only the clan leader can change the tag color.");
            return;
        }

        int slot = event.getRawSlot();
        if (slot == RESET_SLOT) {
            plugin.clans().setColor(clan, null);
            plugin.msg(player, "Your clan tag color was reset to the default.");
        } else {
            int index = -1;
            for (int i = 0; i < COLOR_SLOTS.length; i++) {
                if (COLOR_SLOTS[i] == slot) {
                    index = i;
                    break;
                }
            }
            if (index < 0 || index >= TagColor.values().length) {
                return;
            }
            TagColor color = TagColor.values()[index];
            if (!player.hasPermission(color.permission())) {
                plugin.err(player, "You don't have access to the " + color.getDisplayName().toLowerCase() + " color.");
                return;
            }
            plugin.clans().setColor(clan, color);
            plugin.msg(player, "Your clan tag color is now " + color.getDisplayName().toLowerCase() + ".");
        }
        plugin.display().refreshClan(clan);
        fill(holder.inventory, player, clan);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
