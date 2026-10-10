package me.arcade.games.game.impl;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.arena.Arena;
import me.arcade.games.game.Game;
import me.arcade.games.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;

import java.util.*;

/**
 * Up to 4 teams. Each team owns a Wither. While your Wither lives you respawn;
 * once it dies you only get one life. Last team standing wins.
 */
public class MiniWallsGame extends Game {
    private static final String[] NAMES = {"", "Red", "Green", "Blue", "Yellow"};
    private static final String[] CODES = {"", "&c", "&a", "&9", "&e"};
    private static final Color[] COLORS = {Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
    private static final double WITHER_HP = 300;
    private static final int TIME_LIMIT = 15 * 60 * 20;

    private final Map<UUID, Integer> teamOf = new HashMap<>();
    private final Map<Integer, Wither> withers = new HashMap<>();
    private final Set<Integer> deadWithers = new HashSet<>();
    private final List<Integer> teams = new ArrayList<>();
    private final Set<Block> placed = new HashSet<>();
    private final Map<UUID, UUID> lastHit = new HashMap<>();

    public MiniWallsGame(ArcadePlugin plugin, Arena arena) {
        super(plugin, arena);
    }

    @Override
    protected void onStart() {
        List<Integer> ids = new ArrayList<>(arena.spawns.keySet());
        int n = Math.min(ids.size(), players.size());
        if (n <= 0) return;
        teams.addAll(ids.subList(0, n));

        List<Player> list = allPlayers();
        Collections.shuffle(list);
        for (int i = 0; i < list.size(); i++) {
            Player p = list.get(i);
            int t = teams.get(i % n);
            teamOf.put(p.getUniqueId(), t);
            p.teleport(arena.spawns.get(t));
            p.setGameMode(GameMode.SURVIVAL);
            kit(p, t);
            Msg.title(p, CODES[t] + "&l" + NAMES[t] + " Team", "&7Protect your Wither!");
        }

        for (int t : teams) {
            final int team = t;
            Location loc = arena.withers.get(team);
            Wither w = loc.getWorld().spawn(loc, Wither.class, x -> {
                x.setAI(false);
                x.setRemoveWhenFarAway(false);
                x.setPersistent(false);
                x.setCustomNameVisible(true);
                x.customName(Msg.c(CODES[team] + NAMES[team] + " Wither"));
                AttributeInstance ai = x.getAttribute(Attribute.MAX_HEALTH);
                if (ai != null) ai.setBaseValue(WITHER_HP);
                x.setHealth(WITHER_HP);
            });
            withers.put(team, w);
        }
        broadcast("&eProtect your Wither and destroy the others! Once your Wither dies, you can no longer respawn.");
    }

    private void kit(Player p, int t) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(leather(Material.LEATHER_HELMET, t));
        inv.setChestplate(leather(Material.LEATHER_CHESTPLATE, t));
        inv.setLeggings(leather(Material.LEATHER_LEGGINGS, t));
        inv.setBoots(leather(Material.LEATHER_BOOTS, t));
        inv.addItem(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.BOW),
                new ItemStack(Material.ARROW, 32), new ItemStack(Material.STONE_PICKAXE),
                new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COOKED_BEEF, 16),
                new ItemStack(Material.GOLDEN_APPLE, 2));
    }

    private ItemStack leather(Material m, int t) {
        ItemStack is = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) is.getItemMeta();
        meta.setColor(COLORS[t]);
        meta.setUnbreakable(true);
        is.setItemMeta(meta);
        return is;
    }

    private Integer witherTeam(Entity e) {
        for (Map.Entry<Integer, Wither> en : withers.entrySet()) {
            if (en.getValue().getUniqueId().equals(e.getUniqueId())) return en.getKey();
        }
        return null;
    }

    private int lowestSpawnY() {
        double y = Double.MAX_VALUE;
        for (Location l : arena.spawns.values()) y = Math.min(y, l.getY());
        return (int) y;
    }

    @Override
    protected void tick() {
        double voidY = arena.hasRegion() ? arena.minY() - 5 : lowestSpawnY() - 60;
        for (Player p : alivePlayers()) {
            if (state != State.ACTIVE) return;
            if (p.getLocation().getY() < voidY) die(p, null);
        }
        if (state != State.ACTIVE) return;
        if (ticks % 20 == 0) {
            for (Map.Entry<Integer, Wither> en : withers.entrySet()) {
                Wither w = en.getValue();
                if (w.isDead()) continue;
                int t = en.getKey();
                w.customName(Msg.c(CODES[t] + NAMES[t] + " Wither &7(" + (int) w.getHealth() + " HP)"));
            }
        }
        if (ticks >= TIME_LIMIT) timeUp();
    }

    private void timeUp() {
        int best = -1;
        double hp = -1;
        for (int t : teams) {
            Wither w = withers.get(t);
            if (!deadWithers.contains(t) && w != null && w.getHealth() > hp) {
                hp = w.getHealth();
                best = t;
            }
        }
        end("&6&lTime is up! " + (best < 0 ? "&7Nobody wins."
                : CODES[best] + NAMES[best] + " Team &6wins with the healthiest Wither!"));
    }

    // ------------------------------------------------------------ combat

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        UUID id = victim.getUniqueId();
        if (!alive.contains(id)) {
            e.setCancelled(true);
            return;
        }
        Player a = attacker(e);
        if (a != null && !a.equals(victim)) {
            if (!alive.contains(a.getUniqueId())
                    || Objects.equals(teamOf.get(a.getUniqueId()), teamOf.get(id))) {
                e.setCancelled(true);
                return;
            }
            lastHit.put(id, a.getUniqueId());
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            die(victim, a != null && !a.equals(victim) ? a : null);
        }
    }

    private void die(Player victim, Player killer) {
        UUID id = victim.getUniqueId();
        int t = teamOf.getOrDefault(id, 0);
        if (killer == null) {
            UUID k = lastHit.get(id);
            if (k != null) killer = Bukkit.getPlayer(k);
        }
        lastHit.remove(id);
        String vn = CODES[t] + victim.getName();
        if (killer != null) {
            int kt = teamOf.getOrDefault(killer.getUniqueId(), 0);
            broadcast(vn + " &7was killed by " + CODES[kt] + killer.getName());
        } else {
            broadcast(vn + " &7died");
        }
        if (deadWithers.contains(t)) {
            eliminate(victim);
        } else {
            for (PotionEffect pe : new ArrayList<>(victim.getActivePotionEffects())) victim.removePotionEffect(pe.getType());
            victim.setHealth(20.0);
            victim.setFoodLevel(20);
            victim.setFireTicks(0);
            victim.setFallDistance(0f);
            victim.teleport(arena.spawns.get(t));
            kit(victim, t);
        }
    }

    @Override
    public boolean ownsEntity(Entity e) {
        return witherTeam(e) != null;
    }

    @Override
    public void onEntityDamage(EntityDamageEvent e) {
        if (state != State.ACTIVE) {
            e.setCancelled(true);
            return;
        }
        Player d = attacker(e);
        if (d == null || !alive.contains(d.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        Integer t = witherTeam(e.getEntity());
        if (t != null && t.equals(teamOf.get(d.getUniqueId()))) {
            e.setCancelled(true);
            Msg.bar(d, "&cYou can't hurt your own Wither!");
        }
    }

    @Override
    public void onEntityDeath(EntityDeathEvent e) {
        e.getDrops().clear();
        e.setDroppedExp(0);
        Integer t = witherTeam(e.getEntity());
        if (t == null || state != State.ACTIVE) return;
        deadWithers.add(t);
        broadcast(CODES[t] + NAMES[t] + " Team&c's Wither was destroyed! &7They can no longer respawn.");
        for (Player p : allPlayers()) {
            Integer pt = teamOf.get(p.getUniqueId());
            if (pt != null && pt.equals(t)) Msg.title(p, "&c&lYour Wither died!", "&7No more respawns");
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.7f, 1f);
        }
        checkEnd();
    }

    @Override
    protected void checkEnd() {
        if (teams.isEmpty()) return;
        List<Integer> living = new ArrayList<>();
        for (int t : teams) {
            boolean has = !deadWithers.contains(t);
            if (!has) {
                for (UUID id : alive) {
                    if (Objects.equals(teamOf.get(id), t)) {
                        has = true;
                        break;
                    }
                }
            }
            if (has) living.add(t);
        }
        if (living.size() <= 1) {
            if (living.isEmpty()) end("&6&lNobody won Mini Walls!");
            else end(CODES[living.get(0)] + "&l" + NAMES[living.get(0)] + " Team &6won Mini Walls!");
        }
    }

    // ------------------------------------------------------------ blocks

    @Override
    public boolean allowPlace(Player p, Block b) {
        if (!alive.contains(p.getUniqueId())) return false;
        placed.add(b);
        return true;
    }

    @Override
    public boolean allowBreak(Player p, Block b) {
        return alive.contains(p.getUniqueId()) && placed.remove(b);
    }

    @Override public boolean allowDrops() { return true; }
    @Override public boolean allowPickup() { return true; }
    @Override public boolean allowInventory() { return true; }

    @Override
    protected void onEnd() {
        for (Wither w : withers.values()) w.remove();
        for (Block b : placed) b.setType(Material.AIR, false);
        withers.clear();
        placed.clear();
        deadWithers.clear();
        teams.clear();
        teamOf.clear();
        lastHit.clear();
    }
}
