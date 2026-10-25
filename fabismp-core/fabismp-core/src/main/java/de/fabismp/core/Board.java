package de.fabismp.core;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class Board {
    private static final int LINES = 8;
    private final FabiCore plugin;
    private final Data data;
    private final Combat combat;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public Board(FabiCore plugin, Data data, Combat combat) {
        this.plugin = plugin;
        this.data = data;
        this.combat = combat;
    }

    private static String entry(int i) { return "\u00a7" + Integer.toHexString(i) + "\u00a7r"; }

    public void add(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Component title = mm.deserialize(plugin.getConfig().getString("scoreboard-title", "<gold>FabiSMP"));
        Objective obj = sb.registerNewObjective("fabi", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        for (int i = 0; i < LINES; i++) {
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry(i));
            obj.getScore(entry(i)).setScore(LINES - i);
        }
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        update(p);
    }

    public void remove(Player p) { boards.remove(p.getUniqueId()); }

    public void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        UUID u = p.getUniqueId();
        String[] lines = {
                "",
                "<gray>\u2503 <green>Geld <white>" + Fmt.money(data.money(u)),
                "<gray>\u2503 <light_purple>Shards <white>" + Fmt.num(data.shards(u)),
                "<gray>\u2503 <red>Kills <white>" + data.kills(u),
                "<gray>\u2503 <dark_red>Tode <white>" + data.deaths(u),
                "<gray>\u2503 <aqua>Spielzeit <white>" + Fmt.time(p.getStatistic(Statistic.PLAY_ONE_MINUTE)),
                "<gray>\u2503 <yellow>Ping <white>" + p.getPing() + "ms",
                combat.isTagged(u) ? "<gray>\u2503 <red>Kampf <white>" + combat.remaining(u) + "s" : ""
        };
        for (int i = 0; i < LINES; i++) {
            Team t = sb.getTeam("l" + i);
            if (t != null) t.prefix(mm.deserialize(lines[i]));
        }
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }
}
