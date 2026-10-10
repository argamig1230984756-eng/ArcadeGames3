package me.arcade.games.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Arena {
    public final String name;
    public final GameType type;
    public int min, max;
    public Location waiting, pos1, pos2;
    public final TreeMap<Integer, Location> spawns = new TreeMap<>();
    public final TreeMap<Integer, Location> withers = new TreeMap<>();

    public Arena(String name, GameType type) {
        this.name = name;
        this.type = type;
        this.min = type.min;
        this.max = type.max;
    }

    public boolean hasRegion() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
    }

    public int minX() { return Math.min(pos1.getBlockX(), pos2.getBlockX()); }
    public int maxX() { return Math.max(pos1.getBlockX(), pos2.getBlockX()); }
    public int minY() { return Math.min(pos1.getBlockY(), pos2.getBlockY()); }
    public int maxY() { return Math.max(pos1.getBlockY(), pos2.getBlockY()); }
    public int minZ() { return Math.min(pos1.getBlockZ(), pos2.getBlockZ()); }
    public int maxZ() { return Math.max(pos1.getBlockZ(), pos2.getBlockZ()); }

    public World world() {
        if (pos1 != null && pos1.getWorld() != null) return pos1.getWorld();
        if (waiting != null) return waiting.getWorld();
        if (!spawns.isEmpty()) return spawns.firstEntry().getValue().getWorld();
        return null;
    }

    public boolean contains(Block b) {
        return hasRegion() && b.getWorld().equals(pos1.getWorld())
                && b.getX() >= minX() && b.getX() <= maxX()
                && b.getY() >= minY() && b.getY() <= maxY()
                && b.getZ() >= minZ() && b.getZ() <= maxZ();
    }

    public List<Location> spawnList() {
        return new ArrayList<>(spawns.values());
    }

    public Location center() {
        if (hasRegion()) {
            return new Location(world(), (minX() + maxX() + 1) / 2.0, minY(), (minZ() + maxZ() + 1) / 2.0);
        }
        return spawns.isEmpty() ? waiting.clone() : spawns.firstEntry().getValue().clone();
    }

    public Location spectatorSpawn() {
        Location c = center();
        c.setY(hasRegion() ? maxY() + 3 : c.getY() + 6);
        return c;
    }

    /** @return null if the arena is ready to play, otherwise a description of what is missing. */
    public String validate() {
        if (waiting == null || waiting.getWorld() == null) return "waiting lobby not set (/arcade setwaiting)";
        if (spawns.isEmpty()) return "no spawns set (/arcade setspawn)";
        for (Location l : spawns.values()) if (l.getWorld() == null) return "a spawn is in an unloaded world";
        if ((type == GameType.BLOCK_PARTY || type == GameType.TNT_RUN || type == GameType.PARTY_GAMES) && !hasRegion())
            return "region not set (/arcade pos1 and /arcade pos2)";
        if (type == GameType.MINI_WALLS) {
            if (spawns.size() < 2) return "Mini Walls needs at least 2 team spawns (numbered 1-4)";
            for (int k : spawns.keySet()) {
                if (k < 1 || k > 4) return "team spawns must be numbered 1-4";
                Location w = withers.get(k);
                if (w == null || w.getWorld() == null) return "team " + k + " has no wither (/arcade setwither)";
            }
        }
        if (max < min) return "max players is lower than min players";
        return null;
    }
}
