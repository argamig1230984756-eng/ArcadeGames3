package me.arcade.games.game.impl;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.arena.Arena;
import me.arcade.games.game.Game;
import me.arcade.games.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class BlockPartyGame extends Game {
    private static final Material[] COLORS = {
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL};
    private static final String[] NAMES = {
            "White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray",
            "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};
    private static final int MAX_ROUNDS = 30;

    private final Map<Block, BlockData> original = new HashMap<>();
    private final Random rnd = new Random();
    private int round, timer, total, floorY;
    private boolean cleared;
    private Material target;

    public BlockPartyGame(ArcadePlugin plugin, Arena arena) {
        super(plugin, arena);
    }

    @Override
    protected void onStart() {
        World w = arena.world();
        floorY = arena.minY();
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                Block b = w.getBlockAt(x, floorY, z);
                original.put(b, b.getBlockData());
            }
        }
        int i = 0;
        for (Player p : allPlayers()) {
            p.teleport(spawnFor(i++));
            p.setGameMode(GameMode.ADVENTURE);
        }
        round = 0;
        startRound();
    }

    private void startRound() {
        if (round >= MAX_ROUNDS) {
            List<String> names = new ArrayList<>();
            for (UUID id : alive) names.add(nameOf(id));
            end("&6&lBlockParty over! &eSurvivors: &f" + String.join(", ", names));
            return;
        }
        round++;
        cleared = false;
        World w = arena.world();
        int size = round > 15 ? 1 : 2;
        int cw = (arena.maxX() - arena.minX()) / size + 1;
        int cd = (arena.maxZ() - arena.minZ()) / size + 1;
        int[][] cells = new int[cw][cd];
        Set<Integer> used = new HashSet<>();
        for (int[] row : cells) {
            for (int k = 0; k < row.length; k++) {
                row[k] = rnd.nextInt(COLORS.length);
                used.add(row[k]);
            }
        }
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                int c = cells[(x - arena.minX()) / size][(z - arena.minZ()) / size];
                w.getBlockAt(x, floorY, z).setType(COLORS[c], false);
            }
        }
        List<Integer> usedList = new ArrayList<>(used);
        int idx = usedList.get(rnd.nextInt(usedList.size()));
        target = COLORS[idx];
        String name = NAMES[idx];
        total = Math.max(30, 150 - round * 7);
        timer = total;
        ItemStack it = Msg.item(target, 1, "&f" + name);
        for (Player p : alivePlayers()) {
            PlayerInventory inv = p.getInventory();
            inv.clear();
            for (int s = 0; s < 9; s++) inv.setItem(s, it.clone());
            inv.setHeldItemSlot(4);
            Msg.title(p, "&e&l" + name.toUpperCase(), "&7Round " + round + " - stand on this color!");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        }
    }

    @Override
    protected void tick() {
        for (Player p : alivePlayers()) {
            if (state != State.ACTIVE) return;
            if (p.getLocation().getY() < floorY - 3) {
                broadcast("&c" + p.getName() + " &7fell off! &e" + (alive.size() - 1) + " &7left.");
                eliminate(p);
            }
        }
        if (state != State.ACTIVE) return;
        timer--;
        if (!cleared) {
            float f = Math.max(0f, (float) timer / total);
            for (Player p : alivePlayers()) {
                p.setExp(Math.min(1f, f));
                p.setLevel((timer + 19) / 20);
            }
            if (timer <= 0) {
                World w = arena.world();
                for (int x = arena.minX(); x <= arena.maxX(); x++) {
                    for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                        Block b = w.getBlockAt(x, floorY, z);
                        if (b.getType() != target) b.setType(Material.AIR, false);
                    }
                }
                cleared = true;
                timer = 50;
                for (Player p : alivePlayers()) {
                    p.setExp(0f);
                    p.setLevel(0);
                    p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                }
            }
        } else if (timer <= 0) {
            startRound();
        }
    }

    @Override
    protected void checkEnd() {
        if (alive.size() > 1) return;
        String winner = alive.isEmpty() ? "Nobody" : nameOf(alive.iterator().next());
        end("&6&l" + winner + " &ewon BlockParty!");
    }

    @Override
    protected void onEnd() {
        for (Map.Entry<Block, BlockData> en : original.entrySet()) en.getKey().setBlockData(en.getValue(), false);
        original.clear();
    }
}
