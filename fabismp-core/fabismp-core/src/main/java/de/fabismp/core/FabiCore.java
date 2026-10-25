package de.fabismp.core;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class FabiCore extends JavaPlugin implements Listener {
    private Data data;
    private Board board;
    private Combat combat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new Data(this);
        combat = new Combat(this);
        board = new Board(this, data, combat);
        Cmds cmds = new Cmds(this, data);

        for (String name : new String[]{"bal", "pay", "baltop", "sell", "worth", "shards", "eco", "rtp",
                "tpa", "tpaccept", "tpdeny", "home", "sethome", "delhome", "homes", "stats"}) {
            PluginCommand c = getCommand(name);
            if (c == null) continue;
            c.setExecutor(cmds);
            c.setTabCompleter(cmds);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        combat.start();
        for (Player p : Bukkit.getOnlinePlayers()) { data.ensure(p); board.add(p); }

        Bukkit.getScheduler().runTaskTimer(this, board::updateAll, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, data::save, 6000L, 6000L);
        getLogger().info("fabismp-core aktiviert.");
    }

    @Override
    public void onDisable() {
        if (data != null) data.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        data.ensure(e.getPlayer());
        board.add(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        board.remove(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        data.addDeath(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) data.addKill(killer.getUniqueId());
    }
}
