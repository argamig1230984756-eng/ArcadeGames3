package me.arcade.games.game.impl;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.arena.Arena;
import me.arcade.games.game.Game;
import me.arcade.games.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;

/**
 * A series of short micro-games. Points are awarded by survival order; highest total wins.
 */
public class PartyGamesGame extends Game {
    private enum Mini {
        ANVIL_RAIN("Anvil Rain", "Dodge the falling anvils!", 45),
        BOMBARDMENT("Bombardment", "Dodge the falling TNT!", 45),
        SUMO("Sumo", "Knock everyone off the platform!", 90),
        SPLEEF("Spleef", "Break the snow under your rivals!", 90);

        final String title, desc;
        final int seconds;

        Mini(String title, String desc, int seconds) {
            this.title = title;
            this.desc = desc;
            this.seconds = seconds;
        }
    }

    private enum Phase { INTRO, PLAY, BREAK }

    private final Map<UUID, Integer> points = new HashMap<>();
    private final List<UUID> outOrder = new ArrayList<>();
    private final Map<Block, BlockData> removed = new HashMap<>();
    private final Random rnd = new Random();
    private List<Mini> queue = new ArrayList<>();
    private Mini current;
    private Phase phase = Phase.INTRO;
    private int index, timer, elapsed, floorY;

    public PartyGamesGame(ArcadePlugin plugin, Arena arena) {
        super(plugin, arena);
    }

    @Override
    protected void onStart() {
        floorY = arena.minY();
        for (UUID id : players) points.put(id, 0);
        queue = new ArrayList<>(Arrays.asList(Mini.values()));
        Collections.shuffle(queue);
        index = -1;
        nextRound();
    }

    private void nextRound() {
        restoreFloor();
        clearHazards();
        index++;
        if (index >= queue.size()) {
            finishGame();
            return;
        }
        current = queue.get(index);
        phase = Phase.INTRO;
        timer = 100;
        elapsed = 0;
        outOrder.clear();
        alive.clear();
        alive.addAll(players);
        if (current == Mini.SPLEEF) buildSpleefFloor();
        int i = 0;
        for (Player p : allPlayers()) {
            reset(p, GameMode.ADVENTURE);
            p.teleport(spawnFor(i++));
            Msg.title(p, "&e&l" + current.title, "&7" + current.desc);
        }
        broadcast("&6Round " + (index + 1) + "/" + queue.size() + ": &e" + current.title + " &7- " + current.desc);
    }

    private void beginRound() {
        phase = Phase.PLAY;
        timer = current.seconds * 20;
        elapsed = 0;
        for (Player p : alivePlayers()) {
            switch (current) {
                case SUMO -> p.getInventory().addItem(Msg.item(Material.STICK, 1, "&eKnockback Stick"));
                case SPLEEF -> {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.getInventory().addItem(Msg.item(Material.IRON_SHOVEL, 1, "&bSnow Digger"));
                }
                default -> {}
            }
            Msg.title(p, "&a&lGO!", "");
        }
    }

    @Override
    protected void tick() {
        timer--;
        switch (phase) {
            case INTRO -> {
                if (ticks % 20 == 0) {
                    for (Player p : allPlayers()) Msg.bar(p, "&eStarting in &b" + (timer / 20 + 1));
                }
                if (timer <= 0) beginRound();
            }
            case PLAY -> {
                elapsed++;
                playTick();
                if (phase == Phase.PLAY && timer <= 0) endRound();
            }
            case BREAK -> {
                if (timer <= 0) nextRound();
            }
        }
    }

    private void playTick() {
        for (Player p : alivePlayers()) {
            if (state != State.ACTIVE || phase != Phase.PLAY) return;
            if (p.getLocation().getY() < floorY - 3) {
                broadcast("&c" + p.getName() + " &7is out!");
                out(p);
            }
        }
        if (state != State.ACTIVE || phase != Phase.PLAY) return;
        World w = arena.world();
        if (current == Mini.ANVIL_RAIN) {
            int interval = Math.max(3, 14 - elapsed / 200);
            if (elapsed % interval == 0) {
                int count = 1 + elapsed / 300;
                for (int i = 0; i < count; i++) {
                    FallingBlock fb = w.spawnFallingBlock(randomTop(), Material.ANVIL.createBlockData());
                    fb.setHurtEntities(true);
                    fb.setDropItem(false);
                    fb.setDamagePerBlock(2f);
                    fb.setMaxDamage(12);
                    fb.addScoreboardTag("ag_fall");
                }
            }
        } else if (current == Mini.BOMBARDMENT) {
            int interval = Math.max(4, 16 - elapsed / 200);
            if (elapsed % interval == 0) {
                int count = 1 + elapsed / 300;
                for (int i = 0; i < count; i++) {
                    w.spawn(randomTop(), TNTPrimed.class, t -> {
                        t.setFuseTicks(45);
                        t.addScoreboardTag("ag_tnt");
                    });
                }
            }
        }
    }

    private Location randomTop() {
        World w = arena.world();
        int y = Math.min(floorY + 15, w.getMaxHeight() - 2);
        List<Player> al = alivePlayers();
        if (!al.isEmpty() && rnd.nextInt(4) == 0) {
            Location pl = al.get(rnd.nextInt(al.size())).getLocation();
            return new Location(w, pl.getX(), y, pl.getZ());
        }
        double x = arena.minX() + rnd.nextInt(arena.maxX() - arena.minX() + 1) + 0.5;
        double z = arena.minZ() + rnd.nextInt(arena.maxZ() - arena.minZ() + 1) + 0.5;
        return new Location(w, x, y, z);
    }

    private void out(Player p) {
        outOrder.add(p.getUniqueId());
        points.merge(p.getUniqueId(), outOrder.size() - 1, Integer::sum);
        eliminate(p);
    }

    private void endRound() {
        if (phase != Phase.PLAY) return;
        phase = Phase.BREAK;
        timer = 80;
        int bonus = outOrder.size() + 3;
        List<String> names = new ArrayList<>();
        for (UUID id : alive) {
            points.merge(id, bonus, Integer::sum);
            names.add(nameOf(id));
        }
        broadcast("&6Round over! &7Survivors: &e" + (names.isEmpty() ? "nobody" : String.join(", ", names)));
        broadcast("&7Standings: " + standings(3));
        clearHazards();
    }

    private String standings(int limit) {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> en : points.entrySet()) if (players.contains(en.getKey())) list.add(en);
        list.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            if (i > 0) sb.append("&8, ");
            sb.append("&e").append(nameOf(list.get(i).getKey())).append(" &7(").append(list.get(i).getValue()).append(")");
        }
        return sb.toString();
    }

    private void finishGame() {
        if (state != State.ACTIVE) return;
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> en : points.entrySet()) if (players.contains(en.getKey())) list.add(en);
        list.sort((a, b) -> b.getValue() - a.getValue());
        List<String> lines = new ArrayList<>();
        lines.add("&6&lPARTY GAMES FINISHED!");
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            lines.add("&e#" + (i + 1) + " &f" + nameOf(list.get(i).getKey()) + " &7- &b" + list.get(i).getValue() + " points");
        }
        end(lines.toArray(new String[0]));
    }

    @Override
    protected void checkEnd() {
        if (players.size() < 2) {
            finishGame();
            return;
        }
        if (phase == Phase.PLAY && alive.size() <= 1) endRound();
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        if (phase != Phase.PLAY || !alive.contains(victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        switch (current) {
            case SUMO -> {
                Player a = attacker(e);
                if (a != null && !a.equals(victim) && alive.contains(a.getUniqueId())) e.setDamage(0);
                else e.setCancelled(true);
            }
            case SPLEEF -> e.setCancelled(true);
            default -> {
                EntityDamageEvent.DamageCause c = e.getCause();
                if (c != EntityDamageEvent.DamageCause.FALLING_BLOCK
                        && c != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                        && c != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                    e.setCancelled(true);
                    return;
                }
                if (victim.getHealth() - e.getFinalDamage() <= 0) {
                    e.setCancelled(true);
                    broadcast("&c" + victim.getName() + " &7was crushed!");
                    out(victim);
                }
            }
        }
    }

    @Override
    public boolean allowBreak(Player p, Block b) {
        return phase == Phase.PLAY && current == Mini.SPLEEF
                && alive.contains(p.getUniqueId()) && b.getType() == Material.SNOW_BLOCK;
    }

    private void buildSpleefFloor() {
        World w = arena.world();
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                Block b = w.getBlockAt(x, floorY, z);
                removed.putIfAbsent(b, b.getBlockData());
                b.setType(Material.SNOW_BLOCK, false);
            }
        }
    }

    private void restoreFloor() {
        for (Map.Entry<Block, BlockData> en : removed.entrySet()) en.getKey().setBlockData(en.getValue(), false);
        removed.clear();
    }

    private void clearHazards() {
        World w = arena.world();
        if (w == null) return;
        for (Entity en : w.getEntities()) {
            Set<String> tags = en.getScoreboardTags();
            if (tags.contains("ag_fall") || tags.contains("ag_tnt")) en.remove();
        }
    }

    @Override
    protected void onEnd() {
        restoreFloor();
        clearHazards();
    }
}
