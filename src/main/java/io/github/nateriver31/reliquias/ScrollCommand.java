package io.github.nateriver31.reliquias;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class ScrollCommand implements TabExecutor {
    private final Plugin plugin;
    private final ScrollItems items;
    ScrollCommand(Plugin plugin, ScrollItems items) { this.plugin = plugin; this.items = items; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("reliquias.admin")) return true;
        if (args.length < 3 || args.length > 4 || !args[0].equalsIgnoreCase("dar")) return false;
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage("Jogador não encontrado ou desconectado."); return true; }
        ScrollItems.Scroll scroll;
        if (args[2].equalsIgnoreCase("flor")) scroll = ScrollItems.Scroll.FLOWER;
        else if (args[2].equalsIgnoreCase("creeper")) scroll = ScrollItems.Scroll.CREEPER;
        else return false;
        int amount;
        try { amount = args.length == 4 ? Integer.parseInt(args[3]) : 1; }
        catch (NumberFormatException e) { return false; }
        if (amount < 1 || amount > 64) { sender.sendMessage("Escolha uma quantidade entre 1 e 64."); return true; }
        // Never drop administrative items into the world or discard inventory overflow.
        var item = items.create(scroll, amount);
        int room = 0;
        for (var slot : target.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) room += item.getMaxStackSize();
            else if (slot.isSimilar(item)) room += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
        }
        if (room < amount) { sender.sendMessage("O inventário do jogador não tem espaço suficiente."); return true; }
        target.getInventory().addItem(item);
        sender.sendMessage(Component.text("Entregue: " + amount + " pergaminho(s) para " + target.getName() + "."));
        return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("reliquias.admin")) return List.of();
        List<String> choices = switch (args.length) {
            case 1 -> List.of("dar");
            case 2 -> plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
            case 3 -> List.of("flor", "creeper");
            case 4 -> List.of("1", "16", "64");
            default -> List.of();
        };
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
