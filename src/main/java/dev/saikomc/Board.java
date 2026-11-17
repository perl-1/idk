package dev.saikomc;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sidebar stat board (money, rank, clan, kills, playtime, ip ...). Fully configurable. */
public class Board implements Listener {
    private final SaikoMC p;
    private final File file;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Integer> sizes = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private BukkitTask task;
    private boolean errorLogged;

    public Board(SaikoMC p) {
        this.p = p;
        this.file = new File(p.getDataFolder(), "board.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("hidden")) {
            try {
                hidden.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID u : hidden) list.add(u.toString());
        y.set("hidden", list);
        try {
            p.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            p.getLogger().severe("Could not save board.yml: " + e.getMessage());
        }
    }

    public void start() {
        stop();
        if (!p.getConfig().getBoolean("scoreboard.enabled", true)) return;
        long ticks = Math.max(5L, p.getConfig().getLong("scoreboard.update-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(p, this::updateAll, 20L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player pl : Bukkit.getOnlinePlayers()) clear(pl);
        boards.clear();
        sizes.clear();
    }

    private void clear(Player pl) {
        if (boards.remove(pl.getUniqueId()) != null) {
            sizes.remove(pl.getUniqueId());
            pl.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Returns true if the board is now visible for the player. */
    public boolean toggle(Player pl) {
        boolean nowVisible;
        if (hidden.remove(pl.getUniqueId())) {
            nowVisible = true;
        } else {
            hidden.add(pl.getUniqueId());
            nowVisible = false;
        }
        save();
        if (nowVisible) safeUpdate(pl);
        else clear(pl);
        return nowVisible;
    }

    private void updateAll() {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!hidden.contains(pl.getUniqueId())) safeUpdate(pl);
        }
    }

    private void safeUpdate(Player pl) {
        try {
            update(pl);
        } catch (Exception e) {
            if (!errorLogged) {
                errorLogged = true;
                p.getLogger().warning("Scoreboard update failed: " + e);
            }
        }
    }

    private void update(Player pl) {
        List<String> lines = p.getConfig().getStringList("scoreboard.lines");
        if (lines.size() > 15) lines = lines.subList(0, 15);
        UUID id = pl.getUniqueId();

        Scoreboard sb = boards.get(id);
        if (sb == null || sizes.getOrDefault(id, -1) != lines.size()) {
            sb = build(lines.size());
            boards.put(id, sb);
            sizes.put(id, lines.size());
            pl.setScoreboard(sb);
        }

        Objective obj = sb.getObjective("saiko");
        if (obj != null) {
            obj.setDisplayName(p.color(replace(pl, p.getConfig().getString("scoreboard.title", "&b&lSAIKOMC"))));
        }
        for (int i = 0; i < lines.size(); i++) {
            Team t = sb.getTeam("l" + i);
            if (t != null) t.setPrefix(fit(p.color(replace(pl, lines.get(i)))));
        }
    }

    @SuppressWarnings("deprecation")
    private Scoreboard build(int n) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("saiko", "dummy", "SaikoMC");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        hideNumbers(obj);
        for (int i = 0; i < n; i++) {
            Team t = sb.registerNewTeam("l" + i);
            String entry = "\u00a7" + Integer.toHexString(i) + "\u00a7r"; // invisible unique entry
            t.addEntry(entry);
            obj.getScore(entry).setScore(n - i);
        }
        return sb;
    }

    /** Hides the red score numbers on Paper 1.20.3+. Silently does nothing elsewhere. */
    private void hideNumbers(Objective obj) {
        try {
            Class<?> nf = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = nf.getMethod("blank").invoke(null);
            Objective.class.getMethod("numberFormat", nf).invoke(obj, blank);
        } catch (Throwable ignored) {
        }
    }

    private String fit(String s) {
        if (s.length() > 64) s = s.substring(0, 64);
        if (s.endsWith("\u00a7")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String replace(Player pl, String s) {
        long money = p.eco().get(pl.getUniqueId());
        int kills = pl.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = pl.getStatistic(Statistic.DEATHS);
        long secs = pl.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        double kdr = deaths == 0 ? kills : (double) kills / deaths;

        s = s.replace("%player%", pl.getName())
                .replace("%money%", p.symbol() + p.shortNum(money))
                .replace("%money_full%", p.fmt(money))
                .replace("%kills%", String.valueOf(kills))
                .replace("%deaths%", String.valueOf(deaths))
                .replace("%kdr%", String.format(Locale.US, "%.2f", kdr))
                .replace("%playtime%", p.duration(secs))
                .replace("%ip%", p.getConfig().getString("scoreboard.server-ip", "play.saikomc.net"))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%ping%", String.valueOf(pl.getPing()));
        if (s.contains("%rank%")) s = s.replace("%rank%", p.hooks().rank(pl));
        if (s.contains("%clan%")) s = s.replace("%clan%", p.hooks().clan(pl));
        if (s.indexOf('%') >= 0) s = p.hooks().papi(pl, s); // any other PlaceholderAPI placeholder
        return s;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player pl = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(p, () -> {
            if (task != null && pl.isOnline() && !hidden.contains(pl.getUniqueId())) safeUpdate(pl);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
        sizes.remove(e.getPlayer().getUniqueId());
    }
}
