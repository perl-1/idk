package dev.saikomc;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Gambling {
    private static final String[] NAMES = {"Cherry", "Lemon", "Bell", "Star", "Diamond", "Seven"};
    private static final String[] COLORS = {"&c", "&e", "&6", "&b", "&3", "&4"};

    private final SaikoMC p;
    private final Random rnd = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public Gambling(SaikoMC p) {
        this.p = p;
    }

    /** Validates the bet and takes the money. Returns false if the bet is not allowed. */
    private boolean placeBet(Player pl, long bet) {
        long min = p.getConfig().getLong("gambling.min-bet", 10);
        long max = p.getConfig().getLong("gambling.max-bet", 100000);
        if (bet < min || bet > max) {
            p.send(pl, "&cYour bet must be between &6" + p.fmt(min) + " &cand &6" + p.fmt(max) + "&c.");
            return false;
        }
        long now = System.currentTimeMillis();
        long cd = p.getConfig().getLong("gambling.cooldown-seconds", 3) * 1000L;
        long last = cooldowns.getOrDefault(pl.getUniqueId(), 0L);
        if (now - last < cd) {
            long left = (cd - (now - last) + 999) / 1000;
            p.send(pl, "&cSlow down! Wait " + left + "s before betting again.");
            return false;
        }
        if (!p.eco().take(pl.getUniqueId(), bet)) {
            p.send(pl, "&cYou don't have enough money. Balance: &6" + p.fmt(p.eco().get(pl.getUniqueId())));
            return false;
        }
        cooldowns.put(pl.getUniqueId(), now);
        return true;
    }

    private long afterEdge(long profit) {
        double edge = p.getConfig().getDouble("gambling.house-edge-percent", 5) / 100.0;
        return (long) Math.floor(profit * (1.0 - edge));
    }

    private void maybeBroadcast(Player pl, String game, long profit) {
        long threshold = p.getConfig().getLong("gambling.broadcast-wins-over", 10000);
        if (profit >= threshold) {
            p.broadcast("&e" + pl.getName() + " &7just won &6" + p.fmt(profit) + " &7playing &d" + game + "&7!");
        }
    }

    public void coinflip(Player pl, long bet, String side) {
        if (side != null && !side.equalsIgnoreCase("heads") && !side.equalsIgnoreCase("tails")) {
            p.send(pl, "&cPick &fheads &cor &ftails&c.");
            return;
        }
        if (!placeBet(pl, bet)) return;
        String pick = side != null ? side.toLowerCase() : (rnd.nextBoolean() ? "heads" : "tails");
        String result = rnd.nextBoolean() ? "heads" : "tails";
        if (pick.equals(result)) {
            long profit = afterEdge(bet);
            p.eco().add(pl.getUniqueId(), bet + profit);
            p.send(pl, "&7The coin landed on &e" + result + "&7. &aYou won &6" + p.fmt(profit) + "&a!");
            maybeBroadcast(pl, "Coinflip", profit);
        } else {
            p.send(pl, "&7The coin landed on &e" + result + "&7. &cYou lost &6" + p.fmt(bet) + "&c.");
        }
    }

    public void slots(Player pl, long bet) {
        if (!placeBet(pl, bet)) return;
        int a = rnd.nextInt(6), b = rnd.nextInt(6), c = rnd.nextInt(6);
        String line = "&8[ " + COLORS[a] + NAMES[a] + " &8| " + COLORS[b] + NAMES[b] + " &8| " + COLORS[c] + NAMES[c] + " &8]";

        double two = p.getConfig().getDouble("gambling.slots.two-multiplier", 1.3);
        double three = p.getConfig().getDouble("gambling.slots.three-multiplier", 10);
        double jackpot = p.getConfig().getDouble("gambling.slots.jackpot-multiplier", 25);

        long payout = 0;
        boolean isJackpot = false;
        if (a == b && b == c) {
            isJackpot = a == 5;
            payout = (long) (bet * (isJackpot ? jackpot : three));
        } else if (a == b || b == c || a == c) {
            payout = (long) (bet * two);
        }

        p.send(pl, "&dSlots: " + line);
        if (payout > 0) {
            p.eco().add(pl.getUniqueId(), payout);
            long profit = payout - bet;
            if (isJackpot) p.send(pl, "&6&lJACKPOT! &aYou won &6" + p.fmt(profit) + "&a!");
            else p.send(pl, "&aYou won &6" + p.fmt(profit) + "&a!");
            maybeBroadcast(pl, "Slots", profit);
        } else {
            p.send(pl, "&cNo match. You lost &6" + p.fmt(bet) + "&c.");
        }
    }

    public void dice(Player pl, long bet) {
        if (!placeBet(pl, bet)) return;
        int you = rnd.nextInt(6) + 1 + rnd.nextInt(6) + 1;
        int house = rnd.nextInt(6) + 1 + rnd.nextInt(6) + 1;
        String roll = "&7You rolled &e" + you + "&7, the house rolled &e" + house + "&7. ";
        if (you > house) {
            long profit = afterEdge(bet);
            p.eco().add(pl.getUniqueId(), bet + profit);
            p.send(pl, roll + "&aYou won &6" + p.fmt(profit) + "&a!");
            maybeBroadcast(pl, "Dice", profit);
        } else if (you == house) {
            p.eco().add(pl.getUniqueId(), bet);
            p.send(pl, roll + "&eTie - bet refunded.");
        } else {
            p.send(pl, roll + "&cYou lost &6" + p.fmt(bet) + "&c.");
        }
    }
}
