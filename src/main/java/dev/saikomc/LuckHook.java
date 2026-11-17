package dev.saikomc;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Only ever touched when LuckPerms is installed (see Hooks.rank). */
final class LuckHook {
    private LuckHook() {
    }

    static String rank(Player pl, String mode) {
        RegisteredServiceProvider<LuckPerms> prov = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (prov == null) return null;
        User user = prov.getProvider().getUserManager().getUser(pl.getUniqueId());
        if (user == null) return null;
        if (mode.equalsIgnoreCase("prefix")) {
            String prefix = user.getCachedData().getMetaData().getPrefix();
            if (prefix != null && !prefix.isBlank()) return prefix;
        }
        String g = user.getPrimaryGroup();
        if (g == null || g.isEmpty()) return null;
        return Character.toUpperCase(g.charAt(0)) + g.substring(1);
    }
}
