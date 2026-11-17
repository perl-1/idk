package dev.saikomc;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Daily rewards, bounties, banknotes, lottery and mob-kill rewards. */
public class Extras implements Listener {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private final SaikoMC p;
    private final File file;
    private final Random rnd = new Random();
    private final NamespacedKey noteKey;
    private YamlConfiguration yml = new YamlConfiguration();
    private final Map<UUID, Long> bounties = new HashMap<>();
    private final Map<UUID, Integer> tickets = new HashMap<>();
    private BukkitTask lotteryTask;
    private long nextDraw;

    public Extras(SaikoMC p) {
        this.p = p;
        this.file = new File(p.getDataFolder(), "extras.yml");
        this.noteKey = new NamespacedKey(p, "banknote");
    }

    public void load() {
        yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection b = yml.getConfigurationSection("bounties");
        if (b != null) {
            for (String k : b.getKeys(false)) {
                try {
                    bounties.put(UUID.fromString(k), b.getLong(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection t = yml.getConfigurationSection("lottery.tickets");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                try {
                    tickets.put(UUID.fromString(k), t.getInt(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        yml.set("bounties", null);
        for (Map.Entry<UUID, Long> e : bounties.entrySet()) yml.set("bounties." + e.getKey(), e.getValue());
        yml.set("lottery.tickets", null);
        for (Map.Entry<UUID, Integer> e : tickets.entrySet()) yml.set("lottery.tickets." + e.getKey(), e.getValue());
        try {
            p.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            p.getLogger().severe("Could not save extras.yml: " + ex.getMessage());
        }
    }

    private String time(long ms) {
        long m = ms / 60000;
        long h = m / 60;
        m = m % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    // ---------- /daily ----------

    public void daily(Player pl) {
        String base = "daily." + pl.getUniqueId();
        long now = System.currentTimeMillis();
        long last = yml.getLong(base + ".last", 0);
        if (now - last < DAY) {
            p.send(pl, "&cYou already claimed today. Come back in &f" + time(DAY - (now - last)) + "&c.");
            return;
        }
        int streak = yml.getInt(base + ".streak", 0);
        if (now - last > 2 * DAY) streak = 0;
        streak++;
        int maxStreak = p.getConfig().getInt("daily.max-streak-days", 14);
        long reward = p.getConfig().getLong("daily.base", 100)
                + (long) Math.min(streak - 1, maxStreak) * p.getConfig().getLong("daily.streak-bonus", 25);
        p.eco().add(pl.getUniqueId(), reward);
        yml.set(base + ".last", now);
        yml.set(base + ".streak", streak);
        save();
        p.send(pl, "&aDaily reward: &6" + p.fmt(reward) + " &7(streak: &f" + streak + " &7day" + (streak == 1 ? "" : "s") + ")");
    }

    // ---------- /bounty ----------

    public void placeBounty(Player pl, java.util.UUID target, long amount) {
        long min = p.getConfig().getLong("bounty.min-amount", 100);
        if (target.equals(pl.getUniqueId())) {
            p.send(pl, "&cYou can't place a bounty on yourself.");
            return;
        }
        if (amount < min) {
            p.send(pl, "&cMinimum bounty is &6" + p.fmt(min) + "&c.");
            return;
        }
        if (!p.eco().take(pl.getUniqueId(), amount)) {
            p.send(pl, "&cYou don't have enough money.");
            return;
        }
        long total = bounties.merge(target, amount, Long::sum);
        save();
        String tn = p.eco().name(target);
        p.broadcast("&e" + pl.getName() + " &7placed a bounty on &c" + tn + "&7! Total: &6" + p.fmt(total));
    }

    public void listBounties(Player pl) {
        if (bounties.isEmpty()) {
            p.send(pl, "&7There are no active bounties.");
            return;
        }
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(bounties.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        p.send(pl, "&6Active bounties:");
        int i = 0;
        for (Map.Entry<UUID, Long> e : list) {
            if (i++ >= 10) break;
            pl.sendMessage(p.color(" &c" + p.eco().name(e.getKey()) + " &8- &6" + p.fmt(e.getValue())));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        Long bounty = bounties.remove(victim.getUniqueId());
        if (bounty == null) return;
        p.eco().add(killer.getUniqueId(), bounty);
        save();
        p.broadcast("&e" + killer.getName() + " &7collected the &6" + p.fmt(bounty) + " &7bounty on &c" + victim.getName() + "&7!");
    }

    // ---------- banknotes ----------

    public void withdraw(Player pl, long amount) {
        if (!p.eco().take(pl.getUniqueId(), amount)) {
            p.send(pl, "&cYou don't have enough money.");
            return;
        }
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta m = note.getItemMeta();
        m.setDisplayName(p.color("&a&lBanknote &6" + p.fmt(amount)));
        List<String> lore = new ArrayList<>();
        lore.add(p.color("&7Right-click to redeem"));
        lore.add(p.color("&8Issued by " + pl.getName()));
        m.setLore(lore);
        m.getPersistentDataContainer().set(noteKey, PersistentDataType.LONG, amount);
        note.setItemMeta(m);
        Map<Integer, ItemStack> left = pl.getInventory().addItem(note);
        for (ItemStack s : left.values()) pl.getWorld().dropItemNaturally(pl.getLocation(), s);
        p.send(pl, "&aCreated a banknote worth &6" + p.fmt(amount) + "&a.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        Long value = item.getItemMeta().getPersistentDataContainer().get(noteKey, PersistentDataType.LONG);
        if (value == null) return;
        e.setCancelled(true);
        Player pl = e.getPlayer();
        item.setAmount(item.getAmount() - 1);
        p.eco().add(pl.getUniqueId(), value);
        p.send(pl, "&aRedeemed a banknote for &6" + p.fmt(value) + "&a.");
    }

    // ---------- lottery ----------

    public void startLottery() {
        if (lotteryTask != null) lotteryTask.cancel();
        long minutes = Math.max(1, p.getConfig().getLong("lottery.draw-interval-minutes", 60));
        long ticks = minutes * 60 * 20;
        nextDraw = System.currentTimeMillis() + minutes * 60_000;
        lotteryTask = Bukkit.getScheduler().runTaskTimer(p, this::draw, ticks, ticks);
    }

    public void buyTickets(Player pl, int n) {
        long price = p.getConfig().getLong("lottery.ticket-price", 100);
        int max = p.getConfig().getInt("lottery.max-tickets-per-player", 20);
        int have = tickets.getOrDefault(pl.getUniqueId(), 0);
        if (n < 1 || have + n > max) {
            p.send(pl, "&cYou can hold at most &f" + max + " &ctickets (you have &f" + have + "&c).");
            return;
        }
        if (!p.eco().take(pl.getUniqueId(), price * n)) {
            p.send(pl, "&cYou need &6" + p.fmt(price * n) + "&c.");
            return;
        }
        tickets.put(pl.getUniqueId(), have + n);
        save();
        p.send(pl, "&aBought &f" + n + " &alottery ticket(s) for &6" + p.fmt(price * n) + "&a.");
    }

    public void lotteryInfo(Player pl) {
        long price = p.getConfig().getLong("lottery.ticket-price", 100);
        long sum = 0;
        for (int v : tickets.values()) sum += v;
        p.send(pl, "&6Lottery &7- pot: &6" + p.fmt(sum * price) + " &7| tickets sold: &f" + sum
                + " &7| yours: &f" + tickets.getOrDefault(pl.getUniqueId(), 0)
                + " &7| next draw in &f" + time(Math.max(0, nextDraw - System.currentTimeMillis())));
        p.send(pl, "&7Ticket price: &6" + p.fmt(price) + " &7- use &f/lottery buy <n>");
    }

    private void draw() {
        long minutes = Math.max(1, p.getConfig().getLong("lottery.draw-interval-minutes", 60));
        nextDraw = System.currentTimeMillis() + minutes * 60_000;
        if (tickets.isEmpty()) return;
        long sum = 0;
        for (int v : tickets.values()) sum += v;
        long pot = sum * p.getConfig().getLong("lottery.ticket-price", 100);
        long prize = pot - pot * p.getConfig().getLong("lottery.tax-percent", 10) / 100;
        long roll = (long) (rnd.nextDouble() * sum);
        long acc = 0;
        UUID winner = null;
        for (Map.Entry<UUID, Integer> e : tickets.entrySet()) {
            acc += e.getValue();
            if (roll < acc) {
                winner = e.getKey();
                break;
            }
        }
        if (winner == null) winner = tickets.keySet().iterator().next();
        p.eco().add(winner, prize);
        p.broadcast("&d&lLOTTERY! &e" + p.eco().name(winner) + " &7won &6" + p.fmt(prize) + " &7out of &f" + sum + " &7tickets!");
        tickets.clear();
        save();
    }

    // ---------- mob rewards ----------

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (ent instanceof Player) return;
        Player killer = ent.getKiller();
        if (killer == null) return;
        ConfigurationSection s = p.getConfig().getConfigurationSection("mob-rewards");
        if (s == null) return;
        String key = ent.getType().name();
        if (!s.contains(key)) return;
        long amount = Math.max(1, s.getLong(key) * (80 + rnd.nextInt(41)) / 100);
        p.eco().add(killer.getUniqueId(), amount);
        killer.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(p.color("&6+" + p.fmt(amount))));
    }
}
