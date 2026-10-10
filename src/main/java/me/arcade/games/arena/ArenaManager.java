package me.arcade.games.arena;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.game.Game;
import me.arcade.games.game.impl.*;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ArenaManager {
    private final ArcadePlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<String, Game> games = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();

    public ArenaManager(ArcadePlugin plugin) {
        this.plugin = plugin;
    }

    private File file() {
        plugin.getDataFolder().mkdirs();
        return new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        File f = file();
        if (!f.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = c.getConfigurationSection("arenas");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection a = root.getConfigurationSection(key);
            if (a == null) continue;
            GameType t = GameType.parse(a.getString("type", ""));
            if (t == null) continue;
            Arena ar = new Arena(key, t);
            ar.min = a.getInt("min", t.min);
            ar.max = a.getInt("max", t.max);
            ar.waiting = a.getLocation("waiting");
            ar.pos1 = a.getLocation("pos1");
            ar.pos2 = a.getLocation("pos2");
            readLocs(a.getConfigurationSection("spawns"), ar.spawns);
            readLocs(a.getConfigurationSection("withers"), ar.withers);
            arenas.put(key.toLowerCase(), ar);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    private void readLocs(ConfigurationSection s, Map<Integer, Location> into) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                Location l = s.getLocation(k);
                if (l != null) into.put(Integer.parseInt(k), l);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Arena ar : arenas.values()) {
            String b = "arenas." + ar.name;
            c.set(b + ".type", ar.type.name());
            c.set(b + ".min", ar.min);
            c.set(b + ".max", ar.max);
            c.set(b + ".waiting", ar.waiting);
            c.set(b + ".pos1", ar.pos1);
            c.set(b + ".pos2", ar.pos2);
            for (Map.Entry<Integer, Location> e : ar.spawns.entrySet()) c.set(b + ".spawns." + e.getKey(), e.getValue());
            for (Map.Entry<Integer, Location> e : ar.withers.entrySet()) c.set(b + ".withers." + e.getKey(), e.getValue());
        }
        try {
            c.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save arenas.yml: " + ex.getMessage());
        }
    }

    public Arena arena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public Collection<Arena> arenas() {
        return arenas.values();
    }

    public Arena create(String name, GameType type) {
        if (arenas.containsKey(name.toLowerCase())) return null;
        Arena a = new Arena(name, type);
        arenas.put(name.toLowerCase(), a);
        return a;
    }

    public boolean delete(String name) {
        Arena a = arenas.remove(name.toLowerCase());
        if (a == null) return false;
        Game g = games.remove(name.toLowerCase());
        if (g != null) g.shutdown();
        return true;
    }

    public Game game(Arena a) {
        return games.computeIfAbsent(a.name.toLowerCase(), k -> make(a));
    }

    private Game make(Arena a) {
        return switch (a.type) {
            case TNT_RUN -> new TNTRunGame(plugin, a);
            case BLOCK_PARTY -> new BlockPartyGame(plugin, a);
            case PROP_HUNT -> new PropHuntGame(plugin, a);
            case MINI_WALLS -> new MiniWallsGame(plugin, a);
            case PARTY_GAMES -> new PartyGamesGame(plugin, a);
        };
    }

    public List<Game> all() {
        return new ArrayList<>(games.values());
    }

    public Game gameOf(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public void register(UUID id, Game g) {
        byPlayer.put(id, g);
    }

    public void unregister(UUID id) {
        byPlayer.remove(id);
    }

    public void shutdown() {
        for (Game g : all()) g.shutdown();
        byPlayer.clear();
    }
}
