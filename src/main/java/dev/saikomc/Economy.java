package dev.saikomc;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Economy implements Listener {
    private static final long MAX = 1_000_000_000_000_000L;

    private final SaikoMC p;
    private final File file;
    private final Map<UUID, Long> bal = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private boolean dirty;

    public Economy(SaikoMC p) {
        this.p = p;
        this.file = new File(p.getDataFolder(), "data.yml");
    }

    public synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection b = y.getConfigurationSection("balances");
        if (b == null) return;
        for (String k : b.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                bal.put(id, b.getLong(k));
                String n = y.getString("names." + k);
                if (n != null) names.put(id, n);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : bal.entrySet()) {
            y.set("balances." + e.getKey(), e.getValue());
            String n = names.get(e.getKey());
            if (n != null) y.set("names." + e.getKey(), n);
        }
        try {
            p.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            p.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public synchronized long get(UUID id) {
        return bal.getOrDefault(id, 0L);
    }

    public synchronized void set(UUID id, long v) {
        bal.put(id, Math.max(0L, Math.min(v, MAX)));
        dirty = true;
    }

    public synchronized void add(UUID id, long v) {
        set(id, get(id) + v);
    }

    public synchronized boolean take(UUID id, long v) {
        if (get(id) < v) return false;
        set(id, get(id) - v);
        return true;
    }

    public synchronized UUID find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    public synchronized String name(UUID id) {
        String n = names.get(id);
        if (n != null) return n;
        n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    public synchronized List<Map.Entry<UUID, Long>> top(int n) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(bal.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }

    private synchronized void register(Player pl) {
        names.put(pl.getUniqueId(), pl.getName());
        if (!bal.containsKey(pl.getUniqueId())) {
            set(pl.getUniqueId(), p.getConfig().getLong("starting-balance", 500));
        }
        dirty = true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        register(e.getPlayer());
    }
}
