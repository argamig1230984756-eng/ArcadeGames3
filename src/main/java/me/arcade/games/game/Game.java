package me.arcade.games.game;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.arena.Arena;
import me.arcade.games.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Shared lifecycle for every arcade game: join/leave, countdown, tick loop, elimination, ending.
 */
public abstract class Game {
    public enum State { WAITING, STARTING, ACTIVE, ENDING }

    private record Saved(Location loc, ItemStack[] contents, ItemStack[] armor, ItemStack[] extra, GameMode mode,
                         double health, int food, float sat, int level, float exp, boolean allowFlight, boolean flying) {}

    protected final ArcadePlugin plugin;
    protected final Arena arena;
    protected State state = State.WAITING;
    protected final Set<UUID> players = new LinkedHashSet<>();
    protected final Set<UUID> alive = new LinkedHashSet<>();
    protected long ticks;

    private final Map<UUID, Saved> saved = new HashMap<>();
    private BukkitTask countdownTask, tickTask;
    private int countdownLeft;
    private boolean cleaned;

    protected Game(ArcadePlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    // ------------------------------------------------------------ hooks for subclasses

    protected abstract void onStart();
    protected abstract void tick();
    protected abstract void checkEnd();
    protected void onEnd() {}
    protected void onLeave(Player p, boolean wasAlive) {}
    protected void onEliminated(Player p) {}

    /** Damage to a player taking part in an ACTIVE game. Default: no damage. */
    public void onDamage(Player victim, EntityDamageEvent e) { e.setCancelled(true); }
    public boolean ownsEntity(Entity e) { return false; }
    public void onEntityDamage(EntityDamageEvent e) {}
    public void onEntityDeath(EntityDeathEvent e) {}
    public boolean allowBreak(Player p, Block b) { return false; }
    public boolean allowPlace(Player p, Block b) { return false; }
    public boolean allowDrops() { return false; }
    public boolean allowPickup() { return false; }
    public boolean allowInventory() { return false; }
    public void onInteract(Player p, PlayerInteractEvent e) {}
    public void onFlight(Player p, PlayerToggleFlightEvent e) {}

    // ------------------------------------------------------------ accessors

    public State state() { return state; }
    public Arena arena() { return arena; }
    public int playerCount() { return players.size(); }
    public boolean has(Player p) { return players.contains(p.getUniqueId()); }
    public boolean joinable() {
        return (state == State.WAITING || state == State.STARTING) && players.size() < arena.max;
    }

    // ------------------------------------------------------------ join / leave

    /** @return null on success, otherwise an error message. */
    public String join(Player p) {
        if (!joinable()) return state == State.WAITING || state == State.STARTING ? "That arena is full." : "That game is already running.";
        PlayerInventory inv = p.getInventory();
        saved.put(p.getUniqueId(), new Saved(p.getLocation(), inv.getContents().clone(), inv.getArmorContents().clone(),
                inv.getExtraContents().clone(), p.getGameMode(), p.getHealth(), p.getFoodLevel(), p.getSaturation(),
                p.getLevel(), p.getExp(), p.getAllowFlight(), p.isFlying()));
        players.add(p.getUniqueId());
        plugin.manager().register(p.getUniqueId(), this);
        reset(p, GameMode.ADVENTURE);
        p.teleport(arena.waiting);
        p.getInventory().setItem(8, Msg.item(Material.RED_BED, 1, "&cLeave &7(Right Click)"));
        broadcast("&e" + p.getName() + " &7joined &8(&b" + players.size() + "&8/&b" + arena.max + "&8)");
        if (state == State.WAITING && players.size() >= arena.min) startCountdown();
        return null;
    }

    public void leave(Player p) {
        UUID id = p.getUniqueId();
        if (!players.remove(id)) return;
        boolean wasAlive = alive.remove(id);
        plugin.manager().unregister(id);
        onLeave(p, wasAlive);
        restore(p);
        broadcast("&e" + p.getName() + " &7left the game.");
        if (state == State.STARTING && players.size() < arena.min) {
            if (countdownTask != null) countdownTask.cancel();
            state = State.WAITING;
            for (Player o : allPlayers()) o.setLevel(0);
            broadcast("&cNot enough players - countdown cancelled.");
        } else if (state == State.ACTIVE) {
            if (players.isEmpty()) stopQuiet(); else checkEnd();
        } else if (state == State.ENDING && players.isEmpty()) {
            finish();
        }
    }

    // ------------------------------------------------------------ lifecycle

    private void startCountdown() {
        state = State.STARTING;
        countdownLeft = 15;
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdownLeft <= 0) {
                countdownTask.cancel();
                start();
                return;
            }
            if (countdownLeft <= 5 || countdownLeft % 10 == 0) {
                broadcast("&7Starting in &e" + countdownLeft + "&7s...");
                sound(Sound.BLOCK_NOTE_BLOCK_PLING);
            }
            for (Player p : allPlayers()) p.setLevel(countdownLeft);
            countdownLeft--;
        }, 0L, 20L);
    }

    public boolean forceStart() {
        if ((state == State.WAITING || state == State.STARTING) && !players.isEmpty()) {
            if (countdownTask != null) countdownTask.cancel();
            start();
            return true;
        }
        return false;
    }

    protected void start() {
        state = State.ACTIVE;
        cleaned = false;
        ticks = 0;
        alive.clear();
        alive.addAll(players);
        for (Player p : allPlayers()) {
            p.getInventory().clear();
            p.setLevel(0);
        }
        onStart();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks++;
            if (state == State.ACTIVE) tick();
        }, 1L, 1L);
    }

    protected void eliminate(Player p) {
        if (!alive.remove(p.getUniqueId())) return;
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(arena.spectatorSpawn());
        Msg.title(p, "&c&lELIMINATED", "&7You are now spectating");
        onEliminated(p);
        checkEnd();
    }

    protected void end(String... lines) {
        if (state != State.ACTIVE) return;
        state = State.ENDING;
        if (tickTask != null) tickTask.cancel();
        for (String s : lines) broadcast(s);
        sound(Sound.ENTITY_PLAYER_LEVELUP);
        cleanup();
        Bukkit.getScheduler().runTaskLater(plugin, this::finish, 100L);
    }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        onEnd();
    }

    private void finish() {
        if (state != State.ENDING) return;
        for (Player p : allPlayers()) {
            restore(p);
            plugin.sendToSpawn(p);
        }
        for (UUID id : players) plugin.manager().unregister(id);
        players.clear();
        alive.clear();
        state = State.WAITING;
        cleaned = false;
    }

    private void stopQuiet() {
        cancelTasks();
        cleanup();
        state = State.WAITING;
        cleaned = false;
        alive.clear();
    }

    private void cancelTasks() {
        if (countdownTask != null) countdownTask.cancel();
        if (tickTask != null) tickTask.cancel();
    }

    /** Hard stop (plugin disable / arena delete / /arcade forcestop). */
    public void shutdown() {
        cancelTasks();
        cleanup();
        for (Player p : allPlayers()) restore(p);
        for (UUID id : players) plugin.manager().unregister(id);
        players.clear();
        alive.clear();
        state = State.WAITING;
        cleaned = false;
    }

    // ------------------------------------------------------------ helpers

    protected void reset(Player p, GameMode mode) {
        p.getInventory().clear();
        for (PotionEffect pe : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(pe.getType());
        p.setGameMode(mode);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    private static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    private void restore(Player p) {
        Saved s = saved.remove(p.getUniqueId());
        for (PotionEffect pe : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(pe.getType());
        p.getInventory().clear();
        if (s == null) return;
        p.teleport(s.loc());
        p.setGameMode(s.mode());
        PlayerInventory inv = p.getInventory();
        inv.setContents(s.contents());
        inv.setArmorContents(s.armor());
        inv.setExtraContents(s.extra());
        p.setHealth(Math.min(s.health(), maxHealth(p)));
        p.setFoodLevel(s.food());
        p.setSaturation(s.sat());
        p.setLevel(s.level());
        p.setExp(s.exp());
        p.setAllowFlight(s.allowFlight());
        p.setFlying(s.flying() && s.allowFlight());
        p.setFireTicks(0);
    }

    protected List<Player> online(Collection<UUID> ids) {
        List<Player> l = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) l.add(p);
        }
        return l;
    }

    protected List<Player> allPlayers() { return online(players); }
    protected List<Player> alivePlayers() { return online(alive); }

    protected String nameOf(UUID id) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(id);
        return o.getName() == null ? "?" : o.getName();
    }

    protected void broadcast(String msg) {
        for (Player p : allPlayers()) Msg.send(p, msg);
    }

    protected void sound(Sound s) {
        for (Player p : allPlayers()) p.playSound(p.getLocation(), s, 1f, 1f);
    }

    protected Location spawnFor(int i) {
        List<Location> l = arena.spawnList();
        return l.get(Math.floorMod(i, l.size())).clone();
    }

    /** The player responsible for a damage event (resolves projectiles), or null. */
    protected Player attacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent by)) return null;
        Entity d = by.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        return d instanceof Player p ? p : null;
    }
}
