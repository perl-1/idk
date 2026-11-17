package dev.saikomc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SaikoMC extends JavaPlugin {
    private Economy economy;
    private ShopManager shop;
    private MarketManager market;
    private Gambling gambling;
    private Extras extras;
    private Hooks hooks;
    private Board board;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economy = new Economy(this);
        shop = new ShopManager(this);
        market = new MarketManager(this);
        gambling = new Gambling(this);
        extras = new Extras(this);
        hooks = new Hooks(this);
        board = new Board(this);

        economy.load();
        shop.load();
        market.load();
        extras.load();
        board.load();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(economy, this);
        pm.registerEvents(shop, this);
        pm.registerEvents(market, this);
        pm.registerEvents(extras, this);
        pm.registerEvents(board, this);

        Commands handler = new Commands(this);
        for (String name : getDescription().getCommands().keySet()) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(handler);
                pc.setTabCompleter(handler);
            }
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            economy.save();
            extras.save();
        }, 6000L, 6000L);

        extras.startLottery();
        board.start();
        getLogger().info("Enabled.");
    }

    @Override
    public void onDisable() {
        if (board != null) board.stop();
        if (economy != null) economy.save();
        if (market != null) market.save();
        if (extras != null) extras.save();
    }

    public void reload() {
        reloadConfig();
        shop.load();
        extras.startLottery();
        board.start();
    }

    public Economy eco() { return economy; }
    public ShopManager shop() { return shop; }
    public MarketManager market() { return market; }
    public Gambling gambling() { return gambling; }
    public Extras extras() { return extras; }
    public Hooks hooks() { return hooks; }
    public Board board() { return board; }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String symbol() {
        return color(getConfig().getString("currency.symbol", "$"));
    }

    /** Full number with currency symbol, e.g. $1,250,000 */
    public String fmt(long v) {
        return symbol() + String.format("%,d", v);
    }

    /** Short number without symbol, e.g. 1.2M */
    public String shortNum(long v) {
        String[] suf = {"", "K", "M", "B", "T", "Q"};
        double d = v;
        int i = 0;
        while (d >= 1000 && i < suf.length - 1) {
            d /= 1000;
            i++;
        }
        String s = (i == 0 || d >= 100) ? String.valueOf((long) d) : String.format(java.util.Locale.US, "%.1f", d);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + suf[i];
    }

    public String duration(long seconds) {
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    public void send(CommandSender to, String msg) {
        to.sendMessage(color(getConfig().getString("prefix", "&b&lSaikoMC &8» &r") + msg));
    }

    public void broadcast(String msg) {
        Bukkit.broadcastMessage(color(getConfig().getString("prefix", "&b&lSaikoMC &8» &r") + msg));
    }
}
