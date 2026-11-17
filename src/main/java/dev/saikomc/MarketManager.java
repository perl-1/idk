package dev.saikomc;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Player-to-player market: list the item in your hand, others buy it from a GUI. */
public class MarketManager implements Listener {
    static class Listing {
        int id;
        UUID seller;
        String sellerName;
        long price;
        ItemStack item;
    }

    static class MarketHolder implements InventoryHolder {
        int page;
        final Map<Integer, Integer> slots = new HashMap<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final SaikoMC p;
    private final File file;
    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private int nextId = 1;

    public MarketManager(SaikoMC p) {
        this.p = p;
        this.file = new File(p.getDataFolder(), "market.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next", 1);
        ConfigurationSection s = y.getConfigurationSection("listings");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                Listing l = new Listing();
                l.id = Integer.parseInt(k);
                l.seller = UUID.fromString(s.getString(k + ".seller", ""));
                l.sellerName = s.getString(k + ".name", "?");
                l.price = s.getLong(k + ".price");
                l.item = s.getItemStack(k + ".item");
                if (l.item != null) listings.put(l.id, l);
            } catch (IllegalArgumentException ex) {
                p.getLogger().warning("Skipping broken market listing " + k);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", nextId);
        for (Listing l : listings.values()) {
            String b = "listings." + l.id;
            y.set(b + ".seller", l.seller.toString());
            y.set(b + ".name", l.sellerName);
            y.set(b + ".price", l.price);
            y.set(b + ".item", l.item);
        }
        try {
            p.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            p.getLogger().severe("Could not save market.yml: " + ex.getMessage());
        }
    }

    public void sell(Player pl, long price) {
        ItemStack hand = pl.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            p.send(pl, "&cHold the item you want to list.");
            return;
        }
        int max = p.getConfig().getInt("market.max-listings-per-player", 5);
        long mine = listings.values().stream().filter(l -> l.seller.equals(pl.getUniqueId())).count();
        if (mine >= max) {
            p.send(pl, "&cYou can only have &f" + max + " &clistings at once.");
            return;
        }
        Listing l = new Listing();
        l.id = nextId++;
        l.seller = pl.getUniqueId();
        l.sellerName = pl.getName();
        l.price = price;
        l.item = hand.clone();
        pl.getInventory().setItemInMainHand(null);
        listings.put(l.id, l);
        save();
        p.send(pl, "&aListed &f" + l.item.getAmount() + "x " + l.item.getType().name().toLowerCase().replace('_', ' ')
                + " &afor &6" + p.fmt(price) + "&a. Click your own listing in &f/market &ato take it back.");
    }

    public void open(Player pl, int page) {
        List<Listing> all = new ArrayList<>(listings.values());
        int pages = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        page = Math.max(0, Math.min(page, pages - 1));

        MarketHolder h = new MarketHolder();
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, p.color("&6&lMarket &8(" + (page + 1) + "/" + pages + ")"));
        h.inv = inv;

        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= all.size()) break;
            Listing l = all.get(idx);
            ItemStack d = l.item.clone();
            ItemMeta m = d.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(p.color("&7Seller: &f" + l.sellerName));
            lore.add(p.color("&7Price: &6" + p.fmt(l.price)));
            lore.add(p.color(l.seller.equals(pl.getUniqueId()) ? "&eClick to take back" : "&aClick to buy"));
            m.setLore(lore);
            d.setItemMeta(m);
            inv.setItem(i, d);
            h.slots.put(i, l.id);
        }
        if (page > 0) inv.setItem(45, named(Material.ARROW, "&ePrevious page"));
        if (page < pages - 1) inv.setItem(53, named(Material.ARROW, "&eNext page"));
        inv.setItem(49, named(Material.PAPER, "&7" + all.size() + " listings - use &f/market sell <price>"));
        pl.openInventory(inv);
    }

    private ItemStack named(Material mat, String name) {
        ItemStack st = new ItemStack(mat);
        ItemMeta m = st.getItemMeta();
        m.setDisplayName(p.color(name));
        st.setItemMeta(m);
        return st;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MarketHolder)) return;
        MarketHolder h = (MarketHolder) top.getHolder();
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player pl = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        if (slot == 45) {
            Bukkit.getScheduler().runTask(p, () -> open(pl, h.page - 1));
            return;
        }
        if (slot == 53) {
            Bukkit.getScheduler().runTask(p, () -> open(pl, h.page + 1));
            return;
        }
        Integer id = h.slots.get(slot);
        if (id == null) return;
        Listing l = listings.get(id);
        if (l == null) {
            Bukkit.getScheduler().runTask(p, () -> open(pl, h.page));
            return;
        }

        if (l.seller.equals(pl.getUniqueId())) {
            listings.remove(id);
            give(pl, l.item.clone());
            save();
            p.send(pl, "&eListing removed and item returned.");
        } else {
            if (!p.eco().take(pl.getUniqueId(), l.price)) {
                p.send(pl, "&cYou need &6" + p.fmt(l.price) + "&c.");
                return;
            }
            listings.remove(id);
            long tax = l.price * p.getConfig().getLong("market.tax-percent", 5) / 100;
            p.eco().add(l.seller, l.price - tax);
            give(pl, l.item.clone());
            save();
            p.send(pl, "&aYou bought the item for &6" + p.fmt(l.price) + "&a.");
            Player seller = Bukkit.getPlayer(l.seller);
            if (seller != null) {
                p.send(seller, "&e" + pl.getName() + " &abought your item and you received &6" + p.fmt(l.price - tax)
                        + " &7(tax " + p.fmt(tax) + ")");
            }
        }
        Bukkit.getScheduler().runTask(p, () -> open(pl, h.page));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MarketHolder) e.setCancelled(true);
    }

    private void give(Player pl, ItemStack item) {
        Map<Integer, ItemStack> left = pl.getInventory().addItem(item);
        for (ItemStack s : left.values()) {
            pl.getWorld().dropItemNaturally(pl.getLocation(), s);
        }
    }
}
