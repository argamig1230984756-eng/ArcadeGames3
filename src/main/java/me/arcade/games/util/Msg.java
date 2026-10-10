package me.arcade.games.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class Msg {
    public static final String PREFIX = "&8[&bArcade&8] &7";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Msg() {}

    public static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(c(PREFIX + s));
    }

    public static void plain(CommandSender to, String s) {
        to.sendMessage(c(s));
    }

    public static void title(Player p, String title, String sub) {
        p.showTitle(Title.title(c(title), c(sub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(300))));
    }

    public static void bar(Player p, String s) {
        p.sendActionBar(c(s));
    }

    public static ItemStack item(Material m, int amount, String name, String... lore) {
        ItemStack is = new ItemStack(m, amount);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(c(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        is.setItemMeta(meta);
        return is;
    }
}
