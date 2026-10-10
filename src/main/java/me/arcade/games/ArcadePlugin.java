package me.arcade.games;

import me.arcade.games.arena.ArenaManager;
import me.arcade.games.command.ArcadeCommand;
import me.arcade.games.game.GameListener;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ArcadePlugin extends JavaPlugin {
    private ArenaManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new ArenaManager(this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        ArcadeCommand cmd = new ArcadeCommand(this);
        PluginCommand pc = getCommand("arcade");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        // load next tick so every world (Multiverse etc.) is available
        getServer().getScheduler().runTask(this, manager::load);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public ArenaManager manager() {
        return manager;
    }

    /** Sends a player to the spawn world after a match ends (see config.yml). */
    public void sendToSpawn(Player p) {
        String cmd = getConfig().getString("spawn-command", "");
        String worldName = getConfig().getString("spawn-world", "");
        String name = p.getName();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (cmd != null && !cmd.trim().isEmpty()) {
                String c = cmd.trim();
                if (c.startsWith("/")) c = c.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", name));
                return;
            }
            World target = null;
            if (worldName != null && !worldName.trim().isEmpty()) {
                target = Bukkit.getWorld(worldName.trim());
                if (target == null) getLogger().warning("spawn-world '" + worldName + "' not found - using main world.");
            }
            if (target == null) {
                List<World> worlds = Bukkit.getWorlds();
                if (!worlds.isEmpty()) target = worlds.get(0);
            }
            if (target != null) p.teleport(target.getSpawnLocation());
        }, 2L);
    }
}
