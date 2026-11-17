package dev.saikomc;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class Commands implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "saikomc.admin";
    private final SaikoMC p;

    public Commands(SaikoMC p) {
        this.p = p;
    }

    static long parseAmount(String in) {
        return parseAmount(in, false);
    }

    /** Parses 1000, 1,000, 2.5k, 3m, 1b. Returns -1 if invalid. */
    static long parseAmount(String in, boolean allowZero) {
        try {
            String s = in.toLowerCase().replace(",", "");
            double mult = 1;
            if (s.endsWith("k")) { mult = 1_000; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m")) { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("b")) { mult = 1_000_000_000; s = s.substring(0, s.length() - 1); }
            double v = Double.parseDouble(s) * mult;
            if (Double.isNaN(v) || v < (allowZero ? 0 : 1) || v > 1_000_000_000_000.0) return -1;
            return (long) v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Player player(CommandSender s) {
        if (s instanceof Player) return (Player) s;
        p.send(s, "&cOnly players can use this command.");
        return null;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission(ADMIN)) return true;
        p.send(s, "&cYou don't have permission.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase()) {
            case "saikomc": return plugin(s, a);
            case "balance": return balance(s, a);
            case "pay": return pay(s, a);
            case "baltop": return baltop(s);
            case "eco": return eco(s, a);
            case "setbalance": return adminBal(s, a, "set");
            case "addbalance": return adminBal(s, a, "add");
            case "removebalance": return adminBal(s, a, "remove");
            case "resetbalance": return resetBal(s, a);
            case "giveall": return giveAll(s, a);
            case "rain": return rain(s, a);
            case "stats": return stats(s, a);
            case "playtime": return playtime(s, a);
            case "scoreboard": {
                Player pl = player(s);
                if (pl != null) {
                    boolean on = p.board().toggle(pl);
                    p.send(pl, on ? "&aScoreboard enabled." : "&cScoreboard hidden. Use &f/sb &cto show it again.");
                }
                return true;
            }
            case "worth": { Player pl = player(s); if (pl != null) p.shop().worth(pl); return true; }
            case "daily": { Player pl = player(s); if (pl != null) p.extras().daily(pl); return true; }
            case "coinflip": return bet(s, a, "coinflip");
            case "slots": return bet(s, a, "slots");
            case "dice": return bet(s, a, "dice");
            case "shop": { Player pl = player(s); if (pl != null) p.shop().openMain(pl); return true; }
            case "sell": { Player pl = player(s); if (pl != null) p.shop().sellHand(pl); return true; }
            case "sellall": { Player pl = player(s); if (pl != null) p.shop().sellAll(pl); return true; }
            case "market": return market(s, a);
            case "bounty": return bounty(s, a);
            case "withdraw": return withdraw(s, a);
            case "lottery": return lottery(s, a);
            default: return false;
        }
    }

    // ---------- help / reload ----------

    private boolean plugin(CommandSender s, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            if (!admin(s)) return true;
            p.reload();
            p.send(s, "&aConfig reloaded.");
            return true;
        }
        String[] lines = {
            "&b&lSaikoMC commands",
            "&f/balance [player] /pay <player> <amt> /baltop /daily /withdraw <amt>",
            "&f/stats [player] /playtime [player] /sb &7(toggle scoreboard)",
            "&f/coinflip <amt> [heads|tails] /slots <amt> /dice <amt>",
            "&f/shop /sell /sellall /worth /market [sell <price>]",
            "&f/bounty <player> <amt> | list /lottery [buy <n>|info]",
            "&7Amounts accept k/m/b: 2.5k, 1m"
        };
        for (String l : lines) s.sendMessage(p.color(l));
        if (s.hasPermission(ADMIN)) {
            s.sendMessage(p.color("&cAdmin: &f/setbalance /addbalance /removebalance /resetbalance <player> [amt]"));
            s.sendMessage(p.color("&cAdmin: &f/giveall <amt> /rain <total> [winners] /eco /saikomc reload"));
        }
        return true;
    }

    // ---------- money ----------

    private boolean balance(CommandSender s, String[] a) {
        UUID id;
        String name = null;
        if (a.length > 0) {
            id = p.eco().find(a[0]);
            if (id == null) { p.send(s, "&cUnknown player."); return true; }
            name = p.eco().name(id);
        } else {
            Player pl = player(s);
            if (pl == null) return true;
            id = pl.getUniqueId();
        }
        p.send(s, (name == null ? "&7Your balance: " : "&7" + name + "'s balance: ") + "&6" + p.fmt(p.eco().get(id)));
        return true;
    }

    private boolean pay(CommandSender s, String[] a) {
        Player pl = player(s);
        if (pl == null) return true;
        if (a.length < 2) { p.send(s, "&cUsage: /pay <player> <amount>"); return true; }
        UUID target = p.eco().find(a[0]);
        if (target == null) { p.send(s, "&cUnknown player."); return true; }
        if (target.equals(pl.getUniqueId())) { p.send(s, "&cYou can't pay yourself."); return true; }
        long amt = parseAmount(a[1]);
        if (amt < 1) { p.send(s, "&cInvalid amount."); return true; }
        if (!p.eco().take(pl.getUniqueId(), amt)) { p.send(s, "&cYou don't have enough money."); return true; }
        p.eco().add(target, amt);
        p.send(s, "&aSent &6" + p.fmt(amt) + " &ato &f" + p.eco().name(target) + "&a.");
        Player tp = Bukkit.getPlayer(target);
        if (tp != null) p.send(tp, "&aYou received &6" + p.fmt(amt) + " &afrom &f" + pl.getName() + "&a.");
        return true;
    }

    private boolean baltop(CommandSender s) {
        List<Map.Entry<UUID, Long>> top = p.eco().top(10);
        p.send(s, "&b&lRichest players");
        int i = 1;
        for (Map.Entry<UUID, Long> e : top) {
            s.sendMessage(p.color(" &e#" + i++ + " &f" + p.eco().name(e.getKey()) + " &8- &6" + p.fmt(e.getValue())));
        }
        return true;
    }

    private boolean eco(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length < 3) { p.send(s, "&cUsage: /eco <give|take|set> <player> <amount>"); return true; }
        switch (a[0].toLowerCase()) {
            case "give": return adminBal(s, new String[]{a[1], a[2]}, "add");
            case "take": return adminBal(s, new String[]{a[1], a[2]}, "remove");
            case "set": return adminBal(s, new String[]{a[1], a[2]}, "set");
            default: p.send(s, "&cUsage: /eco <give|take|set> <player> <amount>"); return true;
        }
    }

    private boolean adminBal(CommandSender s, String[] a, String mode) {
        if (!admin(s)) return true;
        if (a.length < 2) { p.send(s, "&cUsage: /" + mode + "balance <player> <amount>"); return true; }
        UUID id = p.eco().find(a[0]);
        if (id == null) { p.send(s, "&cUnknown player (they must have joined once)."); return true; }
        long amt = parseAmount(a[1], mode.equals("set"));
        if (amt < 0) { p.send(s, "&cInvalid amount."); return true; }
        long before = p.eco().get(id);
        String verb;
        switch (mode) {
            case "set": p.eco().set(id, amt); verb = "set to"; break;
            case "add": p.eco().add(id, amt); verb = "increased by"; break;
            default: p.eco().set(id, before - amt); verb = "decreased by"; break;
        }
        String name = p.eco().name(id);
        p.send(s, "&f" + name + "&7's balance " + verb + " &6" + p.fmt(amt) + "&7. Now: &a" + p.fmt(p.eco().get(id)));
        Player tp = Bukkit.getPlayer(id);
        if (tp != null && !tp.equals(s)) {
            p.send(tp, "&7Your balance is now &a" + p.fmt(p.eco().get(id)) + "&7.");
        }
        return true;
    }

    private boolean resetBal(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length < 1) { p.send(s, "&cUsage: /resetbalance <player>"); return true; }
        UUID id = p.eco().find(a[0]);
        if (id == null) { p.send(s, "&cUnknown player."); return true; }
        p.eco().set(id, p.getConfig().getLong("starting-balance", 500));
        p.send(s, "&f" + p.eco().name(id) + "&7's balance reset to &a" + p.fmt(p.eco().get(id)) + "&7.");
        return true;
    }

    private boolean giveAll(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length < 1) { p.send(s, "&cUsage: /giveall <amount>"); return true; }
        long amt = parseAmount(a[0]);
        if (amt < 1) { p.send(s, "&cInvalid amount."); return true; }
        for (Player pl : Bukkit.getOnlinePlayers()) p.eco().add(pl.getUniqueId(), amt);
        p.broadcast("&aEveryone online just received &6" + p.fmt(amt) + "&a!");
        return true;
    }

    private boolean rain(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length < 1) { p.send(s, "&cUsage: /rain <totalAmount> [winners]"); return true; }
        long total = parseAmount(a[0]);
        if (total < 1) { p.send(s, "&cInvalid amount."); return true; }
        int winners = 3;
        if (a.length > 1) {
            try { winners = Integer.parseInt(a[1]); } catch (NumberFormatException e) { p.send(s, "&cInvalid winner count."); return true; }
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return true;
        Collections.shuffle(online);
        int n = Math.max(1, Math.min(winners, online.size()));
        long each = total / n;
        if (each < 1) { p.send(s, "&cAmount too small for that many winners."); return true; }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Player w = online.get(i);
            p.eco().add(w.getUniqueId(), each);
            if (i > 0) names.append("&7, ");
            names.append("&e").append(w.getName());
        }
        p.broadcast("&b&lMONEY RAIN! " + names + " &7each got &6" + p.fmt(each) + "&7!");
        return true;
    }

    // ---------- stats ----------

    private Player onlineTarget(CommandSender s, String[] a) {
        if (a.length > 0) {
            Player t = Bukkit.getPlayerExact(a[0]);
            if (t == null) p.send(s, "&cThat player must be online.");
            return t;
        }
        return player(s);
    }

    private boolean stats(CommandSender s, String[] a) {
        Player t = onlineTarget(s, a);
        if (t == null) return true;
        int kills = t.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = t.getStatistic(Statistic.DEATHS);
        int mobs = t.getStatistic(Statistic.MOB_KILLS);
        long secs = t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        double kdr = deaths == 0 ? kills : (double) kills / deaths;
        s.sendMessage(p.color("&b&l" + t.getName() + "'s stats"));
        s.sendMessage(p.color(" &7Money: &a" + p.fmt(p.eco().get(t.getUniqueId()))));
        s.sendMessage(p.color(" &7Rank: &e" + p.hooks().rank(t)));
        s.sendMessage(p.color(" &7Clan: &d" + p.hooks().clan(t)));
        s.sendMessage(p.color(" &7Kills: &c" + kills + " &8| &7Deaths: &c" + deaths + " &8| &7KDR: &f"
                + String.format(Locale.US, "%.2f", kdr)));
        s.sendMessage(p.color(" &7Mob kills: &f" + mobs));
        s.sendMessage(p.color(" &7Playtime: &b" + p.duration(secs)));
        return true;
    }

    private boolean playtime(CommandSender s, String[] a) {
        Player t = onlineTarget(s, a);
        if (t == null) return true;
        long secs = t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        p.send(s, "&f" + t.getName() + " &7has played for &b" + p.duration(secs) + "&7.");
        return true;
    }

    // ---------- gambling / shops / extras ----------

    private boolean bet(CommandSender s, String[] a, String game) {
        Player pl = player(s);
        if (pl == null) return true;
        if (a.length < 1) { p.send(s, "&cUsage: /" + game + " <amount>" + (game.equals("coinflip") ? " [heads|tails]" : "")); return true; }
        long amt = parseAmount(a[0]);
        if (amt < 1) { p.send(s, "&cInvalid amount."); return true; }
        switch (game) {
            case "coinflip": p.gambling().coinflip(pl, amt, a.length > 1 ? a[1] : null); break;
            case "slots": p.gambling().slots(pl, amt); break;
            default: p.gambling().dice(pl, amt); break;
        }
        return true;
    }

    private boolean market(CommandSender s, String[] a) {
        Player pl = player(s);
        if (pl == null) return true;
        if (a.length >= 1 && a[0].equalsIgnoreCase("sell")) {
            if (a.length < 2) { p.send(s, "&cUsage: /market sell <price>"); return true; }
            long price = parseAmount(a[1]);
            if (price < 1) { p.send(s, "&cInvalid price."); return true; }
            p.market().sell(pl, price);
        } else {
            p.market().open(pl, 0);
        }
        return true;
    }

    private boolean bounty(CommandSender s, String[] a) {
        Player pl = player(s);
        if (pl == null) return true;
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) { p.extras().listBounties(pl); return true; }
        if (a.length < 2) { p.send(s, "&cUsage: /bounty <player> <amount>"); return true; }
        UUID target = p.eco().find(a[0]);
        if (target == null) { p.send(s, "&cUnknown player."); return true; }
        long amt = parseAmount(a[1]);
        if (amt < 1) { p.send(s, "&cInvalid amount."); return true; }
        p.extras().placeBounty(pl, target, amt);
        return true;
    }

    private boolean withdraw(CommandSender s, String[] a) {
        Player pl = player(s);
        if (pl == null) return true;
        if (a.length < 1) { p.send(s, "&cUsage: /withdraw <amount>"); return true; }
        long amt = parseAmount(a[0]);
        if (amt < 1) { p.send(s, "&cInvalid amount."); return true; }
        p.extras().withdraw(pl, amt);
        return true;
    }

    private boolean lottery(CommandSender s, String[] a) {
        Player pl = player(s);
        if (pl == null) return true;
        if (a.length >= 1 && a[0].equalsIgnoreCase("buy")) {
            int n = 1;
            if (a.length > 1) {
                try { n = Integer.parseInt(a[1]); } catch (NumberFormatException e) { p.send(s, "&cInvalid number."); return true; }
            }
            p.extras().buyTickets(pl, n);
        } else {
            p.extras().lotteryInfo(pl);
        }
        return true;
    }

    // ---------- tab completion ----------

    private List<String> f(String prefix, String... opts) {
        List<String> out = new ArrayList<>();
        for (String o : opts) if (o.startsWith(prefix.toLowerCase())) out.add(o);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        String n = c.getName().toLowerCase();
        if (a.length == 1) {
            switch (n) {
                case "eco": return f(a[0], "give", "take", "set");
                case "saikomc": return f(a[0], "help", "reload");
                case "market": return f(a[0], "sell");
                case "lottery": return f(a[0], "buy", "info");
                case "balance": case "pay": case "stats": case "playtime":
                case "setbalance": case "addbalance": case "removebalance": case "resetbalance":
                    return null;
                case "bounty": {
                    List<String> out = f(a[0], "list");
                    for (Player pl : Bukkit.getOnlinePlayers()) {
                        if (pl.getName().toLowerCase().startsWith(a[0].toLowerCase())) out.add(pl.getName());
                    }
                    return out;
                }
                default: return Collections.emptyList();
            }
        }
        if (a.length == 2) {
            if (n.equals("eco")) return null;
            if (n.equals("coinflip")) return f(a[1], "heads", "tails");
        }
        return Collections.emptyList();
    }
}
