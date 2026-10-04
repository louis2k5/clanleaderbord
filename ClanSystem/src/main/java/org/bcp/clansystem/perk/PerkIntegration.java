package org.bcp.clansystem.perk;

import net.kyori.adventure.text.Component;
import org.bcp.clansystem.ClanSystem;
import org.bcp.clansystem.model.RgbMode;
import org.bcp.clansystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Makes the two new clan tag perks compatible with the existing PerkSystem plugin without touching it:
 * <ul>
 *   <li>Permissions follow its scheme: perks.clan_gradient and perks.clan_breathing (LuckPerms).</li>
 *   <li>The perks are injected into the free slots of the first page of the /perks menu and can be toggled there.</li>
 *   <li>Own vouchers (/clan perkvoucher) work like the PerkSystem vouchers: right click to unlock via LuckPerms.</li>
 * </ul>
 */
public final class PerkIntegration implements Listener {

    private static final String PERKSYSTEM = "PerkSystem";
    private static final Set<String> DURATIONS =
            Set.of("permanent", "15m", "30m", "1h", "3h", "6h", "12h", "24h", "7d", "30d");

    private final ClanSystem plugin;
    private final NamespacedKey perkItemKey;
    private final NamespacedKey voucherKey;
    private final NamespacedKey durationKey;

    public PerkIntegration(ClanSystem plugin) {
        this.plugin = plugin;
        this.perkItemKey = new NamespacedKey(plugin, "perk_item");
        this.voucherKey = new NamespacedKey(plugin, "perk_voucher");
        this.durationKey = new NamespacedKey(plugin, "perk_duration");
    }

    public static Set<String> durations() {
        return DURATIONS;
    }

    // ------------------------------------------------------------------ perk menu detection

    private Plugin perkSystem() {
        Plugin p = Bukkit.getPluginManager().getPlugin(PERKSYSTEM);
        return p != null && p.isEnabled() ? p : null;
    }

    private String perksTitle(Plugin perks) {
        String raw = perks.getConfig().getString("gui.title", "&8&lPerks");
        return Text.stripLegacy(raw == null ? "&8&lPerks" : raw);
    }

    /** True if the view is page 1 of the PerkSystem menu. */
    private boolean isPerksMenu(InventoryView view) {
        if (!plugin.getConfig().getBoolean("perks-integration.enabled", true)) {
            return false;
        }
        Plugin perks = perkSystem();
        if (perks == null) {
            return false;
        }
        Inventory top = view.getTopInventory();
        if (top.getSize() != 54) {
            return false;
        }
        return Text.strip(view.title()).equals(perksTitle(perks));
    }

    private Material statusMaterial(String key, Material fallback) {
        Plugin perks = perkSystem();
        if (perks == null) {
            return fallback;
        }
        String name = perks.getConfig().getString("gui." + key);
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null ? fallback : m;
    }

    // ------------------------------------------------------------------ injecting into the menu

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isPerksMenu(event.getView())) {
            return;
        }
        inject(event.getView().getTopInventory(), player);
    }

    private void inject(Inventory inv, Player player) {
        List<Integer> slots = plugin.getConfig().getIntegerList("perks-integration.slots");
        if (slots.isEmpty()) {
            slots = List.of(33, 34);
        }
        RgbMode[] perks = {RgbMode.GRADIENT, RgbMode.BREATHING};
        RgbMode current = plugin.clans().getRgb(player.getUniqueId());

        for (int i = 0; i < perks.length && i < slots.size(); i++) {
            int slot = slots.get(i);
            int statusSlot = slot + 9;
            if (slot < 0 || statusSlot >= inv.getSize()) {
                continue;
            }
            if (!isFree(inv.getItem(slot)) || !isFree(inv.getItem(statusSlot))) {
                continue; // slot is used by something else (e.g. a future PerkSystem version)
            }
            RgbMode perk = perks[i];
            boolean owned = player.hasPermission(perk.permission());
            boolean enabled = owned && current == perk;
            inv.setItem(slot, buildIcon(perk, owned, enabled));
            inv.setItem(statusSlot, buildStatus(perk, owned, enabled));
        }
    }

    /** A slot is free if it holds nothing, the PerkSystem filler pane, or one of our own items. */
    private boolean isFree(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }
        if (item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return true;
        }
        return readPerk(item) != null;
    }

    private ItemStack buildIcon(RgbMode perk, boolean owned, boolean enabled) {
        ItemStack item = new ItemStack(owned ? perk.getIcon() : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item((owned ? "&6&l" : "&c&l") + perk.getDisplayName()));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.item("&7" + perk.getDescription()));
        lore.add(Component.text(""));
        lore.add(Text.item("&7Status: " + (owned ? (enabled ? "&aEnabled" : "&cDisabled") : "&4Locked")));
        lore.add(Component.text(""));
        lore.add(Text.item(owned ? "&e» Click to toggle" : "&c» Not unlocked"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(perkItemKey, PersistentDataType.STRING, perk.shortName());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStatus(RgbMode perk, boolean owned, boolean enabled) {
        Material mat = owned
                ? (enabled ? statusMaterial("status-on", Material.LIME_DYE) : statusMaterial("status-off", Material.GRAY_DYE))
                : statusMaterial("status-locked", Material.RED_DYE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(enabled ? "&a&lON" : (owned ? "&7&lOFF" : "&4&lLOCKED")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(perkItemKey, PersistentDataType.STRING, perk.shortName());
        item.setItemMeta(meta);
        return item;
    }

    private RgbMode readPerk(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(perkItemKey, PersistentDataType.STRING);
        return value == null ? null : RgbMode.fromId(value);
    }

    // Runs after PerkSystem's own HIGHEST handler (which cancels all clicks in its menu).
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        if (event.getClickedInventory() == null || event.getClickedInventory() != view.getTopInventory()) {
            return;
        }
        RgbMode perk = readPerk(event.getCurrentItem());
        if (perk == null || !isPerksMenu(view)) {
            return;
        }
        event.setCancelled(true);

        if (!player.hasPermission(perk.permission())) {
            plugin.err(player, "You don't own this perk!");
            return;
        }
        toggle(player, perk);
        inject(view.getTopInventory(), player);
    }

    /** Toggles a perk for a player. Only one animated tag can be active at a time. */
    public void toggle(Player player, RgbMode perk) {
        RgbMode current = plugin.clans().getRgb(player.getUniqueId());
        if (current == perk) {
            plugin.clans().setRgb(player.getUniqueId(), RgbMode.NONE);
            plugin.msg(player, "Perk &e" + perk.getDisplayName() + " &cdisabled&7.");
        } else {
            plugin.clans().setRgb(player.getUniqueId(), perk);
            plugin.msg(player, "Perk &e" + perk.getDisplayName() + " &aenabled&7."
                    + (plugin.clans().getClanOf(player.getUniqueId()) == null ? " It becomes visible once you are in a clan." : ""));
        }
        plugin.display().refresh(player);
    }

    // ------------------------------------------------------------------ vouchers

    /** /clan perkvoucher &lt;perk&gt; [duration] [player] */
    public void giveVoucher(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.err(sender, "Usage: /clan perkvoucher <gradient|breathing> [permanent|15m|30m|1h|3h|6h|12h|24h|7d|30d] [player]");
            return;
        }
        RgbMode perk = RgbMode.fromId(args[1]);
        if (perk == null) {
            plugin.err(sender, "Unknown perk. Use gradient or breathing.");
            return;
        }
        String duration = args.length >= 3 ? args[2].toLowerCase() : "permanent";
        if (!DURATIONS.contains(duration)) {
            plugin.err(sender, "Invalid duration. Use: permanent, 15m, 30m, 1h, 3h, 6h, 12h, 24h, 7d, 30d");
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            plugin.err(sender, "Specify a player: /clan perkvoucher <perk> <duration> <player>");
            return;
        }
        if (target == null) {
            plugin.err(sender, "That player is not online.");
            return;
        }
        final Player recipient = target;
        for (ItemStack left : recipient.getInventory().addItem(createVoucher(perk, duration)).values()) {
            recipient.getWorld().dropItemNaturally(recipient.getLocation(), left);
        }
        plugin.msg(sender, "Created a voucher for &e" + perk.getDisplayName() + " &7("
                + duration + ") for &e" + recipient.getName() + "&7.");
    }

    private ItemStack createVoucher(RgbMode perk, String duration) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("voucher.item", "PAPER"));
        ItemStack item = new ItemStack(mat == null ? Material.PAPER : mat);
        ItemMeta meta = item.getItemMeta();
        boolean permanent = duration.equals("permanent");
        meta.displayName(Text.item("&6&lPerk Voucher &8• &e" + perk.getDisplayName()));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(" "));
        lore.add(Text.item("&7Right click to unlock the perk"));
        lore.add(Text.item("&e" + perk.getDisplayName() + (permanent ? " &7permanently." : " &7for &b" + duration + "&7.")));
        lore.add(Component.text(" "));
        lore.add(Text.item("&8» &eRight click to redeem"));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(voucherKey, PersistentDataType.STRING, perk.shortName());
        pdc.set(durationKey, PersistentDataType.STRING, duration);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onVoucherUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String perkValue = pdc.get(voucherKey, PersistentDataType.STRING);
        if (perkValue == null) {
            return;
        }
        event.setCancelled(true);

        final Player player = event.getPlayer();
        RgbMode perk = RgbMode.fromId(perkValue);
        if (perk == null) {
            return;
        }
        if (player.hasPermission(perk.permission())) {
            plugin.err(player, "You already own this perk.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.err(player, "LuckPerms is not installed, this voucher cannot be redeemed.");
            return;
        }
        String duration = pdc.getOrDefault(durationKey, PersistentDataType.STRING, "permanent");
        if (!DURATIONS.contains(duration)) {
            plugin.err(player, "This voucher has an invalid duration.");
            return;
        }

        String command = duration.equals("permanent")
                ? "lp user " + player.getName() + " permission set " + perk.permission() + " true"
                : "lp user " + player.getName() + " permission settemp " + perk.permission() + " true " + duration;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item.getAmount() <= 0 ? null : item);

        plugin.msg(player, "You unlocked the perk &e" + perk.getDisplayName()
                + (duration.equals("permanent") ? " &apermanently" : " &afor &b" + duration) + "&7.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.display().refresh(player), 10L);
    }
}
