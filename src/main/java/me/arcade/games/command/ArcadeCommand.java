package me.arcade.games.command;

import me.arcade.games.ArcadePlugin;
import me.arcade.games.arena.Arena;
import me.arcade.games.arena.ArenaManager;
import me.arcade.games.arena.GameType;
import me.arcade.games.game.Game;
import me.arcade.games.util.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class ArcadeCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "list", "help");
    private static final List<String> ADMIN_SUBS = List.of("create", "delete", "setwaiting", "setspawn", "setwither",
            "pos1", "pos2", "setplayers", "info", "forcestart", "forcestop");
    private static final List<String> GAME_NAMES = List.of("prophunt", "blockparty", "tntrun", "partygames", "miniwalls");

    private final ArenaManager m;

    public ArcadeCommand(ArcadePlugin plugin) {
        this.m = plugin.manager();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "join" -> join(s, a);
            case "leave" -> leave(s);
            case "list" -> list(s);
            case "help" -> help(s);
            default -> admin(s, a);
        }
        return true;
    }

    private void help(CommandSender s) {
        Msg.plain(s, "&8&m      &r &bArcade Games &8&m      ");
        Msg.plain(s, "&e/arcade join [arena|game] &7- join an arena or the best arena of a game");
        Msg.plain(s, "&e/arcade leave &7- leave your game");
        Msg.plain(s, "&e/arcade list &7- list arenas");
        Msg.plain(s, "&7Games: &fprophunt, blockparty, tntrun, partygames, miniwalls");
        if (s.hasPermission("arcade.admin")) {
            Msg.plain(s, "&6Admin: &ecreate <name> <game>&7, &edelete <name>&7, &esetwaiting <name>");
            Msg.plain(s, "&esetspawn <name> [n]&7, &esetwither <name> <1-4>&7, &epos1/pos2 <name>");
            Msg.plain(s, "&esetplayers <name> <min> <max>&7, &einfo <name>&7, &eforcestart/forcestop <name>");
        }
    }

    // ------------------------------------------------------------ player commands

    private void join(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "&cOnly players can do that.");
            return;
        }
        if (m.gameOf(p) != null) {
            Msg.send(p, "&cYou are already in a game. Use &e/arcade leave&c.");
            return;
        }
        Arena target = null;
        if (a.length >= 2) {
            target = m.arena(a[1]);
            if (target == null) {
                GameType t = GameType.parse(a[1]);
                if (t == null) {
                    Msg.send(p, "&cUnknown arena or game &e" + a[1]);
                    return;
                }
                int best = -1;
                for (Arena ar : m.arenas()) {
                    if (ar.type != t || ar.validate() != null) continue;
                    Game g = m.game(ar);
                    if (g.joinable() && g.playerCount() > best) {
                        best = g.playerCount();
                        target = ar;
                    }
                }
                if (target == null) {
                    Msg.send(p, "&cNo " + t.display + " arena is available right now.");
                    return;
                }
            }
        } else {
            int best = -1;
            for (Arena ar : m.arenas()) {
                if (ar.validate() != null) continue;
                Game g = m.game(ar);
                if (g.joinable() && g.playerCount() > best) {
                    best = g.playerCount();
                    target = ar;
                }
            }
            if (target == null) {
                Msg.send(p, "&cNo arena is available right now.");
                return;
            }
        }
        String problem = target.validate();
        if (problem != null) {
            Msg.send(p, "&cThat arena isn't ready: " + problem);
            return;
        }
        String err = m.game(target).join(p);
        if (err != null) Msg.send(p, "&c" + err);
    }

    private void leave(CommandSender s) {
        if (!(s instanceof Player p)) return;
        Game g = m.gameOf(p);
        if (g == null) {
            Msg.send(p, "&cYou are not in a game.");
            return;
        }
        g.leave(p);
        Msg.send(p, "You left the game.");
    }

    private void list(CommandSender s) {
        if (m.arenas().isEmpty()) {
            Msg.send(s, "No arenas yet.");
            return;
        }
        Msg.plain(s, "&8&m      &r &bArenas &8&m      ");
        for (Arena ar : m.arenas()) {
            String problem = ar.validate();
            if (problem != null) {
                Msg.plain(s, "&e" + ar.name + " &8- &7" + ar.type.display + " &c(not ready)");
            } else {
                Game g = m.game(ar);
                Msg.plain(s, "&e" + ar.name + " &8- &7" + ar.type.display + " &8- &f" + g.state().name().toLowerCase()
                        + " &8(&b" + g.playerCount() + "&8/&b" + ar.max + "&8)");
            }
        }
    }

    // ------------------------------------------------------------ admin commands

    private Arena arena(CommandSender s, String[] a, int i) {
        if (a.length <= i) {
            Msg.send(s, "&cMissing arena name.");
            return null;
        }
        Arena ar = m.arena(a[i]);
        if (ar == null) Msg.send(s, "&cUnknown arena &e" + a[i]);
        return ar;
    }

    private void admin(CommandSender s, String[] a) {
        String sub = a[0].toLowerCase();
        if (!ADMIN_SUBS.contains(sub)) {
            help(s);
            return;
        }
        if (!s.hasPermission("arcade.admin")) {
            Msg.send(s, "&cYou don't have permission.");
            return;
        }
        Player p = s instanceof Player pl ? pl : null;
        switch (sub) {
            case "create" -> {
                if (a.length < 3) {
                    Msg.send(s, "&cUsage: /arcade create <name> <prophunt|blockparty|tntrun|partygames|miniwalls>");
                    return;
                }
                if (!a[1].matches("[A-Za-z0-9_-]+")) {
                    Msg.send(s, "&cArena names may only contain letters, numbers, - and _");
                    return;
                }
                GameType t = GameType.parse(a[2]);
                if (t == null) {
                    Msg.send(s, "&cUnknown game. Choose: prophunt, blockparty, tntrun, partygames, miniwalls");
                    return;
                }
                if (m.create(a[1], t) == null) {
                    Msg.send(s, "&cAn arena with that name already exists.");
                    return;
                }
                m.save();
                Msg.send(s, "&aCreated &e" + a[1] + " &a(" + t.display + "). " + setupHint(t));
            }
            case "delete" -> {
                if (a.length < 2 || !m.delete(a[1])) {
                    Msg.send(s, "&cUnknown arena.");
                    return;
                }
                m.save();
                Msg.send(s, "&aArena deleted.");
            }
            case "setwaiting", "setspawn", "setwither", "pos1", "pos2" -> {
                if (p == null) {
                    Msg.send(s, "&cOnly players can do that.");
                    return;
                }
                Arena ar = arena(s, a, 1);
                if (ar == null) return;
                setLocation(p, ar, sub, a);
                m.save();
            }
            case "setplayers" -> {
                Arena ar = arena(s, a, 1);
                if (ar == null) return;
                try {
                    ar.min = Math.max(1, Integer.parseInt(a[2]));
                    ar.max = Math.max(ar.min, Integer.parseInt(a[3]));
                    m.save();
                    Msg.send(s, "&aPlayers set to &e" + ar.min + "-" + ar.max + "&a.");
                } catch (RuntimeException ex) {
                    Msg.send(s, "&cUsage: /arcade setplayers <name> <min> <max>");
                }
            }
            case "info" -> {
                Arena ar = arena(s, a, 1);
                if (ar == null) return;
                String problem = ar.validate();
                Msg.plain(s, "&e" + ar.name + " &7(" + ar.type.display + ") players &b" + ar.min + "-" + ar.max);
                Msg.plain(s, "&7spawns: &f" + ar.spawns.keySet() + " &7withers: &f" + ar.withers.keySet()
                        + " &7region: &f" + (ar.hasRegion() ? "set" : "not set"));
                Msg.plain(s, problem == null ? "&aReady to play." : "&cNot ready: " + problem);
            }
            case "forcestart" -> {
                Arena ar = arena(s, a, 1);
                if (ar == null) return;
                if (ar.validate() != null) {
                    Msg.send(s, "&cArena isn't ready: " + ar.validate());
                    return;
                }
                Msg.send(s, m.game(ar).forceStart() ? "&aStarted." : "&cCan't start (no players or already running).");
            }
            case "forcestop" -> {
                Arena ar = arena(s, a, 1);
                if (ar == null) return;
                m.game(ar).shutdown();
                Msg.send(s, "&aGame stopped.");
            }
            default -> help(s);
        }
    }

    private void setLocation(Player p, Arena ar, String sub, String[] a) {
        Location here = p.getLocation();
        switch (sub) {
            case "setwaiting" -> {
                ar.waiting = here;
                Msg.send(p, "&aWaiting lobby set.");
            }
            case "setspawn" -> {
                int n = ar.spawns.size() + 1;
                if (a.length >= 3) {
                    try {
                        n = Integer.parseInt(a[2]);
                    } catch (NumberFormatException ex) {
                        Msg.send(p, "&cSpawn number must be a number.");
                        return;
                    }
                }
                ar.spawns.put(n, here);
                Msg.send(p, "&aSpawn &e#" + n + " &aset." + (ar.type == GameType.MINI_WALLS ? " &7(= team " + n + ")" : ""));
            }
            case "setwither" -> {
                if (a.length < 3) {
                    Msg.send(p, "&cUsage: /arcade setwither <arena> <team 1-4>");
                    return;
                }
                try {
                    int n = Integer.parseInt(a[2]);
                    ar.withers.put(n, here);
                    Msg.send(p, "&aWither for team &e" + n + " &aset.");
                } catch (NumberFormatException ex) {
                    Msg.send(p, "&cTeam must be a number.");
                }
            }
            case "pos1", "pos2" -> {
                // the block the player is standing ON
                Location b = here.clone().subtract(0, 1, 0).getBlock().getLocation();
                if (sub.equals("pos1")) ar.pos1 = b;
                else ar.pos2 = b;
                Msg.send(p, "&a" + sub + " set to &e" + b.getBlockX() + ", " + b.getBlockY() + ", " + b.getBlockZ());
            }
            default -> {}
        }
    }

    private String setupHint(GameType t) {
        return switch (t) {
            case TNT_RUN -> "&7Next: setwaiting, setspawn, pos1/pos2 (bottom + top corner of the whole floor stack).";
            case BLOCK_PARTY -> "&7Next: setwaiting, setspawn, pos1/pos2 (two corners of the flat dance floor).";
            case PARTY_GAMES -> "&7Next: setwaiting, setspawn, pos1/pos2 (two corners of one flat platform with void/drop around it).";
            case PROP_HUNT -> "&7Next: setwaiting, setspawn 1 (hiders) and setspawn 2 (seekers).";
            case MINI_WALLS -> "&7Next: setwaiting, then setspawn 1-4 and setwither 1-4 (one per team).";
        };
    }

    // ------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (s.hasPermission("arcade.admin")) out.addAll(ADMIN_SUBS);
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase();
            if (sub.equals("join")) {
                out.addAll(GAME_NAMES);
                for (Arena ar : m.arenas()) out.add(ar.name);
            } else if (sub.equals("create")) {
                return List.of();
            } else if (ADMIN_SUBS.contains(sub)) {
                for (Arena ar : m.arenas()) out.add(ar.name);
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("create")) {
            out.addAll(GAME_NAMES);
        }
        String prefix = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(prefix));
        return out;
    }
}
