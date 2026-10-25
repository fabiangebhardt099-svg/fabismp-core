package de.fabismp.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Cmds implements CommandExecutor, TabCompleter {
    private static final String P = "<gradient:#ff9a3c:#ffd23c><bold>FabiSMP</bold></gradient> <dark_gray>\u00bb</dark_gray> ";

    private record Req(UUID from, long expires) {}

    private final FabiCore plugin;
    private final Data d;
    private final Map<UUID, Req> tpa = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();

    public Cmds(FabiCore plugin, Data d) {
        this.plugin = plugin;
        this.d = d;
    }

    private void msg(CommandSender s, String m) { s.sendRichMessage(P + m); }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        msg(s, "<red>Nur fuer Spieler.");
        return null;
    }

    private OfflinePlayer find(String name) {
        Player o = Bukkit.getPlayerExact(name);
        if (o != null) return o;
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null && d.has(op.getUniqueId()) ? op : null;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> bal(s, a);
            case "pay" -> pay(s, a);
            case "baltop" -> baltop(s);
            case "sell" -> sell(s, a);
            case "worth" -> worth(s);
            case "shards" -> shards(s, a);
            case "eco" -> eco(s, a);
            case "rtp" -> rtp(s);
            case "tpa" -> tpa(s, a);
            case "tpaccept" -> tpaccept(s);
            case "tpdeny" -> tpdeny(s);
            case "home" -> home(s, a);
            case "sethome" -> sethome(s, a);
            case "delhome" -> delhome(s, a);
            case "homes" -> homes(s);
            case "stats" -> stats(s, a);
            default -> {}
        }
        return true;
    }

    // ---------- Economy ----------
    private void bal(CommandSender s, String[] a) {
        OfflinePlayer t;
        if (a.length > 0) {
            t = find(a[0]);
            if (t == null) { msg(s, "<red>Spieler nicht gefunden."); return; }
        } else if (s instanceof Player p) t = p;
        else { msg(s, "<red>Nutze /bal <spieler>"); return; }
        msg(s, "<gray>Kontostand von <white>" + t.getName() + "<gray>: <green>" + Fmt.money(d.money(t.getUniqueId())));
    }

    private void pay(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) { msg(s, "<red>/pay <spieler> <betrag>"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) { msg(s, "<red>Spieler nicht online."); return; }
        if (t.equals(p)) { msg(s, "<red>Du kannst dir nicht selbst Geld senden."); return; }
        double amt = Fmt.parse(a[1]);
        if (amt <= 0) { msg(s, "<red>Ungueltiger Betrag."); return; }
        if (d.money(p.getUniqueId()) < amt) { msg(s, "<red>Nicht genug Geld."); return; }
        d.addMoney(p.getUniqueId(), -amt);
        d.addMoney(t.getUniqueId(), amt);
        msg(p, "<gray>Du hast <green>" + Fmt.money(amt) + "<gray> an <white>" + t.getName() + "<gray> gesendet.");
        msg(t, "<white>" + p.getName() + "<gray> hat dir <green>" + Fmt.money(amt) + "<gray> gesendet.");
    }

    private void baltop(CommandSender s) {
        msg(s, "<gold>Top 10 Reichste:");
        int i = 1;
        for (Map.Entry<String, Double> e : d.top(10)) {
            s.sendRichMessage("<dark_gray>" + i++ + ". <white>" + e.getKey() + " <dark_gray>- <green>" + Fmt.money(e.getValue()));
        }
    }

    private void shards(CommandSender s, String[] a) {
        OfflinePlayer t;
        if (a.length > 0) {
            t = find(a[0]);
            if (t == null) { msg(s, "<red>Spieler nicht gefunden."); return; }
        } else if (s instanceof Player p) t = p;
        else { msg(s, "<red>Nutze /shards <spieler>"); return; }
        msg(s, "<gray>Shards von <white>" + t.getName() + "<gray>: <light_purple>" + Fmt.num(d.shards(t.getUniqueId())));
    }

    private void eco(CommandSender s, String[] a) {
        if (a.length < 4) { msg(s, "<red>/eco <money|shards> <give|take|set> <spieler> <betrag>"); return; }
        OfflinePlayer t = find(a[2]);
        if (t == null) { msg(s, "<red>Spieler nicht gefunden."); return; }
        double v = Fmt.parse(a[3]);
        if (v < 0) { msg(s, "<red>Ungueltiger Betrag."); return; }
        UUID u = t.getUniqueId();
        boolean money = a[0].equalsIgnoreCase("money");
        if (!money && !a[0].equalsIgnoreCase("shards")) { msg(s, "<red>Waehrung: money oder shards"); return; }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "give" -> { if (money) d.addMoney(u, v); else d.addShards(u, (long) v); }
            case "take" -> { if (money) d.addMoney(u, -v); else d.addShards(u, -(long) v); }
            case "set" -> { if (money) d.setMoney(u, v); else d.setShards(u, (long) v); }
            default -> { msg(s, "<red>give, take oder set"); return; }
        }
        msg(s, "<green>Erledigt.");
    }

    // ---------- Sell ----------
    private double price(ItemStack it) {
        if (it == null || it.getType().isAir() || it.hasItemMeta()) return 0;
        return plugin.getConfig().getDouble("sell-prices." + it.getType().name(), 0) * it.getAmount();
    }

    private void sell(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        double total = 0;
        int count = 0;
        if (a.length > 0 && a[0].equalsIgnoreCase("all")) {
            ItemStack[] inv = p.getInventory().getStorageContents();
            for (int i = 0; i < inv.length; i++) {
                double pr = price(inv[i]);
                if (pr > 0) { total += pr; count += inv[i].getAmount(); p.getInventory().setItem(i, null); }
            }
        } else {
            ItemStack it = p.getInventory().getItemInMainHand();
            double pr = price(it);
            if (pr > 0) { total = pr; count = it.getAmount(); p.getInventory().setItemInMainHand(null); }
        }
        if (total <= 0) { msg(p, "<red>Nichts Verkaufbares gefunden. Nutze /worth."); return; }
        d.addMoney(p.getUniqueId(), total);
        msg(p, "<gray>Du hast <white>" + count + "<gray> Items fuer <green>" + Fmt.money(total) + "<gray> verkauft.");
    }

    private void worth(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        double pr = price(it);
        if (pr <= 0) msg(p, "<red>Dieses Item ist nicht verkaufbar.");
        else msg(p, "<gray>Wert: <green>" + Fmt.money(pr) + "<gray> (x" + it.getAmount() + ")");
    }

    // ---------- RTP ----------
    private void rtp(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        long now = System.currentTimeMillis();
        Long until = rtpCooldown.get(p.getUniqueId());
        if (until != null && until > now) {
            msg(p, "<red>Warte noch " + ((until - now) / 1000 + 1) + "s.");
            return;
        }
        World w = Bukkit.getWorld(plugin.getConfig().getString("rtp.world", "world"));
        if (w == null) w = p.getWorld();
        msg(p, "<gray>Suche einen Platz...");
        rtpTry(p, w, 10);
    }

    private void rtpTry(Player p, World w, int tries) {
        if (tries <= 0 || !p.isOnline()) { msg(p, "<red>Kein sicherer Platz gefunden, versuch es nochmal."); return; }
        int min = plugin.getConfig().getInt("rtp.min", 500);
        int max = plugin.getConfig().getInt("rtp.max", 5000);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = min + r.nextDouble() * Math.max(1, max - min);
        int x = (int) (Math.cos(ang) * dist), z = (int) (Math.sin(ang) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Block b = w.getHighestBlockAt(x, z);
            Material m = b.getType();
            if (m == Material.WATER || m == Material.LAVA || b.isLiquid() || b.getY() <= w.getMinHeight()) {
                rtpTry(p, w, tries - 1);
                return;
            }
            Location loc = b.getLocation().add(0.5, 1, 0.5);
            p.teleportAsync(loc).thenAccept(ok -> {
                if (ok) {
                    long cd = plugin.getConfig().getLong("rtp.cooldown", 30) * 1000;
                    rtpCooldown.put(p.getUniqueId(), System.currentTimeMillis() + cd);
                    msg(p, "<green>Teleportiert nach <white>" + x + ", " + loc.getBlockY() + ", " + z);
                }
            });
        });
    }

    // ---------- TPA ----------
    private void tpa(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 1) { msg(p, "<red>/tpa <spieler>"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) { msg(p, "<red>Spieler nicht gefunden."); return; }
        long timeout = plugin.getConfig().getLong("tpa-timeout", 60) * 1000;
        tpa.put(t.getUniqueId(), new Req(p.getUniqueId(), System.currentTimeMillis() + timeout));
        msg(p, "<gray>Anfrage an <white>" + t.getName() + "<gray> gesendet.");
        msg(t, "<white>" + p.getName() + "<gray> moechte sich zu dir teleportieren. <green>/tpaccept <gray>oder <red>/tpdeny");
    }

    private Req takeReq(Player p) {
        Req r = tpa.remove(p.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            msg(p, "<red>Keine offene Anfrage.");
            return null;
        }
        return r;
    }

    private void tpaccept(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        Req r = takeReq(p);
        if (r == null) return;
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) { msg(p, "<red>Der Spieler ist offline."); return; }
        from.teleportAsync(p.getLocation());
        msg(p, "<green>Anfrage angenommen.");
        msg(from, "<green>Teleportiere zu " + p.getName() + "...");
    }

    private void tpdeny(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        Req r = takeReq(p);
        if (r == null) return;
        Player from = Bukkit.getPlayer(r.from());
        msg(p, "<gray>Anfrage abgelehnt.");
        if (from != null) msg(from, "<red>" + p.getName() + " hat deine Anfrage abgelehnt.");
    }

    // ---------- Homes ----------
    private void home(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        String name = a.length > 0 ? a[0] : "home";
        Location l = d.home(p.getUniqueId(), name);
        if (l == null) { msg(p, "<red>Home <white>" + name + "<red> existiert nicht."); return; }
        p.teleportAsync(l);
        msg(p, "<green>Teleportiert zu <white>" + name);
    }

    private void sethome(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        String name = (a.length > 0 ? a[0] : "home").toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{1,16}")) { msg(p, "<red>Name: 1-16 Zeichen (a-z, 0-9, _)."); return; }
        Set<String> homes = d.homes(p.getUniqueId());
        int max = plugin.getConfig().getInt("max-homes", 3);
        if (!homes.contains(name) && homes.size() >= max && !p.hasPermission("fabismp.homes.bypass")) {
            msg(p, "<red>Du hast das Limit von " + max + " Homes erreicht.");
            return;
        }
        d.setHome(p.getUniqueId(), name, p.getLocation());
        msg(p, "<green>Home <white>" + name + "<green> gesetzt.");
    }

    private void delhome(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 1) { msg(p, "<red>/delhome <name>"); return; }
        if (d.home(p.getUniqueId(), a[0]) == null) { msg(p, "<red>Home nicht gefunden."); return; }
        d.delHome(p.getUniqueId(), a[0]);
        msg(p, "<gray>Home <white>" + a[0] + "<gray> geloescht.");
    }

    private void homes(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        Set<String> h = d.homes(p.getUniqueId());
        msg(p, h.isEmpty() ? "<gray>Keine Homes gesetzt." : "<gray>Homes: <white>" + String.join("<gray>, <white>", h));
    }

    // ---------- Stats ----------
    private void stats(CommandSender s, String[] a) {
        OfflinePlayer t;
        if (a.length > 0) {
            t = find(a[0]);
            if (t == null) { msg(s, "<red>Spieler nicht gefunden."); return; }
        } else if (s instanceof Player p) t = p;
        else { msg(s, "<red>Nutze /stats <spieler>"); return; }
        UUID u = t.getUniqueId();
        int k = d.kills(u), de = d.deaths(u);
        double kdr = de == 0 ? k : Math.round((double) k / de * 100) / 100.0;
        long ticks = t.getStatistic(Statistic.PLAY_ONE_MINUTE);
        msg(s, "<gold>Stats von <white>" + t.getName());
        s.sendRichMessage("<gray>Geld: <green>" + Fmt.money(d.money(u)) + " <dark_gray>| <gray>Shards: <light_purple>" + Fmt.num(d.shards(u)));
        s.sendRichMessage("<gray>Kills: <white>" + k + " <dark_gray>| <gray>Tode: <white>" + de + " <dark_gray>| <gray>K/D: <white>" + kdr);
        s.sendRichMessage("<gray>Spielzeit: <aqua>" + Fmt.time(ticks));
    }

    // ---------- Tab ----------
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (n.equals("sell") && a.length == 1) out.addAll(List.of("hand", "all"));
        else if (n.equals("eco")) {
            if (a.length == 1) out.addAll(List.of("money", "shards"));
            else if (a.length == 2) out.addAll(List.of("give", "take", "set"));
            else if (a.length == 3) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 1 && List.of("bal", "pay", "shards", "stats", "tpa").contains(n)) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 1 && s instanceof Player p && List.of("home", "delhome").contains(n)) {
            out.addAll(d.homes(p.getUniqueId()));
        }
        String cur = a.length > 0 ? a[a.length - 1].toLowerCase(Locale.ROOT) : "";
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
