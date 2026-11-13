package io.github.nateriver31.reliquias;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class ScrollItems {
    enum Scroll { FLOWER, CREEPER }
    private final NamespacedKey key;

    ScrollItems(Plugin plugin) { key = new NamespacedKey(plugin, "pergaminho"); }

    ItemStack create(Scroll scroll, int amount) {
        var item = new ItemStack(scroll == Scroll.FLOWER ? Material.FLOWER_BANNER_PATTERN : Material.CREEPER_BANNER_PATTERN, amount);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, scroll.name());
        meta.displayName(Component.text(scroll == Scroll.FLOWER ? "Pergaminho da Flor" : "Pergaminho do Creeper",
                scroll == Scroll.FLOWER ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(scroll == Scroll.FLOWER ? "Acesso ao teto do Nether • uso único" : "Expansão do teto do Nether • reutilizável", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(scroll == Scroll.FLOWER ? "Área: 7×7 chunks • aguarde a preparação" : "Área: 5×5 chunks • Y ≥ 221 • 30 segundos", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    Scroll identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if ("FLOWER".equals(id) && item.getType() == Material.FLOWER_BANNER_PATTERN) return Scroll.FLOWER;
        if ("CREEPER".equals(id) && item.getType() == Material.CREEPER_BANNER_PATTERN) return Scroll.CREEPER;
        return null;
    }
}
