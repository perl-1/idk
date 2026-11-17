package dev.saikomc;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager implements Listener {
    static class ShopItem {
        Material mat;
        long buy;
        long sell;
    }

    static class Category {
        String id;
        String name;
        Material icon;
        List<ShopItem> items = new ArrayList<>();
    }

    static class ShopHolder implements InventoryHolder {
        String cat; // null = main menu
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final SaikoMC p;
    private final Map<String, Category> cats = new LinkedHashMap<>();
    private final Map<Material, Long> sellPrices = new HashMap<>();

    public ShopManager(SaikoMC p) {
        this.p = p;
    }

    public void load() {
        cats.clear();
        sellPrices.clear();
        ConfigurationSection root = p.getConfig().getConfigurationSection("shop.categories");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(id);
            if (cs == null) continue;
            Category c = new Category();
            c.id = id;
            c.name = cs.getString("name", "&f" + id);
            Material icon = Material.matchMaterial(cs.getString("icon", "CHEST"));
            c.icon = icon == null ? Material.CHEST : icon;
            ConfigurationSection items = cs.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    Material m = Material.matchMaterial(key);
                    if (m == null || !m.isItem()) {
                        p.getLogger().warning("Unknown shop material: " + key);
                        continue;
                    }
                    ShopItem it = new ShopItem();
                    it.mat = m;
                    it.buy = items.getLong(key + ".buy", 0);
                    it.sell = items.getLong(key + ".sell", 0);
                    c.items.add(it);
                    if (it.sell > 0) sellPrices.put(m, it.sell);
                }
            }
            cats.put(id, c);
        }
    }

    // ---------- GUI ----------

    public void openMain(Player pl) {
        ShopHolder h = new ShopHolder();
        int size = Math.min(54, Math.max(9, ((cats.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(h, size, p.color(p.getConfig().getString("shop.title", "&6&lShop")));
        h.inv = inv;
        int i = 0;
        for (Category c : cats.values()) {
            if (i >= size) break;
            ItemStack st = new ItemStack(c.icon);
            ItemMeta m = st.getItemMeta();
            m.setDisplayName(p.color(c.name));
            List<String> lore = new ArrayList<>();
            lore.add(p.color("&7Click to browse &8(" + c.items.size() + " items)"));
            m.setLore(lore);
            m.addItemFlags(ItemFlag.values());
            st.setItemMeta(m);
            inv.setItem(i++, st);
        }
        pl.openInventory(inv);
    }

    public void openCategory(Player pl, Category c) {
        ShopHolder h = new ShopHolder();
        h.cat = c.id;
        Inventory inv = Bukkit.createInventory(h, 54, p.color(c.name));
        h.inv = inv;
        for (int i = 0; i < c.items.size() && i < 45; i++) {
            ShopItem it = c.items.get(i);
            ItemStack st = new ItemStack(it.mat);
            ItemMeta m = st.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (it.buy > 0) {
                lore.add(p.color("&aBuy: &6" + p.fmt(it.buy)));
                lore.add(p.color("&8 Left-click: 1  |  Shift+Left: 64"));
            }
            if (it.sell > 0) {
                lore.add(p.color("&cSell: &6" + p.fmt(it.sell)));
                lore.add(p.color("&8 Right-click: 1  |  Shift+Right: all"));
            }
            m.setLore(lore);
            m.addItemFlags(ItemFlag.values());
            st.setItemMeta(m);
            inv.setItem(i, st);
        }
        inv.setItem(45, named(Material.GOLD_NUGGET, "&6Balance: " + p.fmt(p.eco().get(pl.getUniqueId()))));
        inv.setItem(49, named(Material.BARRIER, "&cBack"));
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
        if (!(top.getHolder() instanceof ShopHolder)) return;
        ShopHolder h = (ShopHolder) top.getHolder();
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player pl = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        if (h.cat == null) {
            List<Category> list = new ArrayList<>(cats.values());
            if (slot < list.size()) {
                Category c = list.get(slot);
                Bukkit.getScheduler().runTask(p, () -> openCategory(pl, c));
            }
            return;
        }

        Category c = cats.get(h.cat);
        if (c == null) return;
        if (slot == 49) {
            Bukkit.getScheduler().runTask(p, () -> openMain(pl));
            return;
        }
        if (slot >= 45 || slot >= c.items.size()) return;
        ShopItem it = c.items.get(slot);

        switch (e.getClick()) {
            case LEFT:
                buy(pl, it, 1);
                break;
            case SHIFT_LEFT:
                buy(pl, it, 64);
                break;
            case RIGHT:
                sell(pl, it, 1);
                break;
            case SHIFT_RIGHT:
                sell(pl, it, Integer.MAX_VALUE);
                break;
            default:
                return;
        }
        top.setItem(45, named(Material.GOLD_NUGGET, "&6Balance: " + p.fmt(p.eco().get(pl.getUniqueId()))));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopHolder) e.setCancelled(true);
    }

    // ---------- buying / selling ----------

    private void buy(Player pl, ShopItem it, int qty) {
        if (it.buy <= 0) {
            p.send(pl, "&cThis item can't be bought.");
            return;
        }
        long total = it.buy * qty;
        if (!p.eco().take(pl.getUniqueId(), total)) {
            p.send(pl, "&cYou need &6" + p.fmt(total) + " &cfor that.");
            return;
        }
        Map<Integer, ItemStack> left = pl.getInventory().addItem(new ItemStack(it.mat, qty));
        int notGiven = 0;
        for (ItemStack l : left.values()) notGiven += l.getAmount();
        if (notGiven > 0) p.eco().add(pl.getUniqueId(), it.buy * notGiven);
        int given = qty - notGiven;
        if (given > 0) {
            p.send(pl, "&aBought &f" + given + "x " + pretty(it.mat) + " &afor &6" + p.fmt(it.buy * given) + "&a.");
        } else {
            p.send(pl, "&cYour inventory is full.");
        }
    }

    private void sell(Player pl, ShopItem it, int qty) {
        if (it.sell <= 0) {
            p.send(pl, "&cThis item can't be sold.");
            return;
        }
        int have = count(pl, it.mat);
        if (have == 0) {
            p.send(pl, "&cYou don't have any " + pretty(it.mat) + ".");
            return;
        }
        int n = Math.min(have, qty);
        removePlain(pl, it.mat, n);
        long total = it.sell * n;
        p.eco().add(pl.getUniqueId(), total);
        p.send(pl, "&aSold &f" + n + "x " + pretty(it.mat) + " &afor &6" + p.fmt(total) + "&a.");
    }

    public void sellHand(Player pl) {
        ItemStack hand = pl.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            p.send(pl, "&cHold the item you want to sell.");
            return;
        }
        Long price = sellPrices.get(hand.getType());
        if (price == null || hand.hasItemMeta()) {
            p.send(pl, "&cThat item can't be sold to the server.");
            return;
        }
        long total = price * hand.getAmount();
        int amt = hand.getAmount();
        pl.getInventory().setItemInMainHand(null);
        p.eco().add(pl.getUniqueId(), total);
        p.send(pl, "&aSold &f" + amt + "x " + pretty(hand.getType()) + " &afor &6" + p.fmt(total) + "&a.");
    }

    public void sellAll(Player pl) {
        ItemStack[] contents = pl.getInventory().getStorageContents();
        long total = 0;
        int items = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s == null || s.hasItemMeta()) continue;
            Long price = sellPrices.get(s.getType());
            if (price == null) continue;
            total += price * s.getAmount();
            items += s.getAmount();
            contents[i] = null;
        }
        if (items == 0) {
            p.send(pl, "&cNothing sellable in your inventory.");
            return;
        }
        pl.getInventory().setStorageContents(contents);
        p.eco().add(pl.getUniqueId(), total);
        p.send(pl, "&aSold &f" + items + " &aitems for &6" + p.fmt(total) + "&a.");
    }

    private boolean plain(ItemStack s, Material m) {
        return s != null && s.getType() == m && !s.hasItemMeta();
    }

    private int count(Player pl, Material m) {
        int n = 0;
        for (ItemStack s : pl.getInventory().getStorageContents()) {
            if (plain(s, m)) n += s.getAmount();
        }
        return n;
    }

    private void removePlain(Player pl, Material m, int n) {
        ItemStack[] c = pl.getInventory().getStorageContents();
        for (int i = 0; i < c.length && n > 0; i++) {
            ItemStack s = c[i];
            if (plain(s, m)) {
                int t = Math.min(n, s.getAmount());
                n -= t;
                if (t == s.getAmount()) c[i] = null;
                else s.setAmount(s.getAmount() - t);
            }
        }
        pl.getInventory().setStorageContents(c);
    }

    private String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public void worth(Player pl) {
        ItemStack hand = pl.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            p.send(pl, "&cHold an item to check its worth.");
            return;
        }
        Long price = sellPrices.get(hand.getType());
        if (price == null || hand.hasItemMeta()) {
            p.send(pl, "&7The server doesn't buy that item.");
            return;
        }
        p.send(pl, "&f" + pretty(hand.getType()) + " &7sells for &6" + p.fmt(price) + " &7each (&6"
                + p.fmt(price * hand.getAmount()) + " &7for your stack).");
    }
}
