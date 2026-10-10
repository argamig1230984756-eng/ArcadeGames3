package me.arcade.games.game.impl;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.arena.Arena;
import me.arcade.games.game.Game;
import me.arcade.games.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.*;

public class TNTRunGame extends Game {
    private final Map<Block, BlockData> removed = new HashMap<>();
    private final Set<Block> pending = new HashSet<>();
    private final Map<UUID, Integer> jumps = new HashMap<>();

    public TNTRunGame(ArcadePlugin plugin, Arena arena) {
        super(plugin, arena);
    }

    @Override
    protected void onStart() {
        int i = 0;
        for (Player p : allPlayers()) {
            p.teleport(spawnFor(i++));
            p.setAllowFlight(true);
            jumps.put(p.getUniqueId(), 5);
            Msg.title(p, "&c&lTNT RUN", "&7Keep moving - the floor disappears!");
        }
        broadcast("&eThe floor starts to vanish in 3 seconds. You have &b5 double jumps&e!");
    }

    @Override
    protected void tick() {
        double fallY = arena.minY() - 2;
        for (Player p : alivePlayers()) {
            if (state != State.ACTIVE) return;
            Location l = p.getLocation();
            if (l.getY() < fallY) {
                broadcast("&c" + p.getName() + " &7fell! &e" + (alive.size() - 1) + " &7left.");
                eliminate(p);
                continue;
            }
            if (ticks < 60) continue;
            for (double dx : new double[]{-0.3, 0.3}) {
                for (double dz : new double[]{-0.3, 0.3}) {
                    Block b = l.clone().add(dx, -1, dz).getBlock();
                    if (b.getType().isAir() || !arena.contains(b) || !pending.add(b)) continue;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (state != State.ACTIVE) return;
                        removed.putIfAbsent(b, b.getBlockData());
                        b.setType(Material.AIR, false);
                        Block under = b.getRelative(0, -1, 0);
                        if (under.getType() == Material.TNT) {
                            removed.putIfAbsent(under, under.getBlockData());
                            under.setType(Material.AIR, false);
                        }
                    }, 10L);
                }
            }
        }
    }

    @Override
    public void onFlight(Player p, PlayerToggleFlightEvent e) {
        UUID id = p.getUniqueId();
        if (!alive.contains(id)) return;
        e.setCancelled(true);
        p.setFlying(false);
        int j = jumps.getOrDefault(id, 0);
        if (j <= 0) {
            p.setAllowFlight(false);
            return;
        }
        jumps.put(id, j - 1);
        p.setVelocity(p.getLocation().getDirection().multiply(0.5).setY(0.85));
        Msg.bar(p, "&bDouble jumps left: &e" + (j - 1));
        if (j - 1 <= 0) p.setAllowFlight(false);
    }

    @Override
    protected void checkEnd() {
        if (alive.size() > 1) return;
        String winner = alive.isEmpty() ? "Nobody" : nameOf(alive.iterator().next());
        end("&6&l" + winner + " &ewon TNT Run!");
    }

    @Override
    protected void onEnd() {
        for (Map.Entry<Block, BlockData> en : removed.entrySet()) en.getKey().setBlockData(en.getValue(), false);
        removed.clear();
        pending.clear();
        jumps.clear();
    }
}
