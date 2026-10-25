package de.fabismp.core;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public final class Combat implements Listener {
    private final FabiCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Long> tagged = new HashMap<>();

    public Combat(FabiCore plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("combat.enabled", true); }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isTagged(UUID u) {
        Long until = tagged.get(u);
        return until != null && until > System.currentTimeMillis();
    }

    public int remaining(UUID u) {
        Long until = tagged.get(u);
        if (until == null) return 0;
        return (int) Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    private void tag(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        long dur = plugin.getConfig().getLong("combat.duration", 20) * 1000;
        boolean was = isTagged(p.getUniqueId());
        tagged.put(p.getUniqueId(), System.currentTimeMillis() + dur);
        if (!was) p.sendRichMessage("<red><bold>Du bist jetzt im Kampf!</bold> <gray>Logge nicht aus.");
    }

    private void tick() {
        for (Iterator<Map.Entry<UUID, Long>> it = tagged.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            if (e.getValue() <= System.currentTimeMillis()) {
                it.remove();
                p.sendActionBar(mm.deserialize("<green>Du bist nicht mehr im Kampf."));
            } else {
                p.sendActionBar(mm.deserialize("<red>Im Kampf <gray>\u2503 <white>" + remaining(p.getUniqueId()) + "s"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!enabled() || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        tag(victim);
        tag(attacker);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!enabled() || !isTagged(e.getPlayer().getUniqueId())) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        if (plugin.getConfig().getStringList("combat.blocked-commands").contains(cmd)) {
            e.setCancelled(true);
            e.getPlayer().sendRichMessage("<red>Das geht im Kampf nicht! <gray>(" + remaining(e.getPlayer().getUniqueId()) + "s)");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!enabled() || Bukkit.isStopping()) { tagged.remove(p.getUniqueId()); return; }
        if (isTagged(p.getUniqueId()) && plugin.getConfig().getBoolean("combat.kill-on-logout", true)) {
            tagged.remove(p.getUniqueId());
            Bukkit.broadcast(mm.deserialize("<red>\u2620 <white>" + p.getName() + " <gray>hat sich im Kampf ausgeloggt!"));
            p.setHealth(0);
        }
        tagged.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        tagged.remove(e.getEntity().getUniqueId());
    }
}
