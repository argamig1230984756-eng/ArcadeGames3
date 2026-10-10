package me.arcade.games.game;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.arena.ArenaManager;
import me.arcade.games.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {
    private final ArenaManager m;

    public GameListener(ArcadePlugin plugin) {
        this.m = plugin.manager();
    }

    private Game game(Player p) {
        return m.gameOf(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g = game(e.getPlayer());
        if (g != null) g.leave(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (victim instanceof Player p) {
            Game g = game(p);
            if (g != null) {
                if (g.state() != Game.State.ACTIVE) e.setCancelled(true);
                else g.onDamage(p, e);
                return;
            }
        } else {
            for (Game g : m.all()) {
                if (g.ownsEntity(victim)) {
                    g.onEntityDamage(e);
                    return;
                }
            }
        }
        // players in a game must not hurt anything outside of it
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
            if (d instanceof Player dp && game(dp) != null) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Wither)) return;
        for (Game g : m.all()) {
            if (g.ownsEntity(e.getEntity())) {
                g.onEntityDeath(e);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Game g = game(e.getPlayer());
        if (g == null) return;
        if (g.state() != Game.State.ACTIVE || !g.allowBreak(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        else if (!g.allowDrops()) e.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Game g = game(e.getPlayer());
        if (g == null) return;
        if (g.state() != Game.State.ACTIVE || !g.allowPlace(e.getPlayer(), e.getBlockPlaced())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Game g = game(p);
        if (g == null) return;
        ItemStack it = e.getItem();
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (right && it != null && it.getType() == Material.RED_BED && g.state() != Game.State.ACTIVE) {
            e.setCancelled(true);
            g.leave(p);
            Msg.send(p, "You left the game.");
            return;
        }
        if (g.state() == Game.State.ACTIVE) g.onInteract(p, e);
    }

    @EventHandler
    public void onFlight(PlayerToggleFlightEvent e) {
        Game g = game(e.getPlayer());
        if (g != null && g.state() == Game.State.ACTIVE) g.onFlight(e.getPlayer(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && game(p) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (game(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            Game g = game(p);
            if (g != null && !g.allowPickup()) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            Game g = game(p);
            if (g != null && !g.allowInventory()) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (game(p) == null || p.hasPermission("arcade.admin")) return;
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/arcade") || msg.equals("/ag") || msg.startsWith("/ag ")) return;
        e.setCancelled(true);
        Msg.send(p, "&cYou can only use &e/arcade leave &cwhile in a game.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity().getScoreboardTags().contains("ag_tnt")) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallingLand(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock fb && fb.getScoreboardTags().contains("ag_fall")) {
            e.setCancelled(true);
            fb.remove();
        }
    }
}
