package de.fabismp.core;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Data {
    private final FabiCore plugin;
    private final File file;
    private final YamlConfiguration yml;

    public Data(FabiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        plugin.getDataFolder().mkdirs();
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { yml.save(file); } catch (IOException e) { plugin.getLogger().warning("Speichern fehlgeschlagen: " + e.getMessage()); }
    }

    private String k(UUID u, String f) { return "p." + u + "." + f; }

    public boolean has(UUID u) { return yml.contains("p." + u); }

    public void ensure(Player p) {
        UUID u = p.getUniqueId();
        if (!has(u)) {
            yml.set(k(u, "money"), plugin.getConfig().getDouble("start-money", 0));
            yml.set(k(u, "shards"), 0L);
            yml.set(k(u, "kills"), 0);
            yml.set(k(u, "deaths"), 0);
        }
        yml.set(k(u, "name"), p.getName());
    }

    public double money(UUID u) { return yml.getDouble(k(u, "money")); }
    public void setMoney(UUID u, double v) { yml.set(k(u, "money"), Math.max(0, Math.round(v * 100) / 100.0)); }
    public void addMoney(UUID u, double v) { setMoney(u, money(u) + v); }

    public long shards(UUID u) { return yml.getLong(k(u, "shards")); }
    public void setShards(UUID u, long v) { yml.set(k(u, "shards"), Math.max(0, v)); }
    public void addShards(UUID u, long v) { setShards(u, shards(u) + v); }

    public int kills(UUID u) { return yml.getInt(k(u, "kills")); }
    public int deaths(UUID u) { return yml.getInt(k(u, "deaths")); }
    public void addKill(UUID u) { yml.set(k(u, "kills"), kills(u) + 1); }
    public void addDeath(UUID u) { yml.set(k(u, "deaths"), deaths(u) + 1); }

    public List<Map.Entry<String, Double>> top(int n) {
        List<Map.Entry<String, Double>> list = new ArrayList<>();
        ConfigurationSection sec = yml.getConfigurationSection("p");
        if (sec == null) return list;
        for (String id : sec.getKeys(false)) {
            list.add(Map.entry(sec.getString(id + ".name", "?"), sec.getDouble(id + ".money")));
        }
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(n, list.size()));
    }

    // ---- Homes ----
    public Set<String> homes(UUID u) {
        ConfigurationSection s = yml.getConfigurationSection("homes." + u);
        return s == null ? Set.of() : s.getKeys(false);
    }
    public Location home(UUID u, String name) { return yml.getLocation("homes." + u + "." + name.toLowerCase()); }
    public void setHome(UUID u, String name, Location l) { yml.set("homes." + u + "." + name.toLowerCase(), l); }
    public void delHome(UUID u, String name) { yml.set("homes." + u + "." + name.toLowerCase(), null); }
}
